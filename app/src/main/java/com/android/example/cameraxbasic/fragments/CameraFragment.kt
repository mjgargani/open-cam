// POC - Camera Fragment V1 (Motor de Streaming FOSS)
// app/src/main/java/com/android/example/cameraxbasic/fragments/CameraFragment.kt

package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.window.WindowManager
import com.android.example.cameraxbasic.R
import com.android.example.cameraxbasic.databinding.CameraUiContainerBinding
import com.android.example.cameraxbasic.databinding.FragmentCameraBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.net.wifi.WifiManager

// Imports Web e Imagem Nativa
import android.content.Intent
import androidx.core.content.ContextCompat
import com.android.example.cameraxbasic.OpenCamService

class CameraFragment : Fragment() {

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!
    private var cameraUiContainerBinding: CameraUiContainerBinding? = null

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var windowManager: WindowManager

    private fun updateServerInfo(ip: String, port: Int) {
        cameraUiContainerBinding?.serverInfo?.text = "Servidor: http://$ip:$port"
    }

    private fun getIpAddress(): String {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format("%d.%d.%d.%d", 
            (ipAddress and 0xff), 
            (ipAddress shr 8 and 0xff), 
            (ipAddress shr 16 and 0xff), 
            (ipAddress shr 24 and 0xff))
    }
    
    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private lateinit var cameraExecutor: ExecutorService

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        windowManager = WindowManager(view.context)
        
        // Inicia o serviço em background
        val serviceIntent = Intent(requireContext(), OpenCamService::class.java)
        ContextCompat.startForegroundService(requireContext(), serviceIntent)
        
        // Mostra o ip na tela
        updateServerInfo(getIpAddress(), 8080)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bindCameraUseCases()
    }

    private suspend fun setUpCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()
    }

    private fun bindCameraUseCases() {
        val metrics = windowManager.getCurrentWindowMetrics().bounds
        val screenAspectRatio = aspectRatio(metrics.width(), metrics.height())
        val rotation = fragmentCameraBinding.viewFinder.display.rotation
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Câmera falhou.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        cameraProvider.unbindAll()

        try {
            // Liga apenas a tela (preview), a análise será feita no serviço
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview
            )
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            // Tratar falha silenciosa
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}