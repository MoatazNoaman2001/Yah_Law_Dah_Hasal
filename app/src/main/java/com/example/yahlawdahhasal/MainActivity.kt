package com.example.yahlawdahhasal

import android.Manifest
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import com.example.yahlawdahhasal.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    lateinit var binding: ActivityMainBinding
    lateinit var permissionRequest: ActivityResultLauncher<Array<String>>
    private var imageCapture: ImageCapture? = null
    private var videoCapture: androidx.camera.video.VideoCapture<Recorder>? = null
    private var Recording: Recording? = null
    private var is_Recording = false

    val permissions: Array<String> = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )


    lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT


        permissionRequest =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (it.map { it.value }.any { b -> !b }) {
                    Toast.makeText(this, "must all permission granted", Toast.LENGTH_SHORT).show()
                    permissionRequest.launch(permissions)
                } else {
                    startCamera()
                }
            }
        if (AllPremissionGranted()) {
            startCamera()
        }
        binding.ImageBtn.setOnClickListener {
            TakePicAction()
        }
        binding.VideoBtn.setOnClickListener {
            if (!is_Recording) {
                RecordeVideo2()
            } else {
                Recording?.close()
            }
            is_Recording = !is_Recording
        }

    }


    private fun startCamera() {
        binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            imageCapture = ImageCapture.Builder().build()
//            videoCapture = VideoCapture.Builder()
//                .setVideoFrameRate(40)
//                .build()
            val cameraInfo = cameraProvider.availableCameraInfos.filter {
                Camera2CameraInfo.from(it)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
            }
            for (cameraInfo in cameraInfo) {
                Log.d(TAG, "startCamera: $cameraInfo")
            }
            val supportedQuality = QualitySelector.getSupportedQualities(cameraInfo[0])
            val filteredQuality = arrayListOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                .filter { quality -> supportedQuality.contains(quality) }
            val Recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD))
                .build()

            videoCapture = androidx.camera.video.VideoCapture.withOutput(Recorder)


            val camExecutor = ContextCompat.getMainExecutor(this)


            val imageAnalysis = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(camExecutor) { image ->

                    }
                }
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture ,  videoCapture
                )

            } catch (exc: Exception) {
                Log.d(TAG, "onCreate: " + exc.message)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun TakePicAction() {
        val imageCapture = MainActivity@ this.imageCapture ?: return
        val faceDetector:FaceD

        val file_name = SimpleDateFormat("EEE dd/MM/YY hh:mm a", Locale.ROOT)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file_name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outPutOption = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

        imageCapture.takePicture(
            outPutOption,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    animateFlash()
                    Toast.makeText(baseContext, "Image Saved", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "error happened", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "onError: " + exception.message)
                }

            })
    }

    private fun RecordeVideo2() {
        var videoCapture: androidx.camera.video.VideoCapture<Recorder> =
            this@MainActivity.videoCapture ?: return

        val curRecorder = Recording
        if (curRecorder != null) {
            curRecorder.stop()
            Recording?.stop()
            return
        }
        val name = SimpleDateFormat("EEE dd/MM/YY hh:mm a", Locale.US)
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

        Recording = videoCapture.output.prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                    Manifest.permission.RECORD_AUDIO) ==
                        PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) {
                when (it) {
                    is VideoRecordEvent.Start -> {
                        binding.VideoBtn.setImageDrawable(
                            ResourcesCompat.getDrawable(
                                resources,
                                R.drawable.ic_stop_24,
                                theme
                            )
                        )
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (it.hasError()) {
                            Toast.makeText(baseContext, " error in Record", Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, "RecordeVideo2: " + it.cause?.message)
                        }else{
                            Recording?.close()
                            Recording = null
                            Log.e(TAG, "Video capture ends with No Error")
                        }
                        binding.VideoBtn.setImageDrawable(
                            ResourcesCompat.getDrawable(
                                resources,
                                R.drawable.ic_record_red_24,
                                theme
                            )
                        )
                    }
                }
            }
    }

    private fun RecordVideo1() {
//        val videoCapture: VideoCapture = this.videoCapture ?: return

        binding.VideoBtn.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_stop_24,
                theme
            )
        )

        val curRecord = Recording
        if (curRecord != null) {
            curRecord.stop();
            Recording = null
            return
        }

        val name = SimpleDateFormat("EEE dd/MM/YY hh:mm a", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

//        val outputOptions = VideoCapture.OutputFileOptions
//            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
//            .build()
//        val recorder = videoCapture.startRecording(
//            outputOptions,
//            ContextCompat.getMainExecutor(this),
//            object : VideoCapture.OnVideoSavedCallback {
//                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
//                    Toast.makeText(baseContext, "Video Saved", Toast.LENGTH_SHORT).show()
//
//                    binding.VideoBtn.setImageDrawable(
//                        ResourcesCompat.getDrawable(
//                            resources,
//                            R.drawable.ic_record_red_24,
//                            theme
//                        )
//                    )
//                }
//
//                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
//                    Toast.makeText(baseContext, "Video Failed to Record", Toast.LENGTH_SHORT).show()
//                    Log.d(TAG, "onError: " + cause?.message)
//                }
//
//            }
//        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun animateFlash() {
        binding.root.postDelayed({
            binding.root.foreground = ColorDrawable(Color.WHITE)
            binding.root.postDelayed({
                binding.root.foreground = null
            }, 50)
        }, 100)
    }

    fun AllPremissionGranted(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        } else {
            permissionRequest.launch(permissions)
        }
        return false
    }

    fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        var camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview)
    }
}


