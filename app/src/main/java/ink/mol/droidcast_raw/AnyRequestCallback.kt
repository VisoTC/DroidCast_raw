package ink.mol.droidcast_raw

import android.graphics.Bitmap
import android.graphics.Point
import android.os.Build
import android.util.Log
import androidx.core.text.isDigitsOnly
import com.koushikdutta.async.http.Multimap
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import com.koushikdutta.async.http.server.HttpServerRequestCallback
import java.nio.ByteBuffer

class AnyRequestCallback : HttpServerRequestCallback {
    private var displayUtil: DisplayUtil? = DisplayUtil()

    private data class ScreenshotResult(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val format: String,
        val bytesPerPixel: Int
    )

    override fun onRequest(
        request: AsyncHttpServerRequest?,
        response: AsyncHttpServerResponse?
    ) {
        try {
            val pairs: Multimap? = request?.query
            val width: String? = pairs?.getString("width")
            val height: String? = pairs?.getString("height")
            val format: String = pairs?.getString("format")?.lowercase() ?: "rgb565"

            if (format != "rgb565" && format != "rgb8888") {
                response?.code(400)
                response?.send("Unsupported screenshot format: $format. Supported formats: rgb565, rgb8888")
                return
            }

            if (!width.isNullOrEmpty() && !height.isNullOrEmpty() && width.isDigitsOnly() && height.isDigitsOnly()) {
                Main.setWH(width.toInt(), height.toInt())
            } else {
                val point: Point? = displayUtil?.getCurrentDisplaySize()
                if (point != null && point.x > 0 && point.y > 0) {
                    Main.setWH(point.x, point.y)
                } else {
                    Main.setWH(720, 1080)
                }
            }

            val destWidth: Int = Main.getWidth()
            val destHeight: Int = Main.getHeight()

            val screenshot: ScreenshotResult = getScreenImageInBytes(destWidth, destHeight, format)

            response?.headers?.add("X-Screenshot-Width", screenshot.width.toString())
            response?.headers?.add("X-Screenshot-Height", screenshot.height.toString())
            response?.headers?.add("X-Screenshot-Format", screenshot.format)
            response?.headers?.add("X-Screenshot-Bytes-Per-Pixel", screenshot.bytesPerPixel.toString())
            response?.send("application/octet-stream", screenshot.bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            response?.code(500)
            val manufacturer = Build.MANUFACTURER
            val device = Build.DEVICE
            val osVersion = Build.VERSION.RELEASE
            val error =
                ":(  Failed to generate the screenshot on device / emulator : $manufacturer - $device - Android OS : $osVersion"
            response?.send(error)
        }
    }

    private fun getScreenImageInBytes(
        width: Int,
        height: Int,
        format: String
    ): ScreenshotResult {
        val destWidth = width
        val destHeight = height

        val bitmap: Bitmap? = ScreenCaptorUtils.screenshot(destWidth, destHeight)
        Log.i("DroidCast_raw_log", "Bitmap generated with resolution $destWidth:$destHeight")

        val bitmapConfig = when (format) {
            "rgb8888" -> Bitmap.Config.ARGB_8888
            else -> Bitmap.Config.RGB_565
        }
        val responseFormat = when (format) {
            "rgb8888" -> "ARGB_8888"
            else -> "RGB_565"
        }
        val bytesPerPixel = when (format) {
            "rgb8888" -> 4
            else -> 2
        }
        val buffer = ByteBuffer.allocate(destWidth * destHeight * bytesPerPixel)
        val convertedBitmap = bitmap!!.copy(bitmapConfig, false)!!
        convertedBitmap.copyPixelsToBuffer(buffer)
        convertedBitmap.recycle()
        bitmap.recycle()

        return ScreenshotResult(buffer.array(), destWidth, destHeight, responseFormat, bytesPerPixel)
    }
}
