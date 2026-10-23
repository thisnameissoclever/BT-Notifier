package com.btnotifier.data.history

import java.io.Writer

object HistoryCsv {
    fun write(samples: List<HistorySample>, writer: Writer) {
        writer.write("observed_at_epoch_ms,address,observed_name,rssi_dbm,transport,source\r\n")
        samples.forEach { sample ->
            writer.write(sample.observedAt.toString())
            writer.write(",${text(sample.address)},${text(sample.observedName)},${sample.rssi},")
            writer.write("${text(if (sample.isBle) "BLE" else "Classic")},${text(sample.source)}\r\n")
        }
    }

    private fun text(value: String): String {
        val first = value.dropWhile { it.isWhitespace() || it.isISOControl() }.firstOrNull()
        val safe = if (first in listOf('=', '+', '-', '@') || value.firstOrNull()?.isISOControl() == true) {
            "'$value"
        } else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
