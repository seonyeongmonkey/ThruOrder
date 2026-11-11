package ai.thruorder.app.domain

import ai.thruorder.app.data.CartLine
import ai.thruorder.app.data.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fresh Timing: 도착 예정 시각에서 준비 시간과 운영 버퍼를 역산해 조리 시작 시점을 권고한다.
 * 권장 조리 시작 = 도착 예정 − (주문 내 최대 준비시간 + 픽업 운영 버퍼)
 */
object FreshTiming {

    fun maxPrepSec(lines: List<CartLine>): Int = lines.maxOfOrNull { it.item.prepSec } ?: 0

    fun recommendedStartMs(arrivalMs: Long, lines: List<CartLine>, bufferMin: Int): Long =
        arrivalMs - maxPrepSec(lines) * 1000L - bufferMin * 60_000L

    /** 메뉴별 권장 시작: 준비가 짧은 메뉴는 늦게 시작해 도착 순간 함께 완성 */
    fun itemStartMs(arrivalMs: Long, line: CartLine, bufferMin: Int): Long =
        arrivalMs - line.item.prepSec * 1000L - bufferMin * 60_000L
}

object Geo {
    /** 두 지점 사이 직선 거리(km) */
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** Pleos 응답이 없을 때 쓰는 모의 도착 시간(초): 도로 계수 1.4, 도심 평균 22km/h */
    fun mockEtaSec(from: GeoPoint, to: GeoPoint): Int {
        val roadKm = distanceKm(from, to) * 1.4
        return ((roadKm / 22.0) * 3600).toInt().coerceAtLeast(60)
    }

    /**
     * 경로 이탈 추정(초): 지금 위치(a) → 매장(p) → 목적지(b)로 돌아갈 때 늘어나는 시간.
     * NaviHelper가 경로 좌표(폴리라인)를 주지 않아 직선 거리 기반으로 추정한다 (도로 계수 1.4, 25km/h).
     */
    fun detourSec(a: GeoPoint, p: GeoPoint, b: GeoPoint): Int {
        val extraKm = (distanceKm(a, p) + distanceKm(p, b) - distanceKm(a, b)).coerceAtLeast(0.0)
        return ((extraKm * 1.4 / 25.0) * 3600).toInt()
    }
}
