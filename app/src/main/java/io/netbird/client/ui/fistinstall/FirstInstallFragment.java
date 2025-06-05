package io.netbird.client.ui.fistinstall;

import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentFirstinstallBinding;
import io.netbird.client.ui.server.ChangeServerFragment;

public class FirstInstallFragment extends Fragment {

    private FragmentFirstinstallBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstinstallBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        hideAppBar();

        binding.btnContinue.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main);
            navController.popBackStack(); // or navigate somewhere
        });

        String fullText = getString(R.string.fragment_firstinstall_txt);
        String clickableWord = "change_server"; // from string

        SpannableString spannable = new SpannableString(fullText);
        int startIndex = fullText.indexOf(clickableWord);
        int endIndex = startIndex + clickableWord.length();

        if (startIndex != -1) {
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main);
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(ChangeServerFragment.HideAlertBundleArg, true);

                    NavOptions navOptions = new NavOptions.Builder()
                            .setPopUpTo(R.id.firstInstallFragment, true)
                            .build();

                    navController.navigate(R.id.nav_change_server, bundle, navOptions);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                    ds.setColor(ContextCompat.getColor(requireContext(), R.color.nb_orange));
                }
            };
            spannable.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        binding.txtLicense.setText(spannable);
        binding.txtLicense.setMovementMethod(LinkMovementMethod.getInstance());
        binding.txtLicense.setHighlightColor(Color.TRANSPARENT);
    }

    private void hideAppBar() {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().hide();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().show();
        }
        binding = null;
    }
}
