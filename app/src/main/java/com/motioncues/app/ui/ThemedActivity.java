package com.motioncues.app.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.motioncues.app.R;
import com.motioncues.app.overlay.CueOverlayService;
import com.motioncues.app.settings.AppSettings;
import com.motioncues.app.settings.ThemeController;
import com.motioncues.app.settings.ThemeMode;

/**
 * Общая основа экранов: отрисовка под системными панелями, отступы под них,
 * иконки строки состояния под тему приложения и пересоздание экрана, если тему
 * сменили на другом экране (иначе экран в стеке остался бы со старыми цветами).
 * Пока на экране любой экран приложения, точки эффекта скрыты — их показывает превью.
 * Как только приложение уходит с экрана (домой, в другое приложение), точки появляются
 * сразу, в onPause, — без задержек. Переходы внутри приложения (в настройки и обратно,
 * пересоздание при смене темы) распознаём и точки не трогаем — они не мигают.
 *
 * <p>Корневой View разметки должен иметь id {@code root}.
 */
public abstract class ThemedActivity extends AppCompatActivity {

    /** Этот экран открывает другой экран приложения — уход не наружу. */
    private boolean openingOwnScreen;

    private ThemeMode appliedTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        appliedTheme = new AppSettings(this).theme();
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        boolean night = ThemeController.isNight(this, appliedTheme);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
    }

    @Override
    public void startActivity(Intent intent, Bundle options) {
        ComponentName target = intent.getComponent();
        openingOwnScreen = target != null && getPackageName().equals(target.getPackageName());
        super.startActivity(intent, options);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openingOwnScreen = false;
        CueOverlayService.setAppInForeground(true);
        if (new AppSettings(this).theme() != appliedTheme) {
            recreate();
        }
    }

    @Override
    protected void onPause() {
        // Внутри приложения: открываем свой экран, возвращаемся назад к своему,
        // пересоздаёмся при смене темы. Иначе приложение уходит с экрана — точки сразу
        boolean stayingInApp = openingOwnScreen || isChangingConfigurations()
                || (isFinishing() && !isTaskRoot());
        if (!stayingInApp) {
            CueOverlayService.setAppInForeground(false);
        }
        super.onPause();
    }
}
