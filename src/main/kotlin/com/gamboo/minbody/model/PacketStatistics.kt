package com.gamboo.minbody.model

import java.time.LocalDateTime

data class PacketTypeStats(
    val type: Int,
    var count: Long = 0,
    var firstSeen: LocalDateTime = LocalDateTime.now(),
    var lastSeen: LocalDateTime = LocalDateTime.now(),
    var parsedSuccessfully: Boolean = false,
    var rawDataSamples: MutableList<String> = mutableListOf()
) {
    fun incrementCount() {
        count++
        lastSeen = LocalDateTime.now()
    }

    fun addRawSample(data: ByteArray, maxSamples: Int = 3) {
        if (rawDataSamples.size < maxSamples) {
            rawDataSamples.add(data.toHex())
        }
    }

    private fun ByteArray.toHex(): String =
        this.take(100).joinToString(" ") { "%02x".format(it) } +
        if (this.size > 100) " ... (${this.size} bytes total)" else ""
}

data class PacketStatisticsSummary(
    val totalPackets: Long,
    val uniqueTypes: Int,
    val knownTypes: Int,
    val unknownTypes: Int,
    val typeStats: Map<Int, PacketTypeStats>,
    val generatedAt: LocalDateTime = LocalDateTime.now()
)