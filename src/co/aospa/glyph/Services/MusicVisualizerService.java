/*
 * Copyright (C) 2022 By yours truly, Daniel Jacob Chittoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.aospa.glyph.Services;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Manager.StatusManager;

public class MusicVisualizerService extends Service {

    private static final String TAG = "GlyphMusicVisualizerService";
    private static final boolean DEBUG = true;

    private boolean PULSE_PREVIOUS_STATE = false;

    private SettingObserver PulseSettingObserver;

    private static final int MODE_BEAT = 0;
    private static final int MODE_BRIGHTNESS = 1;
    private static final int MODE_BRIGHTNESS_INVERTED = 2;

    private int mMode = MODE_BEAT;

    private long mLastLedUpdate = 0;
    private static final long LED_FRAME_INTERVAL = 1000 / 60; // ~16ms

    private AudioManager mAudioManager;
    private HandlerThread thread;
    private Handler mHandler;
    private Visualizer mVisualizer;
    private HashSet<String> bandSnapshot;
    private HashSet<String> prevSnapshot;
    private int bufferSize;

    private double[] mCurrentAvgEnergy;      // Average sound energy in one interval (0=low, 1=mid low, 2=mid, 3=mid high, 4=high)

    private double[] bandEnergies;
    private double[] mPeakEnergy;

    private String pulsePrefKey = "";
    
    // Define the max value for a frequency band
    private static final int LOW_FREQUENCY = 250;
    private static final int MID_LOW_FREQUENCY = 500;
    private static final int MID_FREQUENCY = 1500;
    private static final int MID_HIGH_FREQUENCY = 5000;
    private static final int HIGH_FREQUENCY = 10000;

    private static final double RATIO_MAX = 1.3;
    private static final double RATIO_MIN = 0.6;

    private float BEAT_SENSITIVITY = 1.0f;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        if (SettingsManager.Pulse.isLockscreenPulseEnabled()) {
            pulsePrefKey = Constants.PULSE_LOCKSCREEN_ENABLED_SETTING;
        } else if (SettingsManager.Pulse.isPulseEnabled()) {
            pulsePrefKey = Constants.PULSE_ENABLED_SETTING;
        }

        if (!pulsePrefKey.isEmpty()) {
            PULSE_PREVIOUS_STATE = true;
            SettingsManager.setIntSecure(pulsePrefKey, false);
        }
        PulseSettingObserver = new SettingObserver();
        PulseSettingObserver.register(getContentResolver());

        // Run visualizer on a handler thread
        thread = new HandlerThread("MusicVisualizerService");
        thread.start();
        Looper looper = thread.getLooper();
        mHandler = new Handler(looper);

        // Get audio service
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Create a visualizer with the audio session ID (0) which takes the entire output mix
        mVisualizer = new Visualizer(0);

        // Set the capture size to the maximum available
        bufferSize = Visualizer.getCaptureSizeRange()[1];
        mVisualizer.setCaptureSize(bufferSize);
        bandSnapshot = new HashSet<String>();
        prevSnapshot = new HashSet<String>();

        mHandler.post(() -> {
            // Set data capture listener for visualizer
            mVisualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {
                    @Override
                    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                    }

                    @Override
                    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                        if (mAudioManager.isMusicActive() && StatusManager.isGlyphIdle()) {
                            if (DEBUG) Log.d(TAG, "Music is active");
                            processAudioFFT(fft, samplingRate);
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true
            );

            // Enable visualizer
            mVisualizer.setEnabled(true);

            // Initialize instance variables
            mCurrentAvgEnergy = new double[5];
            Arrays.fill(mCurrentAvgEnergy, 1.0);

            mPeakEnergy = new double[5];
            bandEnergies = new double[5];
            Arrays.fill(mPeakEnergy, 1.0);

        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");

        mMode = SettingsManager.getGlyphMusicVisualizerMode();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        mVisualizer.setEnabled(false);
        mVisualizer.release();
        if (PULSE_PREVIOUS_STATE) {
            SettingsManager.setIntSecure(pulsePrefKey, true);
        }
        PulseSettingObserver.unregister(getContentResolver());
        thread.quit();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(
                Settings.Secure.getUriFor(Constants.PULSE_LOCKSCREEN_ENABLED_SETTING), 
                false, 
                this, 
                ActivityManager.getCurrentUser()
            );
            cr.registerContentObserver(
                    Settings.Secure.getUriFor(Constants.PULSE_ENABLED_SETTING),
                    false,
                    this,
                    ActivityManager.getCurrentUser()
            );
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (SettingsManager.Pulse.isPulseEnabled()
                    || SettingsManager.Pulse.isLockscreenPulseEnabled()) {
                SettingsManager.setGlyphMusicVisualizer(false);
                stopSelf();
            }
        }
    }

    private int energyToLedBrightness(double sampleAvgEnergy,
                                      double rollingAvgEnergy,
                                      double peakEnergy) {
        if (rollingAvgEnergy <= 0) return 0;

        double ratio = sampleAvgEnergy / rollingAvgEnergy;
        ratio = Math.max(RATIO_MIN, Math.min(RATIO_MAX, ratio));
        double normalized = (ratio - RATIO_MIN) / (RATIO_MAX - RATIO_MIN);

        // blend: normalized position * how close we are to the peak
        double peakRatio = Math.min(1.0, sampleAvgEnergy / peakEnergy);
        normalized = normalized * peakRatio;


        return (int) Math.round(normalized * Constants.MAX_PATTERN_BRIGHTNESS);
    }

    private int energyToLedBrightnessInverted(double sampleAvgEnergy,
                                              double rollingAvgEnergy,
                                              double peakEnergy) {
        return Constants.MAX_PATTERN_BRIGHTNESS - energyToLedBrightness(sampleAvgEnergy,
                rollingAvgEnergy, peakEnergy);
    }

    private void processAudioFFT(byte[] audioBytes, int samplingRate) {
        bandSnapshot.clear();
        // The first byte is the DC component of the FFT result (real only)
        int energySum = Math.abs(audioBytes[0]);

        // Calculate the average instantaneous energy of the low frequency band
        int k = 2;
        double captureSize = audioBytes.length / 2.0;
        int sampleRate = samplingRate / 2000;
        double nextFrequency = (k / 2.0 * sampleRate) / captureSize;

        // Sum the energy in the low frequency band
        int bandStartK = k;
        while (nextFrequency < LOW_FREQUENCY) {
                // Calculate the energy of the current frequency
            energySum += (int) Math.sqrt(audioBytes[k] * audioBytes[k] + audioBytes[k + 1] * audioBytes[k + 1]);

            // Increment the frequency index
            k += 2;
            nextFrequency = (k / 2.0 * sampleRate) / captureSize;
        }

        // Calculate the average energy in the low frequency band
        int binsInBand = (k - bandStartK) / 2;
        double sampleAvgAudioEnergy = energySum / (double) binsInBand;
        if (mMode == MODE_BRIGHTNESS || mMode == MODE_BRIGHTNESS_INVERTED) bandEnergies[0] = sampleAvgAudioEnergy;
        mCurrentAvgEnergy[0] = mCurrentAvgEnergy[0] * 0.99 + sampleAvgAudioEnergy * 0.01;

        mPeakEnergy[0] = Math.max(sampleAvgAudioEnergy, mPeakEnergy[0] * 0.7);

        // Check for a beat in the low frequency band
        // A beat occurs when the average sound energy of a sample is greater than
        // the average sound energy of a one second part of a song
        // Also make sure the mCurrentAvgEnergy has been set, otherwise its -1 before its first pass
        if (mMode == MODE_BEAT && (sampleAvgAudioEnergy > mCurrentAvgEnergy[0] * BEAT_SENSITIVITY)) {
            if (DEBUG) Log.d(TAG, "Low frequency band beat detected");
            bandSnapshot.add("low");
        }

        energySum = 0;

        // Sum the energy in the mid-low frequency band
        bandStartK = k;
        while (nextFrequency < MID_LOW_FREQUENCY) {
            // Calculate the energy of the current frequency
            energySum += (int) Math.sqrt(audioBytes[k] * audioBytes[k] + audioBytes[k + 1] * audioBytes[k + 1]);

            // Increment the frequency index
            k += 2;
            nextFrequency = (k / 2.0 * sampleRate) / captureSize;
        }

        // Calculate the average energy in the mid-low frequency band
        binsInBand = (k - bandStartK) / 2;
        sampleAvgAudioEnergy = energySum / (double) binsInBand;
        if (mMode == MODE_BRIGHTNESS || mMode == MODE_BRIGHTNESS_INVERTED) bandEnergies[1] = sampleAvgAudioEnergy;
        mCurrentAvgEnergy[1] = mCurrentAvgEnergy[1] * 0.99 + sampleAvgAudioEnergy * 0.01;

        mPeakEnergy[1] = Math.max(sampleAvgAudioEnergy, mPeakEnergy[1] * 0.7);


        // Check for a beat in the mid-low frequency band
        if (mMode == MODE_BEAT && (sampleAvgAudioEnergy > mCurrentAvgEnergy[1] * BEAT_SENSITIVITY)) {
            if (DEBUG) Log.d(TAG, "Mid-low frequency band beat detected");
            bandSnapshot.add("mid_low");
        }

        energySum = 0;

        // Sum the energy in the mid frequency band
        bandStartK = k;
        while (nextFrequency < MID_FREQUENCY) {
            // Calculate the energy of the current frequency
            energySum += (int) Math.sqrt(audioBytes[k] * audioBytes[k] + audioBytes[k + 1]
                    * audioBytes[k + 1]);

            // Increment the frequency index
            k += 2;
            nextFrequency = (k / 2.0 * sampleRate) / captureSize;
        }

        // Calculate the average energy in the mid frequency band
        binsInBand = (k - bandStartK) / 2;
        sampleAvgAudioEnergy = energySum / (double) binsInBand;
        if (mMode == MODE_BRIGHTNESS || mMode == MODE_BRIGHTNESS_INVERTED) bandEnergies[2] = sampleAvgAudioEnergy;
        mCurrentAvgEnergy[2] = mCurrentAvgEnergy[2] * 0.99 + sampleAvgAudioEnergy * 0.01;

        mPeakEnergy[2] = Math.max(sampleAvgAudioEnergy, mPeakEnergy[2] * 0.7);

        // Check for a beat in the mid frequency band
        if (mMode == MODE_BEAT && (sampleAvgAudioEnergy > mCurrentAvgEnergy[2] * BEAT_SENSITIVITY)) {
            if (DEBUG) Log.d(TAG, "Mid frequency band beat detected");
            bandSnapshot.add("mid");
        }

        energySum = 0;

        // Sum the energy in the mid-high frequency band
        bandStartK = k;
        while (nextFrequency < MID_HIGH_FREQUENCY) {
            // Calculate the energy of the current frequency
            energySum += (int) Math.sqrt(audioBytes[k] * audioBytes[k] + audioBytes[k + 1]
                    * audioBytes[k + 1]);

            // Increment the frequency index
            k += 2;
            nextFrequency = (k / 2.0 * sampleRate) / captureSize;
        }

        // Calculate the average energy in the mid-high frequency band
        binsInBand = (k - bandStartK) / 2;
        sampleAvgAudioEnergy = energySum / (double) binsInBand;
        if (mMode == MODE_BRIGHTNESS || mMode == MODE_BRIGHTNESS_INVERTED) bandEnergies[3] = sampleAvgAudioEnergy;
        mCurrentAvgEnergy[3] = mCurrentAvgEnergy[3] * 0.99 + sampleAvgAudioEnergy * 0.01;

        mPeakEnergy[3] = Math.max(sampleAvgAudioEnergy, mPeakEnergy[3] * 0.7);

        // Check for a beat in the mid-high frequency band
        if (mMode == MODE_BEAT && (sampleAvgAudioEnergy > mCurrentAvgEnergy[3] * BEAT_SENSITIVITY)) {
            if (DEBUG) Log.d(TAG, "Mid-high frequency band beat detected");
            bandSnapshot.add("mid_high");
        }

        // Second Byte: Only imaginary part of the last frequency (include in highs)
        energySum = Math.abs(audioBytes[1]);

        // Sum the energy in the high frequency band
        bandStartK = k;
        while (nextFrequency < HIGH_FREQUENCY) {
            // Calculate the energy of the current frequency
            energySum += (int) Math.sqrt(audioBytes[k] * audioBytes[k] + audioBytes[k + 1]
                    * audioBytes[k + 1]);

            // Increment the frequency index
            k += 2;
            nextFrequency = (k / 2.0) * samplingRate / captureSize;
        }

        // Calculate the average energy in the high frequency band
        binsInBand = (k - bandStartK) / 2;
        sampleAvgAudioEnergy = energySum / (double) binsInBand;
        if (mMode == MODE_BRIGHTNESS || mMode == MODE_BRIGHTNESS_INVERTED) bandEnergies[4] = sampleAvgAudioEnergy;
        mCurrentAvgEnergy[4] = mCurrentAvgEnergy[4] * 0.99 + sampleAvgAudioEnergy * 0.01;

        mPeakEnergy[4] = Math.max(sampleAvgAudioEnergy, mPeakEnergy[4] * 0.7);

        // Check for a beat in the high frequency band
        if (mMode == MODE_BEAT && (sampleAvgAudioEnergy > mCurrentAvgEnergy[4] * BEAT_SENSITIVITY)) {
            if (DEBUG) Log.d(TAG, "High frequency band beat detected");
            bandSnapshot.add("high");
        }

        long now = System.currentTimeMillis();
        if (now - mLastLedUpdate >= LED_FRAME_INTERVAL) {
            if (mMode == MODE_BRIGHTNESS) {
                int[] brightnesses = new int[]{
                        energyToLedBrightness(bandEnergies[0], mCurrentAvgEnergy[0], mPeakEnergy[0]),
                        energyToLedBrightness(bandEnergies[1], mCurrentAvgEnergy[1], mPeakEnergy[1]),
                        energyToLedBrightness(bandEnergies[2], mCurrentAvgEnergy[2], mPeakEnergy[2]),
                        energyToLedBrightness(bandEnergies[3], mCurrentAvgEnergy[3], mPeakEnergy[3]),
                        energyToLedBrightness(bandEnergies[4], mCurrentAvgEnergy[4], mPeakEnergy[4])
                };
                AnimationManager.playMusic(brightnesses);
            } else if (mMode == MODE_BRIGHTNESS_INVERTED) {
                int[] brightnesses = new int[]{
                        energyToLedBrightnessInverted(bandEnergies[0], mCurrentAvgEnergy[0], mPeakEnergy[0]),
                        energyToLedBrightnessInverted(bandEnergies[1], mCurrentAvgEnergy[1], mPeakEnergy[1]),
                        energyToLedBrightnessInverted(bandEnergies[2], mCurrentAvgEnergy[2], mPeakEnergy[2]),
                        energyToLedBrightnessInverted(bandEnergies[3], mCurrentAvgEnergy[3], mPeakEnergy[3]),
                        energyToLedBrightnessInverted(bandEnergies[4], mCurrentAvgEnergy[4], mPeakEnergy[4])
                };
                AnimationManager.playMusic(brightnesses);
            } else {
                HashSet<String> current = new HashSet<>(bandSnapshot);
                if (!current.equals(prevSnapshot)) {
                    AnimationManager.playMusic(bandSnapshot);
                    prevSnapshot = current;
                }
            }
            mLastLedUpdate = now;
        }
    }
}
