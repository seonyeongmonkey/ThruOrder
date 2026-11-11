package ai.thruorder.app.ui

import ai.thruorder.app.data.CartLine
import ai.thruorder.app.data.GeoPoint
import ai.thruorder.app.data.MenuItem
import ai.thruorder.app.data.MockData
import ai.thruorder.app.data.Order
import ai.thruorder.app.data.Store
import ai.thruorder.app.data.StoreRepository
import ai.thruorder.app.domain.FreshTiming
import ai.thruorder.app.domain.Geo
import ai.thruorder.app.drive.DrivingStateRepository
import ai.thruorder.app.navi.NaviRepository
import ai.thruorder.app.navi.NaviStatus
import ai.thruorder.app.navi.RouteDestination
import ai.thruorder.app.share.SharedCartRelay
import ai.thruorder.app.BuildConfig
import java.net.URLEncoder
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class CookStage { WAITING, COOKING, READY }

data class StoreEta(
    val store: Store,
    val etaSec: Int,
    val fromPleos: Boolean,
    val distanceKm: Double,
    /** 경로 안내 중일 때 이 매장을 들렀다 가면 늘어나는 시간(초). 경로가 없으면 null */
    val detourSec: Int? = null,
)

enum class StoreFilter(val label: String) { ALL("전체"), CAFE("카페"), BURGER("버거") }

/** 매장 추천 기준 */
enum class StoreMode {
    /** 내비 경로 없음 → 현재 위치 반경 15km */
    RADIUS,
    /** 내비 경로 안내 중 → 경로에서 조금만 벗어나는 DT */
    ROUTE,
    /** 경로 안내 중이지만 경로 주변 DT가 없어 반경 15km로 대신 보여줌 */
    ROUTE_FALLBACK,
}

data class OrderProgress(
    val order: Order,
    val arrivalMs: Long,
    val recommendedStartMs: Long,
    val cookStartedAtMs: Long?,
    val stage: CookStage,
    /** 직전 권장 시작 대비 변화(분), 도착 지연으로 시작이 늦춰지면 양수 */
    val shiftMin: Int,
    /** 조리 시작 후 도착이 늦어져 품질 유지 시간을 넘길 위험 */
    val delayWarning: Boolean,
    val etaFromPleos: Boolean,
)

data class UiState(
    val nowMs: Long = System.currentTimeMillis(),
    val naviStatus: NaviStatus = NaviStatus.CONNECTING,
    /** 최근 내비 오류 (시연 패널 진단용) */
    val naviError: String? = null,
    /** 주문 매장이 내비 경로(경유지/목적지)에 실제로 들어갔는지 */
    val pickupInNavi: Boolean = false,
    /** 권한 안내 카드를 닫았는지 */
    val permissionDismissed: Boolean = false,
    val storeFilter: StoreFilter = StoreFilter.ALL,
    /** 메뉴판에서 고른 카테고리 (null = 첫 카테고리) */
    val menuCategory: String? = null,
    val showCart: Boolean = false,
    /** 같이 고르기 QR 카드를 접었는지 (매장마다 처음엔 펼침) */
    val qrCollapsed: Boolean = false,
    /** 내비 원본 값 진단 (시연 패널) */
    val naviDebug: String = "",
    val showDemo: Boolean = false,
    val location: GeoPoint = MockData.DEMO_ORIGIN,
    val locationFromPleos: Boolean = false,
    /** 내비 위치 주변 15km에 DT 매장이 없거나 위치를 못 받아 시연 출발지(판교 제2테크노밸리)를 쓰는 중 */
    val usingDemoLocation: Boolean = false,
    val storeMode: StoreMode = StoreMode.RADIUS,
    /** 경로 안내 중인 목적지 이름 */
    val routeDestName: String? = null,
    /** 목적지 정보가 Pleos 내비에서 왔는지 (false = 시연 목적지) */
    val routeFromPleos: Boolean = false,
    /** 목적지 도착 예정 시각 (경유 전) */
    val routeArrivalMs: Long? = null,
    /** 경로 위 DT로 보여주는 도착 시간 상한(분) */
    val aheadMin: Int = 30,
    /** 시연 목적지 순번, -1 = 없음 */
    val demoDestIndex: Int = -1,
    val storeSourceNote: String = "",
    val searchingStores: Boolean = false,
    val driving: Boolean = false,
    val drivingSource: String = "",
    val demoDrivingOverride: Boolean? = null,
    val demoDelayMin: Int = 0,
    val storeEtas: List<StoreEta> = emptyList(),
    val selectedStore: Store? = null,
    val cart: List<CartLine> = emptyList(),
    val progress: OrderProgress? = null,
    /** 동승자 공동 장바구니 QR 주소 (매장 선택 시 생성) */
    val shareUrl: String? = null,
    val shareConnected: Boolean = false,
    /** 폰으로 참여한 동승자 별칭 */
    val participants: List<String> = emptyList(),
    val showQrLarge: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val navi = NaviRepository(app, viewModelScope)
    private val drive = DrivingStateRepository(app)
    private val storeRepo = StoreRepository()
    private val share = SharedCartRelay(viewModelScope)

    private var stores: List<Store> = MockData.stores
    private var storeNote = "판교 인근 DT ${MockData.stores.size}곳 · 메뉴는 모의 데이터"
    private var lastRouteKey: String? = null
    private var lastSearchCenter: GeoPoint? = null
    private var searching = false
    private var demoLocation = false
    private var lastSearchFromPleos = false

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var order: Order? = null
    private var orderMockEtaSec = 0
    private var cookStartedAtMs: Long? = null
    private var lastRecommendedStartMs: Long? = null
    private var shiftMin = 0

    init {
        navi.start()
        drive.start()

        // 동승자 폰에서 담은 메뉴를 장바구니에 합치기
        viewModelScope.launch {
            share.adds.collect { add -> applyRemoteAdd(add) }
        }

        // 1초 갱신: 시계·도착 카운트다운·조리 단계
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                refresh()
                if (++tick % 60 == 0) requestEtasForList() // 1분마다 도착 시간 갱신
                delay(1_000)
            }
        }
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val pleosLoc = navi.location.value
        maybeSearchStores(pleosLoc)
        val loc = if (demoLocation || pleosLoc == null) MockData.DEMO_ORIGIN else pleosLoc
        val naviOk = navi.status.value == NaviStatus.CONNECTED
        val trips = if (demoLocation || !naviOk) emptyMap() else navi.tripEtaSec.value
        val st = _state.value

        // 목적지: 운전자가 Pleos 내비에 설정한 목적지 > 시연 목적지 버튼
        val pleosDest = if (demoLocation) null else navi.routeDestination.value
        val demoDest = MockData.demoDestinations.getOrNull(st.demoDestIndex)
            ?.let { RouteDestination(it.name, it.location, null) }
        val route = pleosDest ?: demoDest

        fun etaOf(s: Store, detour: Int?): StoreEta {
            val pleosEta = trips[s.id]
            return StoreEta(
                store = s,
                etaSec = pleosEta ?: Geo.mockEtaSec(loc, s.location),
                fromPleos = pleosEta != null,
                distanceKm = Geo.distanceKm(loc, s.location),
                detourSec = detour,
            )
        }

        val radiusList = stores.filter { Geo.distanceKm(loc, it.location) <= RADIUS_KM }
            .map { etaOf(it, null) }
            .sortedBy { it.etaSec }

        var mode = StoreMode.RADIUS
        var etas = radiusList
        var aheadMin = MAX_AHEAD_MIN
        if (route != null) {
            // 경로 위 DT: 들렀다 가도 늘어나는 시간이 MAX_DETOUR_MIN 이하이고 (조금은 이탈 허용)
            // 앞으로 aheadMin 안에 도착하는 매장 (너무 먼 앞쪽 매장은 제외)
            // - 긴 길(목적지까지 SHORT_TRIP_MIN 초과): 30분 안
            // - 짧은 길: 목적지 근처 매장도 보이도록 목적지 도착 + 5분까지
            val tripSec = route.remainSec ?: Geo.mockEtaSec(loc, route.location)
            aheadMin = if (tripSec <= SHORT_TRIP_MIN * 60) tripSec / 60 + 5 else MAX_AHEAD_MIN
            val along = stores.map { s -> etaOf(s, Geo.detourSec(loc, s.location, route.location)) }
                .filter { (it.detourSec ?: Int.MAX_VALUE) <= MAX_DETOUR_MIN * 60 }
                .filter { it.etaSec <= aheadMin * 60 }
                .sortedWith(compareBy<StoreEta>({ (it.detourSec ?: 0) / 60 }, { it.etaSec }))
                .take(MAX_STORES)
            if (along.isNotEmpty()) {
                mode = StoreMode.ROUTE
                etas = along
            } else {
                mode = StoreMode.ROUTE_FALLBACK
            }
        }

        // 경로가 바뀌면 화면에 보일 매장들의 도착 시간을 바로 다시 요청
        val routeKey = route?.let { "${it.name}@${it.location}" }
        if (routeKey != lastRouteKey) {
            lastRouteKey = routeKey
            pendingEtaRequest = true
        }

        val routeArrival = route?.let { r -> now + 1000L * (r.remainSec ?: Geo.mockEtaSec(loc, r.location)) }

        val autoDriving = drive.uxRestricted.value || drive.gearNotPark.value
        val driving = st.demoDrivingOverride ?: autoDriving

        _state.value = st.copy(
            nowMs = now,
            naviStatus = navi.status.value,
            naviError = navi.lastError.value,
            naviDebug = navi.debug.value,
            pickupInNavi = navi.targetInNavi.value,
            location = loc,
            locationFromPleos = pleosLoc != null && !demoLocation,
            usingDemoLocation = demoLocation,
            storeSourceNote = storeNote,
            searchingStores = searching,
            driving = driving,
            drivingSource = if (st.demoDrivingOverride != null) "시연 스위치" else drive.sourceLabel.value,
            storeEtas = etas,
            storeMode = mode,
            routeDestName = route?.name,
            routeFromPleos = pleosDest != null,
            routeArrivalMs = routeArrival,
            aheadMin = aheadMin,
            progress = computeProgress(now, st.demoDelayMin),
            shareConnected = share.connected.value,
        )

        if (pendingEtaRequest) {
            pendingEtaRequest = false
            requestEtasForList()
        }
    }

    private var pendingEtaRequest = false

    /** 지금 화면에 보이는 매장(가까운 순) 몇 곳만 Pleos 도착 시간 요청 */
    private fun requestEtasForList() {
        if (demoLocation || order != null) return
        val shown = _state.value.storeEtas.map { it.store }
        navi.requestEtas((shown.ifEmpty { stores }).take(MAX_TRIP_REQUESTS))
    }

    private fun computeProgress(now: Long, delayMin: Int): OrderProgress? {
        val o = order ?: return null
        val pleosRemain = if (demoLocation) null else navi.targetRemainSec.value
        val baseArrival = if (pleosRemain != null) {
            now + pleosRemain * 1000L
        } else {
            o.createdAtMs + orderMockEtaSec * 1000L
        }
        val arrival = baseArrival + delayMin * 60_000L
        val rec = FreshTiming.recommendedStartMs(arrival, o.lines, o.store.pickupBufferMin)

        // 조리 전에는 권장 시작을 재조정, 조리가 시작되면 고정
        if (cookStartedAtMs == null) {
            val last = lastRecommendedStartMs
            if (last != null && kotlin.math.abs(rec - last) >= 30_000) {
                shiftMin = ((rec - last) / 60_000L).toInt()
            }
            lastRecommendedStartMs = rec
            if (now >= rec) cookStartedAtMs = now
        }
        val started = cookStartedAtMs
        val prepMs = FreshTiming.maxPrepSec(o.lines) * 1000L
        val stage = when {
            started == null -> CookStage.WAITING
            now < started + prepMs -> CookStage.COOKING
            else -> CookStage.READY
        }
        val minHoldMs = (o.lines.minOfOrNull { it.item.holdMin } ?: 10) * 60_000L
        val warning = started != null && arrival > started + prepMs + minHoldMs

        return OrderProgress(
            order = o,
            arrivalMs = arrival,
            recommendedStartMs = started ?: rec,
            cookStartedAtMs = started,
            stage = stage,
            shiftMin = shiftMin,
            delayWarning = warning,
            etaFromPleos = pleosRemain != null,
        )
    }

    /**
     * 내비 위치 기준 반경 15km 안의 DT 매장 검색.
     * - 위치가 1km 이상 바뀌면 다시 검색 (주문 진행 중에는 목록 유지)
     * - 주변에 DT 매장이 없으면(예: 에뮬레이터 위치가 해외) 시연 출발지(판교 제2테크노밸리)를 사용
     * - 카카오 키가 없거나 네트워크 실패 시 모의 매장 사용
     */
    private fun maybeSearchStores(pleosLoc: GeoPoint?) {
        if (searching || order != null) return
        val center = pleosLoc ?: MockData.DEMO_ORIGIN
        val last = lastSearchCenter
        // Pleos 위치를 처음 받았을 때는 거리와 상관없이 다시 검색
        val firstPleosFix = pleosLoc != null && !lastSearchFromPleos
        if (last != null && !firstPleosFix && Geo.distanceKm(last, center) < 1.0) return
        searching = true
        lastSearchCenter = center
        lastSearchFromPleos = pleosLoc != null
        viewModelScope.launch {
            val hq = MockData.DEMO_ORIGIN
            var demo = pleosLoc == null
            var found = storeRepo.searchNearbyDt(center)
            if (found != null && found.isEmpty() && Geo.distanceKm(center, hq) > 1.0) {
                found = storeRepo.searchNearbyDt(hq)
                demo = true
            }
            when {
                found == null -> {
                    stores = MockData.stores
                    demo = demo || Geo.distanceKm(center, hq) > RADIUS_KM
                    storeNote = if (!storeRepo.hasKey) "판교 인근 DT ${stores.size}곳 · 메뉴는 모의 데이터" else "매장 검색 실패 · 판교 인근 DT ${stores.size}곳"
                }
                found.isEmpty() -> {
                    stores = MockData.stores
                    demo = true
                    storeNote = "주변 15km에 DT 매장 없음 · 판교 인근 DT ${stores.size}곳"
                }
                else -> {
                    stores = found.take(MAX_STORES)
                    storeNote = "매장 정보: 카카오 로컬 · 메뉴: 모의 데이터"
                }
            }
            demoLocation = demo
            searching = false
            pendingEtaRequest = true
        }
    }

    // ---------- 사용자 동작 (정차 중) ----------

    fun selectStore(store: Store) {
        // 매장마다 새 공동 장바구니 세션을 열고 QR 주소를 만든다
        val sessionId = randomSessionId()
        share.start(sessionId)
        val url = BuildConfig.SHARE_BASE_URL +
            "?s=" + sessionId +
            "&b=" + URLEncoder.encode(store.brand, "UTF-8") +
            "&sn=" + URLEncoder.encode(store.name, "UTF-8")
        _state.update {
            it.copy(
                selectedStore = store, cart = emptyList(), shareUrl = url, participants = emptyList(),
                showQrLarge = false, menuCategory = null, showCart = false, qrCollapsed = false,
            )
        }
    }

    fun backToStores() {
        share.stop()
        _state.update {
            it.copy(selectedStore = null, cart = emptyList(), shareUrl = null, participants = emptyList(), showQrLarge = false, showCart = false)
        }
    }

    fun setStoreFilter(f: StoreFilter) = _state.update { it.copy(storeFilter = f) }
    fun setMenuCategory(c: String) = _state.update { it.copy(menuCategory = c) }
    fun setShowCart(show: Boolean) = _state.update { it.copy(showCart = show) }
    fun setQrCollapsed(collapsed: Boolean) = _state.update { it.copy(qrCollapsed = collapsed) }
    fun toggleDemo() = _state.update { it.copy(showDemo = !it.showDemo) }
    fun dismissPermission() = _state.update { it.copy(permissionDismissed = true) }

    /** MainActivity 권한 요청 결과 */
    fun onPermissionResult() {
        navi.onPermissionsChanged()
        lastRouteKey = null
        refresh()
    }

    fun setQrLarge(show: Boolean) {
        _state.update { it.copy(showQrLarge = show) }
    }

    /** 차량 화면에서 담기 */
    fun addToCart(item: MenuItem) {
        if (item.soldOut) return
        updateCart { cart -> addLine(cart, item, 1, SEAT_CAR) }
    }

    /** 차량 화면에서 빼기: 차량 화면에서 담은 것부터, 없으면 해당 메뉴의 마지막 줄에서 */
    fun removeFromCart(item: MenuItem) {
        updateCart { cart ->
            val target = cart.lastOrNull { it.item.id == item.id && it.seat == SEAT_CAR }
                ?: cart.lastOrNull { it.item.id == item.id }
                ?: return@updateCart cart
            cart.mapNotNull { if (it !== target) it else if (it.qty > 1) it.copy(qty = it.qty - 1) else null }
        }
    }

    private fun applyRemoteAdd(add: SharedCartRelay.RemoteAdd) {
        val store = _state.value.selectedStore ?: return
        if (order != null) return
        val item = MockData.menuFor(store).find { it.id == add.itemId } ?: return
        if (item.soldOut) return
        _state.update { st ->
            if (add.who in st.participants) st else st.copy(participants = st.participants + add.who)
        }
        updateCart { cart -> addLine(cart, item, add.qty, add.who) }
    }

    private fun addLine(cart: List<CartLine>, item: MenuItem, qty: Int, seat: String): List<CartLine> {
        val existing = cart.find { it.item.id == item.id && it.seat == seat }
        return if (existing != null) {
            cart.map { if (it === existing) it.copy(qty = it.qty + qty) else it }
        } else {
            cart + CartLine(item, qty, seat)
        }
    }

    private fun updateCart(transform: (List<CartLine>) -> List<CartLine>) {
        _state.update { it.copy(cart = transform(it.cart)) }
        share.publishCart(_state.value.cart)
    }

    private fun randomSessionId(): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        return (1..10).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    fun placeOrder() {
        val st = _state.value
        val store = st.selectedStore ?: return
        if (st.cart.isEmpty()) return
        val now = System.currentTimeMillis()
        order = Order(
            id = "ORD-" + now.toString().takeLast(6),
            code = Random.nextInt(1000, 9999).toString(),
            store = store,
            lines = st.cart,
            createdAtMs = now,
        )
        orderMockEtaSec = st.storeEtas.find { it.store.id == store.id }?.etaSec ?: 600
        cookStartedAtMs = null
        lastRecommendedStartMs = null
        shiftMin = 0
        navi.setPickupTarget(store)
        _state.update { it.copy(cart = emptyList(), demoDelayMin = 0, showQrLarge = false, showCart = false) }
        refresh()
        // 동승자 폰에도 주문 완료를 알리고 공동 장바구니 세션을 닫는다
        _state.value.progress?.let { share.publishOrdered(order!!.code, it.arrivalMs.hhmm()) }
        viewModelScope.launch {
            delay(2_000)
            share.stop()
            _state.update { it.copy(shareUrl = null) }
        }
    }

    fun completePickup() {
        order = null
        cookStartedAtMs = null
        navi.clearPickupTarget()
        _state.update { it.copy(selectedStore = null, progress = null, demoDelayMin = 0, participants = emptyList()) }
    }

    // ---------- 시연 컨트롤 ----------

    /** null = 차량 상태 자동, true = 주행, false = 정차 */
    fun setDemoDriving(value: Boolean?) {
        _state.update { it.copy(demoDrivingOverride = value) }
        refresh()
    }

    /**
     * 시연 목적지 순환: 없음 → 잠실 → 광교 → 강남역 → 없음.
     * Pleos 내비가 연결되어 있으면 실제 내비에도 목적지를 설정한다 (내비 화면에 경로가 뜸).
     */
    fun cycleDemoDestination() {
        if (order != null) return
        val next = _state.value.demoDestIndex + 1
        val idx = if (next >= MockData.demoDestinations.size) -1 else next
        if (idx >= 0) navi.startDemoRoute(MockData.demoDestinations[idx]) else navi.cancelDemoRoute()
        _state.update { it.copy(demoDestIndex = idx) }
        refresh()
    }

    fun addDemoDelay(min: Int) {
        _state.update { it.copy(demoDelayMin = it.demoDelayMin + min) }
        refresh()
    }

    companion object {
        /** 차량 화면에서 담은 메뉴의 표시 이름 */
        const val SEAT_CAR = "차량 화면"
        private const val RADIUS_KM = 15.0
        /** 경로 위 DT로 인정하는 최대 추가 시간(분) */
        const val MAX_DETOUR_MIN = 8
        /** 경로 위 DT로 보여줄 최대 도착 시간(분): 지금부터 이 시간 안에 닿는 매장만 */
        const val MAX_AHEAD_MIN = 30
        /** 목적지까지 이 시간 이하인 짧은 길이면 길 전체를 본다 */
        private const val SHORT_TRIP_MIN = 45
        private const val MAX_STORES = 10
        /** requestTrip은 한 번에 하나씩 순서대로 처리되므로 가까운 매장 몇 곳만 요청 */
        private const val MAX_TRIP_REQUESTS = 6
    }

    override fun onCleared() {
        navi.stop()
        drive.stop()
        share.stop()
        super.onCleared()
    }
}
