package com.motioncues.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
 * Пока открыт любой экран приложения, точки эффекта скрыты — их показывает превью.
 *
 * <p>Корневой View разметки должен иметь id {@code root}.
 */
public abstract class ThemedActivity extends AppCompatActivity {

    /** Задержка перед показом точек: при переходе между экранами приложения они не мигают. */
    private static final long SHOW_CUES_DELAY_MS = 300;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable showCues = () -> CueOverlayService.setAppInForeground(false);
    private static int resumedScreens;

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
    protected void onResume() {
        super.onResume();
        resumedScreens++;
        handler.removeCallbacks(showCues);
        CueOverlayService.setAppInForeground(true);
        if (new AppSettings(this).theme() != appliedTheme) {
            recreate();
        }
    }

    @Override
    protected void onPause() {
        resumedScreens--;
        if (resumedScreens == 0) {
            handler.postDelayed(showCues, SHOW_CUES_DELAY_MS);
        }
        super.onPause();
    }
}
