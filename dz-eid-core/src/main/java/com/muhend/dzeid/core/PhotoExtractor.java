package com.muhend.dzeid.core;

import com.muhend.dzeid.core.model.IdentityRecord.Photo;
import com.muhend.dzeid.core.util.IoUtil;
import org.jmrtd.lds.iso19794.FaceImageInfo;
import org.jmrtd.lds.iso19794.FaceInfo;
import org.jmrtd.lds.icao.DG2File;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Extraction de la photo du titulaire depuis le DG2 (ISO/IEC 19794-5).
 * La photo est renvoyée telle qu'enregistrée sur la puce (souvent JPEG2000).
 */
public final class PhotoExtractor {

    private PhotoExtractor() {
    }

    @SuppressWarnings("deprecation") // getFaceInfos() reste l'accès le plus simple et stable aux images
    public static Photo extract(byte[] dg2) {
        if (dg2 == null || dg2.length == 0) {
            return null;
        }
        try {
            DG2File file = new DG2File(new ByteArrayInputStream(dg2));
            for (FaceInfo face : file.getFaceInfos()) {
                for (FaceImageInfo image : face.getFaceImageInfos()) {
                    byte[] bytes = IoUtil.readFully(image.getImageInputStream());
                    if (bytes.length > 0) {
                        return new Photo(normalizeMime(image.getMimeType(), bytes),
                                Base64.getEncoder().encodeToString(bytes), null);
                    }
                }
            }
        } catch (Exception ignored) {
            // repli : recherche directe de la signature de l'image
        }
        return scanForImage(dg2);
    }

    /** Repli robuste : localise une image JPEG ou JPEG2000 par sa signature binaire. */
    static Photo scanForImage(byte[] data) {
        for (int i = 0; i + 12 <= data.length; i++) {
            int b0 = data[i] & 0xFF;
            int b1 = data[i + 1] & 0xFF;
            int b2 = data[i + 2] & 0xFF;
            int b3 = data[i + 3] & 0xFF;
            boolean jpeg = b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF;
            boolean jp2Box = b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x0C
                    && (data[i + 4] & 0xFF) == 0x6A && (data[i + 5] & 0xFF) == 0x50;
            boolean j2kStream = b0 == 0xFF && b1 == 0x4F && b2 == 0xFF && b3 == 0x51;
            if (jpeg || jp2Box || j2kStream) {
                byte[] img = new byte[data.length - i];
                System.arraycopy(data, i, img, 0, img.length);
                String mime = jpeg ? "image/jpeg" : "image/jp2";
                return new Photo(mime, Base64.getEncoder().encodeToString(img), null);
            }
        }
        return null;
    }

    private static String normalizeMime(String declared, byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (declared != null && !declared.isEmpty()) {
            return declared;
        }
        return "image/jp2";
    }
}
