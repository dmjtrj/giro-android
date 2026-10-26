package com.motioncues.app.overlay;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

/**
 * Служба специальных возможностей для режима «Поверх шторки и системных окон».
 *
 * <p>Ничего не читает с экрана и не обрабатывает события — нужна только затем, что окна
 * типа {@code TYPE_ACCESSIBILITY_OVERLAY} система показывает поверх шторки уведомлений,
 * клавиатуры и системных окон и считает доверенными: для них нет потолка прозрачности 80%.
 * Сами точки по-прежнему рисует {@link CueOverlayService}.
 */
public final class CueAccessibilityService extends AccessibilityService {

    private static CueAccessibilityService current;

    /** Подключённая служба или {@code null}. Только главный поток. */
    static CueAccessibilityService current() {
        return current;
    }

    /** Включена ли служба в настройках специальных возможностей. */
    public static boolean isEnabled(Context context) {
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        ComponentName ours = new ComponentName(context, CueAccessibilityService.class);
        for (AccessibilityServiceInfo info
                : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ComponentName name = new ComponentName(info.getResolveInfo().serviceInfo.packageName,
                    info.getResolveInfo().serviceInfo.name);
            if (ours.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Имя компонента для подсветки пункта в настройках специальных возможностей. */
    public static String componentKey(Context context) {
        return new ComponentName(context, CueAccessibilityService.class).flattenToString();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        current = this;
        CueOverlayService.refreshWindow();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        release();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // События не нужны: служба лишь даёт окну с точками верхний слой
    }

    @Override
    public void onInterrupt() {
    }

    private void release() {
        if (current == this) {
            current = null;
            // Окно точек принадлежало службе — переносим его в обычный оверлей
            CueOverlayService.refreshWindow();
        }
    }
}
