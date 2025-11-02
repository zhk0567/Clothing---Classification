package com.deepfashion.classifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy

class YuvToRgbConverter(private val context: Context) {
    fun yuvToRgb(image: ImageProxy, output: Bitmap) {
        require(image.format == ImageFormat.YUV_420_888)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val width = image.width
        val height = image.height

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val pixels = IntArray(width * height)

        var yp = 0
        for (j in 0 until height) {
            val pY = yRowStride * j
            val uvRow = uvRowStride * (j shr 1)
            for (i in 0 until width) {
                val y = (yBuffer.get(pY + i).toInt() and 0xFF)
                val uvCol = (i shr 1) * uvPixelStride
                val u = (uBuffer.get(uvRow + uvCol).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvRow + uvCol).toInt() and 0xFF) - 128

                val y1192 = 1192 * (y - 16).coerceAtLeast(0)
                var r = (y1192 + 1634 * v)
                var g = (y1192 - 833 * v - 400 * u)
                var b = (y1192 + 2066 * u)

                r = r.coerceIn(0, 262143)
                g = g.coerceIn(0, 262143)
                b = b.coerceIn(0, 262143)

                pixels[yp++] = (0xFF shl 24) or
                    ((r shl 6) and 0x00FF0000) or
                    ((g shr 2) and 0x0000FF00) or
                    ((b shr 10) and 0x000000FF)
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}


