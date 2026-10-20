package com.example.temanlihat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DocumentRecognitionTest {
    @Test fun recognizesIndonesianPrintedDocument() {
        val bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 52f
        }
        canvas.drawText("Selamat datang di Teman Lihat", 40f, 120f, paint)
        canvas.drawText("Ini dokumen bahasa Indonesia.", 40f, 220f, paint)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
            assertTrue(result.text.contains("Teman Lihat"))
            assertTrue(result.text.contains("bahasa Indonesia"))
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}
