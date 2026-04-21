package co.aospa.glyph.Settings;


import static co.aospa.glyph.Utils.InterfaceUtils.showToast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Utils.AnimationUtils;
import co.aospa.glyph.Utils.ResourceUtils;

public class OggSettingsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private Preference mLivePreviewPreference;

    private Thread livePreviewThread;

    public static final String mapKeyDevice = "Device";
    public static final String mapKeyAnimLength = "Length";
    public static final String mapKeyFilename = "Filename";

    public String csv;

    private Map<String, String> metadata;

    private String TAG = this.getClass().getSimpleName();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        metadata = AnimationUtils.Holder.oggMeta.getMap();

        PreferenceScreen mScreen = getPreferenceManager().createPreferenceScreen(requireContext());

        getActivity().setTitle(R.string.glyph_ogg_metadata_title);

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            Preference pref = new Preference(requireContext());
            String key = entry.getKey();
            String value = entry.getValue();
            pref.setSelectable(false);
            if (key.equals("csv")) {
                this.csv = value;
                continue;
            }
            pref.setKey(entry.getKey());
            switch (key) {
                case mapKeyDevice -> {
                    pref.setTitle(R.string.metadata_device);
                    switch (value) {
                        case Constants.Device.PHONE1 -> {
                            pref.setSummary(R.string.metadata_device_phone1_proper);
                        }
                        case Constants.Device.PHONE2 -> {
                            pref.setSummary(R.string.metadata_device_phone2_proper);
                        }
                        case Constants.Device.PHONE2A -> {
                            pref.setSummary(R.string.metadata_device_phone2a_proper);
                        }
                        case Constants.Device.PHONE3A -> {
                            pref.setSummary(R.string.metadata_device_phone3a_proper);
                        }
                        default -> {
                            pref.setSummary(R.string.metadata_device_unsupported);
                        }
                    }
                }

                case mapKeyAnimLength -> {
                    pref.setTitle(R.string.metadata_pattern_duration);
                    pref.setSummary(AnimationUtils.toReadableDuration(Long.parseLong(value)));
                }
                case mapKeyFilename -> {
                    pref.setTitle(R.string.metadata_filename);
                    pref.setSummary(value);
                }

            }
            mScreen.addPreference(pref);
        }
        setPreferenceScreen(mScreen);

        addPreferencesFromResource(R.xml.glyph_ogg_options);

        mLivePreviewPreference = findPreference(Constants.GLYPH_OGG_LIVE_PREVIEW);

        boolean incompatible = !AnimationUtils.isCompatible(csv);
        if (incompatible) {
            mLivePreviewPreference.setSummary(R.string.glyph_settings_user_animation_incompatible);
            mLivePreviewPreference.setEnabled(false);
        }

    }

    private final ActivityResultLauncher<Intent> mSaveFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    try (OutputStream output =
                                 requireContext().getContentResolver().openOutputStream(uri)) {
                        output.write(csv.getBytes(StandardCharsets.UTF_8));
                        showToast(getString(R.string.file_export_complete_message));
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to save file", e);
                    }
                }
            }
    );

    private void exportCsvFromOgg(String basename) {
        int dot = basename.lastIndexOf('.');
        String nameWithoutExt = dot != -1 ? basename.substring(0, dot) : basename;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, nameWithoutExt + ".csv");
        mSaveFileLauncher.launch(intent);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }


    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (Constants.GLYPH_OGG_LIVE_PREVIEW.equals(preference.getKey())) {
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
                AnimationManager.playExternalCsv(csv, metadata.get(mapKeyFilename));

                if (activity != null) {
                    activity.runOnUiThread(this::resetLivePreview);
                }
            });
            livePreviewThread.start();
        }
        if (Constants.GLYPH_UTILITIES_OGG_EXPORT_CSV.equals(preference.getKey())) {
            exportCsvFromOgg(metadata.get(mapKeyFilename));
        }
        return true;
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
        AnimationUtils.Holder.oggMeta.clear();
    }

}
