package com.example.temanlihat

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ExpiryDetectorTest {
    @Test fun readsReportedPackageWithSpaceSeparatedMonthAndShortYear() {
        val dates = ExpiryDetector.detect("Lot No.26038201 HET:Rp.3.234\nMD:03 26 ED:03 28")
        assertEquals(1, dates.size)
        assertEquals("Maret 2028", dates.single().display)
        assertTrue(dates.single().monthOnly)
        assertTrue(dates.single().shortYear)
        assertTrue(dates.single().description(LocalDate.of(2026, 10, 20)).contains("hari tidak tercantum"))
    }
    @Test fun supportsCommonMonthYearSeparators() {
        listOf("03 28", "03/28", "03.28", "03-28", "03   2028").forEach {
            assertEquals("Maret 2028", ExpiryDetector.detect("ED:$it").single().display)
        }
    }
    @Test fun shortMonthYearRequiresExpiryLabel() {
        assertTrue(ExpiryDetector.detect("Lot:03 28").isEmpty())
        assertTrue(ExpiryDetector.detect("MD:03 26").isEmpty())
        assertTrue(ExpiryDetector.detect("ED:13 28").isEmpty())
    }
    @Test fun ignoresMdAfterExpiryAndHandlesLabelOnPriorLine() {
        assertEquals("Maret 2028", ExpiryDetector.detect("ED:03 28 MD:03 26").single().display)
        assertEquals("Maret 2028", ExpiryDetector.detect("ED:\n03 28").single().display)
    }
    @Test fun invalidFullDateDoesNotBecomeMonthYear() {
        assertTrue(ExpiryDetector.detect("ED:31 02 2028").isEmpty())
        assertTrue(ExpiryDetector.detect("ED:31/02/2028").isEmpty())
    }
    @Test fun expiryDoesNotUseManufacturingDate() {
        val dates = ExpiryDetector.detect("MFG 01/01/2025\nEXP 20/10/2026")
        assertEquals(1, dates.size)
        assertEquals(LocalDate.of(2026, 10, 20), dates.single().date)
        assertTrue(dates.single().labelled)
    }
    @Test fun readsDateOnNextLineAndIndonesianMonth() {
        assertEquals(LocalDate.of(2027, 8, 12), ExpiryDetector.detect("Baik digunakan sebelum:\n12 Agustus 2027").single().date)
    }
    @Test fun supportsYearFirstAndShortYear() {
        assertEquals(LocalDate.of(2027, 12, 31), ExpiryDetector.detect("EXP 2027-12-31").single().date)
        val short = ExpiryDetector.detect("ED 31.12.27").single()
        assertTrue(short.shortYear)
        assertEquals(2027, short.date.year)
    }
    @Test fun rejectsInvalidDatesWithoutGuessing() {
        assertTrue(ExpiryDetector.detect("EXP 31/02/2026").isEmpty())
        assertTrue(ExpiryDetector.detect("EXP 20/13/2026").isEmpty())
        assertTrue(ExpiryDetector.detect("EXP 10/20/2026").isEmpty())
    }
    @Test fun dateWithoutLabelRemainsUnconfirmed() {
        val date = ExpiryDetector.detect("20/10/2026").single()
        assertFalse(date.labelled)
        assertTrue(date.description(LocalDate.of(2027, 1, 1)).contains("belum dapat dipastikan"))
    }
    @Test fun manufacturingAloneIsNotExpiry() {
        assertTrue(ExpiryDetector.detect("MFG 01/02/2026").isEmpty())
    }
    @Test fun monthOnlyDoesNotInventADay() {
        val date = ExpiryDetector.detect("EXP 10/2026").single()
        assertTrue(date.monthOnly)
        assertEquals("Oktober 2026", date.display)
        assertTrue(date.description(LocalDate.of(2026, 10, 20)).contains("hari tidak tercantum"))
    }
    @Test fun distinguishesPastTodayAndFuture() {
        val date = ExpiryDetector.detect("EXP 20/10/2026").single()
        assertTrue(date.description(LocalDate.of(2026, 10, 21)).contains("sudah lewat"))
        assertTrue(date.description(LocalDate.of(2026, 10, 20)).contains("hari ini"))
        assertTrue(date.description(LocalDate.of(2026, 10, 19)).contains("belum lewat"))
    }
    @Test fun choosesLaterDateAndExplainsInference() {
        val result = ExpiryDetector.detect("EXP 20/10/2026 atau 21/10/2026").single()
        assertEquals(LocalDate.of(2026, 10, 21), result.date)
        assertTrue(result.inferredFromPair)
    }
    @Test fun readsReportedNamedMonthAndBatchFormat() {
        val result = ExpiryDetector.detect("Mfg. Date/Batch No./Exp. :\nHET : Rp\nMAY 26 /E26B33 / JUL 28\n3.200").single()
        assertEquals("Juli 2028", result.display)
        assertTrue(result.monthOnly)
        assertTrue(result.inferredFromPair)
    }
    @Test fun selectsLaterUnlabelledMonthPairRegardlessOfOrder() {
        listOf("MAY 26/E26B33/JUL 28", "MAY 26 /E26B33 / JUL 28", "JUL 28 / E26B33 / MAY 26", "05 26 / 07 28").forEach {
            val result = ExpiryDetector.detect(it).single()
            assertEquals("Juli 2028", result.display)
            assertTrue(result.inferredFromPair)
        }
    }
    @Test fun namedMonthsSupportIndonesianAndLongYears() {
        assertEquals("Juli 2028", ExpiryDetector.detect("ED JULI 2028").single().display)
        assertEquals("Mei 2026", ExpiryDetector.detect("EXP MAY 26").single().display)
    }
    @Test fun explicitManufacturingLabelStillExcluded() {
        assertEquals("Juli 2028", ExpiryDetector.detect("MD JUL 2030 ED JUL 28").single().display)
    }
}
