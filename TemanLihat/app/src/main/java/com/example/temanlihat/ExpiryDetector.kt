package com.example.temanlihat

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ExpiryCandidate(
    val date: LocalDate,
    val monthOnly: Boolean,
    val raw: String,
    val labelled: Boolean,
    val shortYear: Boolean,
    val inferredFromPair: Boolean = false
) {
    val display: String get() = date.format(DateTimeFormatter.ofPattern(
        if (monthOnly) "MMMM yyyy" else "d MMMM yyyy", Locale.forLanguageTag("id-ID")))

    fun description(today: LocalDate): String {
        val status = when {
            !labelled && !inferredFromPair -> "Label kedaluwarsa tidak ditemukan; tanggal ini belum dapat dipastikan sebagai tanggal kedaluwarsa."
            monthOnly && YearMonth.from(date) < YearMonth.from(today) -> "Bulan yang tercetak sudah lewat."
            monthOnly && YearMonth.from(date) == YearMonth.from(today) -> "Bulan yang tercetak adalah bulan ini; hari tidak tercantum."
            monthOnly -> "Bulan yang tercetak belum lewat; hari tidak tercantum."
            date < today -> "Tanggal yang tercetak sudah lewat."
            date == today -> "Tanggal yang tercetak adalah hari ini."
            else -> "Tanggal yang tercetak belum lewat."
        }
        return "$display. $status" +
            (if (inferredFromPair) " Dipilih sebagai perkiraan kedaluwarsa karena tanggal ini paling jauh ke depan dari tanggal yang ditemukan." else "") +
            (if (!monthOnly) " Format angka dibaca sebagai tanggal, bulan, tahun." else "") +
            (if (shortYear) " Tahun dua digit ditafsirkan sebagai ${date.year}." else "")
    }
}

internal object ExpiryDetector {
    private val labels = Regex("\\b(EXP(?:IRY|IRES|IRATION)?(?:\\s+DATE)?|ED|BEST\\s+BEFORE|BAIK\\s+DIGUNAKAN\\s+SEBELUM|KEDALUWARSA|KADALUARSA|MFG|MFD|MD|PROD(?:UCTION)?(?:\\s+DATE)?|TANGGAL\\s+PRODUKSI)\\b", RegexOption.IGNORE_CASE)
    private val manufacturing = Regex("^(MFG|MFD|MD|PROD|TANGGAL\\s+PRODUKSI)", RegexOption.IGNORE_CASE)
    // Longer forms come first so month/year cannot consume part of a complete date.
    private val groupedLabel = Regex("""MFG\.?\s*DATE\s*/\s*BATCH\s*NO\.?\s*/\s*EXP\.?""", RegexOption.IGNORE_CASE)
    private val dates = Regex("""(?<![\dA-Za-z])(?:\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.](?:\d{4}|\d{2})|\d{1,2}[ \t]+\d{1,2}[ \t]+(?:\d{4}|\d{2})|\d{1,2}\s+[A-Za-z]+\s+(?:\d{4}|\d{2})|[A-Za-z]+[ \t]+(?:\d{4}|\d{2})|\d{1,2}(?:[-/.]|[ \t]+)(?:\d{4}|\d{2}))(?![\dA-Za-z.-]|/\d)""")
    private val months = listOf(
        listOf("JAN", "JANUARI", "JANUARY"), listOf("FEB", "FEBRUARI", "FEBRUARY"),
        listOf("MAR", "MARET", "MARCH"), listOf("APR", "APRIL"), listOf("MEI", "MAY"),
        listOf("JUN", "JUNI", "JUNE"), listOf("JUL", "JULI", "JULY"),
        listOf("AGU", "AGT", "AGUSTUS", "AUG", "AUGUST"), listOf("SEP", "SEPT", "SEPTEMBER"),
        listOf("OKT", "OKTOBER", "OCT", "OCTOBER"), listOf("NOV", "NOVEMBER"),
        listOf("DES", "DESEMBER", "DEC", "DECEMBER")
    )

    fun detect(text: String): List<ExpiryCandidate> {
        groupedLabel.find(text)?.let { header ->
            val nearby = text.substring(header.range.last + 1).trimStart(' ', ':', '\t', '\n', '\r')
                .lineSequence().take(4).joinToString("\n")
            return selectLatest(parse(nearby, true))
        }
        val markers = labels.findAll(text).toList()
        if (markers.isEmpty()) {
            val candidates = parse(text, false, allowShortMonth = true)
            if (candidates.size >= 2) return selectLatest(candidates)
            // A lone short numeric pair could be a lot number or price.
            return candidates.filterNot { it.monthOnly && it.shortYear && it.raw.first().isDigit() }
        }
        return selectLatest(markers.flatMapIndexed { index, marker ->
            if (manufacturing.containsMatchIn(marker.value)) emptyList() else {
                val end = markers.getOrNull(index + 1)?.range?.first ?: text.length
                // Only the label's line and the following line can supply its date.
                val nearby = text.substring(marker.range.last + 1, end)
                    .trimStart(' ', ':', '.', '-', '\t').lineSequence().take(2).joinToString("\n")
                parse(nearby, true)
            }
        }.distinctBy { Triple(it.date, it.monthOnly, it.labelled) })
    }

    private fun selectLatest(candidates: List<ExpiryCandidate>): List<ExpiryCandidate> {
        if (candidates.size <= 1) return candidates
        val latest = candidates.maxBy { if (it.monthOnly) YearMonth.from(it.date).atEndOfMonth() else it.date }
        return listOf(latest.copy(inferredFromPair = true))
    }

    private fun parse(text: String, labelled: Boolean, allowShortMonth: Boolean = false): List<ExpiryCandidate> = dates.findAll(text).mapNotNull { match ->
        try {
            val raw = match.value
            val parts = raw.split(Regex("[-/.\\s]+"))
            val monthOnly = parts.size == 2
            val yearFirst = parts[0].length == 4 && parts[0].all { it.isDigit() }
            val yearText = if (yearFirst) parts[0] else parts.last()
            // A two-number short date is only meaningful beside an expiry label.
            // Without that context it might be a lot number or a price.
            if (monthOnly && yearText.length == 2 && parts[0].first().isDigit() && !labelled && !allowShortMonth) return@mapNotNull null
            val year = yearText.toInt().let { if (yearText.length == 2) 2000 + it else it }
            if (year !in 1900..2199) return@mapNotNull null
            val monthText = if (monthOnly) parts[0] else parts[1]
            val month = monthText.toIntOrNull() ?: (months.indexOfFirst { monthText.uppercase(Locale.ROOT) in it } + 1)
            val day = if (monthOnly) 1 else if (yearFirst) parts[2].toInt() else parts[0].toInt()
            ExpiryCandidate(LocalDate.of(year, month, day), monthOnly, raw, labelled, yearText.length == 2)
        } catch (_: Exception) { null }
    }.toList().distinctBy { Triple(it.date, it.monthOnly, it.labelled) }
}
