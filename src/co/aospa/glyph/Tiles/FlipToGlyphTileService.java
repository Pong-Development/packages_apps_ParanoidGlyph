/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2022-2024 Paranoid Android
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

package co.aospa.glyph.Tiles;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Utils.ServiceUtils;

/** Quick settings tile: Flip to Glyph **/
public class FlipToGlyphTileService extends TileService {

    Uri uri = Settings.Secure.getUriFor(Constants.GLYPH_ENABLE);

    ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateState();
        }
    };
    
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateState();
        getContentResolver().registerContentObserver(uri, false, observer);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
    }

    private void updateState() {
        if (getAvailable()) {
            boolean enabled = getEnabled();
            getQsTile().setSubtitle(enabled ?
                    getString(R.string.glyph_accessibility_quick_settings_on) :
                    getString(R.string.glyph_accessibility_quick_settings_off));
            getQsTile().setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        } else {
            getQsTile().setSubtitle(getString(R.string.glyph_accessibility_quick_settings_unavailable));
            getQsTile().setState(Tile.STATE_UNAVAILABLE);
        }
        getQsTile().updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        setEnabled(!getEnabled());
        updateState();
    }

    private boolean getEnabled() {
        return SettingsManager.isGlyphFlipEnabled();
    }

    private boolean getAvailable() {
        return SettingsManager.isGlyphEnabledIgnoreSchedule();
    }

    private void setEnabled(boolean enabled) {
        SettingsManager.setGlyphFlipEnabled(enabled);
        ServiceUtils.checkGlyphService();
    }
}
