package com.gianlu.aria2lib.internal;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.gianlu.aria2lib.Aria2PK;
import com.gianlu.aria2lib.BadEnvironmentException;
import com.gianlu.aria2lib.BareConfigProvider;
import com.gianlu.aria2lib.R;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.analytics.AnalyticsApplication;
import com.gianlu.commonutils.preferences.Prefs;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

public final class Aria2Service extends Service implements Aria2.MessageListener {
    public static final String ACTION_START_SERVICE = Aria2Service.class.getCanonicalName() + ".START";
    public static final String ACTION_STOP_SERVICE = Aria2Service.class.getCanonicalName() + ".STOP";
    public static final String BROADCAST_MESSAGE = Aria2Service.class.getCanonicalName() + ".BROADCAST_MESSAGE";
    public static final String BROADCAST_STATUS = Aria2Service.class.getCanonicalName() + ".BROADCAST_STATUS";
    public static final int MESSAGE_STATUS = 2;
    public static final int MESSAGE_STOP = 3;
    private static final int MESSAGE_START = 4;
    private static final String CHANNEL_ID = "aria2service";
    private static final String SERVICE_NAME = "Service for aria2";
    private static final int NOTIFICATION_ID = 69;
    private static final String TAG = Aria2Service.class.getSimpleName();
    private final HandlerThread serviceThread = new HandlerThread("aria2-service");
    private Messenger messenger;
    private LocalBroadcastManager broadcastManager;
    private Aria2 aria2;
    private NotificationCompat.Builder defaultNotification;
    private NotificationManager notificationManager;
    private long startTime = System.currentTimeMillis();
    private BareConfigProvider provider;
    private final SharedPreferences.OnSharedPreferenceChangeListener reinitializeNotificationListener = (sharedPreferences, key) -> {
        if (key.equals(Aria2PK.SHOW_PERFORMANCE.key()))
            initializeNotification();
    };

    public static void startService(@NonNull Context context) {
        new Handler(Looper.getMainLooper()).post(() -> {
            ContextCompat.startForegroundService(context, new Intent(context, Aria2Service.class)
                    .setAction(ACTION_START_SERVICE));

            AnalyticsApplication.setCrashlyticsLong("aria2service_intentTime", System.currentTimeMillis());
        });
    }

    public static void stopService(@NonNull Context context) {
        context.startService(new Intent(context, Aria2Service.class)
                .setAction(ACTION_STOP_SERVICE));
    }

    @NonNull
    private static BareConfigProvider loadProvider() {
        String classStr = Prefs.getString(Aria2PK.BARE_CONFIG_PROVIDER, null);
        if (classStr == null) throw new IllegalStateException("Provider not initialized!");

        try {
            Class<?> clazz = Class.forName(classStr);
            return (BareConfigProvider) clazz.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
            throw new RuntimeException("Failed initializing provider!", ex);
        }
    }

    @NonNull
    private PendingIntent getStopServiceIntent() {
        return PendingIntent.getService(this, 1, new Intent(this, Aria2Service.class)
                .setAction(ACTION_STOP_SERVICE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void initializeNotification() {
        defaultNotification = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                .setContentTitle(SERVICE_NAME)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(provider.notificationIcon())
                .setContentIntent(PendingIntent.getActivity(this, 2, new Intent(this, provider.actionClass())
                        .putExtra("openFromNotification", true), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setContentText("aria2c is running...");

        if (!Prefs.getBoolean(Aria2PK.SHOW_PERFORMANCE))
            defaultNotification.addAction(R.drawable.baseline_clear_24, getString(R.string.stopService), getStopServiceIntent());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Prefs.init(this);

        aria2 = Aria2.get();
        aria2.addListener(this);
        serviceThread.start();
        broadcastManager = LocalBroadcastManager.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        provider = loadProvider();

        initializeNotification();

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(reinitializeNotificationListener);

        try {
            Log.d(TAG, aria2.version());
        } catch (BadEnvironmentException | IOException ex) {
            Log.e(TAG, "Failed getting aria2 version.", ex);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (messenger == null) messenger = new Messenger(new LocalHandler(this));
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (aria2 != null) aria2.removeListener(this);

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(reinitializeNotificationListener);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createChannel() {
        NotificationChannel chan = new NotificationChannel(CHANNEL_ID, SERVICE_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (service != null) service.createNotificationChannel(chan);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (Objects.equals(intent.getAction(), ACTION_START_SERVICE)) {
                AnalyticsApplication.setCrashlyticsLong("aria2service_intentReceivedTime", System.currentTimeMillis());

                try {
                    if (messenger == null) messenger = new Messenger(new LocalHandler(this));
                    messenger.send(Message.obtain(null, MESSAGE_START));
                    return flags == 1 ? START_STICKY : START_REDELIVER_INTENT;
                } catch (RemoteException ex) {
                    Log.e(TAG, "Failed starting executable on service thread!", ex);

                    try {
                        start();
                        return flags == 1 ? START_STICKY : START_REDELIVER_INTENT;
                    } catch (IOException | BadEnvironmentException exx) {
                        Log.e(TAG, "Still failed to start service.", exx);
                    }
                }
            } else if (Objects.equals(intent.getAction(), ACTION_STOP_SERVICE)) {
                stop();
            }
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    private void stop() {
        try {
            aria2.stop();
            stopForeground(true);
            dispatchStatus();
        } catch (RuntimeException ignored) {
        }
    }

    private void start() throws IOException, BadEnvironmentException {
        AnalyticsApplication.setCrashlyticsLong("aria2service_startedAt", System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel();
        startForeground(NOTIFICATION_ID, defaultNotification.build());
        if (aria2.start()) startTime = System.currentTimeMillis();

        dispatchStatus();

        AnalyticsApplication.setCrashlyticsLong("aria2service_startedAt_return", System.currentTimeMillis());
    }

    @Override
    public void onMessage(@NonNull com.gianlu.aria2lib.internal.Message msg) {
        dispatch(msg);

        if (msg.type() == com.gianlu.aria2lib.internal.Message.Type.MONITOR_UPDATE)
            updateMonitor((MonitorUpdate) msg.object());
    }

    private void updateMonitor(@Nullable MonitorUpdate update) {
        if (update == null || notificationManager == null || !aria2.isRunning()) {
            if (update != null) update.recycle();
            return;
        }

        RemoteViews layout = new RemoteViews(getPackageName(), R.layout.aria2lib_custom_notification);
        layout.setTextViewText(R.id.customNotification_runningTime, "Running time: " + CommonUtils.timeFormatter((System.currentTimeMillis() - startTime) / 1000));
        layout.setTextViewText(R.id.customNotification_pid, "PID: " + update.pid());
        layout.setTextViewText(R.id.customNotification_cpu, "CPU: " + update.cpu() + "%");
        layout.setTextViewText(R.id.customNotification_memory, "Memory: " + CommonUtils.dimensionFormatter(update.rss(), false));
        layout.setImageViewResource(R.id.customNotification_icon, provider.launcherIcon());
        layout.setImageViewResource(R.id.customNotification_stop, R.drawable.baseline_clear_24);
        layout.setOnClickPendingIntent(R.id.customNotification_stop, getStopServiceIntent());
        defaultNotification.setCustomContentView(layout);

        notificationManager.notify(NOTIFICATION_ID, defaultNotification.build());

        update.recycle();
    }

    private void dispatch(@NonNull com.gianlu.aria2lib.internal.Message msg) {
        Intent intent = new Intent(BROADCAST_MESSAGE);
        intent.putExtra("type", msg.type());
        intent.putExtra("i", msg.integer());
        if (msg.object() instanceof Serializable) intent.putExtra("o", (Serializable) msg.object());
        broadcastManager.sendBroadcast(intent);
    }

    private void dispatchStatus() {
        if (broadcastManager == null) return;

        Intent intent = new Intent(BROADCAST_STATUS);
        intent.putExtra("on", aria2.isRunning());
        broadcastManager.sendBroadcast(intent);
    }

    private static class LocalHandler extends Handler {
        private final Aria2Service service;

        LocalHandler(@NonNull Aria2Service service) {
            super(service.serviceThread.getLooper());
            this.service = service;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATUS:
                    service.dispatchStatus();
                    break;
                case MESSAGE_STOP:
                    service.stop();
                    service.stopSelf();
                    break;
                case MESSAGE_START:
                    try {
                        service.start();
                    } catch (IOException | BadEnvironmentException ex) {
                        Log.e(TAG, "Failed starting service.", ex);
                        service.stop();
                        service.stopSelf();
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
