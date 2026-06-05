package com.android.example.cameraxbasic

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, packageName="com.android.example.cameraxbasic")
class PerformanceTest {

    @Test
    fun benchmarkBaseline() {
        val width = 640
        val height = 480
        val ySize = width * height
        val vuSize = ySize / 2
        val yBufferData = ByteArray(ySize)
        val vuBufferData = ByteArray(vuSize)

        // Mock a simple image analyzer process
        val iterations = 1000
        val start = System.currentTimeMillis()

        for (i in 0 until iterations) {
            val nv21 = ByteArray(ySize + vuSize)

            System.arraycopy(yBufferData, 0, nv21, 0, ySize)
            System.arraycopy(vuBufferData, 0, nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()

            yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
            val latestFrame = out.toByteArray()
        }

        val end = System.currentTimeMillis()
        println("Baseline time for $iterations iterations: ${end - start} ms")
    }

    @Test
    fun benchmarkOptimized() {
        val width = 640
        val height = 480
        val ySize = width * height
        val vuSize = ySize / 2
        val yBufferData = ByteArray(ySize)
        val vuBufferData = ByteArray(vuSize)

        // Mock a simple image analyzer process
        val iterations = 1000
        val start = System.currentTimeMillis()

        // PRE-ALLOCATED
        var nv21 = ByteArray(ySize + vuSize)
        val out = ByteArrayOutputStream(ySize + vuSize) // pre-allocate capacity

        for (i in 0 until iterations) {
            if (nv21.size < ySize + vuSize) {
                nv21 = ByteArray(ySize + vuSize)
            }

            System.arraycopy(yBufferData, 0, nv21, 0, ySize)
            System.arraycopy(vuBufferData, 0, nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)

            out.reset() // reuse stream
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
            val latestFrame = out.toByteArray()
        }

        val end = System.currentTimeMillis()
        println("Optimized time for $iterations iterations: ${end - start} ms")
    }
}
