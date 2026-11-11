package ai.thruorder.app.share

import ai.thruorder.app.BuildConfig
import ai.thruorder.app.data.CartLine
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 동승자 공동 장바구니 중계.
 * - 차량 화면의 QR → 동승자 폰에서 웹 메뉴판(GitHub Pages)이 열림
 * - 폰에서 담은 메뉴는 세션 토픽으로 발행되고, 차량 앱이 구독해 장바구니에 합친다
 * - 차량 앱은 장바구니 현황을 같은 토픽으로 발행해 폰에서도 함께 보이게 한다
 * 데모에서는 공개 중계 서버(ntfy.sh)를 쓰며, 메시지에는 메뉴 ID·수량·별칭만 담는다.
 * 실제 서비스에서는 스루오더 주문 서버가 이 역할을 맡는다.
 */
class SharedCartRelay(private val scope: CoroutineScope) {

    data class RemoteAdd(val who: String, val itemId: String, val qty: Int)

    private val _adds = MutableSharedFlow<RemoteAdd>(extraBufferCapacity = 64)
    val adds: SharedFlow<RemoteAdd> = _adds.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var job: Job? = null
    @Volatile private var conn: HttpURLConnection? = null
    var sessionId: String? = null
        private set

    private fun topicUrl(id: String) = "${BuildConfig.RELAY_BASE_URL}/thruorder-$id"

    fun start(id: String) {
        stop()
        sessionId = id
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val c = (URL(topicUrl(id) + "/json").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 0 // 스트리밍
                    }
                    conn = c
                    c.inputStream.bufferedReader().use { reader ->
                        _connected.value = true
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            handle(line)
                        }
                    }
                } catch (t: Throwable) {
                    if (isActive) Log.w(TAG, "중계 연결 끊김, 재연결 시도", t)
                } finally {
                    _connected.value = false
                    conn?.disconnect()
                    conn = null
                }
                if (isActive) delay(3_000)
            }
        }
    }

    private fun handle(line: String) {
        runCatching {
            val o = JSONObject(line)
            if (o.optString("event") != "message") return
            val msg = JSONObject(o.optString("message"))
            if (msg.optString("type") != "add") return
            val who = msg.optString("who").ifBlank { "동승자" }.take(12)
            val items = msg.optJSONArray("items") ?: return
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val qty = it.optInt("qty", 1).coerceIn(1, 9)
                _adds.tryEmit(RemoteAdd(who, it.optString("id"), qty))
            }
        }.onFailure { Log.w(TAG, "메시지 해석 실패: $line", it) }
    }

    /** 차량 장바구니 현황을 폰 메뉴판에도 보이도록 발행 */
    fun publishCart(lines: List<CartLine>) {
        val arr = JSONArray()
        lines.forEach {
            arr.put(JSONObject().put("name", it.item.shortName).put("qty", it.qty).put("who", it.seat).put("price", it.item.price))
        }
        publish(JSONObject().put("type", "cart").put("lines", arr))
    }

    /** 주문 확정 알림 (폰 메뉴판에 주문코드 표시) */
    fun publishOrdered(code: String, arrivalHhmm: String) {
        publish(JSONObject().put("type", "ordered").put("code", code).put("arrival", arrivalHhmm))
    }

    private fun publish(body: JSONObject) {
        val id = sessionId ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val c = (URL(topicUrl(id)).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                }
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                c.responseCode
                c.disconnect()
            }.onFailure { Log.w(TAG, "발행 실패", it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { conn?.disconnect() }
        conn = null
        _connected.value = false
        sessionId = null
    }

    companion object {
        private const val TAG = "ThruOrder.Share"
    }
}
