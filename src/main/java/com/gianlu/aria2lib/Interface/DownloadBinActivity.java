package com.gianlu.aria2lib.Interface;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.NetworkOnMainThreadException;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.gianlu.aria2lib.Aria2Downloader;
import com.gianlu.aria2lib.Aria2PK;
import com.gianlu.aria2lib.GitHubApi;
import com.gianlu.aria2lib.R;
import com.gianlu.commonutils.Analytics.AnalyticsApplication;
import com.gianlu.commonutils.AskPermission;
import com.gianlu.commonutils.CasualViews.RecyclerViewLayout;
import com.gianlu.commonutils.Dialogs.ActivityWithDialog;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.Preferences.Prefs;
import com.gianlu.commonutils.Toaster;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DownloadBinActivity extends ActivityWithDialog implements ReleasesAdapter.Listener, GitHubApi.OnResult<List<GitHubApi.Release>>, Aria2Downloader.DownloadRelease, Aria2Downloader.ExtractTo {
    public static final String ACTION_IMPORT_BIN = "imported_bin";
    private static final int IMPORT_BIN_CODE = 8;
    private RecyclerViewLayout layout;
    private Aria2Downloader downloader;
    private Class<? extends Activity> startAfter;

    public static void startActivity(@NonNull Context context, @NonNull String title, @NonNull Class<? extends Activity> startAfter, int flags, @Nullable Bundle extras) {
        Intent intent = new Intent(context, DownloadBinActivity.class)
                .addFlags(flags)
                .putExtra("title", title)
                .putExtra("startAfter", startAfter);

        if (extras != null) intent.putExtras(extras);

        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // noinspection unchecked
        startAfter = (Class<? extends Activity>) getIntent().getSerializableExtra("startAfter");

        layout = new RecyclerViewLayout(this);
        layout.useVerticalLinearLayoutManager();
        setContentView(layout);
        setTitle(getIntent().getStringExtra("title"));

        layout.showInfo(R.string.retrievingReleases);
        downloader = new Aria2Downloader();

        if (getIntent().getBooleanExtra("importBin", false)) {
            importBin();
            getIntent().removeExtra("importBin");
        } else {
            downloader.getReleases(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.aria2lib_download_bin, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.downloadBin_custom) {
            importBin();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void importBin() {
        AskPermission.ask(this, Manifest.permission.READ_EXTERNAL_STORAGE, new AskPermission.Listener() {
            @Override
            public void permissionGranted(@NonNull String permission) {
                try {
                    startActivityForResult(Intent.createChooser(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*"), getString(R.string.customBin)), IMPORT_BIN_CODE);
                } catch (ActivityNotFoundException ex) {
                    Toaster.with(DownloadBinActivity.this).message(R.string.failedImportingBin).ex(ex).show();
                }
            }

            @Override
            public void permissionDenied(@NonNull String permission) {
                Toaster.with(DownloadBinActivity.this).message(R.string.readPermissionDenied).error(true).show();
            }

            @Override
            public void askRationale(@NonNull AlertDialog.Builder builder) {
                builder.setTitle(R.string.readPermission)
                        .setMessage(R.string.readStorage_message);
            }
        });
    }

    public void writeStreamAsBin(@Nullable InputStream in) throws IOException {
        if (in == null) throw new IOException(new NullPointerException("InputStream is null!"));

        int count;
        byte[] buffer = new byte[4096];
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "aria2c"))) {
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMPORT_BIN_CODE && resultCode == RESULT_OK && data.getData() != null) {
            try {
                writeStreamAsBin(getContentResolver().openInputStream(data.getData()));
            } catch (IOException | NetworkOnMainThreadException ex) {
                Toaster.with(this).message(R.string.failedImportingBin).ex(ex).show();
                return;
            }

            AnalyticsApplication.sendAnalytics(ACTION_IMPORT_BIN);
            Prefs.putBoolean(Aria2PK.CUSTOM_BIN, true);

            startActivity(new Intent(this, startAfter)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onResult(@NonNull List<GitHubApi.Release> result) {
        layout.loadListData(new ReleasesAdapter(this, result, this));
    }

    @Override
    public void onException(@NonNull Exception ex) {
        Logging.log(ex);
        layout.showError(R.string.failedRetrievingReleases_reason, ex.getMessage());
    }

    @Override
    public void onReleaseSelected(@NonNull GitHubApi.Release release) {
        layout.showInfo(R.string.downloadingBin);

        downloader.setRelease(release);
        downloader.downloadRelease(this);
    }

    @Override
    public void doneDownload(@NonNull File tmp) {
        layout.showInfo(R.string.extractingBin);
        downloader.extractTo(getEnvDir(), (entry, name) -> name.equals("aria2c"), this);
    }

    @NonNull
    private File getEnvDir() {
        return new File(getFilesDir(), "env");
    }

    @Override
    public void failedDownload(@NonNull Exception ex) {
        onException(ex);
    }

    @Override
    public void doneExtract(@NonNull File dest) {
        layout.showInfo(R.string.binExtracted);

        Prefs.putBoolean(Aria2PK.CUSTOM_BIN, false);
        Prefs.putString(Aria2PK.ENV_LOCATION, dest.getAbsolutePath());
        startActivity(new Intent(this, startAfter)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    @Override
    public void failedExtract(@NonNull Exception ex) {
        onException(ex);
    }
}
