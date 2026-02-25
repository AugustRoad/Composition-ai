package com.example.composition

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.composition.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.camera.core.Camera
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias LumaListener = (luma: Double) -> Unit

private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {

        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        listener(luma)

        image.close()
    }
}
class MainActivity : AppCompatActivity() {
    enum class Mode { PORTRAIT, PHOTO, VIDEO, PRO }
    private var currentMode = Mode.PHOTO

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private lateinit var cameraExecutor: ExecutorService

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    private var originalOverlayBitmap: Bitmap? = null
    private var edgeOverlayBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            loadAndProcessImage(uri)
        } else {
            viewBinding.overlayImage.visibility = android.view.View.GONE
            viewBinding.overlayOpacitySlider.visibility = android.view.View.GONE
            viewBinding.switchEdgeDetection.visibility = android.view.View.GONE
        }
    }

    private fun loadAndProcessImage(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val rotatedBitmap = rotateImageIfRequired(bitmap, uri)
                    originalOverlayBitmap = rotatedBitmap
                    edgeOverlayBitmap = applyEdgeDetection(rotatedBitmap)

                    withContext(Dispatchers.Main) {
                        updateOverlayImage()
                        viewBinding.overlayImage.visibility = android.view.View.VISIBLE
                        viewBinding.overlayOpacitySlider.visibility = android.view.View.VISIBLE
                        viewBinding.switchEdgeDetection.visibility = android.view.View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image", e)
            }
        }
    }

    private fun rotateImageIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        inputStream.close()

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun updateOverlayImage() {
        if (viewBinding.switchEdgeDetection.isChecked) {
            viewBinding.overlayImage.setImageBitmap(edgeOverlayBitmap)
        } else {
            viewBinding.overlayImage.setImageBitmap(originalOverlayBitmap)
        }
    }

    private fun applyEdgeDetection(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val edgeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val edgePixels = IntArray(width * height)

        // Simple Sobel operator
        val gx = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )
        val gy = arrayOf(
            intArrayOf(-1, -2, -1),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 1)
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumX = 0
                var sumY = 0

                for (i in -1..1) {
                    for (j in -1..1) {
                        val pixel = pixels[(y + i) * width + (x + j)]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()

                        sumX += gray * gx[i + 1][j + 1]
                        sumY += gray * gy[i + 1][j + 1]
                    }
                }

                val magnitude = Math.sqrt((sumX * sumX + sumY * sumY).toDouble()).toInt()
                val edgeColor = if (magnitude > 128) Color.WHITE else Color.TRANSPARENT
                edgePixels[y * width + x] = edgeColor
            }
        }

        edgeBitmap.setPixels(edgePixels, 0, width, 0, 0, width, height)
        return edgeBitmap
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.btnShutter.setOnClickListener {
            when (currentMode) {
                Mode.VIDEO -> captureVideo()
                else -> takePhoto()
            }
        }

        viewBinding.modePortrait.setOnClickListener { selectMode(Mode.PORTRAIT) }
        viewBinding.modePhoto.setOnClickListener { selectMode(Mode.PHOTO) }
        viewBinding.modeVideo.setOnClickListener { selectMode(Mode.VIDEO) }
        viewBinding.modePro.setOnClickListener { selectMode(Mode.PRO) }
        
        viewBinding.btnSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        viewBinding.btnFlash.setOnClickListener {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            imageCapture?.flashMode = flashMode
            Toast.makeText(this, "Flash Mode: $flashMode", Toast.LENGTH_SHORT).show()
        }

        viewBinding.zoom05.setOnClickListener { camera?.cameraControl?.setLinearZoom(0f) }
        viewBinding.zoom1.setOnClickListener { camera?.cameraControl?.setLinearZoom(0.33f) }
        viewBinding.zoom2.setOnClickListener { camera?.cameraControl?.setLinearZoom(0.66f) }
        viewBinding.zoom5.setOnClickListener { camera?.cameraControl?.setLinearZoom(1f) }

        viewBinding.btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
            }
            startActivity(intent)
        }

        viewBinding.btnAiMode.setOnClickListener {
            Toast.makeText(this, "AI Mode toggled", Toast.LENGTH_SHORT).show()
        }

        viewBinding.btnOverlayToggle.setOnClickListener {
            if (viewBinding.overlayImage.visibility == android.view.View.VISIBLE) {
                viewBinding.overlayImage.visibility = android.view.View.GONE
                viewBinding.overlayOpacitySlider.visibility = android.view.View.GONE
                viewBinding.switchEdgeDetection.visibility = android.view.View.GONE
            } else {
                pickImageLauncher.launch("image/*")
            }
        }

        viewBinding.overlayOpacitySlider.addOnChangeListener { _, value, _ ->
            viewBinding.overlayImage.alpha = value
        }

        viewBinding.switchEdgeDetection.setOnCheckedChangeListener { _, _ ->
            updateOverlayImage()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.btnShutter.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.btnShutterInner.apply {
                            setCardBackgroundColor(Color.RED)
                            radius = 8 * resources.displayMetrics.density
                        }
                        viewBinding.btnShutter.isEnabled = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.btnShutterInner.apply {
                            setCardBackgroundColor(Color.WHITE)
                            radius = 32 * resources.displayMetrics.density
                        }
                        viewBinding.btnShutter.isEnabled = true
                    }
                }
            }
    }

    private fun selectMode(mode: Mode) {
        currentMode = mode
        
        val unselectedColor = Color.parseColor("#B3FFFFFF")
        val selectedColor = Color.BLACK
        
        viewBinding.modePortrait.apply {
            background = null
            setTextColor(unselectedColor)
            typeface = Typeface.DEFAULT
        }
        viewBinding.modePhoto.apply {
            background = null
            setTextColor(unselectedColor)
            typeface = Typeface.DEFAULT
        }
        viewBinding.modeVideo.apply {
            background = null
            setTextColor(unselectedColor)
            typeface = Typeface.DEFAULT
        }
        viewBinding.modePro.apply {
            background = null
            setTextColor(unselectedColor)
            typeface = Typeface.DEFAULT
        }

        val selectedView = when (mode) {
            Mode.PORTRAIT -> viewBinding.modePortrait
            Mode.PHOTO -> viewBinding.modePhoto
            Mode.VIDEO -> viewBinding.modeVideo
            Mode.PRO -> viewBinding.modePro
        }
        
        selectedView.apply {
            setBackgroundResource(R.drawable.bg_mode_selected)
            setTextColor(selectedColor)
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.cameraPreview.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(flashMode)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

//            val imageAnalyzer = ImageAnalysis.Builder()
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                        Log.d(TAG, "Average luminosity: $luma")
//                    })
//                }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

}