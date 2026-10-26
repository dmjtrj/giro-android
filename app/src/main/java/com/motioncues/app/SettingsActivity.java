package com.motioncues.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.provider.Settings;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.motioncues.app.haptics.HapticEffect;
import com.motioncues.app.haptics.Haptics;
import com.motioncues.app.overlay.CueAccessibilityService;
import com.motioncues.app.settings.AppSettings;
import com.motioncues.app.settings.DotColor;
import com.motioncues.app.settings.MotionMode;
import com.motioncues.app.settings.ThemeController;
import com.motioncues.app.settings.ThemeMode;
import com.motioncues.app.ui.ThemedActivity;

import java.util.function.IntConsumer;

/**
 * Настройки: точки (цвет, размер, непрозрачность), чувствительность, тема и виброотклик.
 * Всё сохраняется сразу и применяется к работающему эффекту на лету.
 */
public final class SettingsActivity extends ThemedActivity {

    private AppSettings settings;
    private Haptics haptics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        settings = new AppSettings(this);
        haptics = Haptics.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        setupIphoneMode();
        setupDotColor();
        setupLevelSlider(R.id.dot_size_slider, settings.dotSize(), settings::setDotSize);
        setupLevelSlider(R.id.dot_count_slider, settings.dotCount(), settings::setDotCount);
        setupLevelSlider(R.id.dot_opacity_slider, settings.dotOpacity(), settings::setDotOpacity);
        setupMotionMode();
        setupLevelSlider(R.id.sensitivity_slider, settings.sensitivity(), settings::setSensitivity);
        setupTheme();
        setupAboveSystem();
        setupHaptics();
        updateLocks();
    }

    private void setupIphoneMode() {
        MaterialSwitch toggle = findViewById(R.id.iphone_switch);
        toggle.setChecked(settings.iphoneMode());
        findViewById(R.id.iphone_row).setOnClickListener(v -> {
            boolean enable = !settings.iphoneMode();
            haptics.play(enable ? HapticEffect.TOGGLE_ON : HapticEffect.TOGGLE_OFF);
            settings.setIphoneMode(enable);
            toggle.setChecked(enable);
            updateLocks();
        });
    }

    /**
     * В режиме «Как на iPhone» настройки точек и движения задаёт профиль — разделы
     * приглушены и недоступны. Сохранённые значения не трогаем: после выключения режима
     * всё вернётся как было.
     */
    private void updateLocks() {
        boolean locked = settings.iphoneMode();
        for (int id : new int[]{R.id.dots_card, R.id.motion_card}) {
            View card = findViewById(id);
            setEnabledDeep(card, !locked);
            // Прозрачность неактивных элементов по Material — 38%
            card.setAlpha(locked ? 0.38f : 1f);
        }
        if (!locked) {
            // Возвращаем частные ограничения, которые сняло общее включение
            findViewById(R.id.dot_color_monet).setEnabled(settings.theme() == ThemeMode.MONET);
            updateOpacitySlider(settings.dotColor());
        }
    }

    private static void setEnabledDeep(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabledDeep(group.getChildAt(i), enabled);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Пользователь мог включить или выключить службу в системных настройках
        updateAboveSystem();
    }

    private void setupAboveSystem() {
        findViewById(R.id.above_system_row).setOnClickListener(v -> {
            boolean enable = !settings.aboveSystem();
            haptics.play(enable ? HapticEffect.TOGGLE_ON : HapticEffect.TOGGLE_OFF);
            settings.setAboveSystem(enable);
            updateAboveSystem();
            if (enable && !CueAccessibilityService.isEnabled(this)) {
                showAccessibilityDialog();
            }
        });
    }

    private void updateAboveSystem() {
        boolean enabled = settings.aboveSystem();
        MaterialSwitch toggle = findViewById(R.id.above_system_switch);
        toggle.setChecked(enabled);
        TextView summary = findViewById(R.id.above_system_summary);
        if (!enabled) {
            summary.setText(R.string.above_system_summary_off);
        } else if (CueAccessibilityService.isEnabled(this)) {
            summary.setText(R.string.above_system_summary_on);
        } else {
            summary.setText(R.string.above_system_summary_needs_service);
        }
    }

    /**
     * Включить службу может только сам пользователь. Открываем раздел специальных
     * возможностей и просим систему подсветить наш пункт.
     */
    private void showAccessibilityDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.above_system_dialog_title)
                .setMessage(R.string.above_system_dialog_message)
                .setPositiveButton(R.string.above_system_dialog_open, (dialog, which) -> {
                    String key = CueAccessibilityService.componentKey(this);
                    Bundle args = new Bundle();
                    args.putString(":settings:fragment_args_key", key);
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .putExtra(":settings:fragment_args_key", key)
                            .putExtra(":settings:show_fragment_args", args));
                })
                .setNegativeButton(R.string.overlay_dialog_later, null)
                .show();
    }

    private void setupDotColor() {
        MaterialButtonToggleGroup group = findViewById(R.id.dot_color_group);
        // Цвет «Monet» — только в теме Monet: в светлой и тёмной теме цвета обоев не используются
        findViewById(R.id.dot_color_monet).setEnabled(settings.theme() == ThemeMode.MONET);
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
            haptics.play(HapticEffect.SELECT);
            settings.setDotColor(color);
            updateOpacitySlider(color);
        });
        updateOpacitySlider(settings.dotColor());
    }

    /** Белые и чёрные всегда полностью непрозрачные — ползунок для них неактивен. */
    private void updateOpacitySlider(DotColor color) {
        boolean solid = color == DotColor.WHITE || color == DotColor.BLACK;
        findViewById(R.id.dot_opacity_slider).setEnabled(!solid);
        // Прозрачность неактивного текста по Material — 38%
        findViewById(R.id.dot_opacity_title).setAlpha(solid ? 0.38f : 1f);
    }

    /** Ползунок сохраняет значение на каждом делении — превью и эффект меняются во время перетаскивания. */
    private void setupLevelSlider(int id, int level, IntConsumer save) {
        Slider slider = findViewById(id);
        slider.setValue(level);
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) {
                return;
            }
            int newLevel = Math.round(value);
            // Каждое деление — «зубчик», крайние — более плотный упор
            boolean edge = value <= s.getValueFrom() || value >= s.getValueTo();
            haptics.play(edge ? HapticEffect.SLIDER_EDGE : HapticEffect.SLIDER_TICK);
            save.accept(newLevel);
        });
    }

    private void setupMotionMode() {
        MaterialButtonToggleGroup group = findViewById(R.id.motion_mode_group);
        TextView summary = findViewById(R.id.motion_mode_summary);
        MotionMode current = settings.motionMode();
        group.check(current == MotionMode.GYROSCOPE ? R.id.motion_mode_gyroscope : R.id.motion_mode_inertia);
        summary.setText(summaryFor(current));
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            MotionMode mode = checkedId == R.id.motion_mode_gyroscope ? MotionMode.GYROSCOPE : MotionMode.INERTIA;
            haptics.play(HapticEffect.SELECT);
            settings.setMotionMode(mode);
            summary.setText(summaryFor(mode));
        });
    }

    private static int summaryFor(MotionMode mode) {
        return mode == MotionMode.GYROSCOPE
                ? R.string.motion_mode_gyroscope_summary
                : R.string.motion_mode_inertia_summary;
    }

    private void setupTheme() {
        MaterialButtonToggleGroup group = findViewById(R.id.theme_group);
        switch (settings.theme()) {
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
            haptics.play(HapticEffect.SELECT);
            ThemeController.change(this, mode);
        });
    }

    private void setupHaptics() {
        MaterialCardView row = findViewById(R.id.haptics_row);
        MaterialSwitch hapticsSwitch = findViewById(R.id.haptics_switch);
        hapticsSwitch.setChecked(settings.hapticsEnabled());
        row.setOnClickListener(v -> {
            boolean enable = !settings.hapticsEnabled();
            if (enable) {
                settings.setHapticsEnabled(true);
                haptics.play(HapticEffect.TOGGLE_ON);
            } else {
                // Последний отклик до выключения — подтверждает, что вибрация отключена
                haptics.play(HapticEffect.TOGGLE_OFF);
                settings.setHapticsEnabled(false);
            }
            hapticsSwitch.setChecked(enable);
        });
    }
}
