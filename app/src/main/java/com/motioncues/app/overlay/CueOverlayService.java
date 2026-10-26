package com.motioncues.app.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.motioncues.app.MainActivity;
import com.motioncues.app.R;
import com.motioncues.app.motion.MotionEngine;
import com.motioncues.app.settings.AppSettings;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Foreground service, который держит поверх всех приложений окно с точками.
 *
 * <p>Окно не перехватывает касания и не получает фокус. Его прозрачность не выше
 * системного порога для «недоверенных» окон — иначе Android 12+ заблокирует
 * касания приложений под ним. На выключенном экране датчики не работают.
 */
public final class CueOverlayService extends Service {

    /** Слушатель включения и выключения эффекта; вызывается в главном потоке. */
    public interface StateListener {
        void onCuesStateChanged(boolean running);
    }

    private static final String ACTION_START = "com.motioncues.app.action.START";
    private static final String ACTION_STOP = "com.motioncues.app.action.STOP";
    private static final String CHANNEL_ID = "cues";
    private static final int NOTIFICATION_ID = 1;

    private static final CopyOnWriteArraySet<StateListener> listeners = new CopyOnWriteArraySet<>();
    private static volatile boolean running;

    private WindowManager windowManager;
    private CueOverlayView overlayView;
    private AppSettings settings;
    private boolean receiverRegistered;

    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> {
        if (overlayView == null) {
            return;
        }
        if (AppSettings.KEY_THEME.equals(key) || AppSettings.KEY_DOT_COLOR.equals(key)) {
            overlayView.setColors(settings.theme(), settings.dotColor());
        } else if (AppSettings.KEY_DOT_SIZE.equals(key) || AppSettings.KEY_DOT_OPACITY.equals(key)) {
            overlayView.setDotStyle(settings.dotSize(), settings.dotOpacity());
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (overlayView == null) {
                return;
            }
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                overlayView.stop();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                overlayView.start();
            }
        }
    };

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, CueOverlayService.class).setAction(ACTION_START));
    }

    public static void stop(Context context) {
        if (running) {
            context.startService(new Intent(context, CueOverlayService.class).setAction(ACTION_STOP));
        }
    }

    public static boolean isRunning() {
        return running;
    }

    /** Есть ли разрешение «Поверх других окон». */
    public static boolean canShowOverlay(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static void addStateListener(StateListener listener) {
        listeners.add(listener);
    }

    public static void removeStateListener(StateListener listener) {
        listeners.remove(listener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // startForeground обязателен даже если сразу выходим — иначе система уронит приложение
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        if (overlayView == null && !showOverlay()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        setRunning(true);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        setRunning(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean showOverlay() {
        if (!canShowOverlay(this)) {
            return false;
        }
        MotionEngine engine = new MotionEngine(this);
        if (!engine.isSupported()) {
            return false;
        }

        // Оконный контекст получает смену ориентации и темы, как обычное окно
        Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        Context windowContext = createWindowContext(display,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        windowManager = windowContext.getSystemService(WindowManager.class);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setFitInsetsTypes(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            params.setCanPlayMoveAnimation(false);
        }
        params.alpha = windowContext.getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch();
        params.setTitle(getString(R.string.app_name));

        settings = new AppSettings(this);
        overlayView = new CueOverlayView(windowContext, engine, params.alpha,
                settings.theme(), settings.dotColor(), settings.dotSize(), settings.dotOpacity());
        windowManager.addView(overlayView, params);
        overlayView.start();
        settings.registerListener(settingsListener);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        return true;
    }

    private void hideOverlay() {
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        if (overlayView != null) {
            settings.unregisterListener(settingsListener);
            overlayView.stop();
            windowManager.removeViewImmediate(overlayView);
            overlayView = null;
        }
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, CueOverlayService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, getString(R.string.action_turn_off), stop).build())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private static void setRunning(boolean value) {
        if (running == value) {
            return;
        }
        running = value;
        for (StateListener listener : listeners) {
            listener.onCuesStateChanged(value);
        }
    }
}
