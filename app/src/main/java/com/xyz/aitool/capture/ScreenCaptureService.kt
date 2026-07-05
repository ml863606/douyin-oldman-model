package com.xyz.aitool.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xyz.aitool.R
import com.xyz.aitool.ocr.OcrProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var densityDpi = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            tearDownProjection(stopProjection = false)
            active = null
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        active = this
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent?.action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            if (resultCode != 0 && data != null) {
                startProjection(resultCode, data)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tearDownProjection()
        active = null
        super.onDestroy()
    }

    suspend fun captureVisibleText(): String? = withContext(Dispatchers.Default) {
        val reader = imageReader ?: return@withContext null
        val image = waitForLatestImage(reader) ?: return@withContext null
        val fullBitmap = try {
            image.toBitmap()
        } finally {
            image.close()
        }

        val cropX = 0
        val cropY = (fullBitmap.height * 0.80f).toInt()
        val cropWidth = (fullBitmap.width * 0.90f).toInt().coerceAtLeast(1)
        val cropHeight = (fullBitmap.height * 0.20f).toInt().coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(
            fullBitmap,
            cropX,
            cropY,
            cropWidth.coerceAtMost(fullBitmap.width - cropX),
            cropHeight.coerceAtMost(fullBitmap.height - cropY),
        )
        fullBitmap.recycle()

        runCatching { OcrProcessor.recognizeText(cropped) }
            .also { cropped.recycle() }
            .getOrNull()
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        if (mediaProjection != null) return

        val metrics = resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi

        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(resultCode, data)
        if (projection == null) {
            stopSelf()
            return
        }
        projection.registerCallback(projectionCallback, null)
        mediaProjection = projection

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "ai-tool-screen-capture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
        )
    }

    private suspend fun waitForLatestImage(reader: ImageReader): Image? {
        repeat(8) {
            reader.acquireLatestImage()?.let { return it }
            delay(80)
        }
        return null
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val imageWidth = this.width
        val imageHeight = this.height
        val rowPadding = rowStride - pixelStride * imageWidth
        val bitmapWidth = imageWidth + rowPadding / pixelStride

        return Bitmap.createBitmap(bitmapWidth, imageHeight, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(buffer)
        }.let { padded ->
            Bitmap.createBitmap(padded, 0, 0, imageWidth, imageHeight).also {
                padded.recycle()
            }
        }
    }

    private fun tearDownProjection(stopProjection: Boolean = true) {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.unregisterCallback(projectionCallback)
        if (stopProjection) {
            mediaProjection?.stop()
        }
        mediaProjection = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_capture_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.screen_capture_notification_title))
            .setContentText(getString(R.string.screen_capture_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.xyz.aitool.capture.START"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        private var active: ScreenCaptureService? = null

        fun isRunning(): Boolean = active?.mediaProjection != null

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }

        suspend fun captureTextFromCurrentScreen(): String? {
            return active?.captureVisibleText()
        }
    }
}
