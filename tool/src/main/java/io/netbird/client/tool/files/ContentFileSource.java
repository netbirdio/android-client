package io.netbird.client.tool.files;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import io.netbird.gomobile.android.FileSource;
import io.netbird.gomobile.android.SourceStream;

/**
 * Feeds one shared file to the Go sender, which pulls bytes rather than being
 * handed a path: Android hands out Uris the Go layer cannot open itself.
 * <p>
 * The content is copied into the app's own cache first. A share grants read
 * access only for the lifetime of the receiving activity, while an upload
 * outlives it by design — reading the Uri lazily would fail with a permission
 * denial as soon as the share screen closed. The copy is deleted once the
 * transfer stops reading it.
 */
public class ContentFileSource implements FileSource {

    private static final String LOGTAG = "ContentFileSource";
    private static final String STAGING_DIR = "filedrop-outgoing";
    // Caps one JNI hop; the Go side asks for whatever its own buffer holds.
    private static final int CHUNK_LIMIT = 256 * 1024;
    // Providers are allowed to omit SIZE; -1 marks "the provider did not say".
    private static final long UNKNOWN_SIZE = -1;
    private static final byte[] EMPTY = new byte[0];

    private final File staged;
    private final String name;
    private final long size;
    private final String contentType;

    private ContentFileSource(File staged, String name, long size, String contentType) {
        this.staged = staged;
        this.name = name;
        this.size = size;
        this.contentType = contentType;
    }

    /**
     * Copies a shared Uri into app storage and wraps it. Returns null when the
     * Uri cannot be read, which is the normal outcome for a revoked or stale
     * share grant. Must be called while the grant is still live, so on the
     * receiving activity's own thread of work rather than after it finishes.
     */
    @Nullable
    public static ContentFileSource of(@NonNull Context context, @NonNull Uri uri) {
        Context app = context.getApplicationContext();
        ContentResolver resolver = app.getContentResolver();

        String name = displayName(resolver, uri);
        if (name == null) {
            Log.w(LOGTAG, "no display name for " + uri);
            return null;
        }

        File staged = stage(app, resolver, uri);
        if (staged == null) {
            return null;
        }

        String contentType = resolver.getType(uri);
        return new ContentFileSource(staged, name, staged.length(),
                contentType == null ? "" : contentType);
    }

    /**
     * Name, size and MIME type of a shared Uri, read without copying anything.
     * Used to describe what is about to be sent before a target is picked.
     * Returns null when the Uri cannot be resolved.
     */
    @Nullable
    public static Details describe(@NonNull Context context, @NonNull Uri uri) {
        ContentResolver resolver = context.getApplicationContext().getContentResolver();

        String name = null;
        long size = UNKNOWN_SIZE;
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cursor.getString(nameIndex);
                }
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.w(LOGTAG, "cannot query " + uri, e);
        }

        if (name == null) {
            name = uri.getLastPathSegment();
        }
        if (name == null) {
            return null;
        }

        String type = resolver.getType(uri);
        return new Details(name, size, type == null ? "" : type);
    }

    /** What a shared Uri is, before it is staged. */
    public static final class Details {
        private final String name;
        private final long size;
        private final String contentType;

        Details(String name, long size, String contentType) {
            this.name = name;
            this.size = size;
            this.contentType = contentType;
        }

        public String name() { return name; }

        /** Byte count, or -1 when the provider does not report one. */
        public long size() { return size; }

        public String contentType() { return contentType; }
    }

    /** Removes every staged copy left behind by a killed process. */
    public static void clearStaging(@NonNull Context context) {
        File dir = stagingDir(context.getApplicationContext());
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (!f.delete()) {
                Log.w(LOGTAG, "cannot delete stale staging file " + f);
            }
        }
    }

    public String name() {
        return name;
    }

    public long size() {
        return size;
    }

    public String contentType() {
        return contentType;
    }

    @Override
    public SourceStream open(long offset) throws Exception {
        InputStream stream = new FileInputStream(staged);
        try {
            skipFully(stream, offset);
        } catch (Exception e) {
            stream.close();
            throw e;
        }
        return new StagedStream(stream);
    }

    /** Drops the staged copy; call once the transfer no longer needs it. */
    public void release() {
        if (staged.exists() && !staged.delete()) {
            Log.w(LOGTAG, "cannot delete staging file " + staged);
        }
    }

    @Nullable
    private static String displayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            Log.w(LOGTAG, "cannot query " + uri, e);
        }
        return name != null ? name : uri.getLastPathSegment();
    }

    @Nullable
    private static File stage(Context app, ContentResolver resolver, Uri uri) {
        File dir = stagingDir(app);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(LOGTAG, "cannot create staging dir " + dir);
            return null;
        }

        File target = new File(dir, UUID.randomUUID().toString());
        try (InputStream in = resolver.openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("cannot open " + uri);
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            Log.w(LOGTAG, "cannot stage " + uri, e);
            if (target.exists() && !target.delete()) {
                Log.w(LOGTAG, "cannot delete partial staging file " + target);
            }
            return null;
        }
        return target;
    }

    private static File stagingDir(Context app) {
        return new File(app.getCacheDir(), STAGING_DIR);
    }

    private static void skipFully(InputStream stream, long offset) throws IOException {
        long remaining = offset;
        while (remaining > 0) {
            long skipped = stream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (stream.read() < 0) {
                throw new IOException("stream ended " + remaining + " bytes before the requested offset");
            }
            remaining--;
        }
    }

    /**
     * Adapts InputStream to the Go-facing stream. Bytes travel as the return
     * value because that is the only direction gomobile copies them in; an
     * empty array marks end of stream.
     */
    private static final class StagedStream implements SourceStream {

        private final InputStream stream;

        StagedStream(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public byte[] nextChunk(long max) throws Exception {
            int size = (int) Math.min(Math.max(max, 1), CHUNK_LIMIT);
            byte[] buffer = new byte[size];

            int read = stream.read(buffer);
            if (read <= 0) {
                return EMPTY;
            }
            if (read == size) {
                return buffer;
            }
            byte[] exact = new byte[read];
            System.arraycopy(buffer, 0, exact, 0, read);
            return exact;
        }

        @Override
        public void close() throws Exception {
            stream.close();
        }
    }
}
