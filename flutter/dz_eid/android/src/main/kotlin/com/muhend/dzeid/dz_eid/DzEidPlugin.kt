package com.muhend.dzeid.dz_eid

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.muhend.dzeid.android.AndroidErrorCodes
import com.muhend.dzeid.android.MrzScannerActivity
import com.muhend.dzeid.android.NfcCardReader
import com.muhend.dzeid.core.AccessKey
import com.muhend.dzeid.core.EidException
import com.muhend.dzeid.core.ReadOptions
import com.muhend.dzeid.core.model.IdentityRecord
import com.muhend.dzeid.core.model.IdentityRecordMaps
import com.muhend.dzeid.core.verify.CscaStore
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/**
 * Pont Flutter du plugin dz_eid. Toute la logique est dans le paquet `com.muhend.dzeid.android`
 * (indépendant de Flutter, extractible en bibliothèque Android autonome).
 *
 * Canal de méthodes `com.muhend.dzeid/dz_eid` : getNfcStatus, openNfcSettings, scanMrz, readCard, cancel.
 * Canal d'événements `com.muhend.dzeid/dz_eid/events` : progression de la lecture.
 */
class DzEidPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {

    private lateinit var channel: MethodChannel
    private lateinit var events: EventChannel
    private lateinit var appContext: Context
    private lateinit var reader: NfcCardReader

    private var eventSink: EventChannel.EventSink? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var pendingScan: Result? = null

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity === activityBinding?.activity) reader.onActivityResumed(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    // --- FlutterPlugin ---

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        reader = NfcCardReader(appContext)
        channel = MethodChannel(binding.binaryMessenger, "com.muhend.dzeid/dz_eid")
        channel.setMethodCallHandler(this)
        events = EventChannel(binding.binaryMessenger, "com.muhend.dzeid/dz_eid/events")
        events.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                eventSink = sink
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
            }
        })
        (appContext as? Application)?.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        reader.cancel()
        channel.setMethodCallHandler(null)
        events.setStreamHandler(null)
        (appContext as? Application)?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    // --- ActivityAware ---

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addActivityResultListener(this)
        reader.attachActivity(binding.activity)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        detachActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        reader.cancel()
        detachActivity()
    }

    private fun detachActivity() {
        activityBinding?.removeActivityResultListener(this)
        activityBinding = null
        reader.attachActivity(null)
    }

    // --- Méthodes ---

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getNfcStatus" -> result.success(reader.nfcStatus().name)
            "openNfcSettings" -> openNfcSettings(result)
            "scanMrz" -> scanMrz(result)
            "readCard" -> readCard(call, result)
            "cancel" -> {
                reader.cancel()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun openNfcSettings(result: Result) {
        val context: Context = activityBinding?.activity ?: appContext
        val intent = Intent(Settings.ACTION_NFC_SETTINGS)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            result.success(true)
        } catch (_: Exception) {
            result.success(false)
        }
    }

    private fun scanMrz(result: Result) {
        val activity = activityBinding?.activity
        if (activity == null) {
            result.error(AndroidErrorCodes.NO_ACTIVITY, "L'application doit être au premier plan.", null)
            return
        }
        if (pendingScan != null) {
            result.error("BUSY", "Un scan est déjà en cours.", null)
            return
        }
        pendingScan = result
        activity.startActivityForResult(MrzScannerActivity.intent(activity), REQUEST_SCAN_MRZ)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_SCAN_MRZ) return false
        val result = pendingScan ?: return true
        pendingScan = null
        val mrz = data?.getStringExtra(MrzScannerActivity.EXTRA_MRZ)
        val errorCode = data?.getStringExtra(MrzScannerActivity.EXTRA_ERROR_CODE)
        when {
            resultCode == Activity.RESULT_OK && mrz != null -> {
                try {
                    val key = AccessKey.fromMrz(mrz)
                    result.success(mapOf(
                        "mrz" to mrz,
                        "documentNumber" to key.documentNumber(),
                        "dateOfBirth" to key.dateOfBirth(),
                        "dateOfExpiry" to key.dateOfExpiry(),
                    ))
                } catch (e: EidException) {
                    result.error(e.code().name, e.message, null)
                }
            }
            errorCode != null -> result.error(errorCode, data.getStringExtra(MrzScannerActivity.EXTRA_ERROR_MESSAGE), null)
            else -> result.success(null) // abandon par l'utilisateur
        }
        return true
    }

    private fun readCard(call: MethodCall, result: Result) {
        val key = try {
            val mrz = call.argument<String>("mrz")
            if (mrz != null) {
                AccessKey.fromMrz(mrz)
            } else {
                AccessKey.of(call.argument("documentNumber"), call.argument("dateOfBirth"), call.argument("dateOfExpiry"))
            }
        } catch (e: EidException) {
            result.error(e.code().name, e.message, null)
            return
        }
        val options = ReadOptions(
            call.argument<Boolean>("readPhoto") ?: true,
            call.argument<Boolean>("includeRaw") ?: false,
            true,
            CscaStore.empty(),
            call.argument<Boolean>("readSignature") ?: true,
            call.argument<Boolean>("activeAuthentication") ?: true,
        )
        val timeoutMs = (call.argument<Int>("timeoutSeconds")?.toLong()?.times(1000)) ?: NfcCardReader.DEFAULT_TIMEOUT_MS

        reader.start(key, options, timeoutMs, object : NfcCardReader.Listener {
            override fun onWaitingForCard(message: String) {
                eventSink?.success(mapOf("type" to "waiting", "message" to message))
            }

            override fun onProgress(step: String, percent: Int, message: String) {
                eventSink?.success(mapOf("type" to "progress", "step" to step, "percent" to percent, "message" to message))
            }

            override fun onSuccess(record: IdentityRecord) {
                result.success(IdentityRecordMaps.toMap(record))
            }

            override fun onError(code: String, message: String) {
                result.error(code, message, null)
            }
        })
    }

    private companion object {
        const val REQUEST_SCAN_MRZ = 0x6D72 // « mr »
    }
}
