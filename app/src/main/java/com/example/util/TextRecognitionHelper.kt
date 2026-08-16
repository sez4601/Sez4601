package com.example.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class OcrResult(
    val fullText: String,
    val textLines: List<String>,
    val bestNameCandidate: String?
)

object TextRecognitionHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(context: Context, imageUri: Uri): OcrResult =
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val fullText = visionText.text

                        // Satırları çıkar ve temizle
                        val lines = mutableListOf<String>()
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val text = line.text.trim()
                                if (text.length >= 2) {
                                    // Sadece barkod/rakam veya tarih olmayan anlamlı satırları filtrele
                                    val hasLetters = text.any { it.isLetter() }
                                    if (hasLetters && !lines.contains(text)) {
                                        lines.add(text)
                                    }
                                }
                            }
                        }

                        // En uygun ürün adı adayını bul (SKT, TETT, Parti No vb. etiketleri hariç tut)
                        val bestCandidate = lines.firstOrNull { candidate ->
                            val upper = candidate.uppercase()
                            candidate.length in 3..50 &&
                            candidate.any { it.isLetter() } &&
                            !upper.startsWith("SKT") &&
                            !upper.startsWith("TETT") &&
                            !upper.startsWith("P.NO") &&
                            !upper.startsWith("PARTI") &&
                            !upper.startsWith("EXP") &&
                            !upper.startsWith("NET") &&
                            !upper.startsWith("İÇİNDEKİLER") &&
                            !upper.startsWith("ICINDEKILER")
                        } ?: lines.firstOrNull()

                        continuation.resume(
                            OcrResult(
                                fullText = fullText,
                                textLines = lines,
                                bestNameCandidate = bestCandidate
                            )
                        )
                    }
                    .addOnFailureListener { exception ->
                        exception.printStackTrace()
                        continuation.resume(OcrResult("", emptyList(), null))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume(OcrResult("", emptyList(), null))
            }
        }
}
