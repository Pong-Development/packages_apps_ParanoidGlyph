package co.aospa.glyph.Utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

public class OGGParser {

    private final String TAG = this.getClass().getSimpleName();

    private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};
    private static final byte[] OPUS_HEAD = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
    private static final byte[] OPUS_TAGS = {'O', 'p', 'u', 's', 'T', 'a', 'g', 's'};

    public static final String composerField = "COMPOSER";
    private static final String glyphDotsField = "CUSTOM1";
    public static final String glyphFrameDataField = "AUTHOR";
    public static final String columnCountField = "CUSTOM2";

    public final Map<String, String> metadata;

    public OGGParser(InputStream is) throws Exception {
        try {
            this.metadata = read(is);
        } catch (Exception e) {
            throw new Exception("Failed to parse OGG file. \n" + e.getMessage());
        }
    }

    public static Map<String, String> read(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(in));
        ByteArrayOutputStream packetBuf = new ByteArrayOutputStream();

        boolean seenOpusHead = false;

        while (true) {
            // ---- Ogg page header ----
            byte[] header = new byte[27];
            dis.readFully(header);

            for (int i = 0; i < 4; i++) {
                if (header[i] != OGG_MAGIC[i])
                    throw new IOException("Not an Ogg stream");
            }

            int pageSegments = header[26] & 0xFF;
            byte[] lacing = new byte[pageSegments];
            dis.readFully(lacing);

            // ---- Page data ----
            for (int i = 0; i < pageSegments; i++) {
                int len = lacing[i] & 0xFF;
                byte[] seg = new byte[len];
                dis.readFully(seg);
                packetBuf.write(seg);

                // packet boundary
                if (len < 255) {
                    byte[] packet = packetBuf.toByteArray();
                    packetBuf.reset();

                    if (!seenOpusHead && isHeader(packet, OPUS_HEAD)) {
                        seenOpusHead = true;
                    } else if (seenOpusHead && isHeader(packet, OPUS_TAGS)) {
                        return parseComments(packet, OPUS_TAGS.length);
                    }
                }
            }
        }
    }

    private static boolean isHeader(byte[] packet, byte[] magic) {
        if (packet.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (packet[i] != magic[i]) return false;
        }
        return true;
    }


    private static Map<String, String> parseComments(byte[] packet, int offset)
            throws IOException {

        DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(packet, offset, packet.length - offset));

        Map<String, String> map = new LinkedHashMap<>();

        int vendorLen = readLE32(in);
        byte[] vendor = new byte[vendorLen];
        in.readFully(vendor);

        int count = readLE32(in);
        for (int i = 0; i < count; i++) {
            int len = readLE32(in);
            byte[] data = new byte[len];
            in.readFully(data);

            String entry = new String(data, StandardCharsets.UTF_8);
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;

            String key = entry.substring(0, eq).toUpperCase(Locale.US);
            String value = entry.substring(eq + 1);

            map.put(key, value);

        }
        return map;
    }

    private static int readLE32(DataInputStream in) throws IOException {
        return (in.readUnsignedByte()) |
                (in.readUnsignedByte() << 8) |
                (in.readUnsignedByte() << 16) |
                (in.readUnsignedByte() << 24);
    }

    public String getAnimation() throws Exception {
        String animation;
        try {
            animation = readCompressedField(glyphFrameDataField);
        } catch (Exception e) {
            throw new Exception("Unable to get animation \n" + e.getMessage());
        }
        return animation;
    }


    public String readField(String fieldName) throws IllegalArgumentException {
        String values = metadata.get(fieldName);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Missing tag: " + fieldName);
        }
        return values;
    }

    public String readCompressedField(String fieldName) throws IllegalArgumentException {
        String base64Data;
        String fieldData;
        byte[] decodedData;

        try {
            base64Data = readField(fieldName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read field: " + fieldName, e);
        }

        try {
            decodedData = parseBase64(base64Data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Field is not base64 data!: " + fieldName, e);
        }

        try {
            fieldData = new String(inflateZlib(decodedData), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to uncompress: " + fieldName, e);
        }

        return fieldData;
    }

    private static byte[] parseBase64(String data) throws IllegalArgumentException {
        byte[] decoded;
        String sanitizedString = data.replaceAll("\\s+", "");
        try {
            decoded = Base64.getMimeDecoder().decode(sanitizedString);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Data is not valid Base64", e);
        }
        return decoded;
    }

    private static byte[] inflateZlib(byte[] data)
            throws IOException {

        Inflater inflater = new Inflater(false);

        try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
             InflaterInputStream zin = new InflaterInputStream(bin, inflater);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buf = new byte[4096];
            int n;
            while ((n = zin.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();

        } catch (ZipException e) {
            throw new ZipException(
                    "No valid zlib-compressed data found");
        }
    }

    public static int countCsvColumns(String csv) {
        if (csv == null || csv.isEmpty()) {
            return 0;
        }

        String[] lines = csv.split("\\R");

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            int columns = 1;
            boolean inQuotes = false;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (c == ',' && !inQuotes) {
                    columns++;
                }
            }
            return columns;
        }

        return 0;
    }
}
