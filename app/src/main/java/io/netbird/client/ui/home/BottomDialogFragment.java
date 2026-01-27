package io.netbird.client.ui.home;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;


import io.netbird.client.PlatformUtils;
import io.netbird.client.R;
import io.netbird.client.databinding.FragmentBottomDialogBinding;


public class BottomDialogFragment extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {

    private FragmentBottomDialogBinding binding;
    private boolean isRunningOnTV;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        isRunningOnTV = PlatformUtils.isAndroidTV(requireContext());
        
        // Apply transparent background theme to the dialog
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        dialog.setOnShowListener(dialogInterface -> {
            // Check if the fragment is still attached to avoid IllegalStateException
            if (getActivity() == null || !isAdded()) {
                return;
            }

            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // Set the bottom sheet to be full screen
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                
                behavior.setFitToContents(false);
                
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setPeekHeight(0);
                
                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowMetrics windowMetrics = requireActivity().getWindowManager().getCurrentWindowMetrics();
                    WindowInsets windowInsets = windowMetrics.getWindowInsets();
                    Insets insets = windowInsets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars()
                    );
                    params.height = windowMetrics.getBounds().height() - insets.top;
                } else {
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                    int screenHeight = displayMetrics.heightPixels;
                    params.height = (int) (screenHeight * 0.91f);
                }
                
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

        // Hide close button on TV (users will use the back button to exit)
        if (isRunningOnTV) {
            binding.buttonClose.setVisibility(View.GONE);
            
            // Make the root view focusable to prevent focus from going to elements behind it
            view.setFocusable(true);
            view.setFocusableInTouchMode(false);
            
            binding.separator.setFocusable(false);
            binding.separator.setFocusableInTouchMode(false);
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
        
        if (isRunningOnTV) {
            viewPager.setFocusable(false);
            viewPager.setFocusableInTouchMode(false);
            
            tabLayout.setFocusable(true);
            tabLayout.setFocusableInTouchMode(false);
            
            tabLayout.postDelayed(() -> {
                if (!tabLayout.requestFocus()) {
                    for (int i = 0; i < tabLayout.getTabCount(); i++) {
                        View tabView = tabLayout.getTabAt(i).view;
                        if (tabView != null && tabView.requestFocus()) {
                            break;
                        }
                    }
                }
            }, 100);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = requireActivity().getWindowManager().getCurrentWindowMetrics();
            WindowInsets windowInsets = windowMetrics.getWindowInsets();
            Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            tabLayout.setPadding(
                tabLayout.getPaddingLeft(),
                tabLayout.getPaddingTop(),
                tabLayout.getPaddingRight(),
                insets.bottom
            );
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}