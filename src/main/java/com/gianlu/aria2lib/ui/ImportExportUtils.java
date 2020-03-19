package com.gianlu.aria2lib.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gianlu.aria2lib.Aria2PK;
import com.gianlu.aria2lib.R;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.dialogs.DialogUtils;
import com.gianlu.commonutils.preferences.Prefs;
import com.gianlu.commonutils.preferences.json.JsonStoring;
import com.gianlu.commonutils.ui.Toaster;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ImportExportUtils {

    private ImportExportUtils() {
    }

    @NonNull
    public static Intent createConfigImportIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        return intent;
    }

    @NonNull
    public static JSONObject toJson(List<Pair<String, String>> list) throws JSONException {
        JSONObject obj = new JSONObject();
        for (Pair<String, String> pair : list) obj.put(pair.first, pair.second);
        return obj;
    }

    private static int indexOf(List<Pair<String, String>> list, String key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).first.equals(key))
                return i;
        }

        return -1;
    }

    public static void importConfigFromStream(@NonNull InputStream in) throws IOException, JSONException {
        List<Pair<String, String>> newOptions = readConfigFromStream(in);

        JSONObject obj = JsonStoring.intoPrefs().getJsonObject(Aria2PK.CUSTOM_OPTIONS);
        List<Pair<String, String>> options = new ArrayList<>();
        if (obj != null) {
            Iterator<String> iterator = obj.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                options.add(new Pair<>(key, obj.getString(key)));
            }
        }

        for (Pair<String, String> option : newOptions) {
            int index = indexOf(options, option.first);
            if (index == -1) options.add(option);
            else options.set(index, option);
        }

        JsonStoring.intoPrefs().putJsonObject(Aria2PK.CUSTOM_OPTIONS, toJson(options));
    }

    @NonNull
    public static List<Pair<String, String>> readConfigFromStream(@NonNull InputStream in) throws IOException {
        if (in.available() > 1024 * 1024 * 10)
            throw new IOException("File is too big: " + in.available());

        String str = CommonUtils.readEntirely(in);

        List<Pair<String, String>> list = new ArrayList<>();
        String[] lines = str.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#")) continue;
            String[] split = line.split("=");
            if (split.length > 0)
                list.add(new Pair<>(split[0], split.length == 1 ? null : split[1]));
        }

        return list;
    }

    @NonNull
    public static String exportConf() throws JSONException, IOException {
        JSONObject obj = JsonStoring.intoPrefs().getJsonObject(Aria2PK.CUSTOM_OPTIONS);

        StringBuilder builder = new StringBuilder();
        if (obj != null) {
            Iterator<String> iterator = obj.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                builder.append(key).append('=').append(obj.getString(key)).append('\n');
            }
        }

        File file = new File(Prefs.getString(Aria2PK.OUTPUT_DIRECTORY), "aria2-exported-conf-" + System.currentTimeMillis() + ".conf");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(builder.toString().getBytes());
        }

        return file.getAbsolutePath();
    }

    @Nullable
    public static String exportSession(@NonNull Context context) throws IOException {
        File from = new File(context.getFilesDir(), "session");
        if (!from.exists() || !from.canRead()) return null;

        File file = new File(Prefs.getString(Aria2PK.OUTPUT_DIRECTORY), "aria2-exported-session-" + System.currentTimeMillis());
        CommonUtils.copyFile(from, file);
        return file.getAbsolutePath();
    }

    public static void showDialog(@NonNull Activity activity, int importCode) {
        String[] options = new String[3];
        options[0] = activity.getString(R.string.exportSessionFile);
        options[1] = activity.getString(R.string.importConfig);
        options[2] = activity.getString(R.string.exportConfig);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(R.string.importExport).setNeutralButton(android.R.string.cancel, null);
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    try {
                        String path = exportSession(activity);
                        if (path == null)
                            DialogUtils.showToast(activity, Toaster.build().message(R.string.noSession));
                        else
                            DialogUtils.showToast(activity, Toaster.build().message(R.string.exportedSession, path));
                    } catch (IOException ex) {
                        DialogUtils.showToast(activity, Toaster.build().message(R.string.failedExportingSession));
                    }
                    break;
                case 1:
                    try {
                        activity.startActivityForResult(Intent.createChooser(ImportExportUtils.createConfigImportIntent(), activity.getString(R.string.importConfig)), importCode);
                    } catch (ActivityNotFoundException ex) {
                        DialogUtils.showToast(activity, Toaster.build().message(R.string.missingFileExplorer));
                    }
                    break;
                case 2:
                    try {
                        String path = exportConf();
                        DialogUtils.showToast(activity, Toaster.build().message(R.string.exportedConfig, path));
                    } catch (JSONException | IOException ex) {
                        DialogUtils.showToast(activity, Toaster.build().message(R.string.failedExportingConf));
                    }
                    break;
            }
        });

        DialogUtils.showDialog(activity, builder);
    }
}
