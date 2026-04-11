package co.aospa.glyph.Utils;

import static co.aospa.glyph.Utils.InterfaceUtils.showToast;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import co.aospa.glyph.Constants.Constants;

import co.aospa.glyph.R;

public class AnimationUtils {

    private final String TAG = this.getClass().getSimpleName();

    public static void validateAnimation(String csv) throws Exception {
        validateAnimation(csv, true, true);
    }

    public static List<String> validateAnimationWithList(String csv) {
        List<String> errorList = new ArrayList<>();
        try {
            errorList = validateAnimation(csv, false, false);
        } catch (Exception ignored) {

        }

        return errorList;
    }

    private static List<String> validateAnimation(String csv,
                                                 boolean shouldThrow,
                                                 boolean shouldRecover) {
        int currentLine = 1;
        int requiredFrameLength = 0;
        int currentFrameLength;

        List<String> errorList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            Iterator<String> it = reader.lines().iterator();
            String frame;
            while (it.hasNext()) {
                frame = it.next();
                if (frame != null) {
                    if (shouldRecover) {
                        frame = sanitizeCsvLine(frame);
                    } else {
                        frame = frame.endsWith(",") ? frame.substring(0, frame.length() - 1) : frame;
                    }
                    if (currentLine == 1) {
                        requiredFrameLength = getFrameLength(frame);
                        if (requiredFrameLength == 0) {
                            String error = "Frame length invalid at line 1";
                            errorList.add(error);
                            if (shouldThrow) throw new IllegalStateException(error);
                        }
                    } else {
                        currentFrameLength = getFrameLength(frame);
                        if (currentFrameLength != requiredFrameLength) {
                            String error = "Frame length invalid at line: " + currentLine +
                                    ". Expected " + requiredFrameLength + ", Found "
                                    + currentFrameLength;
                            errorList.add(error);
                            if (shouldThrow) throw new IllegalArgumentException(error);
                        }
                    }

                    try {
                        errorList.addAll(validateFrameBrightness(frame, shouldThrow));
                    } catch (IllegalArgumentException e) {
                        String error = "Failed to parse frame at line "
                                + currentLine + "(" + e.getMessage() + ")";
                        if (shouldThrow) throw new IllegalArgumentException(error);
                    }
                }
            currentLine++;
            }
        } catch (Exception e) {
           if (shouldThrow) throw new IllegalArgumentException("CSV is invalid at line "
                   + currentLine, e);
        }
        return errorList;
    }

    public static int getFrameLength(String frame) {
        return frame.split(",").length;
    }

    public static List<String> validateFrameBrightness(String frame, boolean shouldThrow)
            throws IllegalArgumentException {
        int max = Constants.MAX_PATTERN_BRIGHTNESS;
        int min = 0;
        int idx = 1;

        List<String> errorList = new ArrayList<>();

        for (String brightness : frame.split(",")) {
            int value = Integer.parseInt(brightness);
            if (value > max || value < min) {
                String error = "Brightness value: " + value
                        + " is out of range at index " + idx;
                errorList.add(error);
                if (shouldThrow) {
                    throw new IllegalArgumentException(error);
                }
            }
            idx++;
        }
        return errorList;
    }

    public static void validateFrameBrightness(String frame) throws IllegalArgumentException {
        validateFrameBrightness(frame, true);
    }

    public static boolean isAnimationComplex(String csv) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            Iterator<String> it = iterateCsvLines(reader, false);
            String line;
            String device = getDevice(csv);
            while (it.hasNext()) {
                line = it.next();
                int[] arr = Arrays.stream(line.split(","))
                        .mapToInt(Integer::parseInt)
                        .toArray();
                if (arr.length == 5) return false;
                switch (device) {
                    case Constants.Device.PHONE3A -> {
                        if (allSame(arr, 0, 20)
                                && allSame(arr, 21, 31)
                                && allSame(arr, 32, 35)) {
                            continue;
                        } else {
                            return true;
                        }
                    }
                    case Constants.Device.PHONE2 -> {
                        if (allSame(arr, 0, 2)
                                && allSame(arr, 3, 18)
                                && allSame(arr, 19, 32)) {
                            continue;
                        } else {
                            return true;
                        }
                    }
                    case Constants.Device.PHONE2A -> {
                        if (allSame(arr, 0, 23)) {
                            continue;
                        } else {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {

        }
        return false;
    }

    public static boolean checkUserAnimation(String animationName) {
        try {
            String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                    StandardCharsets.UTF_8);
            validateAnimation(csv);
            if (!isCompatible(csv)) {
                showToast(R.string.glyph_settings_user_animation_incompatible);
                return false;
            }
        } catch (Exception e) {
            showToast(R.string.glyph_settings_user_animation_invalid);
            Log.w(AnimationUtils.class.getSimpleName(), e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

     static boolean allSame(int[] arr, int start, int end) {
        int first = arr[start];
        for (int i = start + 1; i <= end; i++) {
            if (arr[i] != first) return false;
        }
        return true;
    }

    public static String getDevice(String csv) {

        int frameLength = getFrameLength(sanitizeCsvLine(csv.lines().findFirst().orElse("")));

            switch (frameLength) {
                case 5 -> {
                    return Constants.Device.PHONE1;
                }
                case 26 -> {
                    return Constants.Device.PHONE2A;
                }
                case 33 -> {
                    return Constants.Device.PHONE2;
                }
                case 36 -> {
                    return Constants.Device.PHONE3A;
                }
            }
        return "";
    }

    public static boolean isCompatible(String csv) {

        boolean compatible;

        compatible = getDevice(csv).equals(Constants.Device.getDevice());

        if (Constants.Device.isPhone2()) {
            compatible = getDevice(csv).equals(Constants.Device.getDevice())
                    || getDevice(csv).equals(Constants.Device.PHONE1);
        }

        return compatible;
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

    public static String sanitizeCsvLine(String line) {
        line = line.replaceAll("-\\d+", "0");
        line = line.replaceAll("[^0-9,\n]", "");
        line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
        return line;
    }

    public static int getLineCount(String csv) {
        return csv.split("\n", -1).length;
    }

    public static double calcAnimPlaytime(int lineCount) {
        double frameInterval = 1000.0 / 60;
        return lineCount * frameInterval;
    }

    public static double calcAnimPlaytime(String csv) {
        double frameInterval = 1000.0 / 60;
        return getLineCount(csv) * frameInterval;
    }

    public static String toReadableDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    public static String toReadableDuration(double ms) {
        return toReadableDuration((long) ms);
    }

    public static Iterator<String> iterateCsvLines(BufferedReader reader)
            throws IOException {

        return iterateCsvLines(reader, false, false);
    }

    public static Iterator<String> iterateCsvLines(BufferedReader reader, boolean reverse)
            throws IOException {

        return iterateCsvLines(reader, reverse, false);
    }

    public static Iterator<String> iterateCsvLines(BufferedReader reader, boolean reverse,
                                                   boolean alternate) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = sanitizeCsvLine(line);
            lines.add(line);
        }
        if (alternate) {
            List<String> reversed = new ArrayList<>(lines);
            Collections.reverse(reversed);
            lines.addAll(reversed);
            return lines.iterator();
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

    public static class Holder {

        public static class oggMeta {

            private static Map<String, String> map;

            public static void setMap(Map<String, String> m) {
                map = m;
            }

            public static Map<String, String> getMap() {
                return map;
            }

            public static void clear() {
                map = null;
            }
        }

    }

}
