/*
 * Copyright (C) 2023-2024 Paranoid Android
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

package co.aospa.glyph.Utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;

public final class ResourceUtils {

    private static final String TAG = "GlyphResourceUtils";
    private static final boolean DEBUG = true;

    private static Context context;
    private static AssetManager assetManager;
    private static Resources resources;

    private static String[] callAnimations = null;
    private static String[] notificationAnimations = null;

    private static Context getContext() {
        if (context == null) {
            context = Constants.CONTEXT;
            if (context == null) {
                throw new IllegalStateException("Constants.CONTEXT is not initialized");
            }
        }
        return context;
    }

    private static AssetManager getAssetManager() {
        if (assetManager == null) {
            assetManager = getContext().getAssets();
        }
        return assetManager;
    }

    private static Resources getResources() {
        if (resources == null) {
            resources = getContext().getResources();
        }
        return resources;
    }

    public static int getIdentifier(String id, String type) {
        return getResources().getIdentifier(id, type, getContext().getPackageName());
    }

    public static Boolean getBoolean(String id) {
        return getResources().getBoolean(getIdentifier(id, "bool"));
    }

    public static String getString(String id) {
        return getResources().getString(getIdentifier(id, "string"));
    }

    public static int getInteger(String id) {
        return getResources().getInteger(getIdentifier(id, "integer"));
    }

    public static String[] getStringArray(String id) {
        return getResources().getStringArray(getIdentifier(id, "array"));
    }

    public static int[] getIntArray(String id) {
        return getResources().getIntArray(getIdentifier(id, "array"));
    }

    public static float[] buildPatternArray(float[]... arrays) {
        int totalLength = 0;
        for (float[] arr : arrays) {
            totalLength += arr.length;
        }

        float[] result = new float[totalLength];
        int pos = 0;
        for (float[] arr : arrays) {
            System.arraycopy(arr, 0, result, pos, arr.length);
            pos += arr.length;
        }
        return result;
    }

    public static int[] buildPatternArray(int[]... arrays) {
        int totalLength = 0;
        for (int[] arr : arrays) {
            totalLength += arr.length;
        }

        int[] result = new int[totalLength];
        int pos = 0;
        for (int[] arr : arrays) {
            System.arraycopy(arr, 0, result, pos, arr.length);
            pos += arr.length;
        }
        return result;
    }

    public static Iterator<String> iterateCsvLines(BufferedReader reader, boolean reverse) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.replace(" ", "");
            line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
            lines.add(line);
        }
        if (reverse) Collections.reverse(lines);
        return lines.iterator();
    }

    public static int[] reverseFrameArray(int[] array) {
        int[] copy = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[array.length - 1 - i];
        }
        return copy;
    }

    public static float[] reverseFrameArray(float[] array) {
        float[] copy = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[array.length - 1 - i];
        }
        return copy;
    }

    public static String[] getCallAnimations() {
        if (callAnimations == null) {
            try {
                String[] assets = getAssetManager().list("call");
                for (int i=0; i < assets.length; i++) {
                    assets[i] = assets[i].replaceAll(".csv", "");
                }
                callAnimations = assets;
            } catch (IOException e) { }
        }
        return callAnimations;
    }

    public static String[] getNotificationAnimations() {
        if (notificationAnimations == null) {
            try {
                String[] assets = getAssetManager().list("notification");
                for (int i=0; i < assets.length; i++) {
                    assets[i] = assets[i].replaceAll(".csv", "");
                }
                notificationAnimations = assets;
            } catch (IOException e) { }
        }
        return notificationAnimations;
    }

    public static InputStream getCallAnimation(String name) throws IOException {
        if (callAnimations == null) getCallAnimations();

        if (ArrayUtils.contains(callAnimations, name))
            return getAssetManager().open("call/" + name + ".csv");

        return getAssetManager().open("call/" + ResourceUtils.getString("glyph_settings_call_animations_default") + ".csv");
    }

    public static InputStream getNotificationAnimation(String name) throws IOException {
        if (notificationAnimations == null) getNotificationAnimations();

        if (ArrayUtils.contains(notificationAnimations, name))
            return getAssetManager().open("notification/" + name + ".csv");

        return getAssetManager().open("call/" + ResourceUtils.getString("glyph_settings_notifs_animations_default") + ".csv");
    }

    public static InputStream getAnimation(String name) throws IOException {
        if (callAnimations == null) getCallAnimations();
        if (notificationAnimations == null) getNotificationAnimations();

        if (ArrayUtils.contains(callAnimations, name)) {
            return getCallAnimation(name);
        }

        if (ArrayUtils.contains(notificationAnimations, name)) {
            return getNotificationAnimation(name);
        }

        return getAssetManager().open(name + ".csv");
    }
}
