package com.example.spottercam

import android.Manifest
import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Range
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private enum class CaptureMode { PHOTO, VIDEO }

    private lateinit var previewView: PreviewView
    private lateinit var btnMode: TextView
    private lateinit var btnFlash: TextView
    private lateinit var btnAspect: TextView
    private lateinit var btnFormat: TextView
    private lateinit var btnSwitchCam: TextView
    private lateinit var btnQuality: TextView
    private lateinit var btnFps: TextView
    private lateinit var btnStabilization: TextView
    private lateinit var btnCapture: ImageButton
    private lateinit var seekZoom: SeekBar
    private lateinit var seekExposure: SeekBar
    private lateinit var tvZoomValue: TextView
    private lateinit var tvExposureValue: TextView
    private lateinit var tvRecTimer: TextView
    private lateinit var focusRing: android.view.View
    private lateinit var recIndicator: android.view.View
    private lateinit var photoControlsRow: android.view.View
    private lateinit var videoControlsRow: android.view.View

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var preview: Preview? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var mode = CaptureMode.PHOTO

    private lateinit var cameraExecutor: ExecutorService

    // Aspect ratio cycle (photo mode): 4:3 -> 16:9 -> 1:1 -> repeat
    private val aspectRatios = listOf("4:3", "16:9", "1:1")
    private var aspectIndex = 0

    // Photo format cycle
    private val photoFormats = listOf("JPEG", "PNG")
    private var formatIndex = 0

    // Flash cycle: OFF -> ON -> AUTO -> repeat
    private val flashModes = listOf(
        ImageCapture.FLASH_MODE_OFF,
        ImageCapture.FLASH_MODE_ON,
        ImageCapture.FLASH_MODE_AUTO
    )
    private val flashLabels = listOf("Flash: Off", "Flash: On", "Flash: Auto")
    private var flashIndex = 0

    // Video quality cycle
    private val qualities = listOf(Quality.SD, Quality.HD, Quality.FHD, Quality.UHD)
    private val qualityLabels = listOf("480p", "720p", "1080p", "4K")
    private var qualityIndex = 2 // default 1080p

    // FPS cycle
    private val fpsOptions = listOf(24, 30, 60)
    private var fpsIndex = 1 // default 30

    // Stabilization
    private var stabilizationEnabled = true

    // Recording timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private var recordingStartMs = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed = (System.currentTimeMillis() - recordingStartMs) / 1000
            val mm = elapsed / 60
            val ss = elapsed % 60
            tvRecTimer.text = String.format(Locale.US, "%02d:%02d", mm, ss)
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val requiredPermissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    private val requestPermissionsLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.previewView)
        btnMode = findViewById(R.id.btnMode)
        btnFlash = findViewById(R.id.btnFlash)
        btnAspect = findViewById(R.id.btnAspect)
        btnFormat = findViewById(R.id.btnFormat)
        btnSwitchCam = findViewById(R.id.btnSwitchCam)
        btnQuality = findViewById(R.id.btnQuality)
        btnFps = findViewById(R.id.btnFps)
        btnStabilization = findViewById(R.id.btnStabilization)
        btnCapture = findViewById(R.id.btnCapture)
        seekZoom = findViewById(R.id.seekZoom)
        seekExposure = findViewById(R.id.seekExposure)
        tvZoomValue = findViewById(R.id.tvZoomValue)
        tvExposureValue = findViewById(R.id.tvExposureValue)
        tvRecTimer = findViewById(R.id.tvRecTimer)
        focusRing = findViewById(R.id.focusRing)
        recIndicator = findViewById(R.id.recIndicator)
        photoControlsRow = findViewById(R.id.photoControlsRow)
        videoControlsRow = findViewById(R.id.videoControlsRow)

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        }

        btnMode.setOnClickListener { toggleMode() }
        btnFlash.setOnClickListener { cycleFlash() }
        btnAspect.setOnClickListener { cycleAspectRatio() }
        btnFormat.setOnClickListener { cycleFormat() }
        btnSwitchCam.setOnClickListener { switchCamera() }
        btnQuality.setOnClickListener { cycleQuality() }
        btnFps.setOnClickListener { cycleFps() }
        btnStabilization.setOnClickListener { toggleStabilization() }
        btnCapture.setOnClickListener { onCaptureClicked() }

        setupZoomSlider()
        setupTapToFocus()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---------- Camera setup ----------

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        val targetAspectRatio = when (aspectRatios[aspectIndex]) {
            "16:9" -> AspectRatio.RATIO_16_9
            else -> AspectRatio.RATIO_4_3 // base for both 4:3 and 1:1 (1:1 is cropped post-capture)
        }

        val previewBuilder = Preview.Builder().setTargetAspectRatio(targetAspectRatio)

        if (mode == CaptureMode.VIDEO) {
            // Apply frame-rate and stabilization hints via Camera2 interop on the repeating request.
            val fps = fpsOptions[fpsIndex]
            val ext = Camera2Interop.Extender(previewBuilder)
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(fps, fps)
            )
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                if (stabilizationEnabled) CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                else CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            )
        }

        preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            provider.unbindAll()

            if (mode == CaptureMode.PHOTO) {
                imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatio(targetAspectRatio)
                    .setFlashMode(flashModes[flashIndex])
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                videoCapture = null
                camera = provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } else {
                val quality = qualities[qualityIndex]
                val qualitySelector = QualitySelector.from(
                    quality,
                    FallbackStrategy.higherQualityOrLowerThan(quality)
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                imageCapture = null
                camera = provider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            }

            observeZoomAndExposure()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun switchCamera() {
        if (activeRecording != null) {
            Toast.makeText(this, "Stop recording first", Toast.LENGTH_SHORT).show()
            return
        }
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindUseCases()
    }

    // ---------- Mode ----------

    private fun toggleMode() {
        if (activeRecording != null) {
            Toast.makeText(this, "Stop recording first", Toast.LENGTH_SHORT).show()
            return
        }
        mode = if (mode == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
        btnMode.text = if (mode == CaptureMode.PHOTO) "Photo" else "Video"
        photoControlsRow.visibility = if (mode == CaptureMode.PHOTO) android.view.View.VISIBLE else android.view.View.GONE
        videoControlsRow.visibility = if (mode == CaptureMode.VIDEO) android.view.View.VISIBLE else android.view.View.GONE
        bindUseCases()
    }

    // ---------- Flash ----------

    private fun cycleFlash() {
        flashIndex = (flashIndex + 1) % flashModes.size
        btnFlash.text = flashLabels[flashIndex]
        imageCapture?.flashMode = flashModes[flashIndex]
    }

    // ---------- Aspect ratio & format (photo) ----------

    private fun cycleAspectRatio() {
        aspectIndex = (aspectIndex + 1) % aspectRatios.size
        btnAspect.text = aspectRatios[aspectIndex]
        bindUseCases()
    }

    private fun cycleFormat() {
        formatIndex = (formatIndex + 1) % photoFormats.size
        btnFormat.text = photoFormats[formatIndex]
    }

    // ---------- Video quality / fps / stabilization ----------

    private fun cycleQuality() {
        qualityIndex = (qualityIndex + 1) % qualities.size
        btnQuality.text = qualityLabels[qualityIndex]
        bindUseCases()
    }

    private fun cycleFps() {
        fpsIndex = (fpsIndex + 1) % fpsOptions.size
        btnFps.text = "${fpsOptions[fpsIndex]} fps"
        bindUseCases()
    }

    private fun toggleStabilization() {
        stabilizationEnabled = !stabilizationEnabled
        btnStabilization.text = if (stabilizationEnabled) "Stabilize: On" else "Stabilize: Off"
        bindUseCases()
    }

    // ---------- Zoom ----------

    private fun setupZoomSlider() {
        seekZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    camera?.cameraControl?.setLinearZoom(progress / 100f)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun observeZoomAndExposure() {
        val cam = camera ?: return

        cam.cameraInfo.zoomState.observe(this) { state ->
            val linear = state.linearZoom
            seekZoom.progress = (linear * 100).roundToInt()
            tvZoomValue.text = String.format(Locale.US, "%.1fx", state.zoomRatio)
        }

        val exposureState = cam.cameraInfo.exposureState
        val range = exposureState.exposureCompensationRange
        if (exposureState.isExposureCompensationSupported && !range.isEmpty()) {
            seekExposure.max = range.upper - range.lower
            seekExposure.progress = exposureState.exposureCompensationIndex - range.lower
            updateExposureLabel(exposureState.exposureCompensationIndex, exposureState.exposureCompensationStep.toFloat())

            seekExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val index = progress + range.lower
                        try {
                            camera?.cameraControl?.setExposureCompensationIndex(index)
                            updateExposureLabel(index, exposureState.exposureCompensationStep.toFloat())
                        } catch (e: Exception) {
                            // ignore transient errors while camera reconfigures
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } else {
            seekExposure.isEnabled = false
            tvExposureValue.text = "N/A"
        }
    }

    private fun updateExposureLabel(index: Int, step: Float) {
        val ev = index * step
        tvExposureValue.text = String.format(Locale.US, "%.1f", ev)
    }

    // ---------- Tap to focus ----------

    private fun setupTapToFocus() {
        previewView.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                camera?.cameraControl?.startFocusAndMetering(action)
                showFocusRing(event.x, event.y)
                view.performClick()
            }
            true
        }
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.translationX = x - focusRing.width / 2f
        focusRing.translationY = y - focusRing.height / 2f
        focusRing.alpha = 1f
        focusRing.visibility = android.view.View.VISIBLE
        focusRing.animate().cancel()
        focusRing.animate()
            .alpha(0f)
            .setStartDelay(500)
            .setDuration(400)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    focusRing.visibility = android.view.View.INVISIBLE
                }
            })
            .start()
    }

    // ---------- Capture (photo or video) ----------

    private fun onCaptureClicked() {
        if (mode == CaptureMode.PHOTO) {
            takePhoto()
        } else {
            if (activeRecording == null) startRecording() else stopRecording()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val rotation = image.imageInfo.rotationDegrees
                image.close()

                if (rotation != 0 && bitmap != null) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

                if (bitmap != null && aspectRatios[aspectIndex] == "1:1") {
                    val size = minOf(bitmap.width, bitmap.height)
                    val x = (bitmap.width - size) / 2
                    val y = (bitmap.height - size) / 2
                    bitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
                }

                if (bitmap != null) {
                    saveBitmap(bitmap)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Capture failed to decode", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val useJpeg = photoFormats[formatIndex] == "JPEG"
        val extension = if (useJpeg) "jpg" else "png"
        val mimeType = if (useJpeg) "image/jpeg" else "image/png"
        val compressFormat = if (useJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG

        val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SpotterCam")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            runOnUiThread { Toast.makeText(this, "Failed to save image", Toast.LENGTH_LONG).show() }
            return
        }

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(compressFormat, 95, out)
            }
            bitmap.recycle()
            runOnUiThread {
                Toast.makeText(this, "Saved .$extension to Pictures/SpotterCam", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // ---------- Video recording ----------

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val capture = videoCapture ?: return

        val name = "VID_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SpotterCam")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        var pending = capture.output.prepareRecording(this, outputOptions)
        if (hasAudioPermission()) {
            pending = pending.withAudioEnabled()
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordingStartMs = System.currentTimeMillis()
                    recIndicator.visibility = android.view.View.VISIBLE
                    btnCapture.background = ContextCompat.getDrawable(this, R.drawable.bg_capture_button_recording)
                    timerHandler.post(timerRunnable)
                }
                is VideoRecordEvent.Finalize -> {
                    recIndicator.visibility = android.view.View.GONE
                    btnCapture.background = ContextCompat.getDrawable(this, R.drawable.bg_capture_button)
                    timerHandler.removeCallbacks(timerRunnable)
                    tvRecTimer.text = "00:00"
                    if (event.hasError()) {
                        Toast.makeText(this, "Recording error: ${event.error}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Saved to Movies/SpotterCam", Toast.LENGTH_SHORT).show()
                    }
                    activeRecording = null
                }
                else -> {}
            }
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        timerHandler.removeCallbacks(timerRunnable)
    }
}
