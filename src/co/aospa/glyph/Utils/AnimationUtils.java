package co.aospa.glyph.Utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import co.aospa.glyph.Constants.Constants;

public class AnimationUtils {

    private final String TAG = this.getClass().getSimpleName();

    public static boolean validateAnimation(String csv) throws Exception {
        int currentLine = 1;
        int requiredFrameLength = 0;
        int currentFrameLength;

        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            Iterator<String> it = reader.lines().iterator();
            String frame;
            while (it.hasNext()) {
                frame = it.next();
                if (frame != null) {
                    frame = sanitizeCsvLine(frame);
                    if (currentLine == 1) {
                        requiredFrameLength = getFrameLength(frame);
                        if (requiredFrameLength == 0) {
                            throw new IllegalStateException("Frame length invalid at line 1");
                        }
                    } else {
                        currentFrameLength = getFrameLength(frame);
                        if (currentFrameLength != requiredFrameLength) {
                            throw new IllegalArgumentException("Frame length invalid at line: "
                                    + currentLine);
                        }
                    }

                    try {
                        validateFrameBrightness(frame);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Failed to parse frame at line"
                                + currentLine, e);
                    }
                }
            currentLine++;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV is invalid at line: " + currentLine);
        }
        return true;
    }

    public static int getFrameLength(String frame) {
        return frame.split(",").length;
    }

    public static void validateFrameBrightness(String frame) throws IllegalArgumentException {
        int max = Constants.MAX_PATTERN_BRIGHTNESS;
        int min = 0;
        int idx = 1;

        for (String brightness : frame.split(",")) {
            int value = Integer.parseInt(brightness);
            if (value > max || value < min) {
                throw new IllegalArgumentException("Brightness value: " + value
                        + " is out of range at index " + idx);
            }
            idx++;
        }
    }

    public static String getDevice(String csv) {

        int frameLength = getFrameLength(csv.lines().findFirst().orElse(""));

            switch (frameLength) {
                case 5 -> {
                    return "phone1";
                }
                case 26 -> {
                    return "phone2a";
                }
                case 33 -> {
                    return "phone2";
                }
                case 36 -> {
                    return "phone3a";
                }
            }
        return null;
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
        line = line.replace(" ", "");
        line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
        return line;
    }

    public static Iterator<String> iterateCsvLines(BufferedReader reader, boolean reverse) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = sanitizeCsvLine(line);
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

}
