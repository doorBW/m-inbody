package com.gamboo.minbody.rest

import com.gamboo.minbody.model.PacketStatisticsSummary
import com.gamboo.minbody.service.packet.PacketStatisticsService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/packet-stats")
class PacketStatsRestController(
    private val packetStatisticsService: PacketStatisticsService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * 패킷 통계 요약 조회
     */
    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<PacketStatisticsSummary> {
        val summary = packetStatisticsService.getSummary()
        return ResponseEntity.ok(summary)
    }

    /**
     * 통계를 파일로 저장 (수동 트리거)
     */
    @PostMapping("/save")
    fun saveStatistics(): ResponseEntity<Map<String, Any>> {
        return try {
            packetStatisticsService.saveStatistics()
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Statistics saved successfully",
                "outputDir" to System.getProperty("user.home") + "/m-inbody-packets"
            ))
        } catch (e: Exception) {
            logger.error(e) { "Failed to save statistics" }
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "Failed to save statistics: ${e.message}"
            ))
        }
    }

    /**
     * 통계 초기화
     */
    @DeleteMapping("/clear")
    fun clearStatistics(): ResponseEntity<Map<String, Any>> {
        packetStatisticsService.clearStats()
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Statistics cleared successfully"
        ))
    }
}