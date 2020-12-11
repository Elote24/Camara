package com.rockbass2560.mycamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.util.concurrent.Executors

const val COUNTER_IMAGE = "COUNTER_IMAGE"
const val REQUEST_CAMERA = 10
const val DEFAULT_SHARED_PREFERENCES = "DEFAULT_SHARED_PREFERENCES"

class MainActivity : AppCompatActivity() {
    private val executorCamera = Executors.newSingleThreadExecutor()
    private lateinit var session: CameraCaptureSession
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var cameraDevice: CameraDevice
    private var activeCamera = 0
    private lateinit var folderImage: File
    private var imageCapture: CaptureRequest? = null
    private lateinit var imageReader: ImageReader
    private lateinit var cameraManager: CameraManager
    private var reOpenToggle = true
    private lateinit var imageView: ImageView

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                firstConfigCamera()
            } else {
                Toast.makeText(
                    this,
                    "Es necesario aceptar el permiso de la camara para este ejercicio",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            firstConfigCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA
            )
        }

        createFolderImage()

        configButtons()

        loadLastImage()
    }

    private fun loadLastImage() {
        imageView = findViewById<ImageView>(R.id.lastimage_imageview)

        val files = folderImage.listFiles(FilenameFilter { dir, name ->
            name.contains("image_")
        })

        val lastFile = files?.maxByOrNull { file ->
            val onlyName = file.name.split(".")[0]

            onlyName.split("_")[1].toInt()
        }

        loadLastImage(lastFile)
    }

    private fun loadLastImage(lastFile: File?) {
        val bitmap = BitmapFactory.decodeFile(lastFile?.absolutePath)
        imageView.setImageBitmap(bitmap)
    }

    private fun createFolderImage() {
        folderImage = File(filesDir, "image")
        if (!folderImage.exists()) {
            folderImage.mkdir()
        }
    }

    private fun configButtons() {
        val flipButton = findViewById<ImageButton>(R.id.flip_button)
        flipButton.setOnClickListener {
            session.stopRepeating()
            imageReader.close()
            cameraDevice.close()
        }

        val buttonTakePhoto = findViewById<Button>(R.id.button_takephoto)
        buttonTakePhoto.setOnClickListener {
            imageCapture?.let {
                session.capture(it, null, null)
            }
        }

        val lastImageImageView = findViewById<ImageView>(R.id.lastimage_imageview)
        lastImageImageView.setOnClickListener {
            val intent = Intent(this, ImagePager::class.java)
            startActivity(intent)
        }
    }

    @SuppressLint("MissingPermission")
    fun firstConfigCamera() {
        val surfacePreview = findViewById<SurfaceView>(R.id.surface_preview)

        surfacePreview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {

                surfaceHolder = holder

                cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

                val idCamera = cameraManager.cameraIdList[activeCamera]

                createImageReader(idCamera)

                cameraManager.openCamera(idCamera, cameraDeviceCallBack, null)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {}

        })
    }

    fun createImageReader(idCamera: String) {
        val size = getSize(idCamera)

        val sharedPreferences = getSharedPreferences(DEFAULT_SHARED_PREFERENCES, 0)
        configImageReader(size, sharedPreferences)
    }

    private fun getSize(idCamera: String): Size {
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(idCamera)
        val scalerStream =
            cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputSizes = scalerStream?.getOutputSizes(ImageFormat.JPEG)

        return getMaxOutput(outputSizes)
    }

    private fun getMaxOutput(outputSizes: Array<Size>?): Size {
        return outputSizes?.maxByOrNull { s -> s.height + s.width }!!
    }

    private fun fixImage(byteArray: ByteArray, file: File) {
        val bitmapOriginal = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        val matrix = Matrix()
        matrix.preRotate(90F)
        val bitmapFinal = Bitmap.createBitmap(
            bitmapOriginal,
            0,
            0,
            bitmapOriginal.width,
            bitmapOriginal.height,
            matrix,
            true
        )
        FileOutputStream(file).use { stream ->
            bitmapFinal.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        }
    }

    private fun configImageReader(size: Size, sharedPreferences: SharedPreferences) {
        imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)

        imageReader.setOnImageAvailableListener({ imgReader ->
            val image = imgReader.acquireLatestImage()
            val buffer = image.planes.first().buffer
            val byteArray = ByteArray(buffer.remaining())
            buffer.get(byteArray)

            val counterImage = sharedPreferences.getInt(COUNTER_IMAGE, 1)

            val fileImage = File(folderImage, "image_$counterImage.jpeg")

            fixImage(byteArray, fileImage)

            loadLastImage(fileImage)

            sharedPreferences.edit().putInt(COUNTER_IMAGE, counterImage + 1).apply()
        }, null)
    }

    @SuppressLint("MissingPermission")
    private fun reOpenCam(toggleCam: Boolean = true) {
        if (toggleCam)
            activeCamera = if (activeCamera == 0) 1 else 0

        val idCamera = cameraManager.cameraIdList[activeCamera]

        createImageReader(idCamera)

        cameraManager.openCamera(idCamera, cameraDeviceCallBack, null)
    }

    val cameraDeviceCallBack = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera

            val builderCapture = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builderCapture.addTarget(surfaceHolder.surface)
            val captureRequest = builderCapture.build()

            val builderCaptureImage =
                camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builderCaptureImage.addTarget(imageReader.surface)
            imageCapture = builderCaptureImage.build()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                camera.createCaptureSession(
                    listOf(surfaceHolder.surface, imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            this@MainActivity.session = session
                            session.setRepeatingRequest(captureRequest, null, null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}

                    },
                    null
                )
            } else {
                val sessionConfiguration = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(
                        OutputConfiguration(surfaceHolder.surface),
                        OutputConfiguration(imageReader.surface)
                    ),
                    executorCamera,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            this@MainActivity.session = session
                            session.setRepeatingRequest(captureRequest, null, null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}

                    }
                )

                camera.createCaptureSession(sessionConfiguration)
            }
        }

        override fun onClosed(camera: CameraDevice) {
            if (reOpenToggle) {
                reOpenCam()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {}

        override fun onError(camera: CameraDevice, error: Int) {}

    }
}