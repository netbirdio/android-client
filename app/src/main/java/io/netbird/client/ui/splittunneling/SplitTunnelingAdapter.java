package io.netbird.client.ui.splittunneling;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.netbird.client.R;

public class SplitTunnelingAdapter extends RecyclerView.Adapter<SplitTunnelingAdapter.ViewHolder> {

    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> filteredApps = new ArrayList<>();
    private final OnAppClickListener listener;

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    public SplitTunnelingAdapter(OnAppClickListener listener) {
        this.listener = listener;
    }

    public void setApps(List<AppInfo> apps) {
        this.allApps = apps;
        this.filteredApps = new ArrayList<>(apps);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        if (query.isEmpty()) {
            filteredApps = new ArrayList<>(allApps);
        } else {
            String lowerQuery = query.toLowerCase();
            filteredApps = allApps.stream()
                    .filter(app -> app.name.toLowerCase().contains(lowerQuery) || app.packageName.toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = filteredApps.get(position);
        holder.textAppName.setText(app.name);
        holder.textPackageName.setText(app.packageName);
        holder.imgAppIcon.setImageDrawable(app.icon);
        holder.checkApp.setChecked(app.isSelected);
        holder.itemView.setOnClickListener(v -> listener.onAppClick(app));
    }

    @Override
    public int getItemCount() {
        return filteredApps.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAppIcon;
        TextView textAppName;
        TextView textPackageName;
        CheckBox checkApp;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAppIcon = itemView.findViewById(R.id.img_app_icon);
            textAppName = itemView.findViewById(R.id.text_app_name);
            textPackageName = itemView.findViewById(R.id.text_package_name);
            checkApp = itemView.findViewById(R.id.check_app);
        }
    }
}
