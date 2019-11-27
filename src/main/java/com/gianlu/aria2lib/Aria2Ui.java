package com.gianlu.aria2lib;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.gianlu.aria2lib.internal.Aria2;
import com.gianlu.aria2lib.internal.Aria2Service;
import com.gianlu.aria2lib.internal.Message;
import com.gianlu.commonutils.logging.Logging;
import com.gianlu.commonutils.preferences.Prefs;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Aria2Ui {
    public static final int MAX_LOG_LINES = 100;
    private final Aria2 aria2;
    private final Context context;
    private final Listener listener;
    private final LocalBroadcastManager broadcastManager;
    private final List<LogMessage> messages = new ArrayList<>(MAX_LOG_LINES);
    private ServiceBroadcastReceiver receiver;
    private Messenger messenger;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            messenger = new Messenger(service);

            askForStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            messenger = null;
        }
    };

    public Aria2Ui(@NonNull Context context, @Nullable Listener listener) {
        this.context = context;
        this.listener = listener;
        this.aria2 = Aria2.get();
        this.broadcastManager = LocalBroadcastManager.getInstance(context);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Aria2Service.BROADCAST_MESSAGE);
        filter.addAction(Aria2Service.BROADCAST_STATUS);
        broadcastManager.registerReceiver(receiver = new ServiceBroadcastReceiver(), filter);
    }

    public static void provider(@NonNull Class<? extends BareConfigProvider> providerClass) {
        Prefs.putString(Aria2PK.BARE_CONFIG_PROVIDER, providerClass.getCanonicalName());
    }

    public void bind() {
        if (messenger != null) return;

        context.bindService(new Intent(context, Aria2Service.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void finalize() {
        if (receiver != null) broadcastManager.unregisterReceiver(receiver);
    }

    public void unbind() {
        if (messenger == null) return;

        try {
            context.unbindService(serviceConnection);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void askForStatus() {
        try {
            if (messenger != null)
                messenger.send(android.os.Message.obtain(null, Aria2Service.MESSAGE_STATUS));
        } catch (RemoteException ex) {
            Logging.log(ex);
        }
    }

    public void loadEnv(@NonNull Context context) throws BadEnvironmentException {
        aria2.loadEnv(new File(context.getApplicationInfo().nativeLibraryDir, "libaria2c.so"),
                new File(context.getFilesDir(), "session"));
    }

    @NonNull
    public String version() throws IOException, BadEnvironmentException {
        return aria2.version();
    }

    public void startService() {
        bind();

        try {
            Aria2Service.startService(context);
        } catch (SecurityException ex) {
            if (listener != null) {
                publishMessage(new LogMessage(Message.Type.PROCESS_ERROR, 0, ex.getMessage()));
                listener.updateUi(false);
            }

            Logging.log(ex);
        }
    }

    public void startServiceFromReceiver() {
        try {
            Aria2Service.startService(context);
        } catch (SecurityException ex) {
            if (listener != null) {
                publishMessage(new LogMessage(Message.Type.PROCESS_ERROR, 0, ex.getMessage()));
                listener.updateUi(false);
            }

            Logging.log(ex);
        }
    }

    public void stopService() {
        if (messenger != null) {
            try {
                messenger.send(android.os.Message.obtain(null, Aria2Service.MESSAGE_STOP));
                return;
            } catch (RemoteException ex) {
                Logging.log(ex);
            }
        }

        Aria2Service.stopService(context);
    }

    public boolean delete() {
        return aria2.delete();
    }

    public boolean hasEnv() {
        return aria2.hasEnv();
    }

    @UiThread
    public void updateLogs(@NonNull Listener listener) {
        listener.onUpdateLogs(Collections.unmodifiableList(messages));
    }

    private void publishMessage(@NonNull LogMessage msg) {
        if (msg.type != Message.Type.MONITOR_UPDATE) {
            if (messages.size() >= MAX_LOG_LINES)
                messages.remove(0);

            messages.add(msg);
        }

        if (listener != null) listener.onMessage(msg);
    }

    @UiThread
    public interface Listener {
        void onUpdateLogs(@NonNull List<LogMessage> msg);

        void onMessage(@NonNull LogMessage msg);

        void updateUi(boolean on);
    }

    public static class LogMessage {
        public final Message.Type type;
        public final int i;
        public final Serializable o;

        private LogMessage(@NonNull Message.Type type, int i, @Nullable Serializable o) {
            this.type = type;
            this.i = i;
            this.o = o;
        }
    }

    private class ServiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), Aria2Service.BROADCAST_MESSAGE)) {
                Message.Type type = (Message.Type) intent.getSerializableExtra("type");
                int i = intent.getIntExtra("i", 0);
                Serializable o = intent.getSerializableExtra("o");
                publishMessage(new LogMessage(type, i, o));
            } else if (Objects.equals(intent.getAction(), Aria2Service.BROADCAST_STATUS)) {
                if (listener != null) listener.updateUi(intent.getBooleanExtra("on", false));
            }
        }
    }
}
