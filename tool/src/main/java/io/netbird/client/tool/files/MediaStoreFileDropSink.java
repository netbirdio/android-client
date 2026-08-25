package io.netbird.client.tool.files;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netbird.gomobile.android.FileDropSink;
import io.netbird.gomobile.android.FileDropWriter;

/**
 * Receives file drop payloads straight into the shared Downloads collection, so
 * a delivered file is where the user looks for it rather than inside app
 * storage only this app can read.
 * <p>
 * Nothing is staged twice: bytes go into their final entry as they arrive, and
 * the entry stays invisible to other apps until the transfer completes, which
 * is what {@code IS_PENDING} buys. A cancelled or expired transfer is deleted
 * rather than swept up later, so an abandoned partial never surfaces in the
 * gallery and no cleanup has to guess which files are the user's.
 * <p>
 * Below Android 10 there is no pending flag and no Downloads collection to
 * insert into, and the public Downloads folder there needs a storage permission
 * this app does not ask for. The payload lands in the app's own external files
 * directory instead, staged under a hidden name and renamed on delivery, which
 * keeps the same "invisible until complete" behaviour without a prompt.
 */
public class MediaStoreFileDropSink implements FileDropSink {

    private static final String LOGTAG = "FileDropSink";
    /** Subdirectory of Downloads received files land in. */
    private static final String RELATIVE_DIR = Environment.DIRECTORY_DOWNLOADS + "/NetBird";
    private static final String FALLBACK_MIME = "application/octet-stream";
    /** Marks an incomplete legacy payload, and hides it from the media scanner. */
    private static final String LEGACY_PARTIAL_PREFIX = ".nb-partial-";

    private static final boolean HAS_PENDING_MEDIA = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

    private final Context context;
    // One entry per payload being received, keyed by offer and index. Holds the
    // destination each write reopens and each delivery publishes.
    private final Map<String, Payload> payloads = new ConcurrentHashMap<>();

    public MediaStoreFileDropSink(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** One payload's destination, from the first write to delivery. */
    private static final class Payload {
        private final String offerID;
        private final long index;
        private final String name;
        // Set on the modern path: the pending MediaStore entry bytes go into.
        @Nullable
        private final Uri uri;
        // Set on the legacy path: the hidden file bytes go into.
        @Nullable
        private final File file;

        Payload(String offerID, long index, String name, @Nullable Uri uri, @Nullable File file) {
            this.offerID = offerID;
            this.index = index;
            this.name = name;
            this.uri = uri;
            this.file = file;
        }
    }

    @Override
    public String destinationLabel() {
        if (HAS_PENDING_MEDIA) {
            return RELATIVE_DIR;
        }
        return legacyDir().getAbsolutePath();
    }

    @Override
    public void prepare(String offerID) {
        // Destinations are created lazily, by the first write of each payload:
        // an offer may never be accepted, and an entry created here would have
        // to be cleaned up for every offer that is not.
    }

    @Override
    public long received(String offerID, long index) {
        Payload payload = payloads.get(key(offerID, index));
        if (payload == null) {
            return 0;
        }
        if (payload.file != null) {
            return payload.file.length();
        }
        return length(payload.uri);
    }

    @Override
    public FileDropWriter openWriter(String offerID, long index, String name, long offset, long size)
            throws Exception {
        String key = key(offerID, index);
        Payload payload = payloads.get(key);
        if (payload == null) {
            payload = create(offerID, index, name);
            payloads.put(key, payload);
        }

        if (payload.file != null) {
            return new LegacyWriter(payload.file, offset);
        }
        return new PendingWriter(context.getContentResolver(), payload.uri, offset);
    }

    /**
     * Publishes every payload of one offer. A failure part-way leaves nothing
     * half-delivered: what already went out is withdrawn again, so the transfer
     * fails whole rather than dropping some of its files on the user.
     */
    @Override
    public String deliver(String offerID) throws Exception {
        List<Payload> ordered = payloadsOf(offerID);
        List<Payload> published = new ArrayList<>(ordered.size());
        List<String> delivered = new ArrayList<>(ordered.size());

        try {
            for (Payload payload : ordered) {
                delivered.add(publish(payload));
                published.add(payload);
            }
        } catch (Exception e) {
            for (Payload payload : published) {
                discard(payload);
            }
            throw e;
        } finally {
            for (Payload payload : ordered) {
                payloads.remove(key(payload.offerID, payload.index));
            }
        }
        return TextUtils.join("\n", delivered);
    }

    @Override
    public void remove(String offerID) {
        for (Payload payload : payloadsOf(offerID)) {
            discard(payload);
            payloads.remove(key(payload.offerID, payload.index));
        }
    }

    @Override
    public void cleanup(long maxAgeSeconds) {
        // A destination is created only for a payload being received and is
        // deleted the moment that stops, so there is nothing here to sweep. The
        // process dying mid-transfer is the exception, handled by clearPartials.
    }

    /**
     * Deletes destinations abandoned by a killed process. Pending MediaStore
     * entries this app owns and hidden legacy files are both invisible to the
     * user, so a leftover is invisible clutter rather than a stray file, but it
     * still occupies space until this runs.
     */
    public void clearPartials() {
        if (HAS_PENDING_MEDIA) {
            clearPendingEntries();
            return;
        }
        clearLegacyPartials();
    }

    private Payload create(String offerID, long index, String name) throws IOException {
        String safeName = sanitize(name, index);
        if (!HAS_PENDING_MEDIA) {
            return new Payload(offerID, index, safeName, null, legacyPartial(offerID, index));
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(safeName));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(downloads(), values);
        if (uri == null) {
            throw new IOException("cannot create a Downloads entry for " + safeName);
        }
        return new Payload(offerID, index, safeName, uri, null);
    }

    /** Makes a payload visible under its announced name and returns where it landed. */
    private String publish(Payload payload) throws IOException {
        if (payload.file != null) {
            return publishLegacy(payload);
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        if (context.getContentResolver().update(payload.uri, values, null, null) == 0) {
            throw new IOException("cannot publish " + payload.uri);
        }
        return payload.uri.toString();
    }

    private String publishLegacy(Payload payload) throws IOException {
        File dir = legacyDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }

        File target = freeName(dir, payload.name);
        if (!payload.file.renameTo(target)) {
            throw new IOException("cannot move " + payload.file + " to " + target);
        }
        return target.getAbsolutePath();
    }

    private void discard(Payload payload) {
        if (payload.file != null) {
            if (payload.file.exists() && !payload.file.delete()) {
                Log.w(LOGTAG, "cannot delete partial " + payload.file);
            }
            return;
        }
        try {
            context.getContentResolver().delete(payload.uri, null, null);
        } catch (Exception e) {
            Log.w(LOGTAG, "cannot delete pending entry " + payload.uri, e);
        }
    }

    /** One offer's payloads, in the order the offer announced them. */
    private List<Payload> payloadsOf(String offerID) {
        List<Payload> ordered = new ArrayList<>();
        for (Payload payload : payloads.values()) {
            if (payload.offerID.equals(offerID)) {
                ordered.add(payload);
            }
        }
        ordered.sort(Comparator.comparingLong(p -> p.index));
        return ordered;
    }

    private long length(Uri uri) {
        try (Cursor cursor = context.getContentResolver()
                .query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.w(LOGTAG, "cannot read the size of " + uri, e);
        }
        return 0;
    }

    private void clearPendingEntries() {
        String selection = MediaStore.MediaColumns.IS_PENDING + " = 1";
        try (Cursor cursor = context.getContentResolver().query(downloads(),
                new String[]{MediaStore.MediaColumns._ID}, selection, null, null)) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                Uri uri = Uri.withAppendedPath(downloads(), String.valueOf(cursor.getLong(0)));
                try {
                    context.getContentResolver().delete(uri, null, null);
                } catch (Exception e) {
                    Log.w(LOGTAG, "cannot delete stale pending entry " + uri, e);
                }
            }
        } catch (Exception e) {
            Log.w(LOGTAG, "cannot list stale pending entries", e);
        }
    }

    private void clearLegacyPartials() {
        File[] files = legacyPartialDir().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().startsWith(LEGACY_PARTIAL_PREFIX) && !file.delete()) {
                Log.w(LOGTAG, "cannot delete stale partial " + file);
            }
        }
    }

    private File legacyPartial(String offerID, long index) throws IOException {
        File dir = legacyPartialDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        return new File(dir, LEGACY_PARTIAL_PREFIX + offerID + "-" + index);
    }

    private File legacyPartialDir() {
        return new File(context.getFilesDir(), "filedrop-incoming");
    }

    /**
     * Delivery directory on the legacy path. The app-specific external
     * directory needs no permission; a null volume means external storage is
     * unavailable, and internal storage is the last resort.
     */
    private File legacyDir() {
        File external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (external != null) {
            return external;
        }
        return new File(context.getFilesDir(), Environment.DIRECTORY_DOWNLOADS);
    }

    private static Uri downloads() {
        return MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    }

    private static String key(String offerID, long index) {
        return offerID + ":" + index;
    }

    /**
     * Reduces an announced name to a bare filename. A sender is not trusted to
     * stay inside the destination: path separators would otherwise let an offer
     * name its way out of it.
     */
    private static String sanitize(String name, long index) {
        String bare = name == null ? "" : name.replace('\\', '/');
        int slash = bare.lastIndexOf('/');
        if (slash >= 0) {
            bare = bare.substring(slash + 1);
        }
        bare = bare.trim();
        if (bare.isEmpty() || bare.equals(".") || bare.equals("..")) {
            return "file-" + index;
        }
        return bare;
    }

    private static String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return FALLBACK_MIME;
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.US);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? FALLBACK_MIME : mime;
    }

    /** First unused name in dir, counting up the way a browser download does. */
    private static File freeName(File dir, String name) throws IOException {
        File candidate = new File(dir, name);
        if (!candidate.exists()) {
            return candidate;
        }

        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";

        for (int attempt = 1; attempt < 1000; attempt++) {
            candidate = new File(dir, stem + " (" + attempt + ")" + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        throw new IOException("no free name for " + name + " in " + dir);
    }

    /**
     * Writes into a pending MediaStore entry through a file descriptor rather
     * than a plain output stream: a resumed transfer has to position itself at
     * the offset the sender confirmed, and truncate whatever a previous attempt
     * left past it. Append mode cannot do the second part, and providers are
     * not required to honour it at all.
     */
    private static final class PendingWriter implements FileDropWriter {

        private final ParcelFileDescriptor descriptor;
        private final FileOutputStream stream;
        private long written;

        PendingWriter(ContentResolver resolver, Uri uri, long offset) throws IOException {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "rw");
            if (pfd == null) {
                throw new IOException("cannot open " + uri);
            }

            FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor());
            try {
                out.getChannel().truncate(offset);
                out.getChannel().position(offset);
            } catch (IOException e) {
                closeQuietly(out, pfd);
                throw e;
            }

            this.descriptor = pfd;
            this.stream = out;
            this.written = offset;
        }

        @Override
        public void writeChunk(byte[] p) throws Exception {
            stream.write(p);
            written += p.length;
        }

        @Override
        public long written() {
            return written;
        }

        @Override
        public void close() throws Exception {
            try {
                stream.flush();
                stream.getFD().sync();
            } finally {
                stream.close();
                descriptor.close();
            }
        }

        private static void closeQuietly(FileOutputStream stream, ParcelFileDescriptor pfd) {
            try {
                stream.close();
            } catch (IOException e) {
                Log.w(LOGTAG, "cannot close a rejected destination", e);
            }
            try {
                pfd.close();
            } catch (IOException e) {
                Log.w(LOGTAG, "cannot close a rejected descriptor", e);
            }
        }
    }

    /** Writes into a hidden file, for Android versions without a pending flag. */
    private static final class LegacyWriter implements FileDropWriter {

        private final FileOutputStream stream;
        private long written;

        LegacyWriter(File file, long offset) throws IOException {
            FileOutputStream out = new FileOutputStream(file, true);
            try {
                out.getChannel().truncate(offset);
                out.getChannel().position(offset);
            } catch (IOException e) {
                out.close();
                throw e;
            }
            this.stream = out;
            this.written = offset;
        }

        @Override
        public void writeChunk(byte[] p) throws Exception {
            stream.write(p);
            written += p.length;
        }

        @Override
        public long written() {
            return written;
        }

        @Override
        public void close() throws Exception {
            try {
                stream.flush();
                stream.getFD().sync();
            } finally {
                stream.close();
            }
        }
    }
}
