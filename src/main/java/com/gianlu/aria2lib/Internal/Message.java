package com.gianlu.aria2lib.Internal;

import com.gianlu.commonutils.Logging;

import java.util.LinkedList;
import java.util.Queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Message {
    private static final Queue<Message> cache = new LinkedList<>();

    static {
        for (int i = 0; i < 10; i++)
            cache.add(new Message());
    }

    private Object o;
    private int i;
    private Type type;
    private boolean recycled = false;

    private Message() {
    }

    @NonNull
    public synchronized static Message obtain(@NonNull Type type, int i, Object o) {
        Message msg = cache.isEmpty() ? null : cache.poll();
        if (msg == null) msg = new Message();
        msg.recycled = false;
        msg.type = type;
        msg.i = i;
        msg.o = o;
        return msg;
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
        if (!recycled) {
            cache.add(this);
            recycled = true;
        }
    }

    @Override
    public String toString() {
        return "Message{o=" + o + ", i=" + i + ", type=" + type + '}';
    }

    @NonNull
    public Logging.LogLine toLogLine(@NonNull String aria2Version) {
        return new Logging.LogLine(System.currentTimeMillis(), aria2Version, type.getType(), toString());
    }

    public enum Type {
        PROCESS_TERMINATED, PROCESS_STARTED, MONITOR_FAILED, MONITOR_UPDATE,
        PROCESS_WARN, PROCESS_ERROR, PROCESS_INFO;

        @NonNull
        private Logging.LogLine.Type getType() {
            switch (this) {
                case MONITOR_UPDATE:
                case PROCESS_INFO:
                case PROCESS_STARTED:
                case PROCESS_TERMINATED:
                    return Logging.LogLine.Type.INFO;
                case PROCESS_WARN:
                    return Logging.LogLine.Type.WARNING;
                default:
                case PROCESS_ERROR:
                case MONITOR_FAILED:
                    return Logging.LogLine.Type.ERROR;
            }
        }
    }
}
