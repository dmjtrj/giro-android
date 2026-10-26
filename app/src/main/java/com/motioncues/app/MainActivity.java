package com.motioncues.app;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.motioncues.app.haptics.HapticEffect;
import com.motioncues.app.haptics.Haptics;
import com.motioncues.app.motion.MotionEngine;
import com.motioncues.app.overlay.CueOverlayService;
import com.motioncues.app.overlay.CuePreviewView;
import com.motioncues.app.ui.ThemedActivity;

/**
 * Главный экран: окно превью с круглой кнопкой включения по центру и шестерёнка настроек.
 * Включено — кнопка залита акцентным цветом и в превью видны точки; выключено — кнопка
 * приглушена, точки плавно гаснут.
 */
public final class MainActivity extends ThemedActivity implements CueOverlayService.StateListener {

    private static final long BUTTON_ANIMATION_MS = 220;

    private MaterialCardView powerButton;
    private ImageView powerIcon;
    private CuePreviewView preview;
    private Haptics haptics;
    private ValueAnimator buttonAnimator;
    private Boolean shownOn;
    /** Пользователь включил эффект и ушёл выдавать разрешение — включим по возвращении. */
    private boolean pendingStart;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> startCues());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        haptics = Haptics.get(this);
        powerButton = findViewById(R.id.power_button);
        powerIcon = findViewById(R.id.power_icon);
        preview = findViewById(R.id.preview);

        findViewById(R.id.settings_button).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));

        if (!new MotionEngine(this).isSupported()) {
            powerButton.setEnabled(false);
            ((TextView) findViewById(R.id.cues_summary)).setText(R.string.sensors_unsupported);
            showState(false);
            return;
        }
        powerButton.setOnClickListener(v -> {
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

    /**
     * Включено: кнопка залита акцентным цветом, точки в превью видны. Выключено: кнопка
     * приглушена, точки плавно гаснут. Цвета перетекают, а не переключаются скачком.
     */
    private void showState(boolean on) {
        powerButton.setContentDescription(getString(on ? R.string.power_turn_off : R.string.power_turn_on));
        int bg = MaterialColors.getColor(powerButton, on
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorSurfaceContainerHighest);
        int fg = MaterialColors.getColor(powerButton, on
                ? com.google.android.material.R.attr.colorOnPrimary
                : com.google.android.material.R.attr.colorOutline);
        boolean animate = shownOn != null && shownOn != on;
        preview.setDotsShown(on, animate);
        if (animate) {
            animateButton(bg, fg);
        } else if (shownOn == null) {
            powerButton.setCardBackgroundColor(bg);
            powerIcon.setImageTintList(ColorStateList.valueOf(fg));
        }
        shownOn = on;
    }

    private void animateButton(int bg, int fg) {
        if (buttonAnimator != null) {
            buttonAnimator.cancel();
        }
        int fromBg = powerButton.getCardBackgroundColor().getDefaultColor();
        ColorStateList tint = powerIcon.getImageTintList();
        int fromFg = tint != null ? tint.getDefaultColor() : fg;
        ArgbEvaluator evaluator = new ArgbEvaluator();
        buttonAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(BUTTON_ANIMATION_MS);
        buttonAnimator.addUpdateListener(a -> {
            float f = a.getAnimatedFraction();
            powerButton.setCardBackgroundColor((int) evaluator.evaluate(f, fromBg, bg));
            powerIcon.setImageTintList(ColorStateList.valueOf((int) evaluator.evaluate(f, fromFg, fg)));
        });
        buttonAnimator.start();
    }
}
