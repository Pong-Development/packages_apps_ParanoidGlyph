package co.aospa.glyph.Services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import co.aospa.glyph.Manager.StatusManager;

public class BatterySaverService extends Service {

    PowerManager pm;

    private final BroadcastReceiver powerSaveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) {
                StatusManager.setBatterySavingActive(pm.isPowerSaveMode());
                Log.d("GlyphBatterySaver", "Battery saver: " + pm.isPowerSaveMode());
                updateMainSwitch();
            }
        }
    };

    private void updateMainSwitch() {
        Intent intent = new Intent("co.aospa.glyph.UPDATE_MAIN_SWITCH");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        registerReceiver(powerSaveReceiver,
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        Log.d("GlyphBatterySaver", "Battery saver: " + pm.isPowerSaveMode());
        StatusManager.setBatterySavingActive(pm.isPowerSaveMode());
        updateMainSwitch();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(powerSaveReceiver);
        StatusManager.setBatterySavingActive(false);
        Log.d("GlyphBatterySaver", "Battery saver stopping");
        updateMainSwitch();
        super.onDestroy();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
