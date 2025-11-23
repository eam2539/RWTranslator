package com.eam.rwtranslator;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.eam.rwtranslator.ui.main.MainActivity;
import com.eam.rwtranslator.utils.CrashHandler;
import com.eam.rwtranslator.utils.FileLoggingTree;

import java.io.File;
import java.io.FileWriter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public class AppConfig extends Application {
    // 全局Context
    public static volatile Context applicationContext;
    // 全局目录
    public static File externalFileDir,
            externalCacheTmpDir,
            externalCacheSerialDir,
            externalLogDir,
            externalProjectDir,
            logFile;
    // 屏幕密度
    private static float density;


    @Override
    public void onCreate() {
        super.onCreate();

        externalFileDir = getExternalFilesDir("");
        externalCacheTmpDir = new File(getExternalCacheDir(), "tmp");
        externalCacheSerialDir = new File(getExternalCacheDir(), "serial");
        externalLogDir = new File(externalFileDir, "log");
        externalProjectDir = new File(externalFileDir, "project");
        density = this.getResources().getDisplayMetrics().density;
        if (applicationContext == null) {
            applicationContext = getApplicationContext();
        }
        logFile = new File(externalLogDir, "RWTranslator.log");
        try {
            if (!externalCacheTmpDir.exists()) externalCacheTmpDir.mkdir();
            if (!externalCacheSerialDir.exists()) externalCacheSerialDir.mkdir();
            if (!externalProjectDir.exists()) externalProjectDir.mkdir();
            if (!externalLogDir.exists()) externalLogDir.mkdir();
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (Exception err) {
            Timber.e(err);
        }
        checkAndFlushLogs();
        Timber.plant(new FileLoggingTree(logFile));
    /* if (BuildConfig.DEBUG) {
        Timber.plant(new Timber.DebugTree());
    } else {
        Timber.plant(new FileLoggingTree());
    }*/
        schedulePeriodicWork();
        CrashHandler.getInstance().init(this, true, true, 100, MainActivity.class);
    }

    private void checkAndFlushLogs() {
        long size = logFile.length();
        if (size > 10240) {
            try (FileWriter fileWriter = new FileWriter(logFile)) {
                fileWriter.write("");
            } catch (Exception err) {
                Timber.e(err);
            }
        }
    }

    public static int dp2px(float dp) {
        return (int) (dp * density + 0.5f);
    }

    public void schedulePeriodicWork() {
        PeriodicWorkRequest cleanupWorkRequest =
                new PeriodicWorkRequest.Builder(CacheCleanupWorker.class, 1, TimeUnit.DAYS) // 每天执行一次
                        .build();
        WorkManager.getInstance(this).enqueue(cleanupWorkRequest);
    }

    @SuppressLint("WorkerHasAPublicModifier")
    static
    class CacheCleanupWorker extends Worker {

        public CacheCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            try {
                for (File file : Objects.requireNonNull(externalCacheTmpDir.listFiles())) {
                    file.delete();
                }
                return Result.success();
            } catch (Exception e) {
                Timber.e(e);
                return Result.failure();
            }
        }
    }
}
