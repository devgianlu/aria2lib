package com.gianlu.aria2lib.Interface;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;

import com.gianlu.aria2lib.Aria2PK;
import com.gianlu.aria2lib.R;
import com.gianlu.commonutils.CasualViews.RecyclerViewLayout;
import com.gianlu.commonutils.CasualViews.SuperTextView;
import com.gianlu.commonutils.Dialogs.ActivityWithDialog;
import com.gianlu.commonutils.NameValuePair;
import com.gianlu.commonutils.Preferences.Json.JsonStoring;
import com.gianlu.commonutils.Toaster;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ConfigEditorActivity extends ActivityWithDialog implements SimpleOptionsAdapter.Listener {
    private static final int IMPORT_CODE = 1;
    private SimpleOptionsAdapter adapter;
    private RecyclerViewLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        layout = new RecyclerViewLayout(this);
        setContentView(layout);
        setTitle(R.string.customOptions);

        ActionBar bar = getSupportActionBar();
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);

        layout.useVerticalLinearLayoutManager();
        layout.getList().addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new SimpleOptionsAdapter(this, this);
        layout.loadListData(adapter);

        try {
            load();
        } catch (JSONException ex) {
            Toaster.with(this).message(R.string.failedLoadingOptions).ex(ex).show();
            onBackPressed();
            return;
        }

        String importOptions = getIntent().getStringExtra("import");
        if (importOptions != null) {
            try {
                importOptionsFromStream(new FileInputStream(importOptions));
            } catch (FileNotFoundException ex) {
                Toaster.with(this).message(R.string.fileNotFound).ex(ex).show();
            }

            save();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.updatedApp_importedConfig)
                    .setMessage(R.string.updatedApp_importedConfig_message)
                    .setNeutralButton(android.R.string.ok, null);

            showDialog(builder);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.aria2lib_config_editor, menu);
        return true;
    }

    private void load() throws JSONException {
        adapter.load(JsonStoring.intoPrefs().getJsonObject(Aria2PK.CUSTOM_OPTIONS));
    }

    private void save() {
        try {
            JsonStoring.intoPrefs().putJsonObject(Aria2PK.CUSTOM_OPTIONS, NameValuePair.toJson(adapter.get()));
            adapter.saved();
        } catch (JSONException ex) {
            Toaster.with(this).message(R.string.failedSavingCustomOptions).ex(ex).show();
        }
    }

    private void importOptionsFromStream(@NonNull InputStream in) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            if (builder.length() > 1024 * 1024 * 10)
                throw new IOException("File is too big!");

            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        } catch (IOException | OutOfMemoryError ex) {
            System.gc();
            Toaster.with(this).message(R.string.cannotImport).ex(ex).show();
            return;
        }

        adapter.parseAndAdd(builder.toString());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMPORT_CODE) {
            if (resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    InputStream in = getContentResolver().openInputStream(data.getData());
                    if (in != null)
                        importOptionsFromStream(in);
                    else
                        Toaster.with(this).message(R.string.cannotImport).ex(new Exception("InputStream null!")).show();
                } catch (FileNotFoundException ex) {
                    Toaster.with(this).message(R.string.fileNotFound).ex(ex).show();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @SuppressLint("InflateParams")
    private void showAddDialog() {
        LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.aria2lib_dialog_new_option, null, false);
        final EditText key = layout.findViewById(R.id.editOptionDialog_key);
        final EditText value = layout.findViewById(R.id.editOptionDialog_value);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.newOption)
                .setView(layout)
                .setPositiveButton(R.string.apply, (dialogInterface, i) -> {
                    String keyStr = key.getText().toString();
                    if (keyStr.startsWith("--")) keyStr = keyStr.substring(2);
                    adapter.add(new NameValuePair(keyStr, value.getText().toString()));
                })
                .setNegativeButton(android.R.string.cancel, null);

        showDialog(builder);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.configEditor_add) {
            showAddDialog();
            return true;
        } else if (id == android.R.id.home) {
            if (adapter.hasChanged()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.unsavedChanges)
                        .setMessage(R.string.unsavedChanges_message)
                        .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                            save();
                            onBackPressed();
                        })
                        .setNegativeButton(R.string.no, (dialogInterface, i) -> onBackPressed())
                        .setNeutralButton(android.R.string.cancel, null);

                showDialog(builder);
            } else {
                onBackPressed();
            }

            return true;
        } else if (id == R.id.configEditor_import) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Import from another configuration file..."), IMPORT_CODE);
            } catch (ActivityNotFoundException ex) {
                Toaster.with(this).message(R.string.cannotImport).ex(ex).show();
            }

            return true;
        } else if (id == R.id.configEditor_done) {
            save();
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    @SuppressLint("InflateParams")
    public void onEditOption(@NonNull final NameValuePair option) {
        LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.aria2lib_dialog_edit_option, null, false);
        SuperTextView value = layout.findViewById(R.id.editOptionDialog_value);
        value.setHtml(R.string.currentValue, option.value());
        EditText newValue = layout.findViewById(R.id.editOptionDialog_newValue);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(option.key())
                .setView(layout)
                .setPositiveButton(R.string.apply, (dialogInterface, i) -> {
                    String newValueStr = newValue.getText().toString();
                    if (!newValueStr.equals(option.value()))
                        adapter.set(new NameValuePair(option.key(), newValueStr));
                })
                .setNegativeButton(android.R.string.cancel, null);

        showDialog(builder);
    }

    @Override
    public void onItemsCountChanged(int count) {
        if (count <= 0) layout.showInfo(R.string.noCustomOptions);
        else layout.showList();
    }
}
