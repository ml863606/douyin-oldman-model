package com.xyz.aitool.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object OcrProcessor {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognizeText(bitmap: Bitmap): String {
        return recognizeTextLines(bitmap)
            .joinToString(separator = "\n") { it.text }
    }

    suspend fun recognizeTextLines(bitmap: Bitmap): List<RecognizedTextLine> {
        return recognizeMlKitTextLines(bitmap)
    }

    suspend fun recognizeTextLines(
        context: Context,
        bitmap: Bitmap,
        engine: OcrEngine,
    ): OcrRecognitionResult {
        return when (engine) {
            OcrEngine.ML_KIT_CHINESE -> OcrRecognitionResult(
                lines = recognizeMlKitTextLines(bitmap),
                engine = OcrEngine.ML_KIT_CHINESE,
            )
            OcrEngine.PP_OCRV6 -> {
                val ppOcrResult = PpOcrV6Processor.recognizeTextLinesIfReady(context, bitmap)
                ppOcrResult ?: OcrRecognitionResult(
                    lines = recognizeMlKitTextLines(bitmap),
                    engine = OcrEngine.ML_KIT_CHINESE,
                    requestedEngine = OcrEngine.PP_OCRV6,
                    fallbackReason = PpOcrV6Processor.notReadyReason(context),
                )
            }
        }
    }

    private suspend fun recognizeMlKitTextLines(bitmap: Bitmap): List<RecognizedTextLine> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        val lines = result.textBlocks
                            .flatMap { it.lines }
                            .map { line ->
                                RecognizedTextLine(
                                    text = line.text,
                                    bounds = line.boundingBox ?: Rect(),
                                )
                            }
                        continuation.resume(lines)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        }
    }
}

object PpOcrV6Processor {
    private const val MODEL_DIR = "ppocrv6"
    private val requiredFiles = listOf(
        "det.nb",
        "rec.nb",
        "cls.nb",
        "keys.txt",
    )

    fun isReady(context: Context): Boolean {
        val assetFiles = runCatching {
            context.assets.list(MODEL_DIR).orEmpty().toSet()
        }.getOrDefault(emptySet())
        return requiredFiles.all { it in assetFiles }
    }

    fun notReadyReason(context: Context): String {
        val assetFiles = runCatching {
            context.assets.list(MODEL_DIR).orEmpty().toSet()
        }.getOrDefault(emptySet())
        val missingFiles = requiredFiles.filterNot { it in assetFiles }
        return if (missingFiles.isEmpty()) {
            "PP-OCRv6 推理库尚未接入，已临时降级到 ML Kit"
        } else {
            "缺少 PP-OCRv6 模型文件：assets/$MODEL_DIR/${missingFiles.joinToString("、")}，已临时降级到 ML Kit"
        }
    }

    suspend fun recognizeTextLinesIfReady(
        context: Context,
        bitmap: Bitmap,
    ): OcrRecognitionResult? {
        if (!isReady(context)) return null
        @Suppress("UNUSED_PARAMETER")
        val pendingBitmap = bitmap
        return null
    }
}

data class OcrRecognitionResult(
    val lines: List<RecognizedTextLine>,
    val engine: OcrEngine,
    val requestedEngine: OcrEngine = engine,
    val fallbackReason: String? = null,
)

data class RecognizedTextLine(
    val text: String,
    val bounds: Rect,
)
