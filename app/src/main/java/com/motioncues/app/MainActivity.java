package com.motioncues.app;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.motioncues.app.haptics.HapticEffect;
import com.motioncues.app.haptics.Haptics;
import com.motioncues.app.motion.MotionEngine;
import com.motioncues.app.overlay.CueOverlayService;
import com.motioncues.app.ui.ThemedActivity;

/**
 * Главный экран: живое превью точек, главный переключатель и переход в настройки.
 */
public final class MainActivity extends ThemedActivity implements CueOverlayService.StateListener {

    private static final long BAR_ANIMATION_MS = 220;

    private MaterialCardView switchBar;
    private MaterialSwitch cuesSwitch;
    private TextView switchTitle;
    private Haptics haptics;
    private ValueAnimator barAnimator;
    private Boolean barShownOn;
    /** Пользователь включил эффект и ушёл выдавать разрешение — включим по возвращении. */
    private boolean pendingStart;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> startCues());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        haptics = Haptics.get(this);
        switchBar = findViewById(R.id.main_switch_bar);
        cuesSwitch = findViewById(R.id.cues_switch);
        switchTitle = findViewById(R.id.main_switch_title);

        findViewById(R.id.settings_row).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));

        if (!new MotionEngine(this).isSupported()) {
            switchBar.setEnabled(false);
            cuesSwitch.setEnabled(false);
            ((TextView) findViewById(R.id.cues_summary)).setText(R.string.sensors_unsupported);
            return;
        }
        switchBar.setOnClickListener(v -> {
            if (CueOverlayService.isRunning()) {
                haptics.play(HapticEffect.CUES_OFF);
                CueOverlayService.stop(this);
                showState(false);
            } else {
                requestStart();
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
            showState(CueOverlayService.isRunning());
        }
    }

    @Override
    protected void onPause() {
        CueOverlayService.removeStateListener(this);
        super.onPause();
    }

    @Override
    public void onCuesStateChanged(boolean running) {
        showState(running);
    }

    private void requestStart() {
        if (!CueOverlayService.canShowOverlay(this)) {
            showOverlayPermissionDialog();
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

    /**
     * С Android 11 система открывает общий список приложений, а не страницу нашего —
     * поэтому заранее объясняем, что именно там искать.
     */
    private void showOverlayPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.overlay_dialog_title)
                .setMessage(R.string.overlay_dialog_message)
                .setPositiveButton(R.string.overlay_dialog_open, (dialog, which) -> {
                    pendingStart = true;
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                })
                .setNegativeButton(R.string.overlay_dialog_later, null)
                .show();
    }

    private void startCues() {
        haptics.play(HapticEffect.CUES_ON);
        CueOverlayService.start(this);
        showState(true);
    }

    /** Цвет «таблетки» плавно перетекает: акцентный — включено, нейтральный — выключено. */
    private void showState(boolean on) {
        cuesSwitch.setChecked(on);
        switchBar.setContentDescription(getString(on ? R.string.main_switch_on : R.string.main_switch_off));
        int bg = MaterialColors.getColor(switchBar, on
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int fg = MaterialColors.getColor(switchBar, on
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurface);
        if (barShownOn == null) {
            switchBar.setCardBackgroundColor(bg);
            switchTitle.setTextColor(fg);
        } else if (barShownOn != on) {
            animateBar(bg, fg);
        }
        barShownOn = on;
    }

    private void animateBar(int bg, int fg) {
        if (barAnimator != null) {
            barAnimator.cancel();
        }
        int fromBg = switchBar.getCardBackgroundColor().getDefaultColor();
        int fromFg = switchTitle.getCurrentTextColor();
        ArgbEvaluator evaluator = new ArgbEvaluator();
        barAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(BAR_ANIMATION_MS);
        barAnimator.addUpdateListener(a -> {
            float f = a.getAnimatedFraction();
            switchBar.setCardBackgroundColor((int) evaluator.evaluate(f, fromBg, bg));
            switchTitle.setTextColor((int) evaluator.evaluate(f, fromFg, fg));
        });
        barAnimator.start();
    }
}
