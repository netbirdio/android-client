package io.netbird.client.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ServiceAccessor serviceAccessor;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Get reference to the ServiceAccessor interface from the activity
        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement ServiceAccessor");
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textHostname = binding.textHostname;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textHostname::setText);

        final TextView textNetworkAddress = binding.textNetworkAddress;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textNetworkAddress::setText);

        final Button buttonConnect = binding.btnConnect;
        buttonConnect.setOnClickListener(v -> {
            if (serviceAccessor == null) {
                return;
            }

            serviceAccessor.switchConnection(true);
        });
        return root;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        serviceAccessor = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}