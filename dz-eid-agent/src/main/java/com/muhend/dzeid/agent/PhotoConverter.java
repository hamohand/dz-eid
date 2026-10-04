package com.muhend.dzeid.agent;

import com.muhend.dzeid.core.model.IdentityRecord.Photo;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Conversion de la photo JPEG2000 (format de la puce) en JPEG, affichable dans tous les navigateurs.
 */
final class PhotoConverter {

    private static final Logger LOG = Logger.getLogger(PhotoConverter.class.getName());

    private PhotoConverter() {
    }

    /** @return la photo convertie, ou la photo d'origine si la conversion est impossible ou inutile */
    static Photo toJpeg(Photo photo) {
        if (photo == null || "image/jpeg".equals(photo.mimeType())) {
            return photo;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(photo.base64())));
            if (src == null) {
                LOG.warning("Aucun décodeur pour " + photo.mimeType() + " : photo laissée dans son format d'origine.");
                return photo;
            }
            BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(src, 0, 0, java.awt.Color.WHITE, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(rgb, "jpg", out)) {
                return photo;
            }
            return new Photo("image/jpeg", Base64.getEncoder().encodeToString(out.toByteArray()), photo.mimeType());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Conversion de la photo impossible", e);
            return photo;
        }
    }
}
