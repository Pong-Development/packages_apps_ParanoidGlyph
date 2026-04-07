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
import android.os.Looper;
import android.provider.Settings;
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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Preference.GlyphAnimationPreference;
import co.aospa.glyph.Utils.AnimationUtils;
import co.aospa.glyph.Utils.ResourceUtils;
import co.aospa.glyph.Utils.ServiceUtils;

public class AnimationSettingsFragment
        extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener, OnCheckedChangeListener {

    private final String TAG = this.getClass().getSimpleName();

    private static final String FRAGMENT_TYPE_NOTIF = "NOTIFS";
    private static final String FRAGMENT_TYPE_CALL = "CALL";
    private static final String FRAGMENT_TYPE_FLIP = "FLIP";

    private String fragmentType = null;

    private MainSwitchPreference mSwitchBar;

    private List<String> mEssentialApps = new ArrayList<String>();
    private List<String> mEssentialAppsNames = new ArrayList<String>();

    private List<String> userAnimationList = new ArrayList<>();
    private List<String> bundledAnimationList = new ArrayList<>();

    private ListPreference mListPreference;
    private Preference mLivePreviewPreference;
    private MultiSelectListPreference mMultiSelectListPreference;
    private SwitchPreferenceCompat mReverseAnimationSwitch;

    private SwitchPreferenceCompat mGlyphFlipAnimationSwitch;

    private GlyphAnimationPreference mGlyphAnimationPreference;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private String fragmentTitle = null;

    private Thread livePreviewThread;

    private String animationPreviewKey;

    private String enableKey;

    private String animationListKey;

    private String livePreviewKey;

    private String defaultAnimation;

    private String userAnimationPrefix;
    private String reverseAnimationKey;

    private boolean shouldAlternate = false;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        Bundle args = getArguments();
        fragmentType = args.getString("type", "").toUpperCase();

        if (fragmentType.isEmpty() || !fragmentType.equals(FRAGMENT_TYPE_NOTIF)
                && !fragmentType.equals(FRAGMENT_TYPE_CALL)
                && !fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
            getParentFragmentManager().popBackStack();
            showToast("Fragment type is invalid!");
            return;
        }

        evaluatePrefs();

        getActivity().setTitle(fragmentTitle);

        mSwitchBar = findPreference(enableKey);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(isAnimationEnabled());

        mListPreference = findPreference(animationListKey);
        mListPreference.setOnPreferenceChangeListener(this);

        bundledAnimationList = getAnimations(0);
        userAnimationList = getAnimations(1);

        List<String[]> paired = new ArrayList<>();
        for (String name : bundledAnimationList) {
            paired.add(new String[]{name, name});
        }
        for (String name : userAnimationList) {
            paired.add(new String[]{name, userAnimationPrefix + name});
        }

        paired.sort(Comparator.comparing(p -> p[0]));

        List<String> animationEntryList
                = paired.stream().map(p -> p[0]).collect(Collectors.toList());
        List<String> animationEntryValues
                = paired.stream().map(p -> p[1]).collect(Collectors.toList());

        boolean hasFlipCsv = ResourceUtils.hasFlipCsv();
        if (fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
            animationEntryList.addFirst(
                    getString(R.string.glyph_settings_flip_animation_option_follow_notification)
            );
            if (hasFlipCsv) {
                animationEntryList.addFirst(getString(R.string.glyph_settings_default_option));
            }
        }
        if (fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
            animationEntryValues.addFirst(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
            if (hasFlipCsv) {
                animationEntryValues.addFirst("flip");
            }
        }
        mListPreference.setEntries(animationEntryList.toArray(new String[0]));
        mListPreference.setEntryValues(animationEntryValues.toArray(new String[0]));
            if (!animationEntryValues.contains(mListPreference.getValue())) {
                if (!fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
                    mListPreference.setValue(ResourceUtils.getString(defaultAnimation));
                } else {
                    mListPreference.setValue(SettingsManager.getGlyphFlipAnimation());
                }
        }

        mLivePreviewPreference = findPreference(livePreviewKey);
        mGlyphAnimationPreference = findPreference(animationPreviewKey);

        mReverseAnimationSwitch = findPreference(reverseAnimationKey);
        mReverseAnimationSwitch.setOnPreferenceChangeListener(this);

    }

    private void evaluatePrefs() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                addPreferencesFromResource(R.xml.glyph_notifs_settings);
                fragmentTitle = requireContext().getString(R.string.glyph_settings_notifs_toggle_title);

                animationPreviewKey = Constants.GLYPH_NOTIFS_SUB_PREVIEW;

                enableKey = Constants.GLYPH_NOTIFS_SUB_ENABLE;

                livePreviewKey = Constants.GLYPH_NOTIFS_SUB_LIVE_PREVIEW;

                userAnimationPrefix = Constants.GLYPH_USER_NOTIF_CSV_PREFIX;
                animationListKey = Constants.GLYPH_NOTIFS_SUB_ANIMATIONS;

                defaultAnimation = "glyph_settings_notifs_animations_default";
                reverseAnimationKey = Constants.GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE;

                PreferenceCategory mCategory = findPreference(Constants.GLYPH_NOTIFS_SUB_CATEGORY);

                PackageManager mPackageManager = getActivity().getPackageManager();
                List<ApplicationInfo> mApps =
                        mPackageManager.getInstalledApplications(PackageManager.GET_GIDS);
                mApps.sort(new ApplicationInfo.DisplayNameComparator(mPackageManager));
                for (ApplicationInfo app : mApps) {
                    if (mPackageManager.getLaunchIntentForPackage(app.packageName) != null
                            && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, app.packageName)) {// apps with launcher intent
                        SwitchPreferenceCompat mSwitchPreference
                                = new SwitchPreferenceCompat(getPreferenceScreen().getContext());
                        mSwitchPreference.setKey(app.packageName);
                        mSwitchPreference.setTitle(" " + app.loadLabel(mPackageManager)); // add this space since the layout looks off otherwise
                        mSwitchPreference.setIcon(app.loadIcon(mPackageManager));
                        mSwitchPreference.setDefaultValue(true);
                        mSwitchPreference.setOnPreferenceChangeListener(this);
                        mCategory.addPreference(mSwitchPreference);

                        mEssentialApps.add(app.packageName);
                        mEssentialAppsNames.add(app.loadLabel(mPackageManager).toString());
                    }
                }

                mMultiSelectListPreference = findPreference(Constants.GLYPH_NOTIFS_SUB_ESSENTIAL);
                mMultiSelectListPreference.setOnPreferenceChangeListener(this);
                mMultiSelectListPreference.setEntries(mEssentialAppsNames.toArray(new CharSequence[0]));
                mMultiSelectListPreference.setEntryValues(mEssentialApps.toArray(new CharSequence[0]));
            }
            case FRAGMENT_TYPE_CALL -> {
                addPreferencesFromResource(R.xml.glyph_call_settings);
                fragmentTitle =
                        requireContext().getString(R.string.glyph_settings_call_toggle_title);

                animationPreviewKey = Constants.GLYPH_CALL_SUB_PREVIEW;
                userAnimationPrefix = Constants.GLYPH_USER_CALL_CSV_PREFIX;
                enableKey = Constants.GLYPH_CALL_SUB_ENABLE;
                animationListKey = Constants.GLYPH_CALL_SUB_ANIMATIONS;
                defaultAnimation = "glyph_settings_call_animations_default";
                livePreviewKey = Constants.GLYPH_CALL_SUB_LIVE_PREVIEW;
                reverseAnimationKey = Constants.GLYPH_CALL_REVERSE_ANIMATION_ENABLE;

            }
            case FRAGMENT_TYPE_FLIP -> {
                addPreferencesFromResource(R.xml.glyph_flip_settings);
                fragmentTitle =
                        requireContext().getString(R.string.glyph_settings_flip_toggle_title);
                animationPreviewKey = Constants.GLYPH_FLIP_SUB_PREVIEW;
                userAnimationPrefix = Constants.GLYPH_USER_NOTIF_CSV_PREFIX;
                animationListKey = Constants.GLYPH_FLIP_SUB_ANIMATIONS;
                enableKey = Constants.GLYPH_FLIP_SUB_ENABLE;
                livePreviewKey = Constants.GLYPH_FLIP_SUB_LIVE_PREVIEW;
                reverseAnimationKey = Constants.GLYPH_FLIP_REVERSE_ANIMATION_ENABLE;

                mGlyphFlipAnimationSwitch = findPreference(Constants.GLYPH_FLIP_SUB_ANIMATION_ENABLE);
                mGlyphFlipAnimationSwitch.setOnPreferenceChangeListener(this);

            }
        }
    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean isPlayable = true;

        shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                && mListPreference.getValue().equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
        boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;

        mReverseAnimationSwitch.setVisible(!shouldAlternate);

        if (mListPreference.getValue().startsWith(userAnimationPrefix)) {
            String animationName = mListPreference.getValue();
            AnimationUtils.checkUserAnimation(animationName);
            try {
                String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                        StandardCharsets.UTF_8);
                isPlayable = !AnimationUtils.isAnimationComplex(csv);
            } catch (Exception e) {

            }
        }
        if (isPlayable) {
            mGlyphAnimationPreference.updateAnimation(
                    isAnimationEnabled(),
                    getGlyphAnimation(),
                    1500,
                    shouldReverse,
                    shouldAlternate
            );
        }
        mGlyphAnimationPreference.setVisible(isPlayable);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        if (preferenceKey.equals(animationListKey)) {
            String animationName = newValue.toString();
            boolean isPlayable = true;

            shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                    && animationName.equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
            boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;

            mReverseAnimationSwitch.setVisible(!shouldAlternate);

            if (shouldAlternate) animationName = SettingsManager.getGlyphNotifsAnimation();
            if (animationName.startsWith(userAnimationPrefix)) {
                if (!AnimationUtils.checkUserAnimation(animationName)) return false;
                try {
                    String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                            StandardCharsets.UTF_8);
                    isPlayable = !AnimationUtils.isAnimationComplex(csv);
                } catch (Exception e) {

                }
            }
            mGlyphAnimationPreference.updateAnimation(
                    isAnimationEnabled(),
                    animationName,
                    1500,
                    shouldReverse,
                    shouldAlternate
            );
            mGlyphAnimationPreference.setVisible(isPlayable);
            if (livePreviewThread != null && livePreviewThread.isAlive()) {
                livePreviewThread.interrupt();
            }
            if (!isPlayable) {
                showToast(R.string.glyph_settings_user_animation_is_complex);
            }

            return true;
        }

        if (preferenceKey.equals(Constants.GLYPH_FLIP_SUB_ANIMATION_ENABLE)) {
            mGlyphAnimationPreference.updateAnimation((Boolean) newValue);
        }

        if (preferenceKey.equals(reverseAnimationKey)) {
            mGlyphAnimationPreference.updateAnimation(isAnimationEnabled(), 1500, (Boolean) newValue);
        }

        return true;
    }

    private List<String> getAnimations(int domain) {
        switch (domain) {
            case 0 -> {
                switch (fragmentType) {
                    case FRAGMENT_TYPE_NOTIF, FRAGMENT_TYPE_FLIP -> {
                        return ResourceUtils.getBundledNotificationAnimations();
                    }
                    case FRAGMENT_TYPE_CALL -> {
                        return ResourceUtils.getBundledCallAnimations();
                    }
                }
            }
            case 1 -> {
                switch (fragmentType) {
                    case FRAGMENT_TYPE_NOTIF, FRAGMENT_TYPE_FLIP -> {
                        return ResourceUtils.getUserNotificationAnimations();
                    }
                    case FRAGMENT_TYPE_CALL -> {
                        return ResourceUtils.getUserCallAnimations();
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private String getGlyphAnimation() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                return SettingsManager.getGlyphNotifsAnimation();
            }

            case FRAGMENT_TYPE_CALL -> {
                return SettingsManager.getGlyphCallAnimation();
            }

            case FRAGMENT_TYPE_FLIP -> {
                String value = SettingsManager.getGlyphFlipAnimation();
                if (value.equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE)) {
                    return SettingsManager.getGlyphNotifsAnimation();
                } else {
                    return value;
                }
            }
        }
        return "";
    }

    private boolean isAnimationEnabled() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                return SettingsManager.isGlyphNotifsEnabled();
            }

            case FRAGMENT_TYPE_CALL -> {
                return SettingsManager.isGlyphCallEnabled();
            }

            case FRAGMENT_TYPE_FLIP -> {
                return SettingsManager.isGlyphFlipEnabled()
                        && SettingsManager.isGlyphFlipAnimationEnabled();
            }
        }
        return false;
    }

    private void setAnimationEnabled(boolean state){
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                SettingsManager.setGlyphNotifsEnabled(state);
            }
            case FRAGMENT_TYPE_CALL -> {
                SettingsManager.setGlyphCallEnabled(state);
            }
            case FRAGMENT_TYPE_FLIP -> {
                SettingsManager.setGlyphFlipEnabled(state);
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (livePreviewKey.equals(preference.getKey())) {
            mLivePreviewPreference.setEnabled(false);
            mLivePreviewPreference.setSummary(
                    R.string.glyph_settings_animations_live_preview_summary_playing
            );
            livePreviewThread = new Thread(() -> {
                Activity activity = getActivity();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    resetLivePreview();
                    return;
                }
                shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                        && mListPreference.getValue().equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
                boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;
                AnimationManager.playCsv(
                        requireContext(),
                        getGlyphAnimation(),
                        false,
                        shouldReverse,
                        shouldAlternate
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
        setAnimationEnabled(isChecked);
        ServiceUtils.checkGlyphService();
        mGlyphAnimationPreference.updateAnimation(isChecked, getGlyphAnimation(), 1500);
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


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (livePreviewThread != null && livePreviewThread.isAlive()) {
            livePreviewThread.interrupt();
            livePreviewThread = null;
        }
    }

}
