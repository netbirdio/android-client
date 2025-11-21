package io.netbird.client.ui.home;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;


import io.netbird.client.R;
import io.netbird.client.databinding.FragmentBottomDialogBinding;


public class BottomDialogFragment extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {

    private FragmentBottomDialogBinding binding;
    private boolean isRunningOnTV;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Detect if running on Android TV
        isRunningOnTV = isRunningOnAndroidTV();
        
        // Apply transparent background theme to the dialog
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        dialog.setOnShowListener(dialogInterface -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // Set the bottom sheet to be full screen
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                
                behavior.setFitToContents(false);
                
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setPeekHeight(0);
                
                // Set height to 91% to avoid covering system navigation bar on mobile
                DisplayMetrics displayMetrics = new DisplayMetrics();
                requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int screenHeight = displayMetrics.heightPixels;

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = (int) (screenHeight * 0.91f);
                bottomSheet.setLayoutParams(params);

                // Set the background to transparent
                bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
                bottomSheet.requestLayout();

                // Restore dim to create overlay and enable proper touch handling
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setDimAmount(0.5f);
                }
            }
        });

        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBottomDialogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }


    @SuppressLint("NonConstantResourceId")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set rounded corners background on your sheet content
        view.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_top_corners));

        // Make close button non-focusable on TV (only tabs should be focusable)
        if (isRunningOnTV) {
            binding.buttonClose.setFocusable(false);
            binding.buttonClose.setFocusableInTouchMode(false);
        }
        
        binding.buttonClose.setOnClickListener(v -> dismiss());

        setupViewPager();
    }

    private void setupViewPager() {
        ViewPager2 viewPager = binding.peersViewPager;
        TabLayout tabLayout = binding.peersTabLayout;

        PagerAdapter adapter = new PagerAdapter(this, isRunningOnTV);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Peers");
                    tab.setIcon(R.drawable.peers);
                    break;
                case 1:
                    tab.setText("Networks");
                    tab.setIcon(R.drawable.networks);
                    break;
                default:
                    tab.setText("Tab " + position);
            }
        }).attach();
        
        // On TV, ensure focus starts on the tabs
        if (isRunningOnTV) {
            tabLayout.post(() -> tabLayout.requestFocus());
        }
    }

    private boolean isRunningOnAndroidTV() {
        UiModeManager uiModeManager = (UiModeManager) requireContext().getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null) {
            return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}