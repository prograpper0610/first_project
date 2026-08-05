package com.autobuy.core.accessibility.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NTP 기반 서버 시간 동기화 모듈.
 *
 * 오픈 정각에 정확히 동작하기 위해 디바이스 로컬 시간과 서버 시간의 오프셋을 계산합니다.
 * 보정된 현재 시간 = System.currentTimeMillis() + clockOffset
 *
 * 복수 NTP 서버에 쿼리하여 가장 신뢰도 높은 값을 선택합니다.
 */
@Singleton
class NtpTimeSyncer @Inject constructor() {

    companion object {
        private val NTP_SERVERS = listOf(
            "time.google.com",
            "pool.ntp.org",
            "time.cloudflare.com",
            "time.windows.com"
        )
        private const val NTP_PORT = 123
        private const val NTP_TIMEOUT_MS = 3000
        private const val NTP_PACKET_SIZE = 48
        // NTP 에포크 기준 (1900년 1월 1일 ~ 1970년 1월 1일: 70년)
        private const val EPOCH_DELTA = 2208988800L
    }

    // 보정된 오프셋 (밀리초). 양수 = 서버가 로컬보다 빠름
    @Volatile
    private var clockOffsetMs: Long = 0L

    @Volatile
    private var lastSyncedAt: Long = 0L

    @Volatile
    var isSynced: Boolean = false
        private set

    /**
     * NTP 서버와 시간 동기화를 수행합니다.
     * @return 성공 여부
     */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        val offsets = mutableListOf<Long>()

        for (server in NTP_SERVERS) {
            try {
                val offset = queryNtpServer(server)
                if (offset != null) offsets.add(offset)
            } catch (e: Exception) {
                // 서버 연결 실패 → 다음 서버 시도
            }
        }

        if (offsets.isEmpty()) return@withContext false

        // 중앙값 사용 (이상치 제거)
        offsets.sort()
        clockOffsetMs = offsets[offsets.size / 2]
        lastSyncedAt = System.currentTimeMillis()
        isSynced = true
        true
    }

    /**
     * 보정된 현재 시간 (밀리초 에포크).
     */
    fun currentTimeMs(): Long = System.currentTimeMillis() + clockOffsetMs

    /**
     * 오픈 시간까지 남은 밀리초.
     */
    fun millisUntilOpen(openTimeEpochMs: Long): Long = openTimeEpochMs - currentTimeMs()

    /**
     * 단일 NTP 서버에 쿼리하여 오프셋을 계산합니다.
     * RTT의 절반을 네트워크 지연 보정으로 적용합니다.
     */
    private fun queryNtpServer(server: String): Long? {
        val socket = DatagramSocket()
        socket.soTimeout = NTP_TIMEOUT_MS

        try {
            // NTP 요청 패킷 구성
            val buffer = ByteArray(NTP_PACKET_SIZE)
            buffer[0] = 0x1B  // LI=0, VN=3, Mode=3(Client)
            val address = InetAddress.getByName(server)
            val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)

            val t1 = System.currentTimeMillis()  // 요청 전송 시각
            socket.send(request)

            val response = DatagramPacket(ByteArray(NTP_PACKET_SIZE), NTP_PACKET_SIZE)
            socket.receive(response)
            val t4 = System.currentTimeMillis()  // 응답 수신 시각

            // NTP 패킷에서 서버 수신 시각(T2), 서버 전송 시각(T3) 파싱
            val bb = ByteBuffer.wrap(response.data)
            bb.position(32)  // T2: Receive Timestamp
            val t2 = readNtpTimestamp(bb)
            bb.position(40)  // T3: Transmit Timestamp
            val t3 = readNtpTimestamp(bb)

            // 클럭 오프셋 = ((T2 - T1) + (T3 - T4)) / 2
            return ((t2 - t1) + (t3 - t4)) / 2
        } finally {
            socket.close()
        }
    }

    private fun readNtpTimestamp(bb: ByteBuffer): Long {
        val seconds = bb.int.toLong() and 0xFFFFFFFFL
        val fraction = bb.int.toLong() and 0xFFFFFFFFL
        val milliseconds = (seconds - EPOCH_DELTA) * 1000 + fraction * 1000 / 0x100000000L
        return milliseconds
    }

    fun getOffsetMs(): Long = clockOffsetMs

    fun getLastSyncDescription(): String {
        if (!isSynced) return "미동기화"
        val diffSec = (System.currentTimeMillis() - lastSyncedAt) / 1000
        return "동기화됨 (${diffSec}초 전, 오프셋: ${clockOffsetMs}ms)"
    }
}
