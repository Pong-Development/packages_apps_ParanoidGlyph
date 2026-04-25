package co.aospa.glyph.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Utils.AnimationUtils;
import co.aospa.glyph.Utils.OGGParser;

import co.aospa.glyph.R;

public class ToneHelperService extends Service {

    private ContentObserver settingsObserver;
    private ContentResolver resolver;

    private Uri notificationUri;
    private Uri ringtoneUri;
    private Uri ringtone2Uri;

    private boolean saveRing;
    private boolean saveNotif;

    private static final String INTENT_EXTRA_KEY = "changed_key";
    private static final String INTENT_EXTRA_VALUE = "changed_value";


    private static final String CHANNEL_ID = "tone_changes";

    // Keep a map of URI -> key so we can reverse-look it up in onChange
    private final Map<Uri, String> observedUris = new LinkedHashMap<>();

    private final String TAG = this.getClass().getSimpleName();

    @Override
    public void onCreate() {
        resolver = getContentResolver();
        notificationUri = Settings.System.getUriFor(Settings.System.NOTIFICATION_SOUND);
        ringtoneUri = Settings.System.getUriFor(Settings.System.RINGTONE);
        ringtone2Uri = Settings.System.getUriFor(Settings.System.RINGTONE + "2");
        registerObserver();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //saveRing = SettingsManager.isGlyphRingtoneSyncEnabled();

        saveNotif = SettingsManager.isGlyphNotifsSyncEnabled();

        if (saveNotif) {
            String key = Settings.System.NOTIFICATION_SOUND;
            handleSettingChange(key, Settings.System.getString(resolver, key), false);
        } else {
            AnimationUtils.Holder.Notification.clear();
        }

//        if (saveRing) {
//            String key = Settings.System.RINGTONE;
//            handleSettingChange(key, Settings.System.getString(resolver, key), false);
//        } else {
//            AnimationUtils.Holder.Call.clear();
//        }
        return START_STICKY;
    }

    private void registerObserver() {

        observedUris.put(notificationUri, Settings.System.NOTIFICATION_SOUND);
//        observedUris.put(ringtoneUri, Settings.System.RINGTONE);
//        observedUris.put(ringtone2Uri, Settings.System.RINGTONE + "2");

        settingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                String key = observedUris.get(uri);
                if (key == null) return;

                String value = Settings.System.getString(resolver, key);
                if (value == null) return;

                handleSettingChange(key, value, true);
            }
        };

        for (Uri uri : observedUris.keySet()) {
            resolver.registerContentObserver(uri, false, settingsObserver);
        }
    }

    private void handleSettingChange(String key, String value, boolean shouldNotify) {
        boolean isOgg = false;
            try {
                String mimeType = resolver.getType(Uri.parse(value));
                isOgg = "audio/ogg".equals(mimeType)
                        || "audio/x-ogg".equals(mimeType)
                        || "application/ogg".equals(mimeType);
            } catch (Exception e) {
                Log.w(TAG, "Could not resolve MIME type for: " + value, e);
            }

        if (!isOgg) return;

        Log.d(TAG, key + " changed to OGG: " + value);

       tryAnimationData(key, value, shouldNotify);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_title),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(getString(R.string.notification_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (settingsObserver != null) {
            getContentResolver().unregisterContentObserver(settingsObserver);
        }
        AnimationUtils.Holder.Call.clear();
        AnimationUtils.Holder.Notification.clear();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void notifyUser(String key, String value) {

        String title = "";
        String message = getString(R.string.sound_changed_notification_message);

        switch (key) {
//            case Settings.System.RINGTONE -> {
//                title = getString(R.string.ringtone_changed_notification_title);
//            }
//            case Settings.System.RINGTONE + "2" -> {
//                title = getString(R.string.ringtone_changed_notification_title)
//                        + " (SIM 2)";
//            }
            case Settings.System.NOTIFICATION_SOUND -> {
                title = getString(R.string.notification_sound_changed_notification_title);
            }
        }

        Intent actionIntent = new Intent(getApplicationContext(), this.getClass());
        actionIntent.putExtra(INTENT_EXTRA_KEY, key);
        actionIntent.putExtra(INTENT_EXTRA_VALUE, value);


        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_glyphs_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build();

        getSystemService(NotificationManager.class).notify(key.hashCode(), notification);

    }

    private void tryAnimationData(String key, String value, boolean shouldNotify) {
        OGGParser parser;
        Uri soundUri = Uri.parse(value);
        try {
            InputStream inputStream = resolver.openInputStream(soundUri);
            parser = new OGGParser(inputStream);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Ogg file not found!");
            return;
        } catch (Exception e) {
            Log.w(TAG, "Unable to obtain animation data from OGG file.");
            switch (key) {
//                case Settings.System.RINGTONE -> {
//                    AnimationUtils.Holder.Call.clear();
//                }
                case Settings.System.NOTIFICATION_SOUND -> {
                    AnimationUtils.Holder.Notification.clear();
                }
            }
            return;
        }
        String animationData;
        try {
            animationData = parser.getAnimation();
        } catch (Exception e) {
            animationData = null;
        }
        if (animationData != null && !animationData.isEmpty()) {
            Log.d(TAG,"Animation data found!");
            boolean compatible = AnimationUtils.isCompatible(animationData);
            if (!compatible) {
                Log.w(TAG, "Animation is not compatible with this device!");
                switch (key) {
//                    case Settings.System.RINGTONE -> {
//                        AnimationUtils.Holder.Call.clear();
//                    }
                    case Settings.System.NOTIFICATION_SOUND -> {
                        AnimationUtils.Holder.Notification.clear();
                    }
                }
                return;
            }

            switch (key) {
//                case Settings.System.RINGTONE -> {
//                    if (saveRing) {
//                        if (AnimationUtils.Holder.Call.isAvailable()
//                                && AnimationUtils.Holder.Call.getCsv().equals(animationData)) {
//                            return;
//                        }
//                        AnimationUtils.Holder.Call.setCsv(animationData);
//                        if (shouldNotify) notifyUser(key, value);
//                    }
//                }
                case Settings.System.NOTIFICATION_SOUND -> {
                    if (saveNotif) {
                        if (AnimationUtils.Holder.Notification.isAvailable()
                                && AnimationUtils.Holder.Notification.getCsv().equals(animationData)) {
                            return;
                        }
                        AnimationUtils.Holder.Notification.setCsv(animationData);
                        if (shouldNotify) notifyUser(key, value);
                    }
                }
            }
        }
    }

}
