package io.netbird.client.ui.home;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class PagerAdapter extends FragmentStateAdapter {

    private boolean isRunningOnTV;

    public PagerAdapter(@NonNull Fragment fragment, boolean isRunningOnTV) {
        super(fragment);
        this.isRunningOnTV = isRunningOnTV;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment;
        switch (position) {
            case 0:
                fragment = new PeersFragment();
                break;
            case 1:
                fragment = new NetworksFragment();
                break;
            default:
                fragment = new PeersFragment();
                break;
        }
        
        // Pass TV flag to fragments
        Bundle args = new Bundle();
        args.putBoolean("isRunningOnTV", isRunningOnTV);
        fragment.setArguments(args);
        
        return fragment;
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}