package io.netbird.client.ui.home;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentNetworksBinding;
import io.netbird.gomobile.android.Network;
import io.netbird.gomobile.android.NetworkArray;

public class NetworksFragment extends Fragment {

   private FragmentNetworksBinding binding;
   private ServiceAccessor serviceAccessor;
   private RecyclerView resourcesRecyclerView;
   private NetworksAdapter adapter;
   private List<Resource> resources = new ArrayList<>();

   public static NetworksFragment newInstance() {
      return new NetworksFragment();
   }

   @Override
   public void onAttach(@NonNull Context context) {
      super.onAttach(context);
      if (context instanceof ServiceAccessor) {
         serviceAccessor = (ServiceAccessor) context;
      } else {
         throw new RuntimeException(context + " must implement ServiceAccessor");
      }
   }

   @Nullable
   @Override
   public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
      binding = FragmentNetworksBinding.inflate(inflater, container, false);
      return binding.getRoot();
   }


   @Override
   public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
      super.onViewCreated(view, savedInstanceState);

      var model = new ViewModelProvider(this,
              ViewModelProvider.Factory.from(NetworksFragmentViewModel.initializer))
              .get(NetworksFragmentViewModel.class);

//      ZeroPeerView.updateVisibility(binding.zeroPeerLayout, binding.networksList, !resources.isEmpty());
      ZeroPeerView.setupLearnWhyClick(binding.zeroPeerLayout, requireContext());

      adapter = new NetworksAdapter(resources);

      resourcesRecyclerView = binding.networksRecyclerView;
      resourcesRecyclerView.setAdapter(adapter);
      resourcesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

      model.getUiState().observe(getViewLifecycleOwner(), uiState -> {
         resources.clear();
         resources.addAll(uiState.getResources());

         updateResourcesCounter(resources);
         ZeroPeerView.updateVisibility(binding.zeroPeerLayout, binding.networksList, !resources.isEmpty());
//         Log.d(NetworksFragment.class.getSimpleName(), "observing viewModel. Is it running on UI thread? "
//                 + Looper.getMainLooper().equals(Looper.myLooper()));
         adapter.notifyDataSetChanged();
      });

//      NetworkArray networks = serviceAccessor.getNetworks();
//      updateNetworkCount(networks);

     // ZeroPeerView.updateVisibility(binding.zeroPeerLayout, binding.networksList, networks.size() > 0);

//      ArrayList<Resource> resources = new ArrayList<>();
//      for( int i = 0; i < networks.size(); i++) {
//         Network network = networks.get(i);
//         Status status = Status.fromString(network.getStatus());
//         resources.add(new Resource(status, network.getName(), network.getNetwork(), network.getPeer()));
//      }

      binding.searchView.clearFocus();
        binding.searchView.addTextChangedListener(new TextWatcher() {
           @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
           @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
              adapter.filterBySearchQuery(s.toString());
           }
           @Override public void afterTextChanged(Editable s) {}
        });

      binding.searchView.setOnFocusChangeListener((v, hasFocus) -> {
         if (hasFocus) {
            binding.searchView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
         } else {
            Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.search);
            binding.searchView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
         }
      });
   }

   private void updateResourcesCounter(List<Resource> resources) {
      TextView textPeersCount = binding.textOpenPanel;
      int connected = 0;

      for (var resource : resources) {
         if (resource.getStatus().equals(Status.CONNECTED)) {
            connected++;
         }
      }

//      for(int i = 0; i < resources.size(); i++) {
//         Network network = networks.get(i);
//         Status status = Status.fromString(network.getStatus());
//         if (status.equals(Status.CONNECTED)) {
//            connected++;
//         }
//      }

      String text = getString(R.string.resources_connected, connected, resources.size());
      textPeersCount.post(() ->
              textPeersCount.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))
      );
   }

   private void updateNetworkCount(NetworkArray networks) {
      TextView textPeersCount = binding.textOpenPanel;
      int connected = 0;
      for(int i = 0; i < networks.size(); i++) {
         Network network = networks.get(i);
         Status status = Status.fromString(network.getStatus());
         if (status.equals(Status.CONNECTED)) {
            connected++;
         }
      }

      String text = getString(R.string.resources_connected, connected, networks.size());
      textPeersCount.post(() ->
              textPeersCount.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))
      );
   }
}
