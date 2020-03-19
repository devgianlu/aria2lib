package com.gianlu.aria2lib.ui;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

import com.gianlu.aria2lib.R;
import com.gianlu.commonutils.CommonUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@UiThread
public class SimpleOptionsAdapter extends RecyclerView.Adapter<SimpleOptionsAdapter.ViewHolder> {
    private final List<Pair<String, String>> options;
    private final LayoutInflater inflater;
    private final Listener listener;
    private final Set<Integer> edited;
    private boolean changed = false;

    public SimpleOptionsAdapter(@NonNull Context context, Listener listener) {
        this.options = new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
        this.edited = new HashSet<>();
        if (listener != null) listener.onItemsCountChanged(options.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        Pair<String, String> entry = options.get(position);

        holder.key.setText(entry.first);
        holder.value.setText(entry.second);
        holder.edit.setOnClickListener(view -> {
            if (listener != null) listener.onEditOption(entry);
        });
        holder.delete.setOnClickListener(view -> remove(holder.getAdapterPosition()));

        if (edited.contains(position)) CommonUtils.setTextColor(holder.value, R.color.colorPrimary);
        else CommonUtils.setTextColor(holder.value, android.R.color.tertiary_text_light);
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    public void saved() {
        edited.clear();
        notifyDataSetChanged();
        changed = false;

        if (listener != null) listener.onItemsCountChanged(options.size());
    }

    private void changed() {
        changed = true;
    }

    public boolean hasChanged() {
        return changed;
    }

    private void remove(int pos) {
        if (pos == -1) return;

        edited.remove(pos);
        options.remove(pos);
        changed();
        notifyItemRemoved(pos);

        if (listener != null) listener.onItemsCountChanged(options.size());
    }

    public void set(@NonNull Pair<String, String> newOption) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).first.equals(newOption.first)) {
                options.set(i, newOption);
                edited.add(i);
                changed();
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    public List<Pair<String, String>> get() {
        return options;
    }

    public void load(@Nullable JSONObject obj) throws JSONException {
        options.clear();

        if (obj != null) {
            Iterator<String> iterator = obj.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                options.add(new Pair<>(key, obj.getString(key)));
            }
        }

        notifyDataSetChanged();
        if (listener != null) listener.onItemsCountChanged(options.size());
    }

    public void add(@NonNull List<Pair<String, String>> newOptions) {
        options.addAll(newOptions);
        notifyItemRangeInserted(options.size() - newOptions.size(), newOptions.size());
        changed();

        if (listener != null) listener.onItemsCountChanged(options.size());
    }

    public void add(@NonNull Pair<String, String> option) {
        options.add(option);
        edited.add(options.size() - 1);
        changed();
        notifyItemInserted(options.size() - 1);

        if (listener != null) listener.onItemsCountChanged(options.size());
    }

    public interface Listener {
        void onEditOption(@NonNull Pair<String, String> option);

        void onItemsCountChanged(int count);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView key;
        final TextView value;
        final ImageButton edit;
        final ImageButton delete;

        ViewHolder(ViewGroup parent) {
            super(inflater.inflate(R.layout.aria2lib_item_option, parent, false));

            key = itemView.findViewById(R.id.optionItem_key);
            value = itemView.findViewById(R.id.optionItem_value);
            edit = itemView.findViewById(R.id.optionItem_edit);
            delete = itemView.findViewById(R.id.optionItem_delete);
        }
    }
}
