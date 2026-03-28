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

    private AudioManager mAudioManager;
    private HandlerThread thread;
    private Handler mHandler;
    private Visualizer mVisualizer;
    private HashSet<String> bandSnapshot;
    private HashSet<String> prevSnapshot;
    private int bufferSize;
    private boolean isRecording = false;

    private double mRunningSoundAvg[];             // Total sound energy in one interval (0=low, 1=mid low, 2=mid, 3=mid high, 4=high)
    private double mCurrentAvgEnergy[];      // Average sound energy in one interval (0=low, 1=mid low, 2=mid, 3=mid high, 4=high)
    private int mNumberOfSamplesInOneInterval;          // Number of samples in one second
    private long mSystemTimeStartSec;              // System time at the start of an interval

    // Define the max value for a frequency band
    private static final int LOW_FREQUENCY = 250;
    private static final int MID_LOW_FREQUENCY = 500;
    private static final int MID_FREQUENCY = 1500;
    private static final int MID_HIGH_FREQUENCY = 5000;
    private static final int HIGH_FREQUENCY = 10000;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        if (SettingsManager.Pulse.isPulseEnabled()) {
            PULSE_PREVIOUS_STATE = true;
            SettingsManager.Pulse.setPulseVisualizer(false);
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
            mRunningSoundAvg = new double[5];
            mCurrentAvgEnergy = new double[5];
            mCurrentAvgEnergy[0] = -1;
            mCurrentAvgEnergy[1] = -1;
            mCurrentAvgEnergy[2] = -1;
            mCurrentAvgEnergy[3] = -1;
            mCurrentAvgEnergy[4] = -1;

            // Set the start time for the current one second interval
            mSystemTimeStartSec = System.currentTimeMillis();
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        mVisualizer.setEnabled(false);
        mVisualizer.release();
        if (PULSE_PREVIOUS_STATE) {
            SettingsManager.Pulse.setPulseVisualizer(true);
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
            super(new Handler());
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(
                Settings.Secure.getUriFor(Constants.PULSE_LOCKSCREEN_ENABLED_SETTING), 
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
            if (SettingsManager.Pulse.isPulseEnabled()) {
                SettingsManager.setGlyphMusicVisualizer(false);
                stopSelf();
            }
        }
    }

    private void processAudioFFT(byte[] audioBytes, int samplingRate) {
        bandSnapshot.clear();
        // The first byte is the DC component of the FFT result (real only)
        int energySum = Math.abs(audioBytes[0]);
        float SENSITIVITY = 1.1f;

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

        // Accumulate the low frequency band energy over time
        mRunningSoundAvg[0] += sampleAvgAudioEnergy;

        // Check for a beat in the low frequency band
        // A beat occurs when the average sound energy of a sample is greater than
        // the average sound energy of a one second part of a song
        // Also make sure the mCurrentAvgEnergy has been set, otherwise its -1 before its first pass
        if ((sampleAvgAudioEnergy > mCurrentAvgEnergy[0] * SENSITIVITY) && (mCurrentAvgEnergy[0] > 0)) {
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

        // Accumulate the mid low frequency band energy over time
        mRunningSoundAvg[1] += sampleAvgAudioEnergy;

        // Check for a beat in the mid-low frequency band
        if ((sampleAvgAudioEnergy > mCurrentAvgEnergy[1] * SENSITIVITY) && (mCurrentAvgEnergy[1] > 0)) {
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

        // Accumulate the mid frequency band energy over time
        mRunningSoundAvg[2] += sampleAvgAudioEnergy;

        // Check for a beat in the mid frequency band
        if ((sampleAvgAudioEnergy > mCurrentAvgEnergy[2] * SENSITIVITY) && (mCurrentAvgEnergy[2] > 0)) {
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

        // Accumulate the mid high-frequency band energy over time
        mRunningSoundAvg[3] += sampleAvgAudioEnergy;

        // Check for a beat in the mid-high frequency band
        if ((sampleAvgAudioEnergy > mCurrentAvgEnergy[3] * SENSITIVITY) && (mCurrentAvgEnergy[3] > 0)) {
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

        // Accumulate the high frequency band energy over time
        mRunningSoundAvg[4] += sampleAvgAudioEnergy;

        // Check for a beat in the high frequency band
        if ((sampleAvgAudioEnergy > mCurrentAvgEnergy[4] * SENSITIVITY) && (mCurrentAvgEnergy[4] > 0)) {
            if (DEBUG) Log.d(TAG, "High frequency band beat detected");
            bandSnapshot.add("high");
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - mSystemTimeStartSec >= 400) {
            mCurrentAvgEnergy[0] = mRunningSoundAvg[0] / mNumberOfSamplesInOneInterval;
            mCurrentAvgEnergy[1] = mRunningSoundAvg[1] / mNumberOfSamplesInOneInterval;
            mCurrentAvgEnergy[2] = mRunningSoundAvg[2] / mNumberOfSamplesInOneInterval;
            mCurrentAvgEnergy[3] = mRunningSoundAvg[3] / mNumberOfSamplesInOneInterval;
            mCurrentAvgEnergy[4] = mRunningSoundAvg[4] / mNumberOfSamplesInOneInterval;

            // Reset the running energy sum and sample count
            mRunningSoundAvg[0] = 0;
            mRunningSoundAvg[1] = 0;
            mRunningSoundAvg[2] = 0;
            mRunningSoundAvg[3] = 0;
            mRunningSoundAvg[4] = 0;
            mNumberOfSamplesInOneInterval = 0;

            // Update the start time for the next one-second interval
            mSystemTimeStartSec = currentTime;
        }
        HashSet<String> current = new HashSet<>(bandSnapshot);
        if (!current.equals(prevSnapshot)) {
            AnimationManager.playMusic(bandSnapshot);
            prevSnapshot = current;
        }
        mNumberOfSamplesInOneInterval++;
    }
}
