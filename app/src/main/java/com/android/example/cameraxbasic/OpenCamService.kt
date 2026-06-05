package com.android.example.cameraxbasic

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.common.util.concurrent.ListenableFuture
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OpenCamService : LifecycleService() {

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var webServer: CameraServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "OpenCamServiceChannel"
        const val NOTIFICATION_ID = 1

        @Volatile
        var latestFrame: ByteArray? = null

        const val ACTION_STOP_SERVICE = "com.android.example.cameraxbasic.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Acquire WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenCam::StreamingWakeLock")
        wakeLock?.acquire()

        createNotificationChannel()

        // Start Web Server
        webServer = CameraServer()
        try {
            webServer?.start()
            Log.d("OpenCamService", "Web server started on port 8080")
        } catch (e: Exception) {
            Log.e("OpenCamService", "Failed to start web server", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())

        setUpCamera()

        return START_STICKY
    }

    private fun getIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    private fun createNotification(): Notification {
        val ip = getIpAddress()

        val stopIntent = Intent(this, OpenCamService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Open-Cam")
            .setContentText("O servidor open-cam está ativo e transmitindo via http://$ip:8080. Toque aqui para encerrar.")
            .setSmallIcon(R.mipmap.ic_launcher) // Placeholder, replace if needed
            .setContentIntent(pendingStopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Open-Cam Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setUpCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture?.addListener({
            cameraProvider = cameraProviderFuture?.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        val imageAnalyzer = ImageAnalysis.Builder()
            // .setTargetResolution(Size(1280, 720)) // optional
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, FrameAnalyzer())
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e("OpenCamService", "Use case binding failed", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        webServer?.stop()
        cameraExecutor.shutdown()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private class FrameAnalyzer : ImageAnalysis.Analyzer {
        private var nv21: ByteArray? = null
        private val out = ByteArrayOutputStream()

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            val androidImage = image.image ?: return

            val yBuffer = androidImage.planes[0].buffer
            val vuBuffer = androidImage.planes[2].buffer

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()
            val totalSize = ySize + vuSize

            if (nv21 == null || nv21!!.size < totalSize) {
                nv21 = ByteArray(totalSize)
            }

            yBuffer.get(nv21!!, 0, ySize)
            vuBuffer.get(nv21!!, ySize, vuSize)

            val yuvImage = YuvImage(nv21!!, ImageFormat.NV21, androidImage.width, androidImage.height, null)

            out.reset()
            yuvImage.compressToJpeg(Rect(0, 0, androidImage.width, androidImage.height), 80, out)
            latestFrame = out.toByteArray()

            image.close()
        }
    }
}

class CameraServer(port: Int = 8080) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        return if (session.uri == "/cam") {
            val frame = OpenCamService.latestFrame
            if (frame != null) {
                val inputStream = ByteArrayInputStream(frame)
                newFixedLengthResponse(Response.Status.OK, "image/jpeg", inputStream, frame.size.toLong())
            } else {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Aguardando...")
            }
        } else {
            val html = """
                <html>
                <body style="background-color: black; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0;">
                    <img id="cam" src="" style="max-width: 100%; max-height: 100%;">
                    <script>
                        const img = document.getElementById('cam');
                        function loadNextFrame() {
                            setTimeout(() => {
                                img.src = '/cam?' + new Date().getTime();
                            }, 30);
                        }
                        img.onload = loadNextFrame;
                        img.onerror = loadNextFrame;
                        loadNextFrame();
                    </script>
                </body>
                </html>
            """.trimIndent()
            newFixedLengthResponse(Response.Status.OK, "text/html", html)
        }
    }
}
