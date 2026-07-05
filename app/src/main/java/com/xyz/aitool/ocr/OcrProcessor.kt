package com.xyz.aitool.ocr

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

data class RecognizedTextLine(
    val text: String,
    val bounds: Rect,
)
