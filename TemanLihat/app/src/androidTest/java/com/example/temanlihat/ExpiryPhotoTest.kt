package com.example.temanlihat

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExpiryPhotoTest {
    @Test fun recognizesReportedEdMarch2028Photo() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("expiry_ed_03_28.jpg").use { BitmapFactory.decodeStream(it) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS).text
            Log.i("ExpiryPhotoTest", "OCR: $text")
            val results = ExpiryDetector.detect(text)
            assertEquals("OCR: $text", listOf("Maret 2028"), results.map { it.display })
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}
