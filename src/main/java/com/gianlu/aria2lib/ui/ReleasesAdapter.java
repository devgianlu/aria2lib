package com.gianlu.aria2lib.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gianlu.aria2lib.GitHubApi;
import com.gianlu.aria2lib.R;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.misc.SuperTextView;

import java.util.Collections;
import java.util.List;

public class ReleasesAdapter extends RecyclerView.Adapter<ReleasesAdapter.ViewHolder> {
    private final List<GitHubApi.Release.Asset> assets;
    private final LayoutInflater inflater;
    private final Listener listener;

    public ReleasesAdapter(@NonNull Context context, List<GitHubApi.Release.Asset> assets, Listener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
        this.assets = assets;

        Collections.sort(assets, new GitHubApi.Release.SortByVersionComparator());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GitHubApi.Release.Asset asset = assets.get(position);
        holder.name.setText(asset.version());
        holder.uploadedAt.setHtml(R.string.publishedAt, CommonUtils.getFullDateFormatter().format(asset.publishedAt()));
        holder.size.setHtml(R.string.size, CommonUtils.dimensionFormatter(asset.size, false));
        holder.source.setHtml(R.string.source, asset.repoSlug());
        holder.arch.setText(asset.arch());
        holder.itemView.setOnClickListener(view1 -> {
            if (listener != null) listener.onAssetSelected(asset);
        });
    }

    @Override
    public int getItemCount() {
        return assets.size();
    }

    public interface Listener {
        void onAssetSelected(@NonNull GitHubApi.Release.Asset asset);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final SuperTextView uploadedAt;
        final SuperTextView size;
        final TextView arch;
        final SuperTextView source;

        ViewHolder(@NonNull ViewGroup parent) {
            super(inflater.inflate(R.layout.aria2lib_item_release, parent, false));

            name = itemView.findViewById(R.id.releaseItem_name);
            uploadedAt = itemView.findViewById(R.id.releaseItem_publishedAt);
            size = itemView.findViewById(R.id.releaseItem_size);
            arch = itemView.findViewById(R.id.releaseItem_arch);
            source = itemView.findViewById(R.id.releaseItem_source);
        }
    }
}
