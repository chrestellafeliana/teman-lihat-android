package com.example.temanlihat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal enum class DetectionMode(val title: String) {
    Document("Baca Dokumen"), Expiry("Baca Tanggal Kedaluwarsa"), Color("Deteksi Warna")
}

@Composable
internal fun LiveDetectionScreen(mode: DetectionMode, onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var automaticSpeech by rememberSaveable { mutableStateOf(true) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var speechStatus by remember { mutableStateOf("Menyiapkan suara Indonesia…") }
    var ready by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var reset by remember { mutableIntStateOf(0) }
    val currentPaused = rememberUpdatedState(paused)
    val currentAuto = rememberUpdatedState(automaticSpeech)
    val currentReady = rememberUpdatedState(ready)
    val handler = remember { Handler(Looper.getMainLooper()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }

    fun speak(text: String) {
        if (!ready || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        splitSpeechText(text).forEachIndexed { index, chunk ->
            if (engine?.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "live-$index") == TextToSpeech.ERROR) {
                speechStatus = "Suara gagal diputar. Coba lagi."
            }
        }
    }
    val currentSpeak = rememberUpdatedState<(String) -> Unit> { speak(it) }
    LaunchedEffect(paused, ready, automaticSpeech) {
        if (mode != DetectionMode.Color && paused && ready && automaticSpeech && result.isNotBlank()) speak(result)
    }
    DisposableEffect(owner) {
        var active = true
        val tts = TextToSpeech(context) { status -> handler.post {
            if (active) {
                ready = status == TextToSpeech.SUCCESS && (engine?.setLanguage(Locale.forLanguageTag("id-ID")) ?: -1) >= 0
                speechStatus = if (ready) "Suara Indonesia siap." else "Pasang data suara Indonesia melalui pengaturan Text-to-Speech perangkat."
            }
        } }
        engine = tts
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) tts.stop()
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { active = false; owner.lifecycle.removeObserver(observer); tts.stop(); tts.shutdown(); engine = null }
    }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(permission, owner, mode, reset) {
        val active = AtomicBoolean(true)
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
        var provider: ProcessCameraProvider? = null
        var previous = ""
        var repetitions = 0
        var lastFrame = 0L
        var lastSpoken = ""
        var lastSpeech = 0L
        fun deliver(value: String, canFreeze: Boolean) {
            if (value == previous) repetitions++ else { previous = value; repetitions = 1 }
            if (repetitions < 2) return
            main.execute {
                if (active.get() && !currentPaused.value && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    result = value
                    error = ""
                    if (canFreeze) paused = true
                    val now = SystemClock.elapsedRealtime()
                    if (!canFreeze && currentAuto.value && currentReady.value && value != lastSpoken && now - lastSpeech > 4000) {
                        currentSpeak.value(value)
                        lastSpoken = value
                        lastSpeech = now
                    }
                }
            }
        }
        if (permission) {
            analysis.setAnalyzer(executor) { frame ->
                val now = SystemClock.elapsedRealtime()
                if (!active.get() || currentPaused.value || now - lastFrame < 650) { frame.close(); return@setAnalyzer }
                lastFrame = now
                try {
                    val bitmap = frame.toBitmap()
                    if (mode == DetectionMode.Color) {
                        try {
                            val size = maxOf(1, minOf(bitmap.width, bitmap.height) / 5)
                            val pixels = IntArray(size * size)
                            bitmap.getPixels(pixels, 0, size, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size)
                            deliver("Warna ${dominantColor(pixels).name}", false)
                        } finally { bitmap.recycle(); frame.close() }
                    } else {
                        recognizer.process(InputImage.fromBitmap(bitmap, frame.imageInfo.rotationDegrees))
                            .addOnSuccessListener(executor) { text ->
                                if (text.text.isBlank()) { previous = ""; repetitions = 0 }
                                else if (mode == DetectionMode.Document) deliver(text.text.trim(), true)
                                else {
                                    val dates = ExpiryDetector.detect(text.text)
                                    if (dates.size == 1 && (dates.single().labelled || dates.single().inferredFromPair)) {
                                        deliver(dates.single().description(LocalDate.now()) + " Periksa kembali tanggal pada kemasan.", true)
                                    } else {
                                        previous = ""; repetitions = 0
                                        main.execute { if (active.get() && !currentPaused.value) result = if (dates.size > 1) "Ada beberapa tanggal. Dekatkan kamera ke satu label EXP atau ED." else "Mencari label EXP atau ED beserta tanggalnya…" }
                                    }
                                }
                            }
                            .addOnFailureListener(main) { if (active.get()) error = "Teks belum terbaca. Perbaiki pencahayaan dan dekatkan kamera." }
                            .addOnCompleteListener { bitmap.recycle(); frame.close() }
                    }
                } catch (_: Exception) {
                    frame.close()
                    main.execute { if (active.get()) error = "Gambar belum dapat dianalisis. Coba arahkan kamera kembali." }
                }
            }
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (active.get()) try {
                    provider = future.get()
                    provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) { error = "Kamera tidak dapat dibuka. Periksa izin dan tutup aplikasi lain yang memakai kamera." }
            }, main)
        }
        onDispose {
            active.set(false)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            recognizer.close()
            executor.shutdown()
        }
    }
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("Kembali") }
            Text(mode.title, style = MaterialTheme.typography.headlineLarge)
            Text(if (mode == DetectionMode.Color) "Arahkan kamera ke benda. Warna di tengah dikenali otomatis." else "Arahkan kamera ke ${if (mode == DetectionMode.Expiry) "label EXP atau ED" else "dokumen"}. Tahan hingga teks terbaca stabil; pemindaian akan dijeda otomatis.")
            if (permission) {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    if (mode == DetectionMode.Color) Box(Modifier.size(48.dp).border(2.dp, Color.White).padding(2.dp).border(1.dp, Color.Black))
                }
                Text(if (paused) "Pemindaian dijeda. Hasil dipertahankan." else "Kamera aktif · Mendeteksi otomatis")
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    engine?.stop()
                    if (paused) { result = ""; reset++ }
                    paused = !paused
                }) { Text(if (paused) "Pindai Lagi" else "Jeda Pemindaian") }
            } else {
                Text("Izin kamera diperlukan untuk deteksi otomatis.")
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Izinkan Kamera") }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Buka Pengaturan Izin") }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = automaticSpeech, onCheckedChange = { automaticSpeech = it; if (!it) engine?.stop() })
                Text("Bacakan hasil otomatis")
            }
            if (result.isNotBlank()) {
                SelectionContainer { Text(result, style = MaterialTheme.typography.bodyLarge) }
                OutlinedButton(enabled = ready, onClick = { speak(result) }) { Text("Bacakan Ulang") }
            }
            TextButton(onClick = { engine?.stop() }) { Text("Hentikan Suara") }
            Text(speechStatus, style = MaterialTheme.typography.bodySmall)
            if (mode == DetectionMode.Expiry) Text("Hasil OCR dapat keliru; tanggal yang belum lewat bukan penilaian keamanan produk.", style = MaterialTheme.typography.bodySmall)
            if (mode == DetectionMode.Color) Text("Warna merupakan perkiraan dan dipengaruhi pencahayaan.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
