package com.gianlu.aria2lib.internal;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedList;
import java.util.Queue;

public final class Message {
    private static final Queue<Message> cache = new LinkedList<>();

    static {
        for (int i = 0; i < 10; i++)
            cache.add(new Message());
    }

    public int delay;
    private Object o;
    private int i;
    private Type type;
    private boolean recycled = false;

    private Message() {
    }

    @NonNull
    public static Message obtain(@NonNull Type type, Object o) {
        return obtain(type, 0, o);
    }

    @NonNull
    public static Message obtain(@NonNull Type type, int i) {
        return obtain(type, i, null);
    }

    @NonNull
    public static Message obtain(@NonNull Type type) {
        return obtain(type, 0, null);
    }

    @NonNull
    public static Message obtain(@NonNull Type type, int i, Object o) {
        synchronized (cache) {
            Message msg = cache.isEmpty() ? null : cache.poll();
            if (msg == null) msg = new Message();
            msg.recycled = false;
            msg.type = type;
            msg.i = i;
            msg.o = o;
            return msg;
        }
    }

    @NonNull
    public Type type() {
        return type;
    }

    public int integer() {
        return i;
    }

    @Nullable
    public Object object() {
        return o;
    }

    public void recycle() {
        synchronized (cache) {
            if (!recycled) {
                cache.add(this);
                recycled = true;
            }
        }
    }

    @Override
    public String toString() {
        return "Message{o=" + o + ", i=" + i + ", type=" + type + '}';
    }

    public void log(@NonNull String tag) {
        int p = type.getPriority();
        if (p != -1) Log.println(p, tag, toString());
    }

    public enum Type {
        PROCESS_TERMINATED, PROCESS_STARTED, MONITOR_FAILED, MONITOR_UPDATE,
        PROCESS_WARN, PROCESS_ERROR, PROCESS_INFO;

        private int getPriority() {
            switch (this) {
                case MONITOR_UPDATE:
                    return -1;
                case PROCESS_INFO:
                case PROCESS_STARTED:
                case PROCESS_TERMINATED:
                    return Log.INFO;
                case PROCESS_WARN:
                    return Log.WARN;
                default:
                case PROCESS_ERROR:
                case MONITOR_FAILED:
                    return Log.ERROR;
            }
        }
    }
}
