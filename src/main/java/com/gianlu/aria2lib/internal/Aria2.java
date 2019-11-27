package com.gianlu.aria2lib.internal;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gianlu.aria2lib.Aria2PK;
import com.gianlu.aria2lib.BadEnvironmentException;
import com.gianlu.commonutils.logging.Logging;
import com.gianlu.commonutils.preferences.Prefs;
import com.gianlu.commonutils.preferences.json.JsonStoring;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Aria2 {
    private static final Pattern INFO_MESSAGE_PATTERN = Pattern.compile("^\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2} \\[.+] (.+)$");
    private static Aria2 instance;
    private final MessageHandler messageHandler;
    private final Object processLock = new Object();
    private Env env;
    private Monitor monitor;
    private StreamWatcher errorWatcher;
    private StreamWatcher inputWatcher;
    private Process currentProcess;

    private Aria2() {
        messageHandler = new MessageHandler();
        new Thread(messageHandler).start();
    }

    @NonNull
    public static Aria2 get() {
        if (instance == null) instance = new Aria2();
        return instance;
    }

    @NonNull
    private static String startCommandForLog(@NonNull String exec, String... params) {
        StringBuilder builder = new StringBuilder(exec);
        for (String param : params) builder.append(' ').append(param);
        return builder.toString();
    }

    private static boolean waitFor(@NonNull Process process, int timeout, @NonNull TimeUnit unit) throws InterruptedException {
        long startTime = System.nanoTime();
        long rem = unit.toNanos(timeout);

        do {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException ex) {
                if (rem > 0)
                    Thread.sleep(Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
            }

            rem = unit.toNanos(timeout) - (System.nanoTime() - startTime);
        } while (rem > 0);
        return false;
    }

    void logDebugInfo() {
        if (env == null) {
            Logging.log("Cannot retrieve debug info without environment.", false);
            return;
        }

        try {
            Process process = Runtime.getRuntime().exec("ls -al " + env.baseDir.getAbsolutePath());
            process.waitFor();

            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null)
                    result.append(line).append('\n');
            }

            Logging.log(result.toString(), false);
        } catch (IOException | InterruptedException ex) {
            Logging.log("Failed retrieving debug info.", ex);
        }
    }

    void addListener(@NonNull MessageListener listener) {
        messageHandler.listeners.add(listener);
    }

    void removeListener(@NonNull MessageListener listener) {
        messageHandler.listeners.remove(listener);
    }

    public boolean hasEnv() {
        return env != null && env.exec.exists();
    }

    @NonNull
    public String version() throws BadEnvironmentException, IOException {
        if (env == null)
            throw new BadEnvironmentException("Missing environment!");

        try {
            Process process = execWithParams(false, "-v");
            process.waitFor();
            return new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
        } catch (InterruptedException ex) {
            throw new IOException(ex);
        }
    }

    @NonNull
    private Process execWithParams(boolean redirect, String... params) throws BadEnvironmentException, IOException {
        if (env == null)
            throw new BadEnvironmentException("Missing environment!");

        String[] cmdline = new String[params.length + 1];
        cmdline[0] = env.execPath();
        System.arraycopy(params, 0, cmdline, 1, params.length);
        Process process = new ProcessBuilder(cmdline).redirectErrorStream(redirect).start();
        if (process == null) throw new IOException("Process is null!");
        return process;
    }

    public void loadEnv(@NonNull File env, @NonNull File session) throws BadEnvironmentException {
        if (!env.isDirectory())
            throw new BadEnvironmentException(env.getAbsolutePath() + " is not a directory!");

        File exec = new File(env, "aria2c");
        if (!exec.exists())
            throw new BadEnvironmentException(exec.getAbsolutePath() + " doesn't exists!");

        if (!exec.canExecute() && !exec.setExecutable(true))
            throw new BadEnvironmentException(exec.getAbsolutePath() + " can't be executed!");

        if (session.exists()) {
            if (!session.canRead() && !session.setReadable(true))
                throw new BadEnvironmentException(session.getAbsolutePath() + " can't be read!");
        } else {
            try {
                if (!session.createNewFile())
                    throw new BadEnvironmentException(session.getAbsolutePath() + " can't be created!");
            } catch (IOException ex) {
                throw new BadEnvironmentException(ex);
            }
        }

        this.env = new Env(env, exec, session);
    }

    boolean start() throws BadEnvironmentException, IOException {
        if (currentProcess != null) {
            postMessage(Message.obtain(Message.Type.PROCESS_STARTED, "[already started]"));
            return false;
        }

        if (env == null)
            throw new BadEnvironmentException("Missing environment!");

        reloadEnv();

        String execPath = env.execPath();
        String[] params = env.startArgs();

        synchronized (processLock) {
            currentProcess = execWithParams(true, params);
            new Thread(new Waiter(currentProcess), "aria2android-waiterThread").start();
            new Thread(this.inputWatcher = new StreamWatcher(currentProcess.getInputStream()), "aria2-android-inputWatcherThread").start();
            new Thread(this.errorWatcher = new StreamWatcher(currentProcess.getErrorStream()), "aria2-android-errorWatcherThread").start();
        }

        if (Prefs.getBoolean(Aria2PK.SHOW_PERFORMANCE))
            new Thread(this.monitor = new Monitor(), "aria2android-monitorThread").start();

        postMessageDelayed(Message.obtain(Message.Type.PROCESS_STARTED, startCommandForLog(execPath, params)), 500 /* Ensure service is started */);
        return true;
    }

    private void reloadEnv() throws BadEnvironmentException {
        if (env == null)
            throw new BadEnvironmentException("Missing environment!");

        loadEnv(env.baseDir, env.session);
    }

    private void processTerminated(int code) {
        postMessage(Message.obtain(Message.Type.PROCESS_TERMINATED, code));

        if (monitor != null) {
            monitor.close();
            monitor = null;
        }

        if (errorWatcher != null) {
            errorWatcher.close();
            errorWatcher = null;
        }

        if (inputWatcher != null) {
            inputWatcher.close();
            inputWatcher = null;
        }

        stop();
    }

    private void monitorFailed(@NonNull Exception ex) {
        postMessage(Message.obtain(Message.Type.MONITOR_FAILED, ex));
        Logging.log(ex);
    }

    private void postMessage(@NonNull Message message) {
        message.delay = 0;
        messageHandler.queue.add(message);
    }

    private void postMessageDelayed(@NonNull Message message, int millis) {
        message.delay = millis;
        messageHandler.queue.add(message);
    }

    private void handleStreamMessage(@NonNull String line) {
        if (line.startsWith("WARNING: ")) {
            postMessage(Message.obtain(Message.Type.PROCESS_WARN, line.substring(9)));
        } else if (line.startsWith("ERROR: ")) {
            postMessage(Message.obtain(Message.Type.PROCESS_ERROR, line.substring(7)));
        } else {
            String clean;
            Matcher matcher = INFO_MESSAGE_PATTERN.matcher(line);
            if (matcher.find()) clean = matcher.group(1);
            else clean = line;
            postMessage(Message.obtain(Message.Type.PROCESS_INFO, clean));
        }
    }

    void stop() {
        synchronized (processLock) {
            if (currentProcess != null) {
                currentProcess.destroy();
                currentProcess = null;
            }
        }
    }

    public boolean delete() {
        stop();
        return env.delete();
    }

    public boolean isRunning() {
        return currentProcess != null;
    }

    public interface MessageListener {
        void onMessage(@NonNull Message msg);
    }

    private static class MessageHandler implements Runnable, Closeable {
        private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
        private final List<MessageListener> listeners = new ArrayList<>();
        private volatile boolean shouldStop = false;

        @Override
        public void run() {
            while (!shouldStop) {
                try {
                    Message msg = queue.take();

                    if (msg.delay > 0)
                        Thread.sleep(msg.delay);

                    for (MessageListener listener : new ArrayList<>(listeners))
                        listener.onMessage(msg);

                    msg.recycle();
                } catch (InterruptedException ex) {
                    close();
                    Logging.log(ex);
                }
            }
        }

        @Override
        public void close() {
            shouldStop = true;
        }
    }

    private static class Env {
        private final File baseDir;
        private final File exec;
        private final File session;
        private final Map<String, String> params;

        Env(@NonNull File baseDir, @NonNull File exec, @NonNull File session) {
            this.baseDir = baseDir;
            this.exec = exec;
            this.session = session;
            this.params = new HashMap<>();

            // Can be overridden
            params.put("--check-certificate", "false");
            if (Prefs.getBoolean(Aria2PK.SAVE_SESSION))
                params.put("--save-session-interval", "30");

            loadCustomOptions(params);

            params.put("--daemon", "false");
            params.put("--enable-color", "false");
            params.put("--rpc-listen-all", "true");
            params.put("--enable-rpc", "true");
            params.put("--rpc-secret", Prefs.getString(Aria2PK.RPC_TOKEN));
            params.put("--rpc-listen-port", String.valueOf(Prefs.getInt(Aria2PK.RPC_PORT, 6800)));
            params.put("--dir", Prefs.getString(Aria2PK.OUTPUT_DIRECTORY));

            if (Prefs.getBoolean(Aria2PK.SAVE_SESSION)) {
                params.put("--input-file", session.getAbsolutePath());
                params.put("--save-session", session.getAbsolutePath());
            }

            if (Prefs.getBoolean(Aria2PK.RPC_ALLOW_ORIGIN_ALL))
                params.put("--rpc-allow-origin-all", "true");
        }

        private static void loadCustomOptions(@NonNull Map<String, String> options) {
            try {
                JSONObject obj = JsonStoring.intoPrefs().getJsonObject(Aria2PK.CUSTOM_OPTIONS);
                if (obj == null) return;

                Iterator<String> iterator = obj.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    options.put("--" + key, obj.getString(key));
                }
            } catch (JSONException ex) {
                Logging.log(ex);
            }
        }

        @NonNull
        String[] startArgs() {
            String[] args = new String[params.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty())
                    args[i] = entry.getKey();
                else
                    args[i] = entry.getKey() + "=" + entry.getValue();

                i++;
            }
            return args;
        }

        @NonNull
        String execPath() {
            return exec.getAbsolutePath();
        }

        boolean delete() {
            boolean fine = true;
            for (File file : baseDir.listFiles())
                if (!file.delete()) fine = false;
            return fine;
        }
    }

    private abstract static class TopParser {
        static final Pattern TOP_OLD_PATTERN = Pattern.compile("(\\d*?)\\s+(\\d*?)\\s+(\\d*?)%\\s(.)\\s+(\\d*?)\\s+(\\d*?)K\\s+(\\d*?)K\\s+(..)\\s(.*?)\\s+(.*)$");
        static final Pattern TOP_NEW_PATTERN = Pattern.compile("(\\d+)\\s+(\\d+\\.\\d+)\\s+([\\d|.]+?.)\\s+(.*)$");
        static final TopParser OLD_PARSER = new TopParser(TOP_OLD_PATTERN, 1, 3, 7) {
            @Override
            boolean matches(@NonNull String line) {
                return line.endsWith("aria2c");
            }

            @NonNull
            @Override
            String getCommand(int delaySec) {
                return "top -d " + delaySec;
            }

            @Override
            int getMemoryBytes(@NonNull String match) {
                return Integer.parseInt(match) * 1024;
            }
        };
        static final TopParser NEW_PARSER = new TopParser(TOP_NEW_PATTERN, 1, 2, 3) {
            @Override
            int getMemoryBytes(@NonNull String match) {
                int multiplier;
                char lastChar = match.charAt(match.length() - 1);
                if (Character.isAlphabetic(lastChar)) {
                    switch (lastChar) {
                        case 'K':
                            multiplier = 1024;
                            break;
                        case 'M':
                            multiplier = 1024 * 1024;
                            break;
                        case 'G':
                            multiplier = 1024 * 1024 * 1024;
                            break;
                        default:
                            multiplier = 1;
                            break;
                    }
                } else {
                    multiplier = 1;
                }

                return (int) (Float.parseFloat(match.substring(0, match.length() - 1)) * multiplier);
            }

            @Override
            boolean matches(@NonNull String line) {
                return line.contains("aria2c");
            }

            @SuppressLint("DefaultLocale")
            @NonNull
            @Override
            String getCommand(int delaySec) {
                return String.format("top -d %d -q -b -o PID,%%CPU,RES,CMDLINE", delaySec);
            }
        };
        private final Pattern pattern;
        private final int[] pidCpuRss;

        TopParser(@NonNull Pattern pattern, int... pidCpuRss) {
            this.pattern = pattern;
            this.pidCpuRss = pidCpuRss;
            if (pidCpuRss.length != 3) throw new IllegalArgumentException();
        }

        @Nullable
        final MonitorUpdate parseLine(@NonNull String line) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    return MonitorUpdate.obtain(Integer.parseInt(matcher.group(pidCpuRss[0])), matcher.group(pidCpuRss[1]), getMemoryBytes(matcher.group(pidCpuRss[2])));
                } catch (Exception ex) {
                    Logging.log("Failed parsing `top` line: " + line, ex);
                }
            }

            return null;
        }

        abstract int getMemoryBytes(@NonNull String match);

        abstract boolean matches(@NonNull String line);

        @NonNull
        abstract String getCommand(int delaySec);
    }

    private class StreamWatcher implements Runnable, Closeable {
        private final InputStream stream;
        private volatile boolean shouldStop = false;

        StreamWatcher(@NonNull InputStream stream) {
            this.stream = stream;
        }

        @Override
        public void run() {
            try (Scanner scanner = new Scanner(stream)) {
                while (!shouldStop && scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (!line.isEmpty()) handleStreamMessage(line);
                }
            }
        }

        @Override
        public void close() {
            shouldStop = true;
        }
    }

    private class Monitor implements Runnable, Closeable {
        private final byte[] INVALID_STRING = "Invalid argument".getBytes();
        private volatile boolean shouldStop = false;

        @Nullable
        private TopParser selectPattern() throws IOException, InterruptedException {
            Process process = Runtime.getRuntime().exec("top --version");

            if (waitFor(process, 1000, TimeUnit.MILLISECONDS)) {
                int exitCode = process.exitValue();
                if (exitCode != 0) {

                    byte[] buffer = new byte[INVALID_STRING.length];
                    if (buffer.length != process.getErrorStream().read(buffer) || !Arrays.equals(buffer, INVALID_STRING)) {
                        Logging.log(String.format(Locale.getDefault(), "Couldn't identify `top` version. {invalidString: %s, exitCode: %d}", new String(buffer), exitCode), true);
                        return null;
                    } else {
                        return TopParser.OLD_PARSER;
                    }
                } else {
                    return TopParser.NEW_PARSER;
                }
            } else {
                Logging.log("Couldn't identify `top` version, process didn't exit within 1000ms.", true);
                return null;
            }
        }

        @Override
        public void run() {
            TopParser parser;
            try {
                parser = selectPattern();
                if (parser == null) {
                    postMessage(Message.obtain(Message.Type.MONITOR_FAILED));
                    return;
                }
            } catch (IOException | InterruptedException ex) {
                Logging.log("Couldn't find suitable pattern for `top`.", ex);
                return;
            }

            Process process = null;
            try {
                process = Runtime.getRuntime().exec(parser.getCommand(Prefs.getInt(Aria2PK.NOTIFICATION_UPDATE_DELAY, 1)));
                try (Scanner scanner = new Scanner(process.getInputStream())) {
                    while (!shouldStop && scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        if (parser.matches(line)) {
                            MonitorUpdate update = parser.parseLine(line);
                            if (update != null)
                                postMessage(Message.obtain(Message.Type.MONITOR_UPDATE, update));
                        }
                    }
                }
            } catch (IOException ex) {
                monitorFailed(ex);
            } finally {
                if (process != null) process.destroy();
            }
        }

        @Override
        public void close() {
            shouldStop = true;
        }
    }

    private class Waiter implements Runnable {
        private final Process process;

        Waiter(@NonNull Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try {
                int exit = process.waitFor();
                processTerminated(exit);
            } catch (InterruptedException ex) {
                processTerminated(999);
                Logging.log(ex);
            }
        }
    }
}
