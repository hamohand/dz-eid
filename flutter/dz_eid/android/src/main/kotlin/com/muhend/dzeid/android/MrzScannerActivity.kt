package com.muhend.dzeid.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.muhend.dzeid.core.parse.MrzOcr
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lecture de la MRZ au dos de la carte avec la caméra (CameraX + ML Kit, modèle embarqué hors ligne).
 *
 * Les images sont analysées en mémoire puis libérées : rien n'est enregistré ni envoyé.
 * Résultat : [EXTRA_MRZ] (lignes séparées par `\n`, chiffres de contrôle vérifiés), ou
 * [EXTRA_ERROR_CODE] / [EXTRA_ERROR_MESSAGE] en cas d'échec ; RESULT_CANCELED si l'utilisateur abandonne.
 */
class MrzScannerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MRZ = "com.muhend.dzeid.MRZ"
        const val EXTRA_ERROR_CODE = "com.muhend.dzeid.ERROR_CODE"
        const val EXTRA_ERROR_MESSAGE = "com.muhend.dzeid.ERROR_MESSAGE"
        private const val TAG = "DzEid"

        fun intent(context: Context): Intent = Intent(context, MrzScannerActivity::class.java)
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: MrzOverlayView
    private lateinit var hint: TextView
    private lateinit var torchButton: Button

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val stabilizer = MrzStabilizer()
    private var camera: Camera? = null
    private var torchOn = false

    @Volatile
    private var done = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startCamera()
        } else {
            fail(AndroidErrorCodes.CAMERA_PERMISSION_DENIED,
                "L'accès à la caméra a été refusé. Saisissez les informations de la carte manuellement.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUi())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        recognizer.close()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        root.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        overlay = MrzOverlayView(this)
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        hint = TextView(this).apply {
            text = "Cadrez le dos de la carte.\nLes 3 lignes du bas (avec des <<<) doivent être nettes."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(16))
        }
        root.addView(hint, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(40))
        }
        val cancel = Button(this).apply {
            text = "Annuler"
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        torchButton = Button(this).apply {
            text = "Lampe"
            setOnClickListener { toggleTorch() }
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttons.addView(torchButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        root.addView(buttons, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))

        // Toucher l'écran : mise au point à cet endroit
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
            }
            true
        }
        return root
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                // 1080p : les caractères de la MRZ d'une carte ID-1 sont petits ; ML Kit reste rapide à cette taille
                val selector = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor, ::analyze)
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                torchButton.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
            } catch (e: Exception) {
                Log.w(TAG, "Caméra indisponible : ${e.javaClass.simpleName}")
                fail(AndroidErrorCodes.CAMERA_UNAVAILABLE,
                    "Impossible d'ouvrir la caméra. Saisissez les informations de la carte manuellement.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (done || media == null) {
            proxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result -> onText(result.text) }
            .addOnCompleteListener { proxy.close() }
    }

    /** Thread principal. Le texte OCR n'est jamais journalisé (données personnelles). */
    private fun onText(text: String) {
        if (done) return
        val mrz = MrzOcr.extract(text)
        overlay.detected = mrz != null
        val confirmed = stabilizer.offer(mrz) ?: return
        done = true
        overlay.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_MRZ, confirmed))
        finish()
    }

    private fun toggleTorch() {
        val c = camera ?: return
        torchOn = !torchOn
        c.cameraControl.enableTorch(torchOn)
        torchButton.text = if (torchOn) "Éteindre" else "Lampe"
    }

    private fun fail(code: String, message: String) {
        done = true
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR_CODE, code).putExtra(EXTRA_ERROR_MESSAGE, message))
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
