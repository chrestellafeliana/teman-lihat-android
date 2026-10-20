package com.example.temanlihat

import org.junit.Assert.*
import org.junit.Test

class DocumentReaderTest {
    @Test fun longDocumentFitsSpeechLimitWithoutLosingWords() {
        val document = List(100) { "Ini dokumen berbahasa Indonesia untuk dibacakan." }.joinToString(" ")
        val chunks = splitSpeechText(document, 100)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(document, chunks.joinToString(" "))
    }

    @Test fun unbrokenTextStillFitsSpeechLimit() {
        val document = "a".repeat(250)
        val chunks = splitSpeechText(document, 100)
        assertEquals(listOf(100, 100, 50), chunks.map { it.length })
        assertEquals(document, chunks.joinToString(""))
    }

    @Test fun emptyDocumentDoesNotQueueSpeech() {
        assertTrue(splitSpeechText("  \n ", 100).isEmpty())
    }
}
