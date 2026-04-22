package co.aospa.glyph.Services;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Utils.FileUtils;
import co.aospa.glyph.Utils.ResourceUtils;

public class MicActivityService extends Service {

    private AudioManager mAudioManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private int redLED;
    private final int BLINK_INTERVAL = 800;

    private int MAX_BRIGHTNESS = 255;
    private int MIN_BRIGHTNESS = 35;
    private boolean mLedState = false;

    private final int MODE_BLINK = 0;
    private final int MODE_BREATHING = 1;
    private final int MODE_STATIC = 2;

    private int mode = MODE_BLINK;

    private int mBrightness = 0;
    private int mStep = 9;
    private static final int BREATH_INTERVAL = 150;

    private Set<String> mWhitelist = new HashSet<>();

    private final String TAG = this.getClass().getSimpleName();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            redLED = ResourceUtils.getInteger("glyph_red_led_index");
        } catch (Exception e) {
            Log.w(TAG, "Attempted to start service on unsupported device?");
            stopSelf();
        }

        mAudioManager = getSystemService(AudioManager.class);
        mAudioManager.registerAudioRecordingCallback(GlyphCallback,  mHandler);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mode = SettingsManager.getGlyphRedLedMode();
        mWhitelist = SettingsManager.getMonitoredMicApps();
        return START_STICKY;
    }

    private void toggleLed(boolean state) {
        FileUtils.writeSingleLed(redLED, state ? MAX_BRIGHTNESS : 0);
    }

    private void setLedBrightness(int brightness) {
        FileUtils.writeSingleLed(redLED, brightness);
    }

    private void stopLed () {
        if (mHandler.hasCallbacks(LedRunnable)) {
            mHandler.removeCallbacks(LedRunnable);
        }
        toggleLed(false);
    }

    private final Runnable LedRunnable = new Runnable() {
        @Override
        public void run() {
            switch (mode) {
                case MODE_STATIC -> toggleLed(true);
                case MODE_BLINK -> {
                    mLedState = !mLedState;
                    toggleLed(mLedState);
                    mHandler.postDelayed(this, BLINK_INTERVAL);
                }
                case MODE_BREATHING -> {
                    mBrightness += mStep;
                    if (mBrightness >= MAX_BRIGHTNESS || mBrightness <= MIN_BRIGHTNESS) {
                        mStep = -mStep;
                        mBrightness = Math.clamp(mBrightness, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
                    }
                    setLedBrightness(mBrightness);
                    mHandler.postDelayed(this, BREATH_INTERVAL);
                }
            }
        }
    };

    private final AudioManager.AudioRecordingCallback GlyphCallback =
        new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                boolean micInUse = !configs.isEmpty();
                if (micInUse) {
                    boolean whitelistedAppRecording = configs.stream()
                            .map(AudioRecordingConfiguration::getClientPackageName)
                            .anyMatch(mWhitelist::contains);
                    if (whitelistedAppRecording) {
                        mHandler.post(LedRunnable);
                    } else {
                        stopLed();
                    }
                } else {
                    stopLed();
                }
            }
        };

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAudioManager.unregisterAudioRecordingCallback(GlyphCallback);
        stopLed();
    }
}
