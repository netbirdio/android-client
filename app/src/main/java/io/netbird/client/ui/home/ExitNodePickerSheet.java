package io.netbird.client.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.SheetExitNodePickerBinding;
import io.netbird.client.tool.CoalescingWorker;
import io.netbird.gomobile.android.NetworkArray;

/**
 * Single-choice exit node picker: a "None" row followed by the available exit
 * nodes, with a check on the active one. Selecting a node routes all traffic
 * through it; the engine deselects the previously active exit node, so at most
 * one is active at a time.
 */
public class ExitNodePickerSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ExitNodePickerSheet";

    private SheetExitNodePickerBinding binding;
    private volatile ServiceAccessor serviceAccessor;
    private final List<ExitNodePickerAdapter.Entry> entries = new ArrayList<>();
    private ExitNodePickerAdapter adapter;
    // getNetworks() is a JNI call into Go; keep it off the main thread.
    private final CoalescingWorker exitNodesLoader = new CoalescingWorker("nb-exit-node-picker", this::loadExitNodes);

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
        binding = SheetExitNodePickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ExitNodePickerAdapter(entries, this::handlePick);
        RecyclerView list = binding.exitNodePickerList;
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        exitNodesLoader.request();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        serviceAccessor = null;
    }

    @Override
    public void onDestroy() {
        exitNodesLoader.shutdown();
        super.onDestroy();
    }

    private void loadExitNodes() {
        ServiceAccessor accessor = serviceAccessor;
        NetworkArray networks = accessor != null ? accessor.getNetworks() : null;

        List<ExitNodePickerAdapter.Entry> nodes = new ArrayList<>();
        boolean anySelected = false;
        if (networks != null) {
            for (int i = 0; i < networks.size(); i++) {
                var network = networks.get(i);
                if (!Resource.isExitNodeAddress(network.getNetwork())) {
                    continue;
                }
                nodes.add(new ExitNodePickerAdapter.Entry(
                        network.getName(), network.getName(), network.getIsSelected()));
                anySelected |= network.getIsSelected();
            }
        }

        final boolean selected = anySelected;
        View root = binding != null ? binding.getRoot() : null;
        if (root != null) {
            root.post(() -> showExitNodes(nodes, selected));
        }
    }

    private void showExitNodes(List<ExitNodePickerAdapter.Entry> nodes, boolean anySelected) {
        if (binding == null || adapter == null) {
            return;
        }
        entries.clear();
        entries.add(new ExitNodePickerAdapter.Entry(
                null, getString(R.string.exit_node_picker_none), !anySelected));
        entries.addAll(nodes);
        adapter.notifyDataSetChanged();
    }

    private void handlePick(ExitNodePickerAdapter.Entry entry) {
        try {
            if (entry.routeId == null) {
                deselectActiveExitNode();
            } else if (!entry.selected) {
                // The engine deselects the previously active exit node.
                serviceAccessor.selectRoute(entry.routeId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to switch exit node", e);
            Toast.makeText(requireContext(),
                    getString(R.string.exit_node_switch_failed, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
        dismiss();
    }

    private void deselectActiveExitNode() throws Exception {
        for (ExitNodePickerAdapter.Entry entry : entries) {
            if (entry.routeId != null && entry.selected) {
                serviceAccessor.deselectRoute(entry.routeId);
                return;
            }
        }
    }
}
