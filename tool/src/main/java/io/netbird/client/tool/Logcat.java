package io.netbird.client.tool;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

public class Logcat   {
    private final Context context;
    private final File workspace;


    public Logcat(Context context) {
        this.context = context;
        workspace = new File(context.getCacheDir(), "logcat");
    }
    public void dump() throws IOException {
        eraseWorkspace();
        File file = dumpFile();
        dumpLogcatToFile(file);
        share(file);
    }

    private void dumpLogcatToFile(File file) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("/system/bin/logcat", "-d", "-f", file.getAbsolutePath());
        processBuilder.start();
    }

    private void share(File file) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.setType("text/plain");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(shareIntent, "Share log with"));
    }

    private File dumpFile() {
        if (!workspace.exists()) {
            workspace.mkdirs();
        }

        long timestamp = Instant.now().toEpochMilli();
        return new File(workspace, "netbird-" + timestamp + ".log.txt");
    }

    private void eraseWorkspace() {
        File[] files = workspace.listFiles();
        if(files == null) {
            return;
        }

        for(File f: files) {
            f.delete();
        }
    }
}
