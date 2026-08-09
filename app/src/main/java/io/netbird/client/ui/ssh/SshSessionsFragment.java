package io.netbird.client.ui.ssh;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentSshSessionsBinding;

public class SshSessionsFragment extends Fragment {

    private static final int MENU_DUPLICATE = 1;

    private FragmentSshSessionsBinding binding;
    private final SessionsAdapter adapter = new SessionsAdapter();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSshSessionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.sessionsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.sessionsRecycler.setAdapter(adapter);

        SshSessionManager.get().liveSessions().observe(getViewLifecycleOwner(), this::onSessions);

        binding.newSessionFab.setOnClickListener(v ->
                SshConnectDialog.show(requireContext(), null,
                        getString(R.string.ssh_new_connection_title)));
    }

    private void onSessions(List<SshSession.Info> sessions) {
        if (binding == null) {
            return;
        }
        adapter.submit(sessions);
        boolean empty = sessions == null || sessions.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.sessionsRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        binding.sessionsRecycler.setAdapter(null);
        binding = null;
        super.onDestroyView();
    }

    private static int colorForState(SshSession.State state) {
        switch (state) {
            case CONNECTED: return Color.parseColor("#4caf50");
            case CONNECTING: return Color.parseColor("#ffb300");
            case ERROR: return Color.parseColor("#e53935");
            default: return Color.parseColor("#9e9e9e");
        }
    }

    private final class SessionsAdapter extends RecyclerView.Adapter<SessionVH> {

        private final List<SshSession.Info> items = new ArrayList<>();

        void submit(List<SshSession.Info> next) {
            items.clear();
            if (next != null) {
                items.addAll(next);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SessionVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_ssh_session, parent, false);
            return new SessionVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SessionVH holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class SessionVH extends RecyclerView.ViewHolder {

        private final View stateIndicator;
        private final TextView label;
        private final TextView stateText;
        private final ImageButton toggleButton;
        private final ImageButton closeButton;

        SessionVH(@NonNull View itemView) {
            super(itemView);
            stateIndicator = itemView.findViewById(R.id.state_indicator);
            label = itemView.findViewById(R.id.session_label);
            stateText = itemView.findViewById(R.id.session_state);
            toggleButton = itemView.findViewById(R.id.toggle_button);
            closeButton = itemView.findViewById(R.id.close_button);
        }

        void bind(SshSession.Info info) {
            label.setText(info.label());
            String stateLine = stateLabel(info);
            stateText.setText(stateLine);
            stateIndicator.setBackgroundColor(colorForState(info.state));

            // Only hanging up needs a button of its own: reconnecting is what
            // tapping a finished row already does, and once there is output to
            // read the terminal's own bar offers it.
            boolean dead = info.state == SshSession.State.CLOSED
                    || info.state == SshSession.State.ERROR;
            toggleButton.setVisibility(dead ? View.GONE : View.VISIBLE);
            toggleButton.setOnClickListener(v -> {
                SshSession target = SshSessionManager.get().get(info.id);
                if (target != null) {
                    target.disconnect();
                }
            });

            // Reconnect on the spot only when the session left nothing behind.
            // With output to read, just open it and let its bar offer the redial.
            itemView.setOnClickListener(v -> {
                if (dead && !info.hasScrollback) {
                    reconnectAndOpen(info.id);
                    return;
                }
                openTerminal(info.id);
            });
            itemView.setOnLongClickListener(v -> {
                showSessionMenu(v, info);
                return true;
            });

            closeButton.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.ssh_session_close)
                    .setMessage(getString(R.string.ssh_session_close_confirm, info.label()))
                    .setPositiveButton(R.string.ssh_session_close_confirm_yes, (d, w) ->
                            SshSessionManager.get().close(info.id))
                    .setNegativeButton(R.string.ssh_dialog_cancel, null)
                    .show());
        }

        private String stateLabel(SshSession.Info info) {
            String state;
            switch (info.state) {
                case CONNECTING: state = "connecting"; break;
                case CONNECTED:  state = "connected"; break;
                case NEEDS_PASSWORD: state = "password required"; break;
                case CLOSED:     state = "closed"; break;
                case ERROR:      state = "error"; break;
                default:         state = info.state.name().toLowerCase(); break;
            }
            String suffix = (info.stateMessage == null || info.stateMessage.isEmpty())
                    ? "" : " — " + info.stateMessage;
            return state + suffix;
        }

        private void showSessionMenu(View anchor, SshSession.Info info) {
            PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
            popup.getMenu().add(0, MENU_DUPLICATE, 0, R.string.ssh_session_duplicate);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() != MENU_DUPLICATE) {
                    return false;
                }
                SshSession copy = SshSessionManager.get().duplicate(info.id);
                if (copy == null) {
                    Toast.makeText(requireContext(), R.string.ssh_netbird_not_running,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                openTerminal(copy.getId());
                return true;
            });
            popup.show();
        }

        /** Redials and shows the terminal, so the progress is visible as it happens. */
        private void reconnectAndOpen(String sessionId) {
            if (!SshSessionManager.get().reconnect(sessionId)) {
                Toast.makeText(requireContext(), R.string.ssh_netbird_not_running,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            openTerminal(sessionId);
        }

        private void openTerminal(String sessionId) {
            NavController nav = NavHostFragment.findNavController(SshSessionsFragment.this);
            Bundle args = new Bundle();
            args.putString(SSHTerminalFragment.ARG_SESSION_ID, sessionId);
            nav.navigate(R.id.nav_ssh_terminal, args);
        }
    }
}
