package io.netbird.client.ui.files;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentFileDropBinding;
import io.netbird.client.databinding.ListItemFileDayHeaderBinding;
import io.netbird.client.databinding.ListItemFileOfferBinding;
import io.netbird.client.databinding.ListItemFileTransferBinding;
import io.netbird.client.tool.files.FileDropManager;
import io.netbird.gomobile.android.Android;

/**
 * The Files screen: offers awaiting consent pinned to the top, then the transfer
 * log grouped by day, newest first. The receiving policy is not here; it lives
 * under Settings, so this screen stays a log.
 */
public class FileDropFragment extends Fragment {

    private static final int TYPE_OFFER = 0;
    private static final int TYPE_DAY = 1;
    private static final int TYPE_TRANSFER = 2;

    private FragmentFileDropBinding binding;
    private final TransfersAdapter adapter = new TransfersAdapter();
    private List<FileDropManager.Transfer> allTransfers = new ArrayList<>();
    private String query = "";

    // The manager notifies from its own executor, so every update is posted
    // back to the view before it touches the adapter.
    private final FileDropManager.TransfersListener transfersListener =
            transfers -> onUiThread(() -> onTransfers(transfers));

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFileDropBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.transfersRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.transfersRecycler.setAdapter(adapter);

        binding.searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim().toLowerCase(Locale.getDefault());
                render();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        FileDropManager.get().addTransfersListener(transfersListener);

        // The list is only readable through the bound service, which may have
        // arrived after the manager last published.
        FileDropManager.get().refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        FileDropManager.get().removeTransfersListener(transfersListener);
        binding = null;
    }

    private void onTransfers(List<FileDropManager.Transfer> transfers) {
        allTransfers = transfers;
        render();
    }

    private void render() {
        if (binding == null) {
            return;
        }
        List<FileDropManager.Transfer> shown = filtered(allTransfers);
        adapter.submit(buildRows(shown));
        binding.emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        binding.emptyView.setText(query.isEmpty()
                ? R.string.file_drop_empty
                : R.string.file_drop_no_results);
    }

    /** Matches the file names and the peer, the same fields the desktop searches. */
    private List<FileDropManager.Transfer> filtered(List<FileDropManager.Transfer> transfers) {
        if (query.isEmpty()) {
            return transfers;
        }

        List<FileDropManager.Transfer> out = new ArrayList<>();
        for (FileDropManager.Transfer t : transfers) {
            if (matches(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private boolean matches(FileDropManager.Transfer transfer) {
        Locale locale = Locale.getDefault();
        if (transfer.peerName().toLowerCase(locale).contains(query)) {
            return true;
        }
        if (transfer.isText() && transfer.text().toLowerCase(locale).contains(query)) {
            return true;
        }
        for (String name : transfer.fileNames()) {
            if (name.toLowerCase(locale).contains(query)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flattens the transfer log into display rows: pending incoming offers
     * first, then the rest under a header per day.
     */
    private List<Row> buildRows(List<FileDropManager.Transfer> transfers) {
        List<Row> rows = new ArrayList<>();

        for (FileDropManager.Transfer transfer : transfers) {
            if (isAnswerable(transfer)) {
                rows.add(Row.offer(transfer));
            }
        }

        String currentDay = null;
        for (FileDropManager.Transfer transfer : transfers) {
            if (isAnswerable(transfer)) {
                continue;
            }
            String day = dayLabel(transfer.createdAtMillis());
            if (!day.equals(currentDay)) {
                rows.add(Row.day(day));
                currentDay = day;
            }
            rows.add(Row.transfer(transfer));
        }
        return rows;
    }

    private static boolean isAnswerable(FileDropManager.Transfer transfer) {
        return transfer.isPending() && !transfer.outgoing();
    }

    private String dayLabel(long millis) {
        if (millis <= 0) {
            return getString(R.string.file_drop_group_earlier);
        }

        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();

        if (isSameDay(day, today)) {
            return getString(R.string.file_drop_group_today);
        }
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(day, today)) {
            return getString(R.string.file_drop_group_yesterday);
        }
        return DateFormat.getMediumDateFormat(requireContext()).format(new Date(millis));
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private String timeLabel(long millis) {
        if (millis <= 0) {
            return "";
        }
        return DateFormat.getTimeFormat(requireContext()).format(new Date(millis));
    }

    private void report(boolean ok, @Nullable String error) {
        if (ok) {
            return;
        }
        onUiThread(() -> {
            if (binding != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Opens a received file through the app's FileProvider. Received files land
     * in app-private storage, so a plain file Uri would be unreadable to any
     * other app.
     */
    private void open(FileDropManager.Transfer transfer) {
        if (transfer.deliveredPaths().isEmpty()) {
            return;
        }

        File file = new File(transfer.deliveredPaths().get(0));
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, requireContext().getContentResolver().getType(uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.file_drop_open)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String outcomeLabel(FileDropManager.Transfer transfer) {
        if (transfer.isUnreachable()) {
            return getString(R.string.file_drop_state_unreachable);
        }

        long state = transfer.state();
        if (state == Android.FileDropStateTransferring) {
            return progressLabel(transfer);
        }
        if (state == Android.FileDropStatePending) {
            return getString(R.string.file_drop_state_pending);
        }
        if (state == Android.FileDropStateCompleted) {
            return getString(transfer.outgoing()
                    ? R.string.file_drop_state_sent
                    : R.string.file_drop_state_received);
        }
        if (state == Android.FileDropStateDeclined) {
            return getString(R.string.file_drop_state_declined);
        }
        if (state == Android.FileDropStateExpired) {
            return getString(R.string.file_drop_state_expired);
        }
        if (state == Android.FileDropStateCancelled) {
            return getString(R.string.file_drop_state_cancelled);
        }
        return getString(R.string.file_drop_state_failed);
    }

    private String progressLabel(FileDropManager.Transfer transfer) {
        if (transfer.totalSize() <= 0) {
            return getString(R.string.file_drop_state_transferring);
        }
        int percent = (int) (transfer.transferred() * 100 / transfer.totalSize());
        return getString(R.string.file_drop_state_progress, percent);
    }

    /** Only a refusal or a failure is coloured; the rest reads as plain log. */
    private int outcomeColor(FileDropManager.Transfer transfer) {
        long state = transfer.state();
        int color = R.color.nb_txt_light;
        if (state == Android.FileDropStateDeclined || state == Android.FileDropStateFailed) {
            color = R.color.nb_danger;
        }
        return ContextCompat.getColor(requireContext(), color);
    }

    /** Manager callbacks come off its executor; view work has to go back. */
    private void onUiThread(Runnable action) {
        View root = getView();
        if (root != null) {
            root.post(action);
        }
    }

    private static final class Row {
        final int type;
        final FileDropManager.Transfer transfer;
        final String label;

        private Row(int type, FileDropManager.Transfer transfer, String label) {
            this.type = type;
            this.transfer = transfer;
            this.label = label;
        }

        static Row offer(FileDropManager.Transfer transfer) {
            return new Row(TYPE_OFFER, transfer, null);
        }

        static Row day(String label) {
            return new Row(TYPE_DAY, null, label);
        }

        static Row transfer(FileDropManager.Transfer transfer) {
            return new Row(TYPE_TRANSFER, transfer, null);
        }
    }

    private final class TransfersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Row> rows = new ArrayList<>();

        void submit(List<Row> next) {
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            switch (viewType) {
                case TYPE_OFFER:
                    return new OfferViewHolder(
                            ListItemFileOfferBinding.inflate(inflater, parent, false));
                case TYPE_DAY:
                    return new DayViewHolder(
                            ListItemFileDayHeaderBinding.inflate(inflater, parent, false));
                default:
                    return new TransferViewHolder(
                            ListItemFileTransferBinding.inflate(inflater, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof OfferViewHolder) {
                ((OfferViewHolder) holder).bind(row.transfer);
            } else if (holder instanceof DayViewHolder) {
                ((DayViewHolder) holder).bind(row.label);
            } else {
                ((TransferViewHolder) holder).bind(row.transfer);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private final class DayViewHolder extends RecyclerView.ViewHolder {

        private final ListItemFileDayHeaderBinding binding;

        DayViewHolder(ListItemFileDayHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(String label) {
            binding.dayLabel.setText(label);
        }
    }

    private final class OfferViewHolder extends RecyclerView.ViewHolder {

        private final ListItemFileOfferBinding binding;

        OfferViewHolder(ListItemFileOfferBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FileDropManager.Transfer transfer) {
            binding.offerLabel.setText(transfer.isText()
                    ? title(transfer)
                    : getString(R.string.file_drop_offer_label,
                            title(transfer), formatSize(transfer.totalSize())));
            binding.offerSubtitle.setText(
                    getString(R.string.file_drop_offer_subtitle, transfer.peerName()));

            binding.offerAccept.setOnClickListener(v ->
                    FileDropManager.get().accept(transfer.id(), FileDropFragment.this::report));
            binding.offerDecline.setOnClickListener(v ->
                    FileDropManager.get().decline(transfer.id(), FileDropFragment.this::report));
        }
    }

    private final class TransferViewHolder extends RecyclerView.ViewHolder {

        private final ListItemFileTransferBinding binding;

        TransferViewHolder(ListItemFileTransferBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FileDropManager.Transfer transfer) {
            binding.transferDirection.setImageResource(transfer.outgoing()
                    ? R.drawable.ic_arrow_up_small
                    : R.drawable.ic_arrow_down_small);
            binding.transferDirection.setColorFilter(ContextCompat.getColor(requireContext(),
                    transfer.outgoing() ? R.color.nb_orange : R.color.nb_latency_good));

            binding.transferLabel.setText(title(transfer));
            binding.transferSubtitle.setText(subtitle(transfer));
            binding.transferSubtitle.setTextColor(outcomeColor(transfer));
            // A received snippet is worth copying, not opening, so the trailing
            // slot carries the action instead of the timestamp; the plan shows
            // the same swap.
            boolean copyable = transfer.isText() && !transfer.outgoing();
            if (copyable) {
                binding.transferTime.setText(R.string.file_drop_copy);
                binding.transferTime.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.nb_orange));
                binding.transferTime.setOnClickListener(v -> copy(transfer));
            } else {
                binding.transferTime.setText(timeLabel(transfer.createdAtMillis()));
                binding.transferTime.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.nb_txt_light));
                binding.transferTime.setOnClickListener(null);
            }
            binding.transferTime.setClickable(copyable);

            boolean openable = !transfer.outgoing()
                    && transfer.state() == Android.FileDropStateCompleted
                    && !transfer.deliveredPaths().isEmpty();
            binding.getRoot().setOnClickListener(openable ? v -> open(transfer) : null);
            binding.getRoot().setClickable(openable);

            binding.getRoot().setOnLongClickListener(v -> {
                confirmDelete(transfer);
                return true;
            });
        }

        /** "from office-mini · 214 MB · Received", as on the plan's row. */
        private String subtitle(FileDropManager.Transfer transfer) {
            StringBuilder text = new StringBuilder(getString(transfer.outgoing()
                            ? R.string.file_drop_direction_sent
                            : R.string.file_drop_direction_received,
                    transfer.peerName()));

            if (!transfer.isText()) {
                text.append(" · ").append(formatSize(transfer.totalSize()));
            }
            return text.append(" · ").append(outcomeLabel(transfer)).toString();
        }
    }

    /**
     * Asks before dropping a history entry. A live transfer is cancelled by the
     * same action, which the prompt spells out rather than silently aborting it.
     */
    @SuppressLint("InflateParams")
    private void confirmDelete(FileDropManager.Transfer transfer) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_simple_edit_text, null);

        ((TextView) view.findViewById(R.id.text_title_dialog)).setText(title(transfer));
        ((TextView) view.findViewById(R.id.text_label_dialog)).setText(transfer.isTerminal()
                ? R.string.file_drop_delete_confirm
                : R.string.file_drop_delete_confirm_live);
        view.findViewById(R.id.edit_text_dialog).setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setView(view)
                .create();

        MaterialButton confirm = view.findViewById(R.id.btn_ok_dialog);
        confirm.setText(R.string.file_drop_delete);
        confirm.setOnClickListener(v -> {
            FileDropManager.get().delete(transfer.id());
            dialog.dismiss();
        });
        view.findViewById(R.id.btn_cancel_dialog).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void copy(FileDropManager.Transfer transfer) {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("", transfer.text()));
        Toast.makeText(requireContext(), R.string.file_drop_copied, Toast.LENGTH_SHORT).show();
    }

    /** A text snippet reads as the quoted text; files read as their names. */
    private String title(FileDropManager.Transfer transfer) {
        if (transfer.isText()) {
            return getString(R.string.file_drop_text_title, transfer.text());
        }
        List<String> names = transfer.fileNames();
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = -1;
        do {
            size /= 1024;
            unit++;
        } while (size >= 1024 && unit < units.length - 1);

        if (size >= 10) {
            return String.format("%.0f %s", size, units[unit]);
        }
        return String.format("%.1f %s", size, units[unit]);
    }
}
