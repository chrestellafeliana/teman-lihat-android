package com.example.temanlihat

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun ColorDetectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var uriString by rememberSaveable { mutableStateOf<String?>(null) }
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    var request by rememberSaveable { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<DetectedColor?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Letakkan benda di tengah foto agar warnanya dapat dikenali.") }
    var voiceReady by remember { mutableStateOf(false) }
    var voiceMessage by remember { mutableStateOf("Menyiapkan suara bahasa Indonesia…") }
    var voice by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context, owner) {
        var active = true
        val handler = Handler(Looper.getMainLooper())
        val engine = TextToSpeech(context) { status ->
            handler.post {
                if (active) {
                    voiceReady = status == TextToSpeech.SUCCESS &&
                        (voice?.setLanguage(Locale.forLanguageTag("id-ID")) ?: -1) >= 0
                    voiceMessage = if (voiceReady) "Suara bahasa Indonesia siap." else
                        "Pasang data suara bahasa Indonesia di pengaturan Text-to-Speech perangkat untuk mendengarkan hasil."
                }
            }
        }
        voice = engine
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            active = false
            owner.lifecycle.removeObserver(observer)
            engine.stop()
            engine.shutdown()
            voice = null
        }
    }

    LaunchedEffect(uriString, request) {
        val uri = uriString ?: return@LaunchedEffect
        busy = true
        result = null
        preview = null
        voice?.stop()
        message = "Sedang mengenali warna…"
        try {
            val (bitmap, color) = withContext(Dispatchers.IO) {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = minOf(1f, 1000f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                    decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                }
                val width = maxOf(1, bitmap.width / 5)
                val height = maxOf(1, bitmap.height / 5)
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, (bitmap.width - width) / 2, (bitmap.height - height) / 2, width, height)
                bitmap to dominantColor(pixels)
            }
            preview = bitmap
            result = color
            message = "Warna dominan di kotak tengah: ${color.name}."
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "Warna tidak dapat dikenali. Pilih foto lain dengan area tengah yang jelas dan tidak transparan."
        } finally {
            busy = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { uriString = capture; request++ }
        else message = "Pengambilan foto dibatalkan. Silakan coba lagi."
        capture = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { uriString = uri.toString(); request++ }
    }
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("Kembali") }
            Text("Deteksi Warna", style = MaterialTheme.typography.headlineLarge)
            Text("Ambil foto atau pilih gambar. Warna dikenali dari kotak tengah foto. Gunakan cahaya alami dan hindari bayangan.")
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                try {
                    voice?.stop()
                    val directory = File(context.cacheDir, "documents").apply { mkdirs() }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", File(directory, "color.jpg"))
                    capture = uri.toString()
                    camera.launch(uri)
                } catch (_: Exception) {
                    capture = null
                    message = "Kamera tidak dapat dibuka. Silakan pilih gambar dari galeri."
                }
            }) { Text("Ambil Foto Benda") }
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                try { voice?.stop(); gallery.launch("image/*") }
                catch (_: Exception) { message = "Galeri tidak dapat dibuka. Silakan coba lagi." }
            }) { Text("Pilih Gambar dari Galeri") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            preview?.let { bitmap ->
                Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height), contentAlignment = Alignment.Center) {
                    Image(bitmap.asImageBitmap(), "Foto benda yang dianalisis", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Box(Modifier.fillMaxWidth(0.2f).fillMaxHeight(0.2f).border(3.dp, Color.White).padding(3.dp).border(1.dp, Color.Black))
                }
            }
            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            result?.let { color ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(Color(color.argb)).border(1.dp, MaterialTheme.colorScheme.outline))
                    Column {
                        Text(color.name, style = MaterialTheme.typography.headlineMedium)
                        Text("Kode warna ${color.hex}")
                    }
                }
                Button(enabled = voiceReady && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    if (voice?.speak("Warna benda adalah ${color.name}", TextToSpeech.QUEUE_FLUSH, null, "color") == TextToSpeech.ERROR) {
                        voiceMessage = "Pembacaan suara gagal. Silakan coba lagi."
                    }
                }) { Text("Sebutkan Warna") }
                OutlinedButton(enabled = voiceReady, modifier = Modifier.fillMaxWidth(), onClick = { voice?.stop() }) { Text("Hentikan Suara") }
            }
            Text(voiceMessage, style = MaterialTheme.typography.bodyMedium)
            Text("Hasil merupakan perkiraan warna pada foto. Pencahayaan dan kamera dapat memengaruhi hasil.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
