package co.aospa.glyph.Settings;


import static co.aospa.glyph.Utils.InterfaceUtils.showToast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Utils.AnimationUtils;

public class OggSettingsFragment extends SettingsBasePreferenceFragment {

    private Preference mLivePreviewPreference;
    private PreferenceScreen mScreen;

    private Thread livePreviewThread;

    public static final String mapKeyDevice = "Device";
    public static final String mapKeyAnimLength = "Length";
    public static final String mapKeyFilename = "Filename";

    private String csv;
    private String origFilename;

    private Map<String, String> metadata;

    private String TAG = this.getClass().getSimpleName();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        metadata = AnimationUtils.Holder.oggMeta.getMap();
        origFilename = metadata.get(mapKeyFilename);

        mScreen = getPreferenceManager().createPreferenceScreen(requireContext());

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
        } else {
            addSavePreferences();
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

    private boolean exportCsvFromOgg(int type) {
        int dot = origFilename.lastIndexOf('.');
        String nameWithoutExt = dot != -1 ? origFilename.substring(0, dot) : origFilename;
        String path = "";

        switch (type) {
            case 0 -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE, nameWithoutExt + ".csv");
                mSaveFileLauncher.launch(intent);
                return true;
            }
            case 1 -> {
                path = Constants.GLYPH_USER_NOTIF_CSV_PATH;
            }
            case 2 -> {
                path = Constants.GLYPH_USER_CALL_CSV_PATH;
            }
        }

        if (!path.isEmpty()) {
            File target = getUniqueFile(
                    Environment.getExternalStorageDirectory().toString()
                            + "/"
                            + path,
                    nameWithoutExt + ".csv"
            );
            try (FileWriter writer = new FileWriter(target)) {
                writer.write(csv);
                showToast(getString(R.string.ogg_csv_export_complete_message_start) + " "
                        + target.toString());
                return true;
            } catch (IOException e) {
                showToast("Unable to save file!");
                return false;
            }
        }
        return false;
    }

    private void addSavePreferences() {
        Preference saveNotifPref = new Preference(mScreen.getContext());
        PreferenceCategory quickExportCategory = new PreferenceCategory(mScreen.getContext());
        Preference saveCallPref = new Preference(mScreen.getContext());
        saveCallPref.setTitle(R.string.ogg_csv_export_call_animation);
        saveCallPref.setIcon(R.drawable.ic_add_call);
        saveCallPref.setOnPreferenceClickListener(pref -> {
            if (exportCsvFromOgg(2)) {
                pref.setEnabled(false);
                pref.setSummary(R.string.file_already_exported_summary);
            }
            return true;
        });
        saveNotifPref.setTitle(R.string.ogg_csv_export_notification_animation);
        saveNotifPref.setIcon(R.drawable.ic_notification_add);
        saveNotifPref.setOnPreferenceClickListener(pref -> {
            if (exportCsvFromOgg(1)) {
                pref.setEnabled(false);
                pref.setSummary(R.string.file_already_exported_summary);
            }
            return true;
        });
        mScreen.addPreference(quickExportCategory);
        quickExportCategory.addPreference(saveCallPref);
        quickExportCategory.addPreference(saveNotifPref);
    }

    private static File getUniqueFile(String directory, String filename) {
        int dotIndex = filename.lastIndexOf('.');
        String name = dotIndex != -1 ? filename.substring(0, dotIndex) : filename;
        String ext  = dotIndex != -1 ? filename.substring(dotIndex) : "";

        File file = new File(directory, filename);
        int counter = 1;

        while (file.exists()) {
            file = new File(directory, name + "(" + counter + ")" + ext);
            counter++;
        }

        return file;
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
            exportCsvFromOgg(0);
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
