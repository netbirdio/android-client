package io.netbird.client.ui.home;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
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
import io.netbird.client.StateListenerRegistry;
import io.netbird.client.databinding.FragmentNetworksBinding;

public class NetworksFragment extends Fragment {

   private FragmentNetworksBinding binding;
   private NetworksAdapter adapter;
   private final List<Resource> resources = new ArrayList<>();
   private final List<RoutingPeer> peers = new ArrayList<>();
   private NetworksFragmentViewModel model;
   private StateListenerRegistry stateListenerRegistry;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof StateListenerRegistry) {
            stateListenerRegistry = (StateListenerRegistry) context;
        } else {
            throw new RuntimeException(context + " must implement StateListenerRegistry");
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

      model = new ViewModelProvider(this,
              ViewModelProvider.Factory.from(NetworksFragmentViewModel.initializer))
              .get(NetworksFragmentViewModel.class);
      stateListenerRegistry.registerServiceStateListener(model);

      ZeroPeerView.setupLearnWhyClick(binding.zeroPeerLayout, requireContext());

      adapter = new NetworksAdapter(resources, peers, this::routeSwitchToggleHandler);

      RecyclerView resourcesRecyclerView = binding.networksRecyclerView;
      resourcesRecyclerView.setAdapter(adapter);
      resourcesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

      model.getUiState().observe(getViewLifecycleOwner(), uiState -> {
         resources.clear();
         resources.addAll(uiState.getResources());

         peers.clear();
         peers.addAll(uiState.getPeers());

         updateResourcesCounter(resources);
         ZeroPeerView.updateVisibility(binding.zeroPeerLayout, binding.networksList, !resources.isEmpty());
         adapter.notifyDataSetChanged();
         adapter.filterBySearchQuery(binding.searchView.getText().toString());
      });

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

    @Override
    public void onDestroyView() {
        stateListenerRegistry.unregisterServiceStateListener(model);
        super.onDestroyView();
    }

    private void updateResourcesCounter(List<Resource> resources) {
      TextView textPeersCount = binding.textOpenPanel;
      int connected = 0;

      for (var resource : resources) {
         if (resource.isSelected()) {
            connected++;
         }
      }

      String text = getString(R.string.resources_connected, connected, resources.size());
      textPeersCount.post(() ->
              textPeersCount.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))
      );
   }

   private void routeSwitchToggleHandler(String route, boolean isChecked) throws Exception {
      if (isChecked) {
         model.selectRoute(route);
      } else {
         model.deselectRoute(route);
      }
   }
}
