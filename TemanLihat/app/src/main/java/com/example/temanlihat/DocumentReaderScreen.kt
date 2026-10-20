package com.example.temanlihat

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun DocumentReaderScreen(onBack: () -> Unit, expiryMode: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var request by rememberSaveable { mutableIntStateOf(0) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(if (expiryMode) "Foto label kedaluwarsa dengan pencahayaan cukup. Pastikan label dan angka tanggal terlihat jelas." else "Foto dokumen dengan pencahayaan cukup. Pastikan seluruh teks terlihat jelas.") }
    var speechReady by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var speechMessage by remember { mutableStateOf("Menyiapkan suara bahasa Indonesia…") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(context, lifecycleOwner) {
        var active = true
        val engine = TextToSpeech(context) { status ->
            handler.post {
                if (active) {
                    val result = if (status == TextToSpeech.SUCCESS) {
                        tts?.setLanguage(Locale.forLanguageTag("id-ID"))
                    } else null
                    speechReady = result != null && result >= TextToSpeech.LANG_AVAILABLE
                    speechMessage = if (speechReady) "Suara bahasa Indonesia siap." else
                        "Suara bahasa Indonesia belum tersedia. Pasang data suara bahasa Indonesia melalui pengaturan Text-to-Speech perangkat."
                }
            }
        }
        tts = engine
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { handler.post { if (active) speaking = true } }
            override fun onDone(utteranceId: String?) { handler.post { if (active && utteranceId == "document-last") speaking = false } }
            @Deprecated("Legacy callback")
            override fun onError(utteranceId: String?) {
                handler.post {
                    if (active) {
                        speaking = false
                        speechMessage = "Pembacaan suara gagal. Silakan coba lagi."
                    }
                }
            }
        })
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                engine.stop()
                speaking = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            active = false
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }

    LaunchedEffect(imageUri, request) {
        val uri = imageUri ?: return@LaunchedEffect
        busy = true
        text = ""
        tts?.stop()
        speaking = false
        message = "Sedang mengenali teks…"
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, Uri.parse(uri)) }
            val result = suspendCancellableCoroutine<String> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text.trim()) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
            text = if (expiryMode && result.isNotBlank()) {
                val candidates = ExpiryDetector.detect(result)
                val today = java.time.LocalDate.now()
                val summary = when {
                    candidates.isEmpty() -> "Tanggal kedaluwarsa belum dapat dikenali. Foto label EXP atau ED lebih dekat dan pastikan angkanya jelas."
                    candidates.size > 1 -> "Ditemukan beberapa tanggal. Cocokkan setiap hasil dengan label pada kemasan.\n\n" +
                        candidates.joinToString("\n\n") { "Teks tercetak: ${it.raw}. ${it.description(today)}" }
                    else -> candidates.single().let { "Teks tercetak: ${it.raw}. ${it.description(today)}" }
                }
                summary + "\n\nPeriksa kembali pada kemasan. Hasil OCR dapat keliru dan bukan penilaian keamanan produk.\n\nTeks pada foto:\n" + result
            } else result
            message = if (result.isBlank()) "Tidak ada teks yang terbaca. Ambil foto lebih dekat, tajam, dan terang." else
                "Teks berhasil dikenali. Tekan Bacakan Teks untuk mendengarkan."
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "Gambar tidak dapat dibaca. Silakan ambil foto atau pilih gambar lagi."
        } finally {
            recognizer.close()
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            imageUri = pendingCapture
            request++
        } else {
            message = "Pengambilan foto dibatalkan. Anda dapat mencoba lagi."
        }
        pendingCapture = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { imageUri = uri.toString(); request++ }
    }

    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("Kembali") }
            Text(if (expiryMode) "Baca Tanggal Kedaluwarsa" else "Baca Dokumen", style = MaterialTheme.typography.headlineLarge)
            Text(if (expiryMode) "Foto bagian kemasan yang mencantumkan EXP, ED, Best Before, atau Baik Digunakan Sebelum. Sertakan label dan tanggal dengan jelas." else "Ambil foto atau pilih gambar dokumen. Teks akan dibacakan dengan pelafalan bahasa Indonesia.")
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        val directory = File(context.cacheDir, "documents").apply { mkdirs() }
                        // Keep a single private capture; each new photo replaces the previous one.
                        val file = File(directory, if (expiryMode) "expiry.jpg" else "capture.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
                        pendingCapture = uri.toString()
                        imageUri = null
                        camera.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        pendingCapture = null
                        message = "Aplikasi kamera tidak tersedia. Pilih gambar dari galeri."
                    } catch (_: Exception) {
                        pendingCapture = null
                        message = "Kamera tidak dapat dibuka. Silakan gunakan galeri atau coba lagi."
                    }
                }
            ) { Text(if (expiryMode) "Foto Tanggal Kedaluwarsa" else "Ambil Foto Dokumen") }
            OutlinedButton(
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try { gallery.launch("image/*") }
                    catch (_: Exception) { message = "Galeri tidak dapat dibuka. Silakan coba lagi." }
                }
            ) { Text("Pilih Gambar dari Galeri") }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (text.isNotBlank()) {
                Text(if (expiryMode) "Hasil Deteksi Tanggal" else "Hasil Pembacaan", style = MaterialTheme.typography.titleLarge)
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
                Button(
                    modifier = Modifier.fillMaxWidth(), enabled = speechReady && !busy && !speaking,
                    onClick = {
                        val chunks = splitSpeechText(text)
                        speaking = true
                        chunks.forEachIndexed { index, chunk ->
                            val result = tts?.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                                null, if (index == chunks.lastIndex) "document-last" else "document-$index")
                            if (result == TextToSpeech.ERROR) {
                                tts?.stop()
                                speaking = false
                                speechMessage = "Pembacaan suara gagal. Silakan coba lagi."
                                return@Button
                            }
                        }
                    }
                ) { Text(if (speaking) "Sedang Membacakan…" else "Bacakan Teks") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), enabled = speaking,
                    onClick = { tts?.stop(); speaking = false }
                ) { Text("Hentikan Pembacaan") }
            }
            Text(speechMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal fun splitSpeechText(text: String, limit: Int = TextToSpeech.getMaxSpeechInputLength()): List<String> {
    require(limit > 0)
    val chunks = mutableListOf<String>()
    var remaining = text.trim()
    while (remaining.length > limit) {
        val end = remaining.lastIndexOfAny(charArrayOf(' ', '\n'), limit).takeIf { it > 0 } ?: limit
        chunks += remaining.substring(0, end)
        remaining = remaining.substring(end).trimStart()
    }
    if (remaining.isNotEmpty()) chunks += remaining
    return chunks
}
