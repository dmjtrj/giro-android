package com.motioncues.app;

import android.app.Application;

import com.motioncues.app.settings.ThemeController;

public final class MotionCuesApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Светлая/тёмная/Monet — по настройке пользователя
        ThemeController.install(this);
    }
}
