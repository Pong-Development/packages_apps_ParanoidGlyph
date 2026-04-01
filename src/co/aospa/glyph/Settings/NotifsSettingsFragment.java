/*
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

package co.aospa.glyph.Settings;

import static co.aospa.glyph.Utils.InterfaceUtils.showToast;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.ArrayUtils;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Preference.GlyphAnimationPreference;
import co.aospa.glyph.Utils.AnimationUtils;
import co.aospa.glyph.Utils.ResourceUtils;
import co.aospa.glyph.Utils.ServiceUtils;

public class NotifsSettingsFragment extends SettingsBasePreferenceFragment implements OnPreferenceChangeListener,
        OnCheckedChangeListener {

    private final String TAG = this.getClass().getSimpleName();

    private PreferenceScreen mScreen;

    private MainSwitchPreference mSwitchBar;
    private PreferenceCategory mCategory;

    private List<String> mEssentialApps = new ArrayList<String>();
    private List<String> mEssentialAppsNames = new ArrayList<String>();

    private PackageManager mPackageManager;

    private ListPreference mListPreference;
    private Preference mLivePreviewPreference;
    private MultiSelectListPreference mMultiSelectListPreference;
    private SwitchPreferenceCompat mReverseNotifAnimationSwitch;

    private GlyphAnimationPreference mGlyphAnimationPreference;

    private Handler mHandler = new Handler();
    private Thread livePreviewThread;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.glyph_notifs_settings);

        mScreen = this.getPreferenceScreen();
        getActivity().setTitle(R.string.glyph_settings_notifs_toggle_title);

        mSwitchBar = (MainSwitchPreference) findPreference(Constants.GLYPH_NOTIFS_SUB_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(SettingsManager.isGlyphNotifsEnabled());

        mCategory = (PreferenceCategory) findPreference(Constants.GLYPH_NOTIFS_SUB_CATEGORY);

        mListPreference = (ListPreference) findPreference(Constants.GLYPH_NOTIFS_SUB_ANIMATIONS);
        mListPreference.setOnPreferenceChangeListener(this);

        List<String> userAnimationList
                = ResourceUtils.getUserNotificationAnimations();

        List<String> bundledAnimationList
                = ResourceUtils.getBundledNotificationAnimations();

        List<String> animationEntryList = new ArrayList<>();

        animationEntryList.addAll(bundledAnimationList);
        animationEntryList.addAll(userAnimationList);
        animationEntryList.sort(null);

        List<String> animationEntryValues = new ArrayList<>();
        animationEntryValues.addAll(bundledAnimationList);
        animationEntryValues.addAll(userAnimationList
                        .stream()
                        .map(name -> Constants.GLYPH_USER_NOTIF_CSV_PREFIX + name)
                        .toList());
        animationEntryValues.sort(null);

        mListPreference.setEntries(animationEntryList.toArray(new String[0]));
        mListPreference.setEntryValues(animationEntryValues.toArray(new String[0]));
        if (!animationEntryValues.contains(mListPreference.getValue())) {
            mListPreference.setValue(ResourceUtils.getString("glyph_settings_notifs_animations_default"));
        }

        mLivePreviewPreference = (Preference) findPreference(Constants.GLYPH_NOTIFS_SUB_LIVE_PREVIEW);

        mGlyphAnimationPreference = (GlyphAnimationPreference) findPreference(Constants.GLYPH_NOTIFS_SUB_PREVIEW);

        mReverseNotifAnimationSwitch = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE);
        mReverseNotifAnimationSwitch.setOnPreferenceChangeListener(this);

        mPackageManager = getActivity().getPackageManager();
        List<ApplicationInfo> mApps = mPackageManager.getInstalledApplications(PackageManager.GET_GIDS);
        Collections.sort(mApps, new ApplicationInfo.DisplayNameComparator(mPackageManager));
        for (ApplicationInfo app : mApps) {
            if(mPackageManager.getLaunchIntentForPackage(app.packageName) != null  && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, app.packageName)) { // apps with launcher intent
                SwitchPreferenceCompat mSwitchPreference = new SwitchPreferenceCompat(mScreen.getContext());
                mSwitchPreference.setKey(app.packageName);
                mSwitchPreference.setTitle(" " + app.loadLabel(mPackageManager).toString()); // add this space since the layout looks off otherwise
                mSwitchPreference.setIcon(app.loadIcon(mPackageManager));
                mSwitchPreference.setDefaultValue(true);
                mSwitchPreference.setOnPreferenceChangeListener(this);
                mCategory.addPreference(mSwitchPreference);

                mEssentialApps.add(app.packageName);
                mEssentialAppsNames.add(app.loadLabel(mPackageManager).toString());
            }
        }

        mMultiSelectListPreference = (MultiSelectListPreference) findPreference(Constants.GLYPH_NOTIFS_SUB_ESSENTIAL);
        mMultiSelectListPreference.setOnPreferenceChangeListener(this);
        mMultiSelectListPreference.setEntries(mEssentialAppsNames.toArray(new CharSequence[0]));
        mMultiSelectListPreference.setEntryValues(mEssentialApps.toArray(new CharSequence[0]));

    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean isPlayable = true;

        if (mListPreference.getValue().startsWith(Constants.GLYPH_USER_NOTIF_CSV_PREFIX)) {
            String animationName = mListPreference.getValue();
            checkUserAnimation(animationName);
            try {
                String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                        StandardCharsets.UTF_8);
                isPlayable = !AnimationUtils.isAnimationComplex(csv);
            } catch (Exception e) {

            }
        }
        if (isPlayable) {
            mGlyphAnimationPreference.updateAnimation(
                    SettingsManager.isGlyphNotifsEnabled(),
                    SettingsManager.getGlyphNotifsAnimation(),
                    1500,
                    mReverseNotifAnimationSwitch.isChecked());
        }
        mGlyphAnimationPreference.setVisible(isPlayable);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        if (preferenceKey.equals(Constants.GLYPH_NOTIFS_SUB_ANIMATIONS)) {
            String animationName = newValue.toString();
            boolean isPlayable = true;

            if (animationName.startsWith(Constants.GLYPH_USER_NOTIF_CSV_PREFIX)) {
                if (!checkUserAnimation(animationName)) return false;
                try {
                    String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                            StandardCharsets.UTF_8);
                    isPlayable = !AnimationUtils.isAnimationComplex(csv);
                } catch (Exception e) {

                }
            }
            mGlyphAnimationPreference.updateAnimation(
                    isPlayable && SettingsManager.isGlyphNotifsEnabled(),
                    animationName, 1500);
            mGlyphAnimationPreference.setVisible(isPlayable);
            if (!isPlayable) {
                showToast(R.string.glyph_settings_user_animation_is_complex);
            }
            return true;
        }

        if (preferenceKey.equals(Constants.GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE)) {
            mGlyphAnimationPreference.updateAnimation(SettingsManager.isGlyphNotifsEnabled(), 1500, (Boolean) newValue);
        }

        if (preferenceKey.equals(Constants.GLYPH_NOTIFS_SUB_ESSENTIAL)) {
            //if (DEBUG) Log.d(TAG, "onPreferenceChange: " + newValue.toString());
        }

        //mHandler.post(() -> ServiceUtils.checkGlyphService());

        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (Constants.GLYPH_NOTIFS_SUB_LIVE_PREVIEW.equals(preference.getKey())) {
            mLivePreviewPreference.setEnabled(false);
            mLivePreviewPreference.setSummary(R.string.glyph_settings_animations_live_preview_summary_playing);
            livePreviewThread = new Thread(() -> {
                Activity activity = getActivity();
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    resetLivePreview();
                    return;
                }
                AnimationManager.playCsv(
                        requireContext(),
                        SettingsManager.getGlyphNotifsAnimation(),
                        false,
                        mReverseNotifAnimationSwitch.isChecked()
                );

                if (activity != null) {
                    activity.runOnUiThread(this::resetLivePreview);
                }
            });
            livePreviewThread.start();
        }
        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SettingsManager.setGlyphNotifsEnabled(isChecked);
        ServiceUtils.checkGlyphService();
        mGlyphAnimationPreference.updateAnimation(isChecked,
                SettingsManager.getGlyphNotifsAnimation(), 1500);
    }


    @Override
    public void onResume() {
        super.onResume();
        resetLivePreview();
    }

    private void resetLivePreview() {
        mLivePreviewPreference.setEnabled(true);
        mLivePreviewPreference.setSummary(
                R.string.glyph_settings_animations_live_preview_summary
        );
    }

    private boolean checkUserAnimation(String animationName) {
        try {
            String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                    StandardCharsets.UTF_8);
            AnimationUtils.validateAnimation(csv);
            if (!AnimationUtils.isCompatible(csv)) {
                showToast(R.string.glyph_settings_user_animation_incompatible);
                return false;
            }
        } catch (Exception e) {
            showToast(R.string.glyph_settings_user_animation_invalid);
            Log.w(TAG, e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (livePreviewThread != null && livePreviewThread.isAlive()) {
            livePreviewThread.interrupt();
            livePreviewThread = null;
        }
    }


}
