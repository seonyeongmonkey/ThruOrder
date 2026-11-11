package ai.thruorder.app.data

import ai.thruorder.app.BuildConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 주변 드라이브스루(DT) 매장 검색.
 * - 매장 기본정보(이름·주소·전화·좌표)는 카카오 로컬 키워드 검색 API(공식 API)로 가져온다.
 * - Pleos NaviHelper에는 장소 검색 결과를 앱에 돌려주는 API가 없어 외부 장소 API를 쓴다.
 * - 메뉴·가격·준비시간은 브랜드 공통 모의 데이터(MockData)를 붙인다.
 */
class StoreRepository {

    val hasKey: Boolean get() = BuildConfig.KAKAO_REST_KEY.isNotBlank()

    /** 기준 위치에서 반경 radiusM 안의 스타벅스·맥도날드 DT 매장을 가까운 순으로 반환. 키가 없거나 실패하면 null. */
    suspend fun searchNearbyDt(center: GeoPoint, radiusM: Int = 15_000): List<Store>? = withContext(Dispatchers.IO) {
        if (!hasKey) return@withContext null
        try {
            val results = mutableListOf<Store>()
            for (brand in BRANDS) {
                results += search(brand, center, radiusM)
            }
            results.distinctBy { it.id }.sortedBy { distanceOf(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "카카오 로컬 검색 실패 → 모의 매장 사용", t)
            null
        }
    }

    private val distanceCache = mutableMapOf<String, Int>()
    private fun distanceOf(store: Store): Int = distanceCache[store.id] ?: Int.MAX_VALUE

    private fun search(brand: Brand, center: GeoPoint, radiusM: Int): List<Store> {
        val q = URLEncoder.encode(brand.query, "UTF-8")
        val url = URL(
            "https://dapi.kakao.com/v2/local/search/keyword.json" +
                "?query=$q&x=${center.lng}&y=${center.lat}&radius=$radiusM&sort=distance&size=15",
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_KEY}")
        }
        try {
            if (conn.responseCode != 200) {
                Log.w(TAG, "카카오 로컬 응답 ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.readText()}")
                return emptyList()
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val docs = JSONObject(body).getJSONArray("documents")
            val list = mutableListOf<Store>()
            for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                val name = d.optString("place_name")
                // 같은 브랜드의 DT(드라이브스루) 매장만
                if (!name.startsWith(brand.namePrefix)) continue
                if (!(name.contains("DT", ignoreCase = true) || name.contains("드라이브"))) continue
                val lat = d.optString("y").toDoubleOrNull() ?: continue
                val lng = d.optString("x").toDoubleOrNull() ?: continue
                val id = "KAKAO-" + d.optString("id")
                d.optString("distance").toIntOrNull()?.let { distanceCache[id] = it }
                list += Store(
                    id = id,
                    brand = brand.label,
                    name = name,
                    address = d.optString("road_address_name").ifBlank { d.optString("address_name") },
                    location = GeoPoint(lat = lat, lng = lng),
                    pickupBufferMin = 1,
                    laneWaitMin = brand.defaultLaneWaitMin,
                    phone = d.optString("phone"),
                    source = "카카오 로컬",
                )
            }
            return list
        } finally {
            conn.disconnect()
        }
    }

    private data class Brand(val query: String, val namePrefix: String, val label: String, val defaultLaneWaitMin: Int)

    companion object {
        private const val TAG = "ThruOrder.Store"
        private val BRANDS = listOf(
            Brand("스타벅스 DT", "스타벅스", "스타벅스", 4),
            Brand("맥도날드 DT", "맥도날드", "맥도날드", 6),
        )
    }
}
