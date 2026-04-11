package co.aospa.glyph.Settings.Utilities;

import static co.aospa.glyph.Utils.InterfaceUtils.showDialog;
import static co.aospa.glyph.Utils.ResourceUtils.getFileName;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.R;
import co.aospa.glyph.Settings.OggSettingsFragment;
import co.aospa.glyph.Settings.SubSettingsActivity;
import co.aospa.glyph.Utils.AnimationUtils;
import co.aospa.glyph.Utils.OGGParser;

public class  UtilitiesFragment extends SettingsBasePreferenceFragment {

        private String csvContent;

        private String mSaveContent;

        private final String TAG = getClass().getSimpleName();

        private Consumer<Uri> mFilePickerAction;

        private final String[] csvMime = {"text/csv", "text/comma-separated-values"};
        private final String[] oggMime = {"audio/ogg", "application/ogg"};

        private OGGParser parser;

        public static final String mapKeyDevice = "Device";
        public static final String mapKeyAnimLength = "Length";
        public static final String mapKeyAudioLength = "audioLength";
        public static final String mapKeyFilename = "Filename";


        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.glyph_utilities);
            getActivity().setTitle(R.string.glyph_settings_utilities_title);
            Preference csvValidatorPreference =
                    findPreference(Constants.GLYPH_UTILITIES_VALIDATE_CSV);
            Preference OGGmetaPreference =
                    findPreference(Constants.GLYPH_UTILITIES_READ_OGG);

        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            String preferenceKey = preference.getKey();
            switch (preferenceKey) {
                case Constants.GLYPH_UTILITIES_VALIDATE_CSV -> {
                    mFilePickerAction = uri -> {
                        try {
                            InputStream is
                                    = requireContext().getContentResolver().openInputStream(uri);
                            csvContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            showCSVresult(AnimationUtils.validateAnimationWithList(csvContent), uri);
                        } catch (Exception e) {
                            showDialog(requireActivity(),
                                    "Error reading CSV file!", "",
                                    "OK", null,
                                    null, null);
                            Log.w(TAG, e);
                        }
                    };
                    mFilePicker.launch(csvMime);
                }
                case Constants.GLYPH_UTILITIES_READ_OGG -> {
                    mFilePickerAction = uri -> {
                        try {
                            InputStream is
                                    = requireContext().getContentResolver().openInputStream(uri);
                            parser = new OGGParser(is);
                            showUserOggMeta(uri);
                        } catch (Exception e) {
                            showDialog(requireActivity(),
                                    "Error parsing OGG file", "",
                                    "OK", null,
                                    null, null);
                            Log.w(TAG, e);
                        }
                    };
                    mFilePicker.launch(oggMime);
                }
            }
        return true;
        }

        private ActivityResultLauncher<String[]> mFilePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && mFilePickerAction != null) {
                    mFilePickerAction.accept(uri);
                }
            }
        );

        private void showCSVresult(List<String> results, Uri contentUri) {
            View view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_scrollable, null);
            TextView message = view.findViewById(R.id.dialog_message);
            String title;
            if (results.isEmpty()) {
                title = "Results";
                results.add("No errors found.");
            } else {
                int amount = results.size();
                String count = String.valueOf(amount);
                title = count + ((amount > 1) ? " errors found" : " error found");
            }
            results.addFirst("Reading file: " + getFileName(requireContext(), contentUri));
            message.setText(String.join("\n", results));
            showDialog(requireActivity(), title, view, android.R.string.ok,
                    null, null, null);
        }

        private void showUserOggMeta(Uri contentUri) throws Exception {
            AnimationUtils.Holder.oggMeta.setMap(resolveUserOggMeta(contentUri));
            Intent intent = new Intent(requireContext(), SubSettingsActivity.class);
            intent.putExtra("fragment", OggSettingsFragment.class.getName());
            startActivity(intent);
        }

        private Map<String, String> resolveUserOggMeta(Uri contentUri) throws Exception {
            Map<String, String> newMeta = new HashMap<>();

            String csv = parser.getAnimation();
            String device = AnimationUtils.getDevice(csv);
            String fileName = getFileName(requireContext(), contentUri);

            newMeta.put(mapKeyFilename, fileName);
            newMeta.put(mapKeyDevice, device);
            newMeta.put(mapKeyAnimLength, String.valueOf(Math.round(AnimationUtils.calcAnimPlaytime(csv))));
            newMeta.put("csv", csv);

            return newMeta;
        }

}
