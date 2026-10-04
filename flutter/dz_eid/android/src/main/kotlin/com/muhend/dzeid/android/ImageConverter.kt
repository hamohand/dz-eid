package com.muhend.dzeid.android

import android.graphics.Bitmap
import android.util.Log
import com.gemalto.jp2.JP2Decoder
import com.muhend.dzeid.core.model.IdentityRecord
import com.muhend.dzeid.core.model.IdentityRecord.Photo
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Conversion des images de la puce (JPEG2000, que ni Android ni Flutter ne savent afficher)
 * en JPEG (photo) ou PNG (signature : traits nets et transparence conservés).
 * En cas d'échec, l'image d'origine est conservée telle quelle.
 */
object ImageConverter {

    private const val TAG = "DzEid"

    fun convertImages(record: IdentityRecord): IdentityRecord {
        var r = record
        if (r.photo() != null) {
            r = r.withPhoto(convert(r.photo(), Bitmap.CompressFormat.JPEG, "image/jpeg"))
        }
        if (r.signatureImage() != null) {
            r = r.withSignatureImage(convert(r.signatureImage(), Bitmap.CompressFormat.PNG, "image/png"))
        }
        return r
    }

    fun convert(photo: Photo, format: Bitmap.CompressFormat, targetMime: String): Photo {
        val mime = photo.mimeType()
        if (mime == "image/jpeg" || mime == "image/png" || !mime.contains("jp2")) {
            return photo // déjà affichable, ou format non pris en charge (WSQ)
        }
        return try {
            val bytes = Base64.getDecoder().decode(photo.base64())
            val bitmap = JP2Decoder(bytes).decode()
            if (bitmap == null) {
                Log.w(TAG, "Décodage JPEG2000 impossible : image laissée au format d'origine.")
                return photo
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(format, 92, out)
            bitmap.recycle()
            Photo(targetMime, Base64.getEncoder().encodeToString(out.toByteArray()), mime)
        } catch (t: Throwable) { // y compris UnsatisfiedLinkError (bibliothèque native)
            Log.w(TAG, "Conversion JPEG2000 impossible : ${t.javaClass.simpleName}")
            photo
        }
    }
}
