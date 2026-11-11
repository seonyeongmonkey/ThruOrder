package ai.thruorder.app.ui

import ai.thruorder.app.R
import ai.thruorder.app.data.CartLine
import ai.thruorder.app.data.MenuItem
import ai.thruorder.app.data.MockData
import ai.thruorder.app.data.Store
import ai.thruorder.app.navi.NaviStatus
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/*
 * 화면 원칙 (Pleos Connect 가이드가 대전제, 배달의민족·쿠팡플레이의 정리 방식 참고)
 * - 한 화면에 한 가지 일: 매장 고르기 → 메뉴 담기 → 주문 상태
 * - 설명 문장 대신 배지·아이콘·숫자. 한 카드에 텍스트 3줄 이하
 * - 큰 썸네일 카드 목록, 가로 칩 필터, 하단 고정 장바구니 바(배민), 어두운 바탕 + 큰 제목(쿠팡플레이)
 * - 브랜드 컬러는 하나(Brand). 빨강은 품절·지연 경고에만
 * - 주행 중에는 3줄 이하 한 화면, 버튼 없음
 */

@Composable
fun AppRoot(vm: AppViewModel, onRequestPermission: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, onNaviClick = onRequestPermission, onDemoClick = vm::toggleDemo)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.driving -> DrivingView(state)
                    state.progress != null -> OrderStatusView(state, onPickedUp = vm::completePickup)
                    else -> OrderingView(state, vm)
                }
            }
        }
        if (state.showDemo) {
            DemoPanel(state, vm, Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 24.dp))
        }
        if (state.naviStatus == NaviStatus.NO_PERMISSION && !state.permissionDismissed && !state.driving) {
            PermissionCard(onAllow = onRequestPermission, onLater = vm::dismissPermission)
        }
        val url = state.shareUrl
        if (state.showQrLarge && url != null && !state.driving) {
            QrOverlay(url, state, onClose = { vm.setQrLarge(false) })
        }
    }
}

// ---------------- 상단 바 ----------------

@Composable
private fun TopBar(state: UiState, onNaviClick: () -> Unit, onDemoClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(40.dp)
        Spacer(Modifier.width(12.dp))
        Text("스루오더", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        NaviChip(state.naviStatus, onClick = onNaviClick)
        if (state.driving) {
            Spacer(Modifier.width(10.dp))
            Pill("주행 중", filled = true)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "시연",
            style = MaterialTheme.typography.labelMedium,
            color = if (state.showDemo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDemoClick).padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** 앱 아이콘과 같은 모양의 작은 마크 */
@Composable
private fun AppMark(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Brand),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.6f))
    }
}

@Composable
private fun NaviChip(status: NaviStatus, onClick: () -> Unit) {
    val (label, dot) = when (status) {
        NaviStatus.CONNECTED -> "내비 연결" to Brand
        NaviStatus.NO_PERMISSION -> "내비 권한 필요" to Danger
        NaviStatus.CONNECTING -> "내비 연결 중" to Gray.g400
        NaviStatus.UNAVAILABLE -> "모의 모드" to Gray.g400
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = status == NaviStatus.NO_PERMISSION, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Pill(text: String, filled: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Brand else MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 작은 정보 배지 (배민 '배달 시간' 배지처럼 숫자 위주) */
@Composable
private fun Badge(text: String, strong: Boolean = false, danger: Boolean = false) {
    val bg = when {
        danger -> Danger.copy(alpha = 0.15f)
        strong -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        danger -> Danger
        strong -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 사진 대신 쓰는 썸네일 타일 (브랜드 로고·실제 사진은 쓰지 않음) */
@Composable
private fun Thumb(emoji: String, size: Dp, dim: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.48f).sp, color = if (dim) Color.Gray else Color.Unspecified)
    }
}

// ---------------- 정차 중: 매장 → 메뉴 ----------------

@Composable
private fun OrderingView(state: UiState, vm: AppViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1000.dp // landscape(두 윈도우 확장): 매장 목록과 메뉴판을 나란히
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                StoreHome(state, vm, Modifier.weight(0.42f).fillMaxHeight())
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    if (state.selectedStore != null) MenuScreen(state, vm, showBack = false)
                    else EmptyHint("🛻", "매장을 고르면 메뉴판이 열려요")
                }
            }
        } else {
            if (state.selectedStore == null) StoreHome(state, vm, Modifier.fillMaxSize())
            else MenuScreen(state, vm, showBack = true)
        }
    }
}

@Composable
private fun StoreHome(state: UiState, vm: AppViewModel, modifier: Modifier) {
    val filtered = state.storeEtas.filter { e ->
        when (state.storeFilter) {
            StoreFilter.ALL -> true
            StoreFilter.CAFE -> e.store.category() == StoreFilter.CAFE
            StoreFilter.BURGER -> e.store.category() == StoreFilter.BURGER
        }
    }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TripCard(state) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(StoreFilter.entries.toList()) { f ->
                    val count = state.storeEtas.count { f == StoreFilter.ALL || it.store.category() == f }
                    FilterChip("${f.label} $count", selected = state.storeFilter == f) { vm.setStoreFilter(f) }
                }
            }
        }
        if (state.searchingStores) {
            item { EmptyHint("🔍", "가까운 드라이브스루를 찾고 있어요", Modifier.height(240.dp)) }
        } else if (filtered.isEmpty()) {
            item { EmptyHint("🛣️", "15km 안에 이용할 수 있는 매장이 없어요", Modifier.height(240.dp)) }
        }
        items(filtered, key = { it.store.id }) { e ->
            StoreCard(e, selected = state.selectedStore?.id == e.store.id) { vm.selectStore(e.store) }
        }
        item {
            Text(
                "매장명은 시연용 예시이며 해당 브랜드와 제휴·승인 관계가 없습니다",
                style = MaterialTheme.typography.labelSmall,
                color = Gray.g500,
            )
        }
    }
}

/** 상단 여정 카드: 목적지가 있으면 '가는 길', 없으면 '주변 15km' */
@Composable
private fun TripCard(state: UiState) {
    val dest = state.routeDestName
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp)) {
            if (dest != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("목적지", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dest, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    state.routeArrivalMs?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("도착", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it.hhmm(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                val dtLabel = if (state.storeMode == StoreMode.ROUTE) "가는 길 DT ${state.storeEtas.size}곳" else "가는 길 DT 없음"
                RouteStrip(start = "지금", middle = dtLabel, end = dest, middleActive = state.storeMode == StoreMode.ROUTE)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.storeMode == StoreMode.ROUTE) {
                        "${state.aheadMin}분 안에 닿고, 들러도 ${AppViewModel.MAX_DETOUR_MIN}분 이내"
                    } else {
                        "${state.aheadMin}분 안에 들를 곳이 없어 주변 15km를 보여드려요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("주변 드라이브스루", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.locationFromPleos) "현재 위치에서 15km" else "판교 제2테크노밸리에서 15km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("목적지를 정하면 가는 길 DT만 골라드려요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** 지금 ── 매장 ── 목적지 (배민 배달 현황 바처럼 단순한 선 하나) */
@Composable
private fun RouteStrip(start: String, middle: String, end: String, middleActive: Boolean, middleDone: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StripDot(filled = true)
        Spacer(Modifier.width(8.dp))
        Text(start, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StripLine(Modifier.weight(1f))
        Text(
            middle,
            style = MaterialTheme.typography.labelMedium,
            color = if (middleActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (middleActive) Brand else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        StripLine(Modifier.weight(1f))
        Text(end, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        Spacer(Modifier.width(8.dp))
        StripDot(filled = middleDone)
    }
}

@Composable
private fun StripDot(filled: Boolean) {
    Box(
        Modifier.size(16.dp).clip(CircleShape)
            .background(if (filled) Brand else Color.Transparent)
            .border(3.dp, Brand, CircleShape),
    )
}

@Composable
private fun StripLine(modifier: Modifier) {
    Box(modifier.padding(horizontal = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Brand else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun StoreCard(e: StoreEta, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(e.store.emoji(), 104.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(e.store.brand, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(e.store.shortName(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.detourSec?.let { d -> Badge(if (d < 60) "경로 위" else "+${d / 60}분", strong = true) }
                    Badge("${e.etaSec.minutesLabel()} 후", strong = e.detourSec == null)
                    Badge("대기 ${e.store.laneWaitMin}분")
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun MenuScreen(state: UiState, vm: AppViewModel, showBack: Boolean) {
    val store = state.selectedStore ?: return
    val menu = MockData.menuFor(store)
    val categories = menu.map { it.category }.distinct()
    val category = state.menuCategory?.takeIf { it in categories } ?: categories.firstOrNull()
    val eta = state.storeEtas.find { it.store.id == store.id }

    Column(Modifier.fillMaxSize()) {
        // 헤더: 뒤로 · 매장명 · 같이 고르기
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).clickable(onClick = vm::backToStores),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "매장 목록", modifier = Modifier.size(40.dp))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(store.shortName(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(store.brand, eta?.let { "${it.etaSec.minutesLabel()} 후 도착" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 같이 고르기 (핵심 기능): 매장을 고르자마자 QR을 크게 보여주고, 원하면 접는다
        state.shareUrl?.let { url ->
            TogetherCard(
                url = url,
                participants = state.participants,
                connected = state.shareConnected,
                collapsed = state.qrCollapsed,
                onEnlarge = { vm.setQrLarge(true) },
                onCollapse = { vm.setQrCollapsed(it) },
            )
        }

        // 카테고리 탭
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            items(categories) { c -> FilterChip(c, selected = c == category) { vm.setMenuCategory(c) } }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
            val list = menu.filter { it.category == category }
            items(list, key = { it.id }) { item ->
                MenuRow(
                    item,
                    qty = state.cart.filter { it.item.id == item.id }.sumOf { it.qty },
                    onAdd = { vm.addToCart(item) },
                    onRemove = { vm.removeFromCart(item) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        if (state.cart.isNotEmpty()) {
            CartBar(state.cart, expanded = state.showCart, onToggle = { vm.setShowCart(!state.showCart) }, onOrder = vm::placeOrder)
        }
    }
}

@Composable
private fun TogetherCard(
    url: String,
    participants: List<String>,
    connected: Boolean,
    collapsed: Boolean,
    onEnlarge: () -> Unit,
    onCollapse: (Boolean) -> Unit,
) {
    val onBrandSoft = MaterialTheme.colorScheme.onPrimaryContainer
    if (collapsed) {
        Row(
            Modifier
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onCollapse(false) }
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = onBrandSoft, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text("같이 고르기", style = MaterialTheme.typography.titleSmall, color = onBrandSoft)
            if (participants.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Badge("${participants.size}명 참여", strong = true)
            }
            Spacer(Modifier.weight(1f))
            Text("QR 보기", style = MaterialTheme.typography.labelLarge, color = onBrandSoft)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = onBrandSoft, modifier = Modifier.size(40.dp))
        }
        return
    }
    val qr = remember(url) { qrImage(url) }
    Row(
        Modifier
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = qr,
            contentDescription = "같이 고르기 QR",
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .clickable(onClick = onEnlarge)
                .padding(14.dp),
        )
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text("같이 고르기", style = MaterialTheme.typography.labelLarge, color = Brand)
            Text("뒷좌석에서 폰으로 찍어 주세요", style = MaterialTheme.typography.titleSmall, color = onBrandSoft)
            Spacer(Modifier.height(16.dp))
            if (participants.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (connected) Brand else Gray.g400))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (connected) "동승자를 기다리는 중" else "연결 중…",
                        style = MaterialTheme.typography.bodySmall,
                        color = onBrandSoft,
                    )
                }
            } else {
                participants.forEach { who ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Brand, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(who, style = MaterialTheme.typography.bodyMedium, color = onBrandSoft)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "크게 보기",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand)
                        .clickable(onClick = onEnlarge)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "접기",
                    style = MaterialTheme.typography.labelLarge,
                    color = onBrandSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onCollapse(true) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, qty: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(item.emoji(), 96.dp, dim = item.soldOut)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.soldOut) Gray.g500 else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.price.won(), style = MaterialTheme.typography.labelLarge, color = if (item.soldOut) Gray.g500 else MaterialTheme.colorScheme.onSurface)
                if (item.soldOut) Badge("품절", danger = true) else Badge("${item.prepSec.minutesLabel()} 조리")
            }
        }
        if (!item.soldOut) Stepper(qty, onAdd, onRemove)
    }
}

/** 0개일 때는 + 하나, 담으면 − 수량 + (배민 메뉴 담기 방식) */
@Composable
private fun Stepper(qty: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    if (qty == 0) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Brand).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "담기", tint = Color.White, modifier = Modifier.size(40.dp))
        }
    } else {
        Row(
            Modifier.clip(RoundedCornerShape(50)).border(2.dp, Brand, RoundedCornerShape(50)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(72.dp).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Text("−", style = MaterialTheme.typography.titleMedium, color = Brand)
            }
            Text("$qty", style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center)
            Box(Modifier.size(72.dp).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "하나 더", tint = Brand, modifier = Modifier.size(40.dp))
            }
        }
    }
}

/** 하단 고정 장바구니 바. 위쪽을 누르면 누가 무엇을 담았는지 펼쳐짐 */
@Composable
private fun CartBar(cart: List<CartLine>, expanded: Boolean, onToggle: () -> Unit, onOrder: () -> Unit) {
    val total = cart.sumOf { it.item.price * it.qty }
    val count = cart.sumOf { it.qty }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (expanded) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    cart.groupBy { it.seat }.forEach { (seat, lines) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Badge(seat)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                lines.joinToString("\n") { "${it.item.shortName} ×${it.qty}" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(
            Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(28.dp)).background(Brand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(onClick = onToggle).padding(start = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Text("$count", style = MaterialTheme.typography.labelLarge, color = Brand)
                }
                Spacer(Modifier.width(14.dp))
                Text(total.won(), style = MaterialTheme.typography.titleSmall, color = Color.White)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = "담은 메뉴 보기",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            Box(
                Modifier.padding(12.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .clickable(onClick = onOrder).padding(horizontal = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("주문하기", style = MaterialTheme.typography.titleSmall, color = Brand)
            }
        }
    }
}

// ---------------- 주문 상태 (배민 배달 현황 방식) ----------------

@Composable
private fun OrderStatusView(state: UiState, onPickedUp: () -> Unit) {
    val p = state.progress ?: return
    val o = p.order
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(o.store.shortName(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${p.arrivalMs.hhmm()} 도착", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(28.dp))
                    StageStepper(p.stage)
                    Spacer(Modifier.height(20.dp))
                    Text(stageCaption(p), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoTile("주문코드", o.code, highlight = true, modifier = Modifier.weight(1f))
                InfoTile(if (p.cookStartedAtMs == null) "조리 시작" else "조리 시작함", p.recommendedStartMs.hhmm(), modifier = Modifier.weight(1f))
            }
        }
        item { NaviLine(state) }
        if (p.delayWarning) {
            item { NoticeLine(Icons.Filled.Warning, "도착이 늦어져 매장에 알렸어요", danger = true) }
        } else if (p.cookStartedAtMs == null && p.shiftMin != 0) {
            item {
                NoticeLine(
                    Icons.Filled.Info,
                    if (p.shiftMin > 0) "도착이 늦어져 조리를 ${p.shiftMin}분 늦췄어요" else "도착이 빨라져 조리를 ${-p.shiftMin}분 당겼어요",
                )
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    o.lines.groupBy { it.seat }.forEach { (seat, lines) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Badge(seat)
                            Spacer(Modifier.width(14.dp))
                            Text(lines.joinToString("\n") { "${it.item.shortName} ×${it.qty}" }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row {
                        Text("합계", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(o.total.won(), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(28.dp)).background(Brand).clickable(onClick = onPickedUp),
                contentAlignment = Alignment.Center,
            ) {
                Text("받았어요", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}

private val STAGES = listOf("주문", "조리 대기", "조리 중", "완성")

private fun stageIndex(stage: CookStage) = when (stage) {
    CookStage.WAITING -> 1
    CookStage.COOKING -> 2
    CookStage.READY -> 3
}

@Composable
private fun StageStepper(stage: CookStage) {
    val current = stageIndex(stage)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        STAGES.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(4.dp).background(if (i == 0) Color.Transparent else if (i <= current) Brand else MaterialTheme.colorScheme.surfaceVariant))
                    Box(
                        Modifier.size(if (i == current) 40.dp else 28.dp).clip(CircleShape)
                            .background(if (i <= current) Brand else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i < current) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.weight(1f).height(4.dp).background(if (i == STAGES.lastIndex) Color.Transparent else if (i < current) Brand else MaterialTheme.colorScheme.surfaceVariant))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun stageCaption(p: OrderProgress): String = when (p.stage) {
    CookStage.WAITING -> "도착에 맞춰 ${p.recommendedStartMs.hhmm()}에 조리를 시작해요"
    CookStage.COOKING -> "도착 시간에 맞춰 만들고 있어요"
    CookStage.READY -> "픽업 창구에서 기다리고 있어요"
}

private fun stageShort(p: OrderProgress): String = when (p.stage) {
    CookStage.WAITING -> "${p.recommendedStartMs.hhmm()} 조리 시작"
    CookStage.COOKING -> "조리 중"
    CookStage.READY -> "완성"
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.displaySmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 주문 매장이 내비에 들어갔는지 한 줄로 */
@Composable
private fun NaviLine(state: UiState) {
    when {
        state.pickupInNavi -> NoticeLine(Icons.Filled.Check, "내비 경로에 매장을 넣었어요")
        state.naviStatus == NaviStatus.CONNECTED -> NoticeLine(Icons.Filled.LocationOn, "내비에 매장을 넣는 중…")
        state.naviStatus == NaviStatus.NO_PERMISSION -> NoticeLine(Icons.Filled.Warning, "내비 권한이 없어 모의 도착 시간이에요", danger = true)
        else -> NoticeLine(Icons.Filled.Info, "내비 미연결 · 모의 도착 시간")
    }
}

@Composable
private fun NoticeLine(icon: ImageVector, text: String, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Danger else Brand, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------- 주행 중: 3줄 이하, 버튼 없음 ----------------

@Composable
private fun DrivingView(state: UiState) {
    val p = state.progress
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (p == null) {
            Text("정차하면 주문할 수 있어요", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (state.storeMode == StoreMode.ROUTE && state.storeEtas.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Pill("가는 길 DT ${state.storeEtas.size}곳", filled = false)
            }
        } else {
            Text(p.order.store.shortName(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${p.arrivalMs.hhmm()} 도착", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Pill(stageShort(p), filled = p.stage != CookStage.WAITING)
        }
    }
}

// ---------------- 오버레이: 권한 안내 · QR · 시연 패널 ----------------

/** Pleos 가이드: 위치 등 권한을 요청할 때 왜 필요한지 먼저 설명 */
@Composable
private fun PermissionCard(onAllow: () -> Unit, onLater: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 720.dp).padding(24.dp)) {
            Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Brand, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
                Text("내비와 연결할게요", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "가는 길 DT 찾기 · 도착 시간 · 경유지 추가에만 써요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(24.dp)).background(Brand).clickable(onClick = onAllow),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("허용하기", style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "모의 데이터로 둘러보기",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onLater).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun QrOverlay(url: String, state: UiState, onClose: () -> Unit) {
    val qr = remember(url) { qrImage(url) }
    Box(
        Modifier.fillMaxSize().background(Color(0xF0131417)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("뒷좌석에서 폰으로 찍어 주세요", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(24.dp))
            Image(
                bitmap = qr,
                contentDescription = "같이 고르기 QR",
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(520.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(24.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                if (state.participants.isEmpty()) "폰에서 담은 메뉴가 바로 장바구니에 들어와요" else "함께 고르는 중 · " + state.participants.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = Gray.g300,
            )
            Text("화면을 누르면 닫혀요", style = MaterialTheme.typography.labelMedium, color = Gray.g500)
        }
    }
}

@Composable
private fun DemoPanel(state: UiState, vm: AppViewModel, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp, modifier = modifier.widthIn(max = 640.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("시연 컨트롤", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip("자동", state.demoDrivingOverride == null) { vm.setDemoDriving(null) }
                FilterChip("주행", state.demoDrivingOverride == true) { vm.setDemoDriving(true) }
                FilterChip("정차", state.demoDrivingOverride == false) { vm.setDemoDriving(false) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    MockData.demoDestinations.getOrNull(state.demoDestIndex)?.let { "목적지: ${it.name}" } ?: "목적지 없음",
                    state.demoDestIndex >= 0,
                ) { vm.cycleDemoDestination() }
                FilterChip("정체 +4분", false) { vm.addDemoDelay(4) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // 진단: 내비가 실제로 무엇을 주고 있는지
            val loc = state.location
            Text(
                listOf(
                    "내비: " + when (state.naviStatus) {
                        NaviStatus.CONNECTED -> "연결됨"
                        NaviStatus.NO_PERMISSION -> "권한 없음"
                        NaviStatus.CONNECTING -> "연결 중"
                        NaviStatus.UNAVAILABLE -> "없음(모의)"
                    },
                    "위치: " + "%.4f, %.4f".format(loc.lat, loc.lng) + if (state.locationFromPleos) " (내비)" else " (모의)",
                    "목적지: " + (state.routeDestName?.let { it + if (state.routeFromPleos) " (내비)" else " (시연)" } ?: "없음"),
                    "주행 판단: " + state.drivingSource + if (state.demoDelayMin > 0) " · 지연 +${state.demoDelayMin}분" else "",
                    "최근 오류: " + (state.naviError ?: "없음"),
                    state.naviDebug,
                ).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHint(emoji: String, text: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------------- 표시용 헬퍼 ----------------

private fun Store.category(): StoreFilter = when {
    brand.contains("스타벅스") -> StoreFilter.CAFE
    brand.contains("맥도날드") -> StoreFilter.BURGER
    else -> StoreFilter.ALL
}

private fun Store.emoji(): String = when (category()) {
    StoreFilter.CAFE -> "☕"
    StoreFilter.BURGER -> "🍔"
    StoreFilter.ALL -> "🥤"
}

/** "맥도날드 서판교DT점" → "서판교DT" */
private fun Store.shortName(): String = name.removePrefix(brand).trim().removeSuffix("점").ifBlank { name }

private fun MenuItem.emoji(): String = when {
    listOf("블랙티").any { it in name } -> "🍵"
    listOf("블렌디드").any { it in name } -> "🥤"
    listOf("아메리카노", "라떼", "콜드브루", "마키아또").any { it in name } -> "☕"
    "샌드위치" in name -> "🥪"
    "베이글" in name -> "🥯"
    "카스텔라" in name -> "🍰"
    "버거" in name -> "🍔"
    "감자튀김" in name -> "🍟"
    "너겟" in name -> "🍗"
    "해시브라운" in name -> "🥔"
    "주스" in name -> "🧃"
    "콜라" in name -> "🥤"
    "아이스크림" in name -> "🍦"
    else -> "🍽️"
}
