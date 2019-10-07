package com.gianlu.aria2lib;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.gianlu.commonutils.logging.Logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Workflow:
 * - {@link #getReleases(GitHubApi.OnResult)}
 * - {@link #setAsset(GitHubApi.Release.Asset)}
 * - {@link #downloadAsset(OnDownloadAsset)}
 * - {@link #extractTo(File, Filter, ExtractTo)}
 */
public final class Aria2Downloader {
    private final GitHubApi gitHub;
    private final Handler handler;
    private GitHubApi.Release.Asset selectedAsset;
    private File downloadTmpFile;

    public Aria2Downloader() {
        gitHub = GitHubApi.get();
        handler = new Handler(Looper.getMainLooper());
    }

    private void extractOriginal(@NonNull File dest, @Nullable Filter filter, @NonNull ExtractTo listener) {
        gitHub.execute(() -> {
            byte[] buffer = new byte[4096];
            int read;

            try (ZipInputStream in = new ZipInputStream(new FileInputStream(downloadTmpFile))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    if (entry.isDirectory())
                        continue;

                    String[] split = entry.getName().split(Pattern.quote(File.separator));
                    String name = split.length == 1 ? split[0] : split[split.length - 1];

                    if (filter != null && !filter.accept(entry, name))
                        continue;

                    File file = new File(dest, name);
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        while ((read = in.read(buffer)) != -1)
                            out.write(buffer, 0, read);
                    }

                    if (!file.setExecutable(true))
                        Logging.log("Failed setting execute permission on " + file, true);

                    in.closeEntry();
                }

                handler.post(() -> listener.doneExtract(dest));
            } catch (IOException ex) {
                handler.post(() -> listener.failedExtract(ex));
            }
        });
    }

    public void extractTo(@NonNull File dest, @Nullable Filter filter, @NonNull ExtractTo listener) {
        if (downloadTmpFile == null) {
            listener.failedExtract(new IllegalStateException("Missing downloaded release!"));
            return;
        }

        if (selectedAsset == null) {
            listener.failedExtract(new IllegalStateException("Did not select an asset!"));
            return;
        }

        if (dest.exists()) {
            if (!dest.isDirectory()) {
                listener.failedExtract(new IllegalStateException(dest.getAbsolutePath() + " is not a directory!"));
                return;
            }
        } else {
            if (!dest.mkdir()) {
                listener.failedExtract(new IllegalStateException("Failed creating " + dest.getAbsolutePath()));
                return;
            }
        }

        switch (selectedAsset.source()) {
            case ARIA2_ORIGINAL:
                extractOriginal(dest, filter, listener);
                break;
            case ARIA2_DEVGIANLU:
                File file = new File(dest, "aria2c");
                if (downloadTmpFile.renameTo(file) && file.setExecutable(true))
                    listener.doneExtract(dest);
                else
                    listener.failedExtract(new IOException("Failed coping file or setting as executable!"));
                break;
            default:
                listener.failedExtract(new IllegalArgumentException(String.valueOf(selectedAsset.source())));
        }
    }

    public void downloadAsset(@NonNull OnDownloadAsset listener) {
        if (selectedAsset == null) {
            listener.failedDownload(new IllegalStateException("Did not select an asset!"));
            return;
        }

        gitHub.inputStream(selectedAsset.downloadUrl, new GitHubApi.InputStreamWorker() {
            @Override
            public void work(@NonNull InputStream in) throws Exception {
                File tmp = File.createTempFile("aria2", String.valueOf(selectedAsset.id));
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1)
                        out.write(buffer, 0, read);
                }

                setDownloadTmpFile(tmp);
                handler.post(() -> listener.doneDownload(tmp));
            }

            @Override
            public void exception(@NonNull Exception ex) {
                handler.post(() -> listener.failedDownload(ex));
            }
        });
    }

    public void setDownloadTmpFile(@NonNull File file) {
        this.downloadTmpFile = file;
    }

    public void getReleases(@NonNull GitHubApi.OnResult<List<GitHubApi.Release>> listener) {
        gitHub.getReleases("aria2", "aria2", new GitHubApi.OnResult<List<GitHubApi.Release>>() {
            @Override
            public void onResult(@NonNull List<GitHubApi.Release> original) {
                gitHub.getReleases("devgianlu", "aria2-android", new GitHubApi.OnResult<List<GitHubApi.Release>>() {
                    @Override
                    public void onResult(@NonNull List<GitHubApi.Release> custom) {
                        List<GitHubApi.Release> releases = new ArrayList<>();
                        releases.addAll(custom);
                        releases.addAll(original);
                        listener.onResult(releases);
                    }

                    @Override
                    public void onException(@NonNull Exception ex) {
                        listener.onException(ex);
                    }
                });
            }

            @Override
            public void onException(@NonNull Exception ex) {
                listener.onException(ex);
            }
        });
    }

    public void setAsset(@NonNull GitHubApi.Release.Asset asset) {
        this.selectedAsset = asset;
    }

    public interface Filter {
        boolean accept(@NonNull ZipEntry entry, @NonNull String name);
    }

    @UiThread
    public interface ExtractTo {
        void doneExtract(@NonNull File dest);

        void failedExtract(@NonNull Exception ex);
    }

    @UiThread
    public interface OnDownloadAsset {
        void doneDownload(@NonNull File tmp);

        void failedDownload(@NonNull Exception ex);
    }
}
