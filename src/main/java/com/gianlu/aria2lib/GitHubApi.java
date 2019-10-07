package com.gianlu.aria2lib;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GitHubApi {
    private static GitHubApi instance;
    private final Handler handler;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private GitHubApi() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    @NonNull
    public static GitHubApi get() {
        if (instance == null) instance = new GitHubApi();
        return instance;
    }

    @NonNull
    private static String read(@NonNull InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder builder = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null)
            builder.append(line);

        return builder.toString();
    }

    @NonNull
    private static SimpleDateFormat getDateParser() {
        return new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'", Locale.getDefault());
    }

    void execute(@NonNull Runnable runnable) {
        executorService.execute(runnable);
    }

    void inputStream(@NonNull String url, @NonNull InputStreamWorker worker) {
        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = basicRequestSync(url);
                worker.work(conn.getInputStream());
            } catch (Exception ex) {
                worker.exception(ex);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @WorkerThread
    private HttpURLConnection basicRequestSync(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.connect();

        if (conn.getResponseCode() == 200)
            return conn;
        else
            throw new IOException(String.format("%d: %s", conn.getResponseCode(), conn.getResponseMessage()));
    }

    public void getReleases(@NonNull String owner, @NonNull String repo, @NonNull OnResult<List<Release>> listener) {
        inputStream("https://api.github.com/repos/" + owner + "/" + repo + "/releases", new InputStreamWorker() {
            @Override
            public void work(@NonNull InputStream in) throws IOException, JSONException, ParseException {
                JSONArray array = new JSONArray(read(in));
                final List<Release> releases = new ArrayList<>();
                for (int i = 0; i < array.length(); i++)
                    releases.add(new Release(array.getJSONObject(i), owner, repo));

                handler.post(() -> listener.onResult(releases));
            }

            @Override
            public void exception(@NonNull Exception ex) {
                handler.post(() -> listener.onException(ex));
            }
        });
    }

    public void getRelease(@NonNull String owner, @NonNull String repo, int id, OnResult<Release> listener) {
        inputStream("https://api.github.com/repos/" + owner + "/" + repo + "/releases/" + id, new InputStreamWorker() {
            @Override
            public void work(@NonNull InputStream in) throws Exception {
                Release release = new Release(new JSONObject(read(in)), owner, repo);
                handler.post(() -> listener.onResult(release));
            }

            @Override
            public void exception(@NonNull Exception ex) {
                handler.post(() -> listener.onException(ex));
            }
        });
    }

    public enum Arch {
        ARMEABI_V7A("armeabi-v7a"), ARM64_V8A("arm64-v8a"),
        X86_64("x86_64"), X86("x86");

        private String abi;

        Arch(String abi) {
            this.abi = abi;
        }

        @Nullable
        public static Arch detect(@NonNull Release.Asset asset) {
            if (asset.source() == ReleaseSource.ARIA2_ORIGINAL) {
                if (asset.name.contains("arm")) return ARMEABI_V7A;
                else if (asset.name.contains("aarch64")) return ARM64_V8A;
                else return null;
            }

            for (Arch arch : values())
                if (asset.name.contains(arch.abi))
                    return arch;

            return null;
        }
    }

    public enum ReleaseSource {
        ARIA2_ORIGINAL, ARIA2_DEVGIANLU;

        private static void pickAssetsForOriginal(Release release, List<Release.Asset> assets) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if (!Objects.equals(abi, "arm64-v8a") && !Objects.equals(abi, "armeabi-v7a"))
                    continue;

                for (Release.Asset asset : release.assets) {
                    if (!asset.name.contains("android")) continue;

                    if (Objects.equals(abi, "arm64-v8a") && asset.name.contains("aarch64")) {
                        if (Build.VERSION.SDK_INT >= 29) assets.add(asset);
                    } else if (Objects.equals(abi, "armeabi-v7a") && asset.name.contains("arm")) {
                        assets.add(asset);
                    }
                }
            }
        }

        private static void pickAssetsForDevgianlu(Release release, List<Release.Asset> assets) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if (abi.equals("armeabi")) continue;

                for (Release.Asset asset : release.assets) {
                    if (asset.name.contains(abi)) {
                        if (abi.equals("x86")) {
                            if (!asset.name.contains("x86_64"))
                                assets.add(asset);
                        } else {
                            assets.add(asset);
                        }
                    }
                }
            }
        }

        @NonNull
        public String slug() {
            switch (this) {
                case ARIA2_ORIGINAL:
                    return "aria2/aria2";
                case ARIA2_DEVGIANLU:
                    return "devgianlu/aria2-android";
                default:
                    throw new IllegalArgumentException(String.valueOf(this));
            }
        }
    }

    @WorkerThread
    public interface InputStreamWorker {
        void work(@NonNull InputStream in) throws Exception;

        void exception(@NonNull Exception ex);
    }

    @UiThread
    public interface OnResult<E> {
        void onResult(@NonNull E result);

        void onException(@NonNull Exception ex);
    }

    public static class Release {
        public final int id;
        final long publishedAt;
        final ReleaseSource source;
        final List<Asset> assets;
        final String name;
        final String htmlUrl;

        Release(JSONObject obj, String owner, String repo) throws JSONException, ParseException {
            id = obj.getInt("id");
            name = obj.getString("name");
            htmlUrl = obj.getString("html_url");
            publishedAt = getDateParser().parse(obj.getString("published_at")).getTime();

            if (owner.equals("aria2") && repo.equals("aria2"))
                source = ReleaseSource.ARIA2_ORIGINAL;
            else if (owner.equals("devgianlu") && repo.equals("aria2-android"))
                source = ReleaseSource.ARIA2_DEVGIANLU;
            else
                throw new IllegalArgumentException(owner + "/" + repo);

            JSONArray assetsArray = obj.getJSONArray("assets");
            assets = new ArrayList<>(assetsArray.length());
            for (int i = 0; i < assetsArray.length(); i++)
                assets.add(new Asset(assetsArray.getJSONObject(i), this));
        }

        private static void getAllSupportedAssets(@NonNull Release release, List<Asset> assets) {
            switch (release.source) {
                case ARIA2_ORIGINAL:
                    ReleaseSource.pickAssetsForOriginal(release, assets);
                    break;
                case ARIA2_DEVGIANLU:
                    ReleaseSource.pickAssetsForDevgianlu(release, assets);
                    break;
                default:
                    throw new IllegalArgumentException(String.valueOf(release.source));
            }
        }

        @NonNull
        public static List<Asset> getAllSupportedAssets(List<Release> releases) {
            List<Asset> assets = new ArrayList<>();
            for (Release release : releases) getAllSupportedAssets(release, assets);
            return assets;
        }

        @NonNull
        public String version() {
            String[] split = name.split(" ");
            return split.length == 1 ? split[0] : split[1];
        }

        public static class SortByVersionComparator implements Comparator<Asset> {
            @Override
            public int compare(Asset o1, Asset o2) {
                try {
                    return Integer.parseInt(o1.version().replace("\\.", ""))
                            - Integer.parseInt(o2.version().replace("\\.", ""));
                } catch (Exception ex) {
                    return 0;
                }
            }
        }

        public static class Asset {
            public final int id;
            public final String downloadUrl;
            public final long size;
            private final String name;
            private final Release release;

            Asset(JSONObject obj, Release release) throws JSONException {
                this.id = obj.getInt("id");
                this.name = obj.getString("name");
                this.downloadUrl = obj.getString("browser_download_url");
                this.size = obj.getLong("size");
                this.release = release;
            }

            @NonNull
            public String version() {
                return release.version();
            }

            @NonNull
            public String repoSlug() {
                return release.source.slug();
            }

            public long publishedAt() {
                return release.publishedAt;
            }

            @NonNull
            public String arch() {
                Arch arch = Arch.detect(this);
                return arch == null ? "unknown" : arch.abi;
            }

            @NonNull
            public ReleaseSource source() {
                return release.source;
            }
        }
    }
}
