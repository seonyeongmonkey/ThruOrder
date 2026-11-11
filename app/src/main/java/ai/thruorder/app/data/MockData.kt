package ai.thruorder.app.data

/**
 * 시연용 데이터 (ThruOrder_mock_data.xlsx 와 동일).
 * 매장명·주소·좌표는 공개 정보, 메뉴·가격·준비시간·차로 대기는 가정값(모의)이다.
 */
object MockData {

    /**
     * 시연 출발지 = Pleos 에뮬레이터 기본 위치.
     * 에뮬레이터를 처음 켜면 내비 현재 위치가 성남시 수정구 시흥동(판교 제2테크노밸리)으로 잡힌다.
     * Pleos 개발 문서의 예시 좌표(소프트웨어드림센터, 창업로40번길 6)와 같은 곳이다.
     */
    val DEMO_ORIGIN = GeoPoint(37.41275833, 127.09481944)
    const val DEMO_ORIGIN_NAME = "판교 제2테크노밸리 (Pleos 에뮬레이터 기본 위치)"

    /** 시연용 목적지: 내비 경로가 없을 때 '경로 위 DT' 기능을 보여주기 위한 예시 (좌표는 근사값) */
    val demoDestinations = listOf(
        DemoDestination("잠실 롯데월드타워", GeoPoint(37.5125, 127.1025), "서울 송파구 올림픽로 300"),
        DemoDestination("광교 호수공원", GeoPoint(37.2830, 127.0660), "경기 수원시 영통구 광교호수로 57"),
        DemoDestination("강남역", GeoPoint(37.4979, 127.0276), "서울 강남구 강남대로 396"),
    )

    /**
     * 출발지 반경 15km 안의 실제 DT 매장 (각 브랜드 공식 홈페이지 매장 찾기 화면의 공개 정보, 2026-11-07 확인).
     * 메뉴·가격·준비시간·차로 대기는 브랜드 공통 모의 데이터이며, 메뉴명은 상표가 아닌 일반명으로 표기.
     * ※ 매장명은 시연용 예시이며 해당 브랜드와 제휴·승인 관계가 없다.
     */
    val stores = listOf(
        sbx("SBX-BDGN", "분당궁내DT점", "경기 성남시 분당구 대왕판교로 302", 37.370636, 127.102071, 4),
        sbx("SBX-SNMR", "성남모란DT점", "경기 성남시 중원구 둔촌대로 131", 37.42916075591734, 127.13344319390397, 5),
        sbx("SBX-GNSG", "강남세곡DT점", "서울 강남구 헌릉로 727", 37.466993809277, 127.118330806807, 4),
        sbx("SBX-UWCG", "의왕청계DT점", "경기 의왕시 안양판교로 246", 37.3883721704744, 126.998385219178, 3),
        sbx("SBX-GC", "과천DT점", "경기 과천시 중앙로 277", 37.43929087256856, 126.99381334849441, 4),
        sbx("SBX-SPBI", "송파방이DT점", "서울 송파구 오금로 241", 37.506564, 127.120495, 5),
        sbx("SBX-SPNR", "송파나루역DT점", "서울 송파구 오금로 142", 37.510976, 127.110358, 6),
        sbx("SBX-SJSB", "수지성복DT점", "경기 용인시 수지구 성복2로 55", 37.314048, 127.079162, 3),
        mcd("MCD-SPG", "서판교DT점", "경기 성남시 분당구 운중동 1017", 37.3906413, 127.0840039, 4),
        mcd("MCD-SNSH", "성남신흥DT점", "경기 성남시 수정구 신흥동 181", 37.451099, 127.148536, 6),
        mcd("MCD-YJ", "양재SK DT점", "서울 서초구 양재동 81", 37.478071, 127.03961, 6),
        mcd("MCD-YISJ", "용인수지DT점", "경기 용인시 수지구 풍덕천동 43-5", 37.3303186, 127.1023476, 5),
        mcd("MCD-GR", "가락DT점", "서울 송파구 가락동 193-7", 37.494879, 127.130762, 5),
        mcd("MCD-SPJS", "송파잠실DT점", "서울 송파구 잠실동 305-15", 37.504361, 127.077051, 7),
        mcd("MCD-DC", "서울둔촌DT점", "서울 강동구 둔촌동 517-2", 37.52928, 127.137334, 5),
    )

    private fun sbx(id: String, name: String, addr: String, lat: Double, lng: Double, laneWait: Int) = Store(
        id = id, brand = "스타벅스", name = "스타벅스 $name", address = addr,
        location = GeoPoint(lat, lng), pickupBufferMin = 1, laneWaitMin = laneWait,
        phone = "1522-3232", source = "스타벅스 매장 찾기 공개 정보",
    )

    private fun mcd(id: String, name: String, addr: String, lat: Double, lng: Double, laneWait: Int) = Store(
        id = id, brand = "맥도날드", name = "맥도날드 $name", address = addr,
        location = GeoPoint(lat, lng), pickupBufferMin = 1, laneWaitMin = laneWait,
        source = "맥도날드 매장 찾기 공개 정보",
    )

    val menu = listOf(
        MenuItem("SBX-01", "SBX-YJ", "커피", "아이스 아메리카노 (레귤러)", "아이스 아메리카노", 4700, 60, "에스프레소바", 10),
        MenuItem("SBX-02", "SBX-YJ", "커피", "아메리카노 (레귤러)", "아메리카노", 4700, 60, "에스프레소바", 15),
        MenuItem("SBX-03", "SBX-YJ", "커피", "아이스 카페라떼 (레귤러)", "아이스 라떼", 5200, 90, "에스프레소바", 10),
        MenuItem("SBX-04", "SBX-YJ", "커피", "바닐라 크림 콜드브루 (레귤러)", "바닐라 콜드브루", 6100, 60, "콜드바", 10),
        MenuItem("SBX-05", "SBX-YJ", "커피", "콜드브루 (레귤러)", "콜드브루", 5100, 40, "콜드바", 10),
        MenuItem("SBX-06", "SBX-YJ", "커피", "카라멜 마키아또 (레귤러)", "카라멜 마키아또", 5900, 120, "에스프레소바", 15),
        MenuItem("SBX-07", "SBX-YJ", "티", "자몽 허니 블랙티 (레귤러)", "자몽 블랙티", 5900, 90, "콜드바", 15),
        MenuItem("SBX-08", "SBX-YJ", "프라푸치노", "초코칩 블렌디드 커피 (레귤러)", "초코칩 블렌디드", 6300, 150, "블렌더", 5),
        MenuItem("SBX-09", "SBX-YJ", "프라푸치노", "망고 블렌디드 (레귤러)", "망고 블렌디드", 5900, 150, "블렌더", 5, soldOut = true),
        MenuItem("SBX-10", "SBX-YJ", "푸드", "BLT 샌드위치", "BLT 샌드위치", 6500, 120, "푸드워머", 8),
        MenuItem("SBX-11", "SBX-YJ", "푸드", "햄 루꼴라 베이글", "햄 베이글", 5900, 120, "푸드워머", 8),
        MenuItem("SBX-12", "SBX-YJ", "푸드", "생크림 카스텔라", "생크림 카스텔라", 4500, 20, "푸드워머", 30),

        MenuItem("MCD-01", "MCD-YJ", "버거", "클래식 더블버거", "더블버거", 5700, 180, "그릴", 10),
        MenuItem("MCD-02", "MCD-YJ", "버거", "스파이시 치킨버거", "치킨버거", 5700, 180, "그릴", 10),
        MenuItem("MCD-03", "MCD-YJ", "버거", "베이컨 비프버거", "베이컨버거", 6900, 210, "그릴", 10),
        MenuItem("MCD-04", "MCD-YJ", "버거", "불고기 버거", "불고기 버거", 3200, 150, "그릴", 10),
        MenuItem("MCD-05", "MCD-YJ", "버거", "치즈버거", "치즈버거", 3300, 120, "그릴", 10),
        MenuItem("MCD-06", "MCD-YJ", "사이드", "감자튀김 (M)", "감자튀김 M", 2600, 180, "프라이어", 7),
        MenuItem("MCD-07", "MCD-YJ", "사이드", "치킨너겟 6조각", "너겟 6조각", 3700, 180, "프라이어", 8),
        MenuItem("MCD-08", "MCD-YJ", "사이드", "해시브라운", "해시브라운", 1600, 150, "프라이어", 7, soldOut = true),
        MenuItem("MCD-09", "MCD-YJ", "음료", "콜라 (M)", "콜라 M", 2300, 30, "음료", 15),
        MenuItem("MCD-10", "MCD-YJ", "음료", "아이스 아메리카노 (M)", "아이스 아메리카노", 2400, 45, "음료", 10),
        MenuItem("MCD-11", "MCD-YJ", "음료", "오렌지 주스", "오렌지 주스", 2700, 20, "음료", 20),
        MenuItem("MCD-12", "MCD-YJ", "디저트", "쿠키 아이스크림 (컵)", "아이스크림", 3900, 60, "디저트", 3),
    )

    /** 브랜드 공통 메뉴 (DT 매장도 전국 공통 메뉴 기준, 품절만 매장별로 다르다고 가정) */
    fun menuFor(store: Store): List<MenuItem> = when {
        store.brand.contains("스타벅스") -> menu.filter { it.id.startsWith("SBX") }
        store.brand.contains("맥도날드") -> menu.filter { it.id.startsWith("MCD") }
        else -> emptyList()
    }
}
