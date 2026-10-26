package com.motioncues.app.tile;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import com.motioncues.app.MainActivity;
import com.motioncues.app.R;
import com.motioncues.app.haptics.HapticEffect;
import com.motioncues.app.haptics.Haptics;
import com.motioncues.app.overlay.CueOverlayService;

/**
 * Плитка «Сигналы движения» в быстрых настройках: включает и выключает эффект прямо
 * из шторки — с теми же вибрациями, что и кнопка в приложении.
 *
 * <p>Плитка «активная»: когда эффект включают или выключают в приложении или из
 * уведомления, она обновляется сама ({@link #refresh}). Долгое нажатие открывает
 * настройки точек — система находит экран с действием {@code QS_TILE_PREFERENCES}.
 */
public final class CueTileService extends TileService implements CueOverlayService.StateListener {

    private static final String TAG = "CueTile";

    /** Просит систему обновить плитку — вызывать при каждой смене состояния эффекта. */
    public static void refresh(Context context) {
        try {
            requestListeningState(context, new ComponentName(context, CueTileService.class));
        } catch (RuntimeException e) {
            // Плитку ещё не добавляли в шторку — обновлять нечего
            Log.d(TAG, "Tile not active: " + e.getMessage());
        }
    }

    @Override
    public void onTileAdded() {
        updateTile();
    }

    @Override
    public void onStartListening() {
        CueOverlayService.addStateListener(this);
        updateTile();
    }

    @Override
    public void onStopListening() {
        CueOverlayService.removeStateListener(this);
    }

    @Override
    public void onCuesStateChanged(boolean running) {
        updateTile();
    }

    @Override
    public void onClick() {
        Haptics haptics = Haptics.get(this);
        if (CueOverlayService.isRunning()) {
            haptics.play(HapticEffect.CUES_OFF);
            CueOverlayService.stop(this);
            updateTile(false);
            return;
        }
        if (!CueOverlayService.canShowOverlay(this)) {
            // Без разрешения включить нельзя — открываем приложение, там подскажут, что сделать
            openApp();
            return;
        }
        try {
            CueOverlayService.start(this);
            haptics.play(HapticEffect.CUES_ON);
            updateTile(true);
        } catch (IllegalStateException e) {
            // Система не дала запустить службу из фона — включаем через приложение
            Log.w(TAG, "Foreground service start not allowed from tile", e);
            openApp();
        }
    }

    // На Android 12–13 (minSdk 31) есть только вариант с Intent; с Android 14 — PendingIntent
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTile() {
        updateTile(CueOverlayService.isRunning());
    }

    private void updateTile(boolean running) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.app_name));
        tile.setSubtitle(getString(running ? R.string.tile_on : R.string.tile_off));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_notification));
        tile.setContentDescription(getString(R.string.app_name));
        tile.updateTile();
    }
}
