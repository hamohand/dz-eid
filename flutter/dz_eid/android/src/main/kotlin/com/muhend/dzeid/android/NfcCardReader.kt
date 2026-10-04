package com.muhend.dzeid.android

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.muhend.dzeid.core.AccessKey
import com.muhend.dzeid.core.EidException
import com.muhend.dzeid.core.EidReader
import com.muhend.dzeid.core.ErrorCode
import com.muhend.dzeid.core.ReadOptions
import com.muhend.dzeid.core.model.IdentityRecord
import net.sf.scuba.smartcards.IsoDepCardService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lecture d'une carte d'identité électronique par le NFC du téléphone.
 *
 * Le mode lecteur ([NfcAdapter.enableReaderMode]) est activé pendant l'attente : la carte est captée
 * par cette application et non par le système (pas de fenêtre « Nouveau tag »). Les callbacks de
 * [Listener] sont toujours appelés sur le thread principal. Une seule lecture à la fois.
 *
 * Aucune donnée personnelle ni clé d'accès n'est écrite dans les journaux.
 */
class NfcCardReader(private val context: Context) {

    /** Suivi d'une lecture. */
    interface Listener {
        /** Le mode lecteur est actif : demander à l'utilisateur d'approcher la carte. */
        fun onWaitingForCard(message: String)

        fun onProgress(step: String, percent: Int, message: String)

        fun onSuccess(record: IdentityRecord)

        fun onError(code: String, message: String)
    }

    /** État du NFC du téléphone. */
    enum class NfcStatus { AVAILABLE, DISABLED, UNAVAILABLE }

    private enum class State { WAITING, READING, FINISHED }

    private inner class Session(
        val key: AccessKey,
        val options: ReadOptions,
        val timeoutMs: Long,
        val listener: Listener,
    ) {
        @Volatile
        var state = State.WAITING
        @Volatile
        var isoDep: IsoDep? = null
        var retriesLeft = MAX_RETRIES
        val timeout = Runnable {
            finish(this) { listener.onError(ErrorCode.NO_CARD.name, NO_CARD_MESSAGE) }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dz-eid-nfc").apply { isDaemon = true }
    }

    @Volatile
    private var session: Session? = null
    @Volatile
    private var activity: Activity? = null

    /** Activité au premier plan, nécessaire au mode lecteur (à mettre à jour à chaque changement). */
    fun attachActivity(activity: Activity?) {
        this.activity = activity
    }

    fun nfcStatus(): NfcStatus {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcStatus.UNAVAILABLE
        return if (adapter.isEnabled) NfcStatus.AVAILABLE else NfcStatus.DISABLED
    }

    fun isBusy(): Boolean = session != null

    /**
     * Démarre l'attente d'une carte puis sa lecture.
     *
     * @param timeoutMs délai d'attente de la carte (la lecture elle-même n'est pas limitée par ce délai)
     */
    fun start(key: AccessKey, options: ReadOptions, timeoutMs: Long, listener: Listener) {
        main.post { startOnMain(key, options, timeoutMs, listener) }
    }

    /** Annule la lecture en cours (sans effet s'il n'y en a pas). */
    fun cancel() {
        main.post {
            val s = session ?: return@post
            val iso = s.isoDep
            finish(s) { s.listener.onError(AndroidErrorCodes.CANCELLED, "Lecture annulée.") }
            // Interrompt un échange en cours : l'erreur qui en résulte est ignorée (session terminée)
            if (iso != null) worker.execute { closeQuietly(iso) }
        }
    }

    /** À appeler au retour de l'activité au premier plan : réactive le mode lecteur si une lecture attend. */
    fun onActivityResumed(activity: Activity) {
        this.activity = activity
        main.post {
            val s = session ?: return@post
            if (s.state != State.FINISHED) enableReaderMode(activity)
        }
    }

    private fun startOnMain(key: AccessKey, options: ReadOptions, timeoutMs: Long, listener: Listener) {
        if (session != null) {
            listener.onError(ErrorCode.BUSY.name, "Une lecture est déjà en cours.")
            return
        }
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (adapter == null) {
            listener.onError(AndroidErrorCodes.NFC_UNAVAILABLE, "Ce téléphone ne dispose pas du NFC.")
            return
        }
        if (!adapter.isEnabled) {
            listener.onError(AndroidErrorCodes.NFC_DISABLED,
                "Le NFC est désactivé. Activez-le dans les paramètres du téléphone.")
            return
        }
        val current = activity
        if (current == null) {
            listener.onError(AndroidErrorCodes.NO_ACTIVITY, "L'application doit être au premier plan pour lire la carte.")
            return
        }
        CryptoSetup.ensureBouncyCastle()
        val s = Session(key, options, timeoutMs, listener)
        session = s
        if (!enableReaderMode(current)) {
            session = null
            listener.onError(AndroidErrorCodes.NO_ACTIVITY, "L'application doit être au premier plan pour lire la carte.")
            return
        }
        main.postDelayed(s.timeout, timeoutMs)
        listener.onWaitingForCard(WAITING_MESSAGE)
    }

    private fun enableReaderMode(activity: Activity): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000) }
        return try {
            adapter.enableReaderMode(activity, ::onTagDiscovered,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                extras)
            true
        } catch (e: IllegalStateException) { // activité pas au premier plan
            Log.w(TAG, "Mode lecteur NFC indisponible : ${e.message}")
            false
        }
    }

    private fun disableReaderMode() {
        val a = activity ?: return
        try {
            NfcAdapter.getDefaultAdapter(context)?.disableReaderMode(a)
        } catch (e: Exception) {
            Log.w(TAG, "Désactivation du mode lecteur : ${e.javaClass.simpleName}")
        }
    }

    /** Appelé par le système sur un thread Binder à chaque carte détectée. */
    private fun onTagDiscovered(tag: Tag) {
        val s = session ?: return
        synchronized(s) {
            if (s.state != State.WAITING) return
            s.state = State.READING
        }
        main.removeCallbacks(s.timeout)
        val iso = IsoDep.get(tag)
        if (iso == null) {
            main.post {
                finish(s) {
                    s.listener.onError(ErrorCode.NOT_EMRTD.name,
                        "Ce support sans contact n'est pas une carte d'identité électronique.")
                }
            }
            return
        }
        s.isoDep = iso
        worker.execute { read(s, iso) }
    }

    private fun read(s: Session, iso: IsoDep) {
        val start = SystemClock.elapsedRealtime()
        try {
            iso.connect()
            iso.timeout = ISO_DEP_TIMEOUT_MS
            val record = EidReader().read(IsoDepCardService(iso), s.key, s.options) { step, percent, message ->
                main.post { if (s.state == State.READING) s.listener.onProgress(step.name, percent, message) }
            }
            val converted = ImageConverter.convertImages(record)
            val v = converted.verification()
            val pa = v.passiveAuthentication()
            // Journal sans aucune donnée personnelle
            Log.i(TAG, "Lecture réussie en ${SystemClock.elapsedRealtime() - start} ms (${v.accessMethod()}, " +
                "DG ${v.dataGroupsRead()}, PA intégrité=${pa.dataIntegrity()} signature=${pa.signature()} " +
                "chaîne=${pa.certificateChain()}, AA=${v.activeAuthentication()?.result()})")
            main.post { finish(s) { s.listener.onSuccess(converted) } }
        } catch (t: Throwable) {
            val e = EidException.classify(t, ErrorCode.READ_ERROR, "Erreur de lecture de la carte. Réessayez.")
            Log.w(TAG, "Échec de lecture après ${SystemClock.elapsedRealtime() - start} ms : ${e.code()}")
            main.post { onReadFailed(s, e) }
        } finally {
            closeQuietly(iso)
        }
    }

    /** Carte perdue en cours de lecture : on attend qu'elle soit reposée (quelques tentatives). */
    private fun onReadFailed(s: Session, e: EidException) {
        if (s.state == State.FINISHED) return // annulée entre-temps
        if (e.code() == ErrorCode.CARD_LOST && s.retriesLeft > 0) {
            s.retriesLeft--
            s.isoDep = null
            synchronized(s) { s.state = State.WAITING }
            main.postDelayed(s.timeout, s.timeoutMs)
            s.listener.onWaitingForCard(
                "Connexion perdue. Reposez la carte au dos du téléphone et ne la bougez plus pendant la lecture.")
            return
        }
        finish(s) { s.listener.onError(e.code().name, e.message ?: "Erreur de lecture.") }
    }

    /** Termine la session une seule fois, sur le thread principal. */
    private fun finish(s: Session, notify: () -> Unit) {
        if (s.state == State.FINISHED) return
        s.state = State.FINISHED
        main.removeCallbacks(s.timeout)
        if (session === s) session = null
        disableReaderMode()
        notify()
    }

    private fun closeQuietly(iso: IsoDep) {
        try {
            iso.close()
        } catch (_: Exception) {
            // fermeture best-effort
        }
    }

    companion object {
        private const val TAG = "DzEid"
        private const val ISO_DEP_TIMEOUT_MS = 10_000
        private const val MAX_RETRIES = 3
        const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val WAITING_MESSAGE =
            "Approchez la carte du dos du téléphone (au centre) et maintenez-la immobile."
        private const val NO_CARD_MESSAGE =
            "Aucune carte détectée. Placez la carte contre le dos du téléphone, au niveau de l'antenne NFC."
    }
}
