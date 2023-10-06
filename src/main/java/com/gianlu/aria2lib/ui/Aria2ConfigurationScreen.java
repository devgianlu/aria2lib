package com.gianlu.aria2lib.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import com.gianlu.aria2lib.Aria2PK;
import com.gianlu.aria2lib.Aria2Ui;
import com.gianlu.aria2lib.R;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.misc.MessageView;
import com.gianlu.commonutils.misc.SuperTextView;
import com.gianlu.commonutils.preferences.Prefs;
import com.gianlu.commonutils.preferences.json.JsonStoring;
import com.gianlu.commonutils.ui.Toaster;
import com.yarolegovich.lovelyuserinput.LovelyInput;
import com.yarolegovich.mp.AbsMaterialPreference;
import com.yarolegovich.mp.AbsMaterialTextValuePreference;
import com.yarolegovich.mp.MaterialCheckboxPreference;
import com.yarolegovich.mp.MaterialEditTextPreference;
import com.yarolegovich.mp.MaterialPreferenceCategory;
import com.yarolegovich.mp.MaterialPreferenceScreen;
import com.yarolegovich.mp.MaterialSeekBarPreference;
import com.yarolegovich.mp.MaterialStandardPreference;
import com.yarolegovich.mp.io.MaterialPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class Aria2ConfigurationScreen extends MaterialPreferenceScreen {
    private final MaterialPreferenceCategory generalCategory;
    private final MaterialPreferenceCategory rpcCategory;
    private final MaterialPreferenceCategory notificationsCategory;
    private final MaterialPreferenceCategory logsCategory;
    private final SuperTextView nicsText;
    private MaterialEditTextPreference outputPath;
    private LinearLayout logsContainer;
    private MessageView logsMessage;
    private MaterialStandardPreference customOptions;
    private boolean rpcEnabled = false;

    public Aria2ConfigurationScreen(Context context) {
        this(context, null, 0);
    }

    public Aria2ConfigurationScreen(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Aria2ConfigurationScreen(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        useLinearLayout();

        inflate(new ContextThemeWrapper(context, R.style.AppTheme), R.layout.aria2lib_conf_screen, this);

        generalCategory = findViewById(R.id.aria2lib_confScreen_general);
        rpcCategory = findViewById(R.id.aria2lib_confScreen_rpc);
        notificationsCategory = findViewById(R.id.aria2lib_confScreen_notifications);
        logsCategory = findViewById(R.id.aria2lib_confScreen_logs);
        nicsText = findViewById(R.id.aria2lib_confScreen_nics);
    }

    public void refreshNics() {
        nicsText.setHtml(Aria2Ui.getInterfacesIPsFormatted());
    }

    public void refreshCustomOptionsNumber() {
        int customOptionsNum;
        try {
            JSONObject obj = JsonStoring.intoPrefs().getJsonObject(Aria2PK.CUSTOM_OPTIONS);
            if (obj == null) customOptionsNum = 0;
            else customOptionsNum = obj.length();
        } catch (JSONException ex) {
            customOptionsNum = 0;
        }

        customOptions.setSummary(getResources().getQuantityString(R.plurals.customOptions_summary, customOptionsNum, customOptionsNum));
    }

    public void setup(@StyleRes int theme, @NonNull AbsMaterialPreference.OverrideOnClickListener outputPathListener, @Nullable Prefs.KeyWithDefault<Boolean> startAtBootPref, @Nullable Prefs.KeyWithDefault<Boolean> startWithAppPref, boolean rpcEnabled) {
        this.rpcEnabled = rpcEnabled;

        LovelyInput.Builder lovelyInput = new LovelyInput.Builder()
                .addIcon(Aria2PK.OUTPUT_DIRECTORY.key(), R.drawable.baseline_folder_24)
                .addTextFilter(Aria2PK.OUTPUT_DIRECTORY.key(), R.string.invalidOutputPath, text -> {
                    File path = new File(text);
                    return path.exists() && path.canWrite();
                })
                .addIcon(Aria2PK.RPC_PORT.key(), R.drawable.baseline_import_export_24)
                .addTextFilter(Aria2PK.RPC_PORT.key(), R.string.invalidPort, text -> {
                    try {
                        int port = Integer.parseInt(text);
                        return port > 0 && port < 65536;
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .addIcon(Aria2PK.RPC_TOKEN.key(), R.drawable.baseline_vpn_key_24)
                .addTextFilter(Aria2PK.RPC_TOKEN.key(), R.string.invalidToken, text -> !text.isEmpty())
                .addIcon(Aria2PK.NOTIFICATION_UPDATE_DELAY.key(), R.drawable.baseline_notifications_24)
                .setTopColor(ContextCompat.getColor(getContext(), R.color.colorPrimary));

        if (theme != 0) lovelyInput.setTheme(theme);
        MaterialPreferences.setUserInputModule(lovelyInput.build());

        // General
        generalCategory.setTitle(R.string.general);

        outputPath = new MaterialEditTextPreference.Builder(getContext())
                .showValueMode(AbsMaterialTextValuePreference.SHOW_ON_BOTTOM)
                .key(Aria2PK.OUTPUT_DIRECTORY.key())
                .defaultValue(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath())
                .build();
        outputPath.setTitle(R.string.outputPath);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            outputPath.setOverrideClickListener(outputPathListener);
        }

        generalCategory.addView(outputPath);

        MaterialCheckboxPreference saveSession = new MaterialCheckboxPreference.Builder(getContext())
                .key(Aria2PK.SAVE_SESSION.key())
                .defaultValue(Aria2PK.SAVE_SESSION.fallback())
                .build();
        saveSession.setTitle(R.string.saveSession);
        saveSession.setSummary(R.string.saveSession_summary);
        generalCategory.addView(saveSession);

        MaterialCheckboxPreference checkCertificate = new MaterialCheckboxPreference.Builder(getContext())
                .key(Aria2PK.CHECK_CERTIFICATE.key())
                .defaultValue(Aria2PK.CHECK_CERTIFICATE.fallback())
                .build();
        checkCertificate.setTitle(R.string.checkCertificate);
        checkCertificate.setSummary(R.string.checkCertificate_summary);
        generalCategory.addView(checkCertificate);

        if (startAtBootPref != null) {
            MaterialCheckboxPreference startAtBoot = new MaterialCheckboxPreference.Builder(getContext())
                    .key(startAtBootPref.key())
                    .defaultValue(startAtBootPref.fallback())
                    .build();
            startAtBoot.setTitle(R.string.startServiceAtBoot);
            startAtBoot.setSummary(R.string.startServiceAtBoot_summary);
            generalCategory.addView(startAtBoot);
        }

        if (startWithAppPref != null) {
            MaterialCheckboxPreference startWithApp = new MaterialCheckboxPreference.Builder(getContext())
                    .key(startWithAppPref.key())
                    .defaultValue(startWithAppPref.fallback())
                    .build();
            startWithApp.setTitle(R.string.startServiceWithApp);
            startWithApp.setSummary(R.string.startServiceWithApp_summary);
            generalCategory.addView(startWithApp);
        }

        customOptions = new MaterialStandardPreference(getContext());
        customOptions.setOnClickListener(v -> getContext().startActivity(new Intent(getContext(), ConfigEditorActivity.class)));
        customOptions.setTitle(R.string.customOptions);
        generalCategory.addView(customOptions);
        refreshCustomOptionsNumber();

        // RPC
        if (rpcEnabled) {
            rpcCategory.setVisibility(VISIBLE);
            rpcCategory.setTitle(R.string.rpc);

            MaterialEditTextPreference rpcPort = new MaterialEditTextPreference.Builder(getContext())
                    .showValueMode(AbsMaterialTextValuePreference.SHOW_ON_RIGHT)
                    .key(Aria2PK.RPC_PORT.key())
                    .defaultValue(String.valueOf(Aria2PK.RPC_PORT.fallback()))
                    .build();
            rpcPort.setTitle(R.string.rpcPort);
            rpcCategory.addView(rpcPort);

            MaterialEditTextPreference rpcToken = new MaterialEditTextPreference.Builder(getContext())
                    .showValueMode(AbsMaterialTextValuePreference.SHOW_ON_RIGHT)
                    .key(Aria2PK.RPC_TOKEN.key())
                    .defaultValue(String.valueOf(Aria2PK.RPC_TOKEN.fallback()))
                    .build();
            rpcToken.setTitle(R.string.rpcToken);
            rpcCategory.addView(rpcToken);

            MaterialCheckboxPreference listenAll = new MaterialCheckboxPreference.Builder(getContext())
                    .key(Aria2PK.RPC_LISTEN_ALL.key())
                    .defaultValue(Aria2PK.RPC_LISTEN_ALL.fallback())
                    .build();
            listenAll.setTitle(R.string.listenAllInterfaces);
            listenAll.setSummary(R.string.listenAllInterfaces_summary);
            rpcCategory.addView(listenAll);

            MaterialCheckboxPreference allowOriginAll = new MaterialCheckboxPreference.Builder(getContext())
                    .key(Aria2PK.RPC_ALLOW_ORIGIN_ALL.key())
                    .defaultValue(Aria2PK.RPC_ALLOW_ORIGIN_ALL.fallback())
                    .build();
            allowOriginAll.setTitle(R.string.accessControlAllowOriginAll);
            allowOriginAll.setSummary(R.string.accessControlAllowOriginAll_summary);
            rpcCategory.addView(allowOriginAll);
        } else {
            rpcCategory.setVisibility(GONE);
        }

        // Notifications
        notificationsCategory.setTitle(R.string.notification);

        MaterialCheckboxPreference showPerformance = new MaterialCheckboxPreference.Builder(getContext())
                .key(Aria2PK.SHOW_PERFORMANCE.key())
                .defaultValue(Aria2PK.SHOW_PERFORMANCE.fallback())
                .build();
        showPerformance.setTitle(R.string.showPerformance);
        showPerformance.setSummary(R.string.showPerformance_summary);
        notificationsCategory.addView(showPerformance);

        MaterialSeekBarPreference updateDelay = new MaterialSeekBarPreference.Builder(getContext())
                .showValue(true).minValue(1).maxValue(5)
                .key(Aria2PK.NOTIFICATION_UPDATE_DELAY.key())
                .defaultValue(Aria2PK.NOTIFICATION_UPDATE_DELAY.fallback())
                .build();
        updateDelay.setTitle(R.string.updateInterval);
        notificationsCategory.addView(updateDelay);

        setVisibilityController(showPerformance, new AbsMaterialPreference[]{updateDelay}, true);

        // Logs
        logsCategory.setTitle(R.string.logs);

        logsMessage = new MessageView(getContext());
        logsMessage.info(R.string.noLogs);
        logsCategory.addView(logsMessage);
        logsMessage.setVisibility(View.VISIBLE);

        logsContainer = new LinearLayout(getContext());
        logsContainer.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        logsContainer.setPaddingRelative(pad, 0, pad, 0);
        logsCategory.addView(logsContainer);
        logsContainer.setVisibility(View.GONE);

        MaterialStandardPreference clearLogs = new MaterialStandardPreference(getContext());
        clearLogs.setOnClickListener(v -> {
            logsContainer.removeAllViews();
            logsContainer.setVisibility(View.GONE);
            logsMessage.setVisibility(View.VISIBLE);
        });
        clearLogs.setTitle(R.string.clearLogs);
        logsCategory.addView(clearLogs);

        refreshNics();
    }

    public void setup(@NonNull AbsMaterialPreference.OverrideOnClickListener outputPathListener, @Nullable Prefs.KeyWithDefault<Boolean> startAtBootPref, @Nullable Prefs.KeyWithDefault<Boolean> startWithAppPref, boolean rpcEnabled) {
        setup(0, outputPathListener, startAtBootPref, startWithAppPref, rpcEnabled);
    }

    public void lockPreferences(boolean set) {
        if (set) {
            generalCategory.setVisibility(GONE);
            if (rpcEnabled) rpcCategory.setVisibility(GONE);
            notificationsCategory.setVisibility(GONE);
        } else {
            generalCategory.setVisibility(VISIBLE);
            if (rpcEnabled) rpcCategory.setVisibility(VISIBLE);
            notificationsCategory.setVisibility(VISIBLE);
        }
    }

    public void setOutputPathValue(@Nullable String path) {
        outputPath.setValue(path);
    }

    public void appendLogEntry(@NonNull LogEntry entry) {
        if (logsContainer != null) {
            logsContainer.setVisibility(View.VISIBLE);
            logsMessage.setVisibility(View.GONE);
            logsContainer.addView(entry.createView(getContext()), logsContainer.getChildCount());
            if (logsContainer.getChildCount() > Aria2Ui.MAX_LOG_LINES)
                logsContainer.removeViewAt(0);
        }
    }

    public static class LogEntry {
        private final Type type;
        private final String text;

        public LogEntry(@NonNull Type type, @NonNull String text) {
            this.type = type;
            this.text = text;
        }

        @SuppressLint("SetTextI18n")
        @NonNull
        View createView(@NonNull Context context) {
            LinearLayout layout = (LinearLayout) View.inflate(context, R.layout.aria2lib_log_entry, null);
            TextView level = layout.findViewById(R.id.logEntry_level);
            TextView msg = layout.findViewById(R.id.logEntry_msg);

            msg.setText(text);
            msg.setSingleLine(true);
            msg.setTag(true);
            msg.setEllipsize(TextUtils.TruncateAt.END);

            switch (type) {
                case INFO:
                    level.setText("INFO: ");
                    CommonUtils.setTextColor(level, R.color.logLevel_info);
                    break;
                case WARNING:
                    level.setText("WARNING: ");
                    CommonUtils.setTextColor(level, R.color.logLevel_warn);
                    break;
                case ERROR:
                    level.setText("ERROR: ");
                    CommonUtils.setTextColor(level, R.color.logLevel_error);
                    break;
            }

            layout.setOnClickListener(view -> {
                if ((Boolean) msg.getTag()) {
                    msg.setSingleLine(false);
                    msg.setTag(false);
                } else {
                    msg.setSingleLine(true);
                    msg.setTag(true);
                }
            });

            return layout;
        }

        public enum Type {
            INFO, WARNING, ERROR
        }
    }

    public static class OutputPathSelector implements AbsMaterialPreference.OverrideOnClickListener {
        private final Activity activity;
        private final int requestCode;

        public OutputPathSelector(@NonNull Activity activity, int requestCode) {
            this.activity = activity;
            this.requestCode = requestCode;
        }

        @Override
        public boolean onClick(View v) {
            if (activity.isFinishing() || activity.isDestroyed()) return false;

            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.startActivityForResult(intent, requestCode);
                return true;
            } catch (ActivityNotFoundException ex) {
                Toaster.with(activity).message(R.string.noOpenTree).show();
                return false;
            }
        }
    }
}
