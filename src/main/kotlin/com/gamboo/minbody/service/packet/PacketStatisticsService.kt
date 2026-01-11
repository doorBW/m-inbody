package com.gamboo.minbody.service.packet

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.gamboo.minbody.constants.PacketType
import com.gamboo.minbody.model.PacketStatisticsSummary
import com.gamboo.minbody.model.PacketTypeStats
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class PacketStatisticsService {

    private val logger = KotlinLogging.logger {}
    private val stats = ConcurrentHashMap<Int, PacketTypeStats>()
    private val knownPacketTypes = PacketType.entries.map { it.code }.toSet()

    private var totalPackets = 0L
    private val outputDir = Paths.get(System.getProperty("user.home"), "m-inbody-packets").toFile()

    private val objectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        enable(SerializationFeature.INDENT_OUTPUT)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val scheduler = Executors.newScheduledThreadPool(1)

    init {
        // 출력 디렉토리 생성
        if (!outputDir.exists()) {
            outputDir.mkdirs()
            logger.info { "Created packet statistics directory: ${outputDir.absolutePath}" }
        }

        // 30초마다 통계를 파일로 저장
        scheduler.scheduleAtFixedRate({
            try {
                saveStatistics()
            } catch (e: Exception) {
                logger.error(e) { "Failed to save packet statistics" }
            }
        }, 30, 30, TimeUnit.SECONDS)

        logger.info { "PacketStatisticsService initialized. Output directory: ${outputDir.absolutePath}" }
    }

    /**
     * 패킷 수신 기록
     */
    fun recordPacket(type: Int, data: ByteArray, parsedSuccessfully: Boolean) {
        totalPackets++

        val stat = stats.computeIfAbsent(type) {
            PacketTypeStats(
                type = type,
                parsedSuccessfully = parsedSuccessfully
            ).also {
                // 새로운 패킷 타입 발견 시 로깅
                val isKnown = knownPacketTypes.contains(type)
                if (isKnown) {
                    logger.info { "📦 [NEW KNOWN PACKET] Type: $type (${PacketType.fromCode(type)?.name})" }
                } else {
                    logger.warn { "🔍 [NEW UNKNOWN PACKET] Type: $type - Not in PacketType enum!" }
                }
            }
        }

        stat.incrementCount()

        // 파싱되지 않은 패킷의 raw 데이터 샘플 저장
        if (!parsedSuccessfully) {
            stat.addRawSample(data)
        }
    }

    /**
     * 통계 요약 가져오기
     */
    fun getSummary(): PacketStatisticsSummary {
        val knownTypes = stats.keys.count { it in knownPacketTypes }
        val unknownTypes = stats.keys.count { it !in knownPacketTypes }

        return PacketStatisticsSummary(
            totalPackets = totalPackets,
            uniqueTypes = stats.size,
            knownTypes = knownTypes,
            unknownTypes = unknownTypes,
            typeStats = stats.toMap()
        )
    }

    /**
     * 통계를 파일로 저장
     */
    fun saveStatistics() {
        if (stats.isEmpty()) {
            logger.debug { "No packet statistics to save yet" }
            return
        }

        val summary = getSummary()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

        try {
            // JSON 저장
            saveAsJson(summary, timestamp)

            // CSV 저장
            saveAsCsv(summary, timestamp)

            // Raw 데이터 덤프 (파싱 실패한 패킷만)
            saveRawDump(summary, timestamp)

            logger.info { "📊 Packet statistics saved: ${summary.totalPackets} total packets, ${summary.uniqueTypes} unique types (${summary.unknownTypes} unknown)" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save statistics files" }
        }
    }

    private fun saveAsJson(summary: PacketStatisticsSummary, timestamp: String) {
        val jsonFile = File(outputDir, "packet-stats-latest.json")
        val jsonFileWithTimestamp = File(outputDir, "packet-stats-$timestamp.json")

        objectMapper.writeValue(jsonFile, summary)
        objectMapper.writeValue(jsonFileWithTimestamp, summary)

        logger.debug { "Saved JSON: ${jsonFile.absolutePath}" }
    }

    private fun saveAsCsv(summary: PacketStatisticsSummary, timestamp: String) {
        val csvFile = File(outputDir, "packet-stats-latest.csv")

        FileWriter(csvFile).use { writer ->
            // CSV 헤더
            writer.write("PacketType,PacketName,Count,FirstSeen,LastSeen,Parsed,IsKnown\n")

            // 카운트 순으로 정렬
            summary.typeStats.values.sortedByDescending { it.count }.forEach { stat ->
                val packetName = PacketType.fromCode(stat.type)?.name ?: "UNKNOWN"
                val isKnown = knownPacketTypes.contains(stat.type)

                writer.write("${stat.type},$packetName,${stat.count},${stat.firstSeen},${stat.lastSeen},${stat.parsedSuccessfully},$isKnown\n")
            }
        }

        logger.debug { "Saved CSV: ${csvFile.absolutePath}" }
    }

    private fun saveRawDump(summary: PacketStatisticsSummary, timestamp: String) {
        val unparsedStats = summary.typeStats.values.filter { !it.parsedSuccessfully && it.rawDataSamples.isNotEmpty() }

        if (unparsedStats.isEmpty()) {
            logger.debug { "No unparsed packets to dump" }
            return
        }

        val dumpFile = File(outputDir, "raw-dump-latest.txt")

        FileWriter(dumpFile).use { writer ->
            writer.write("=".repeat(80) + "\n")
            writer.write("M-Inbody Raw Packet Dump\n")
            writer.write("Generated: ${summary.generatedAt}\n")
            writer.write("Total Unparsed Types: ${unparsedStats.size}\n")
            writer.write("=".repeat(80) + "\n\n")

            unparsedStats.sortedByDescending { it.count }.forEach { stat ->
                writer.write("\n" + "-".repeat(80) + "\n")
                writer.write("Packet Type: ${stat.type}\n")
                writer.write("Count: ${stat.count}\n")
                writer.write("First Seen: ${stat.firstSeen}\n")
                writer.write("Last Seen: ${stat.lastSeen}\n")
                writer.write("Known Type: ${knownPacketTypes.contains(stat.type)}\n")
                writer.write("-".repeat(80) + "\n")

                stat.rawDataSamples.forEachIndexed { index, sample ->
                    writer.write("\nSample ${index + 1}:\n")
                    writer.write(sample + "\n")
                }
            }
        }

        logger.debug { "Saved raw dump: ${dumpFile.absolutePath}" }
    }

    /**
     * 통계 초기화
     */
    fun clearStats() {
        stats.clear()
        totalPackets = 0
        logger.info { "Packet statistics cleared" }
    }

    /**
     * 종료 시 최종 저장
     */
    @jakarta.annotation.PreDestroy
    fun shutdown() {
        logger.info { "Shutting down PacketStatisticsService..." }
        scheduler.shutdown()
        saveStatistics()
    }
}