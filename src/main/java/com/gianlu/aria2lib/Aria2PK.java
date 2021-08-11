package com.gianlu.aria2lib;

import android.os.Environment;

import com.gianlu.commonutils.preferences.CommonPK;
import com.gianlu.commonutils.preferences.Prefs;

public abstract class Aria2PK extends CommonPK {
    public static final Prefs.KeyWithDefault<Integer> NOTIFICATION_UPDATE_DELAY = new Prefs.KeyWithDefault<>("updateDelay", 1);
    public static final Prefs.KeyWithDefault<Boolean> SHOW_PERFORMANCE = new Prefs.KeyWithDefault<>("showPerformance", true);
    public static final Prefs.KeyWithDefault<Integer> RPC_PORT = new Prefs.KeyWithDefault<>("rpcPort", 6800);
    public static final Prefs.KeyWithDefault<String> RPC_TOKEN = new Prefs.KeyWithDefault<>("rpcToken", "aria2");
    public static final Prefs.KeyWithDefault<Boolean> RPC_ALLOW_ORIGIN_ALL = new Prefs.KeyWithDefault<>("allowOriginAll", false);
    public static final Prefs.KeyWithDefault<Boolean> RPC_LISTEN_ALL = new Prefs.KeyWithDefault<>("listenAll", false);
    public static final Prefs.KeyWithDefault<Boolean> CHECK_CERTIFICATE = new Prefs.KeyWithDefault<>("checkCertificate", false);
    public static final Prefs.KeyWithDefault<String> OUTPUT_DIRECTORY = new Prefs.KeyWithDefault<>("outputPath", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
    public static final Prefs.Key CUSTOM_OPTIONS = new Prefs.Key("customOptions");
    public static final Prefs.KeyWithDefault<Boolean> SAVE_SESSION = new Prefs.KeyWithDefault<>("saveSession", true);
    public static final Prefs.Key BARE_CONFIG_PROVIDER = new Prefs.Key("bareConfigProvider");
}
