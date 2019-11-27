package com.gianlu.aria2lib.internal;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Queue;

public final class MonitorUpdate implements Serializable {
    private static final Queue<MonitorUpdate> cache = new LinkedList<>();

    static {
        for (int i = 0; i < 10; i++)
            cache.add(new MonitorUpdate());
    }

    private boolean recycled = false;
    private int rss;
    private String cpu;
    private int pid;

    private MonitorUpdate() {
    }

    @NonNull
    public static MonitorUpdate obtain(int pid, @NonNull String cpu, int rss) {
        MonitorUpdate msg = cache.poll();
        if (msg == null) msg = new MonitorUpdate();
        msg.recycled = false;
        msg.pid = pid;
        msg.cpu = cpu;
        msg.rss = rss;
        return msg;
    }

    public void recycle() {
        if (!recycled) {
            cache.add(this);
            recycled = true;
        }
    }

    public int pid() {
        return pid;
    }

    @NonNull
    public String cpu() {
        return cpu;
    }

    public int rss() {
        return rss;
    }
}
