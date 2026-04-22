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

import androidx.fragment.app.Fragment;
import android.os.Bundle;

import co.aospa.glyph.Constants.Constants;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class AnimationSettingsActivity extends CollapsingToolbarBaseActivity {

    private AnimationSettingsFragment mAnimationSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Constants.CONTEXT == null) {
            Constants.CONTEXT = getApplicationContext();
        }

        String type = getIntent().getStringExtra("type");
        if (type == null) {
            finish();
            return;
        }

        String contactId = getIntent().getStringExtra("contact_id");
        String pkg = getIntent().getStringExtra("package");

        Bundle args = new Bundle();
        args.putString("type", type);
        if (contactId != null) {
            args.putString("contact_id", contactId);
        }
        if (pkg != null) {
            args.putString("package", pkg);
        }

        Fragment fragment = getSupportFragmentManager().findFragmentById(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame
        );
        if (fragment == null) {
            mAnimationSettingsFragment = new AnimationSettingsFragment();
            mAnimationSettingsFragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                .add(
                        com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                        mAnimationSettingsFragment
                )
                .commit();
        } else {
            mAnimationSettingsFragment = (AnimationSettingsFragment) fragment;
        }
    }
}
