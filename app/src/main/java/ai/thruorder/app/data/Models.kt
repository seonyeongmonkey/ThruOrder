package ai.thruorder.app.data

data class GeoPoint(val lat: Double, val lng: Double)

/** 시연용 목적지 */
data class DemoDestination(val name: String, val location: GeoPoint, val address: String)

data class Store(
    val id: String,
    val brand: String,
    val name: String,
    val address: String,
    val location: GeoPoint,
    /** 픽업 운영 버퍼(분): 완성 후 창구 전달까지 여유 */
    val pickupBufferMin: Int,
    /** 평균 차로 대기(분) */
    val laneWaitMin: Int,
    val phone: String = "",
    /** 매장 정보 출처: "카카오 로컬" 또는 "모의" */
    val source: String = "모의",
)

data class MenuItem(
    val id: String,
    val storeId: String,
    val category: String,
    val name: String,
    /** 주행 중 화면용 짧은 이름 */
    val shortName: String,
    val price: Int,
    /** 주문 후 완성까지 걸리는 시간(초) */
    val prepSec: Int,
    val station: String,
    /** 완성 후 품질 유지 시간(분) */
    val holdMin: Int,
    val soldOut: Boolean = false,
)

data class CartLine(val item: MenuItem, val qty: Int, val seat: String = "공용")

data class Order(
    val id: String,
    val code: String,
    val store: Store,
    val lines: List<CartLine>,
    val createdAtMs: Long,
) {
    val total: Int get() = lines.sumOf { it.item.price * it.qty }
}
