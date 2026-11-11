package ai.thruorder.app.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.thruorder.app.data.DemoDestination
import ai.thruorder.app.data.GeoPoint
import ai.thruorder.app.data.Store
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 내비 연결 상태 (화면 상단 배지·시연 패널 진단에 표시) */
enum class NaviStatus {
    /** 내비 서비스에 연결 중 */
    CONNECTING,
    /** 연결됐지만 NAVI_ROUTE / NAVI_ROUTE_SEARCH 권한이 없어 모든 호출이 거절됨 */
    NO_PERMISSION,
    /** 연결 + 권한 OK */
    CONNECTED,
    /** 내비 서비스가 없거나 연결 실패 (일반 에뮬레이터 등) → 모의 데이터 */
    UNAVAILABLE,
}

data class RouteDestination(
    val name: String,
    val location: GeoPoint,
    /** 목적지까지 남은 시간(초), 알 수 없으면 null */
    val remainSec: Int?,
)

/**
 * Pleos NaviHelper SDK 3.0.5 연동 (SDK 소스로 함수·콜백 이름 확인).
 *
 * 주의: SDK는 모든 호출 직전에 checkSelfPermission으로 권한을 확인하고, 없으면 SecurityException을 던진다.
 * → MainActivity가 실행 시 권한을 요청하고, 여기서는 예외를 잡아 [NaviStatus.NO_PERMISSION]으로 알린다.
 *
 * - 현재 위치: getCurrentLocationInfo → onCurrentLocationInfo
 * - 경로 안내 여부·목적지: getRouteStateInfo → onRouteStateInfo, getDestinationInfo → onDestinationInfo
 * - 매장 후보 도착 시간: requestTrip(경로 변경 없음) → onTripInfo(eta 초)
 * - 주문 매장: 경로 안내 중이면 addWaypoint, 아니면 requestRoute
 *   → onWaypointChanged / onWayPointInfo 로 내비에 실제로 들어갔는지 확인
 */
class NaviRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private var helper: NaviHelper? = null

    private val _status = MutableStateFlow(NaviStatus.CONNECTING)
    val status: StateFlow<NaviStatus> = _status.asStateFlow()

    /** 최근 오류 (진단용) */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _location = MutableStateFlow<GeoPoint?>(null)
    val location: StateFlow<GeoPoint?> = _location.asStateFlow()

    /** storeId → 도착 예정(초), requestTrip 결과 */
    private val _tripEtaSec = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tripEtaSec: StateFlow<Map<String, Int>> = _tripEtaSec.asStateFlow()

    /** 주문 매장까지 남은 시간(초) */
    private val _targetRemainSec = MutableStateFlow<Int?>(null)
    val targetRemainSec: StateFlow<Int?> = _targetRemainSec.asStateFlow()

    /** 주문 매장이 내비 경로(경유지 또는 목적지)에 실제로 들어갔는지 */
    private val _targetInNavi = MutableStateFlow(false)
    val targetInNavi: StateFlow<Boolean> = _targetInNavi.asStateFlow()

    private val _arrived = MutableStateFlow(false)
    val arrived: StateFlow<Boolean> = _arrived.asStateFlow()

    /** 운전자가 내비에 설정한 목적지 (경로 안내 중일 때만). 스루오더가 매장을 목적지로 잡은 경우는 제외 */
    private val _routeDestination = MutableStateFlow<RouteDestination?>(null)
    val routeDestination: StateFlow<RouteDestination?> = _routeDestination.asStateFlow()

    /** 진단용: 내비가 마지막으로 보낸 원본 값 (시연 패널에 표시) */
    private val _debug = MutableStateFlow("아직 받은 값 없음")
    val debug: StateFlow<String> = _debug.asStateFlow()
    @Volatile private var dbgRoute = "-"
    @Volatile private var dbgDest = "-"
    @Volatile private var dbgNav = "-"
    private fun updateDebug() {
        _debug.value = "경로상태 $dbgRoute · 주행상태 $dbgNav\n목적지 원본 $dbgDest"
    }

    @Volatile private var isRouting = false
    @Volatile private var routeStartedByApp = false
    @Volatile private var targetName: String? = null
    @Volatile private var targetIsWaypoint = false

    private val tripLock = Any()
    private val tripQueue = ArrayDeque<Store>()
    private var pendingTrip: Store? = null
    private var pollJob: Job? = null

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val lat = currentLocationInfo.latitude
            val lng = currentLocationInfo.longitude
            if (!(lat == 0.0 && lng == 0.0)) _location.value = GeoPoint(lat, lng)
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            isRouting = routeStateInfo.isRouting
            dbgRoute = "isRouting=${routeStateInfo.isRouting}"
            updateDebug()
            if (isRouting) {
                call { it.getDestinationInfo() }
                if (targetName != null) call { it.getWaypointInfo() }
            }
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            dbgNav = status.name
            updateDebug()
            if (status == NavigationStatus.ROUTE_DRIVE) {
                isRouting = true
                call { it.getDestinationInfo() }
            }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            isRouting = true
            call { it.getDestinationInfo() }
            call { it.getWaypointInfo() }
        }

        override fun onRouteCancelled() = routeEnded()
        override fun onRoutingCompleted() = routeEnded()

        override fun onDestinationChanged() {
            call { it.getDestinationInfo() }
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            call { it.getWaypointInfo() }
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            val store = synchronized(tripLock) { pendingTrip.also { pendingTrip = null } }
            if (store != null) _tripEtaSec.update { it + (store.id to tripInfo.eta) }
            nextTrip()
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            // 경로 안내 중에만 오는 이벤트 → 경로 상태 응답이 없어도 안내 중으로 본다
            if (!isRouting) {
                isRouting = true
                call { it.getDestinationInfo() }
            }
            val name = targetName ?: return
            val state = if (targetIsWaypoint) {
                drivingInfo.waypoints.firstOrNull { it.name == name }
            } else {
                drivingInfo.destination.takeIf { it.name == name }
            }
            state?.let { _targetRemainSec.value = it.duration }
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            val name = targetName ?: return
            if (!targetIsWaypoint) return
            val wp = waypointInfoList.firstOrNull { it.name == name }
            _targetInNavi.value = wp != null
            wp?.let { _targetRemainSec.value = it.remainTime }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val name = targetName
            if (name != null && !targetIsWaypoint) {
                // 스루오더가 매장을 목적지로 잡은 상태 → 매장까지 남은 시간
                if (destinationInfo.name == name) {
                    _targetInNavi.value = true
                    _targetRemainSec.value = destinationInfo.remainTime
                }
                return
            }
            val lat = destinationInfo.latitude
            val lng = destinationInfo.longitude
            dbgDest = "${destinationInfo.name.ifBlank { "(이름 없음)" }} " +
                "%.3f,%.3f".format(lat, lng) +
                " 남은 ${destinationInfo.remainDistance / 1000}km·${destinationInfo.remainTime / 60}분"
            updateDebug()
            // 목적지 좌표가 있고, 안내 중이거나 남은 거리가 있으면 '목적지 있음'으로 본다
            // (경로 상태 응답이 늦거나 오지 않아도 목적지를 놓치지 않게)
            val active = !(lat == 0.0 && lng == 0.0) &&
                (isRouting || destinationInfo.remainDistance > 0 || destinationInfo.remainTime > 0)
            _routeDestination.value = if (!active) {
                null
            } else {
                RouteDestination(
                    name = destinationInfo.name.ifBlank { "목적지" },
                    location = GeoPoint(lat, lng),
                    remainSec = destinationInfo.remainTime.takeIf { it > 0 },
                )
            }
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            if (targetIsWaypoint && waypointArrivedInfo.name == targetName) _arrived.value = true
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            if (!targetIsWaypoint && targetName != null) _arrived.value = true
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "NaviHelper error: $errorCode")
            _lastError.value = errorLabel(errorCode)
            val hadTrip = synchronized(tripLock) { (pendingTrip != null).also { pendingTrip = null } }
            if (hadTrip) nextTrip()
        }
    }

    fun hasPermissions(): Boolean = PERMISSIONS.all {
        appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        if (helper != null) return
        _status.value = NaviStatus.CONNECTING
        try {
            val h = NaviHelper(appContext)
            helper = h
            h.initialize(
                onConnected = {
                    h.addListener(listener)
                    _status.value = if (hasPermissions()) NaviStatus.CONNECTED else NaviStatus.NO_PERMISSION
                    refreshAll()
                    nextTrip()
                },
                onFailed = { t ->
                    Log.w(TAG, "NaviHelper 연결 실패 → 모의 데이터 사용", t)
                    _status.value = NaviStatus.UNAVAILABLE
                    _lastError.value = "내비 서비스 연결 실패: ${t.message ?: t.javaClass.simpleName}"
                },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper 초기화 불가 → 모의 데이터 사용", t)
            _status.value = NaviStatus.UNAVAILABLE
            _lastError.value = "내비 SDK 초기화 실패: ${t.message ?: t.javaClass.simpleName}"
        }

        // 백업 폴링 (주행 중 CPU 부담을 고려해 가볍게)
        // - 5초: 경로 안내 상태 (운전자가 내비에서 목적지를 새로 잡았는지)
        // - 15초: 위치·주문 매장까지 남은 시간
        pollJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(5_000)
                if (_status.value != NaviStatus.CONNECTED) continue
                tick++
                call { it.getRouteStateInfo() }
                if (targetName == null) call { it.getDestinationInfo() }
                if (tick % 3 == 0) {
                    call { it.getCurrentLocationInfo() }
                    if (targetName != null) {
                        call { if (targetIsWaypoint) it.getWaypointInfo() else it.getDestinationInfo() }
                    }
                }
            }
        }
    }

    /** 권한 요청 결과가 돌아왔을 때 */
    fun onPermissionsChanged() {
        if (helper == null) return
        if (_status.value == NaviStatus.UNAVAILABLE || _status.value == NaviStatus.CONNECTING) return
        _status.value = if (hasPermissions()) NaviStatus.CONNECTED else NaviStatus.NO_PERMISSION
        if (_status.value == NaviStatus.CONNECTED) {
            _lastError.value = null
            refreshAll()
            nextTrip()
        }
    }

    private fun refreshAll() {
        call { it.getCurrentLocationInfo() }
        call { it.getRouteStateInfo() }
        call { it.getDestinationInfo() }
    }

    fun stop() {
        pollJob?.cancel()
        runCatching {
            helper?.removeListener(listener)
            helper?.release()
        }
        helper = null
    }

    /** SDK 호출 공통 처리: 권한 없음(SecurityException)·기타 오류를 상태로 남긴다 */
    private fun call(block: (NaviHelper) -> Unit): Boolean {
        val h = helper ?: return false
        if (_status.value == NaviStatus.UNAVAILABLE || _status.value == NaviStatus.CONNECTING) return false
        return try {
            block(h)
            true
        } catch (e: SecurityException) {
            _status.value = NaviStatus.NO_PERMISSION
            _lastError.value = "권한 없음: ${e.message}"
            false
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper 호출 실패", t)
            _lastError.value = "호출 실패: ${t.message ?: t.javaClass.simpleName}"
            false
        }
    }

    private fun routeEnded() {
        isRouting = false
        routeStartedByApp = false
        _routeDestination.value = null
        if (targetName != null) _targetInNavi.value = false
    }

    /** 후보 매장별 도착 시간 요청. onTripInfo에 매장 구분자가 없어 한 번에 하나씩 순서대로 요청한다. */
    fun requestEtas(stores: List<Store>) {
        synchronized(tripLock) {
            tripQueue.clear()
            tripQueue.addAll(stores)
        }
        nextTrip()
    }

    private fun nextTrip() {
        if (_status.value != NaviStatus.CONNECTED) return
        val store = synchronized(tripLock) {
            if (pendingTrip != null) return
            tripQueue.removeFirstOrNull()?.also { pendingTrip = it }
        } ?: return
        val ok = call {
            it.requestTrip(
                TripRequest(
                    latitude = store.location.lat,
                    longitude = store.location.lng,
                    poiId = "",
                    poiName = store.name,
                    poiSubId = null,
                ),
            )
        }
        if (!ok) {
            synchronized(tripLock) { pendingTrip = null }
            return
        }
        // 응답이 없으면 5초 뒤 다음 매장으로
        scope.launch {
            delay(5_000)
            val timedOut = synchronized(tripLock) { (pendingTrip == store).also { if (it) pendingTrip = null } }
            if (timedOut) nextTrip()
        }
    }

    /** 주문 확정: 경로 안내 중이면 경유지로, 아니면 목적지로 매장을 설정한다. */
    fun setPickupTarget(store: Store) {
        targetName = store.name
        _targetRemainSec.value = null
        _targetInNavi.value = false
        _arrived.value = false
        val routing = isRouting || _routeDestination.value != null
        targetIsWaypoint = routing
        if (routing) {
            call {
                it.addWaypoint(
                    RequestWaypointInfo(
                        latitude = store.location.lat,
                        longitude = store.location.lng,
                        poiName = store.name,
                        waypointIndex = WaypointIndex.FIRST,
                        poiId = "",
                        address = store.address,
                        poiSubId = "",
                    ),
                )
            }
        } else {
            call {
                it.requestRoute(
                    RouteInfo(
                        latitude = store.location.lat,
                        longitude = store.location.lng,
                        poiName = store.name,
                        poiId = "",
                        address = store.address,
                        poiSubId = "",
                    ),
                )
            }
        }
        // 내비에 반영됐는지 2초 뒤 확인
        scope.launch {
            delay(2_000)
            call { if (targetIsWaypoint) it.getWaypointInfo() else it.getDestinationInfo() }
        }
    }

    /** 시연: 내비 목적지를 설정해 '경로 안내 중' 상태를 만든다 (실제 사용에서는 운전자가 내비에서 직접 설정) */
    fun startDemoRoute(dest: DemoDestination): Boolean {
        val ok = call {
            it.requestRoute(
                RouteInfo(
                    latitude = dest.location.lat,
                    longitude = dest.location.lng,
                    poiName = dest.name,
                    poiId = "",
                    address = dest.address,
                    poiSubId = "",
                ),
            )
        }
        if (ok) {
            routeStartedByApp = true
            scope.launch {
                delay(1_500)
                call { it.getRouteStateInfo() }
            }
        }
        return ok
    }

    /** 시연 목적지 해제: 스루오더가 시작한 경로만 취소한다 */
    fun cancelDemoRoute() {
        if (routeStartedByApp) call { it.cancelRoute() }
        routeEnded()
    }

    /** 수령 완료: 경유지 정리 */
    fun clearPickupTarget() {
        if (targetName != null) {
            call { if (targetIsWaypoint) it.removeWaypoint(WaypointIndex.FIRST) else it.cancelRoute() }
        }
        targetName = null
        _targetRemainSec.value = null
        _targetInNavi.value = false
        _arrived.value = false
    }

    private fun errorLabel(code: NaviErrorCode): String = when (code) {
        NaviErrorCode.CAUSE_SEARCH_ROUTE -> "경로 탐색 실패"
        NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> "경로가 너무 멀어요"
        NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> "경로가 너무 가까워요"
        NaviErrorCode.CAUSE_SEARCH_TRIP -> "도착 시간 조회 실패"
        NaviErrorCode.CAUSE_LOW_BATTERY -> "배터리 부족"
    }

    companion object {
        private const val TAG = "ThruOrder.Navi"
        const val PERM_ROUTE = "pleos.car.permission.NAVI_ROUTE"
        const val PERM_ROUTE_SEARCH = "pleos.car.permission.NAVI_ROUTE_SEARCH"
        val PERMISSIONS = arrayOf(PERM_ROUTE, PERM_ROUTE_SEARCH)
    }
}
