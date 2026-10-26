package com.motioncues.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.motioncues.app.motion.MotionEngine;
import com.motioncues.app.overlay.CueOverlayService;
import com.motioncues.app.settings.AppSettings;
import com.motioncues.app.settings.DotColor;
import com.motioncues.app.settings.ThemeController;
import com.motioncues.app.settings.ThemeMode;

public final class MainActivity extends AppCompatActivity implements CueOverlayService.StateListener {

    private MaterialSwitch cuesSwitch;
    private TextView summary;
    /** Пользователь включил эффект и ушёл выдавать разрешение — включим по возвращении. */
    private boolean pendingStart;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> startCues());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Иконки строки состояния — под тему приложения, а не системы
        boolean night = ThemeController.isNight(this, new AppSettings(this).theme());
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), root);
        insetsController.setAppearanceLightStatusBars(!night);
        insetsController.setAppearanceLightNavigationBars(!night);

        setupThemePicker();
        setupDotColorPicker();
        setupDotSliders();

        cuesSwitch = findViewById(R.id.cues_switch);
        summary = findViewById(R.id.cues_summary);

        if (!new MotionEngine(this).isSupported()) {
            cuesSwitch.setEnabled(false);
            summary.setText(R.string.sensors_unsupported);
            return;
        }
        cuesSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) {
                return;
            }
            if (checked) {
                requestStart();
            } else {
                CueOverlayService.stop(this);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        CueOverlayService.addStateListener(this);
        if (pendingStart && CueOverlayService.canShowOverlay(this)) {
            requestStart();
        } else {
            pendingStart = false;
            syncState();
        }
    }

    @Override
    protected void onPause() {
        CueOverlayService.removeStateListener(this);
        super.onPause();
    }

    @Override
    public void onCuesStateChanged(boolean running) {
        syncState();
    }

    private void setupThemePicker() {
        MaterialButtonToggleGroup group = findViewById(R.id.theme_group);
        switch (new AppSettings(this).theme()) {
            case LIGHT:
                group.check(R.id.theme_light);
                break;
            case DARK:
                group.check(R.id.theme_dark);
                break;
            default:
                group.check(R.id.theme_monet);
                break;
        }
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            ThemeMode mode = checkedId == R.id.theme_light ? ThemeMode.LIGHT
                    : checkedId == R.id.theme_dark ? ThemeMode.DARK
                    : ThemeMode.MONET;
            ThemeController.change(this, mode);
        });
    }

    private void setupDotColorPicker() {
        AppSettings settings = new AppSettings(this);
        MaterialButtonToggleGroup group = findViewById(R.id.dot_color_group);
        switch (settings.dotColor()) {
            case WHITE:
                group.check(R.id.dot_color_white);
                break;
            case BLACK:
                group.check(R.id.dot_color_black);
                break;
            case MONET:
                group.check(R.id.dot_color_monet);
                break;
            default:
                group.check(R.id.dot_color_auto);
                break;
        }
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            DotColor color = checkedId == R.id.dot_color_white ? DotColor.WHITE
                    : checkedId == R.id.dot_color_black ? DotColor.BLACK
                    : checkedId == R.id.dot_color_monet ? DotColor.MONET
                    : DotColor.AUTO;
            settings.setDotColor(color);
        });
    }

    /** Ползунки сохраняют значение на каждом шаге — точки меняются прямо во время перетаскивания. */
    private void setupDotSliders() {
        AppSettings settings = new AppSettings(this);
        Slider size = findViewById(R.id.dot_size_slider);
        Slider opacity = findViewById(R.id.dot_opacity_slider);
        size.setValue(settings.dotSize());
        opacity.setValue(settings.dotOpacity());
        size.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                settings.setDotSize(Math.round(value));
            }
        });
        opacity.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                settings.setDotOpacity(Math.round(value));
            }
        });
    }

    private void requestStart() {
        if (!CueOverlayService.canShowOverlay(this)) {
            pendingStart = true;
            cuesSwitch.setChecked(false);
            summary.setText(R.string.overlay_permission_needed);
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        pendingStart = false;
        // Без уведомлений эффект тоже работает — просто не будет кнопки «Выключить» в шторке
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startCues();
        }
    }

    private void startCues() {
        CueOverlayService.start(this);
        cuesSwitch.setChecked(true);
        summary.setText(R.string.cues_summary);
    }

    private void syncState() {
        cuesSwitch.setChecked(CueOverlayService.isRunning());
        summary.setText(R.string.cues_summary);
    }
}
