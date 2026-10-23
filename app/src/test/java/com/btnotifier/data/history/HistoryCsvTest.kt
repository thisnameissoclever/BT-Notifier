package com.btnotifier.data.history

import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCsvTest {
    @Test fun quotesNamesAddressesNewlinesAndEmbeddedQuotes() {
        val output = StringWriter()
        HistoryCsv.write(listOf(HistorySample("AA,BB", "Desk \"speaker\"\r\n2", -61, true, 1234, "NEARBY")), output)
        assertEquals(
            "observed_at_epoch_ms,address,observed_name,rssi_dbm,transport,source\r\n" +
                "1234,\"AA,BB\",\"Desk \"\"speaker\"\"\r\n2\",-61,\"BLE\",\"NEARBY\"\r\n",
            output.toString()
        )
    }

    @Test fun neutralizesFormulasIncludingWhitespaceAndControlPrefixes() {
        listOf("=SUM(A1)", "+cmd", "-cmd", "@SUM(A1)", "  =SUM(A1)", "\t=SUM(A1)", "\r=cmd").forEach { input ->
            val output = StringWriter()
            HistoryCsv.write(listOf(HistorySample(input, input, -70, false, 10, "BACKGROUND")), output)
            val escaped = input.replace("\"", "\"\"")
            assertTrue(input, output.toString().contains("\"'$escaped\",\"'$escaped\",-70,"))
        }
    }

    @Test fun preservesUnicodeAndPlainNamesWithoutPrefixingNumericSignal() {
        val output = StringWriter()
        HistoryCsv.write(listOf(HistorySample("AA:BB", "Caf\u00e9 speaker", -90, false, 2, "FINDER")), output)
        assertTrue(output.toString().contains("\"Caf\u00e9 speaker\",-90,\"Classic\""))
    }

    @Test fun emptyExportStillHasStableHeader() {
        val output = StringWriter()
        HistoryCsv.write(emptyList(), output)
        assertEquals("observed_at_epoch_ms,address,observed_name,rssi_dbm,transport,source\r\n", output.toString())
    }
}
