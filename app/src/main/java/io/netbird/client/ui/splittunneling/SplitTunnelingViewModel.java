package io.netbird.client.ui.splittunneling;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds the list of applications the user can pick from.
 *
 * Reading labels and icons hits the package manager once per app, which is slow
 * enough to drop frames on a loaded device, so the whole list is resolved off the
 * main thread and kept for as long as the screen lives.
 */
public class SplitTunnelingViewModel extends AndroidViewModel {

    private final MutableLiveData<List<AppEntry>> apps = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean loadStarted;

    public SplitTunnelingViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<AppEntry>> getApps() {
        return apps;
    }

    public void loadApps() {
        if (loadStarted) {
            return;
        }
        loadStarted = true;
        executor.execute(() -> apps.postValue(queryLaunchableApps()));
    }

    /**
     * Only apps with a launcher entry are listed. That is what the manifest's
     * {@code <queries>} block makes visible, and it keeps the screen clear of the
     * package manager's long tail of services the user has no opinion about —
     * without asking for QUERY_ALL_PACKAGES, which Play treats as sensitive.
     */
    private List<AppEntry> queryLaunchableApps() {
        PackageManager packageManager = getApplication().getPackageManager();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = packageManager.queryIntentActivities(launcherIntent, 0);
        List<AppEntry> entries = new ArrayList<>(resolved.size());
        Set<String> seen = new HashSet<>();

        // This app is left out on purpose: an INCLUDE selection always carries it
        // so the built-in SSH client can reach peers, so a toggle for it would
        // claim an effect it does not have.
        String ownPackage = getApplication().getPackageName();

        for (ResolveInfo info : resolved) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(ownPackage) || !seen.add(packageName)) {
                continue;
            }
            entries.add(new AppEntry(
                    packageName,
                    info.loadLabel(packageManager).toString(),
                    info.loadIcon(packageManager)));
        }

        entries.sort(Comparator.comparing(entry -> entry.getLabel().toLowerCase(Locale.getDefault())));
        return entries;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
