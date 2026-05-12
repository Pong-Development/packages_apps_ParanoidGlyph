package co.aospa.glyph.Services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.StatusManager;
import co.aospa.glyph.Utils.FileUtils;

public class TorchService extends Service {

    private PowerManager.WakeLock wakeLock;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        if (Constants.CONTEXT == null) {
            Constants.CONTEXT = getApplicationContext();
        }
        PowerManager pm = getApplicationContext().getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                getApplication().getPackageName() +
                        ":" +  getClass().getSimpleName()
        );
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();

            if (Constants.ACTION_TORCH_ENABLE.equals(action)) {
                setTorch(true);
            } else if (Constants.ACTION_TORCH_DISABLE.equals(action)) {
                setTorch(false);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        setTorch(false);
        super.onDestroy();
    }
    
    public void setTorch(boolean state) {
        if (state && !wakeLock.isHeld()) wakeLock.acquire();

        StatusManager.setAllLedsActive(state);
        FileUtils.writeAllLed(state ? Constants.getMaxBrightness() : 0);
        if (StatusManager.isEssentialLedActive() && !state) AnimationManager.playEssential();
        if (!state && wakeLock.isHeld()) wakeLock.release();
    }

}
