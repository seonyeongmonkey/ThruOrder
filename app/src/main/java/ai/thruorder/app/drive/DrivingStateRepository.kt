package ai.thruorder.app.drive

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 정차/주행 판단.
 * 1) Android Automotive CarUxRestrictions (Pleos 공식 가이드 권장 방식)
 * 2) 기어 상태 GEAR_SELECTION (P가 아니면 주행으로 간주) — Virtual Console에서 기어 변경으로 시연
 * 둘 중 하나라도 주행이면 주행 모드. 둘 다 쓸 수 없으면 앱 안의 시연 스위치로 전환한다.
 */
class DrivingStateRepository(context: Context) {

    private val appContext = context.applicationContext
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null

    private val _uxRestricted = MutableStateFlow(false)
    val uxRestricted: StateFlow<Boolean> = _uxRestricted.asStateFlow()

    private val _gearNotPark = MutableStateFlow(false)
    val gearNotPark: StateFlow<Boolean> = _gearNotPark.asStateFlow()

    private val _sourceLabel = MutableStateFlow("차량 상태 연결 전")
    val sourceLabel: StateFlow<String> = _sourceLabel.asStateFlow()

    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val gear = value.value as? Int ?: return
            _gearNotPark.value = gear != GEAR_PARK
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "기어 상태 오류: $propertyId")
        }
    }

    fun start() {
        val labels = mutableListOf<String>()
        try {
            val c = Car.createCar(appContext)
            car = c

            runCatching {
                val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                uxManager = ux
                _uxRestricted.value = ux.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
                ux.registerListener { restrictions ->
                    _uxRestricted.value = restrictions.isRequiresDistractionOptimization
                }
                labels += "CarUxRestrictions"
            }.onFailure { Log.w(TAG, "CarUxRestrictions 사용 불가", it) }

            runCatching {
                val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                propertyManager = pm
                pm.registerCallback(
                    gearCallback,
                    VehiclePropertyIds.GEAR_SELECTION,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE,
                )
                labels += "기어"
            }.onFailure { Log.w(TAG, "기어 상태 사용 불가", it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Car 서비스 연결 불가", t)
        }
        _sourceLabel.value = if (labels.isEmpty()) "시연 스위치" else labels.joinToString(" + ")
    }

    fun stop() {
        runCatching { uxManager?.unregisterListener() }
        runCatching { propertyManager?.unregisterCallback(gearCallback) }
        runCatching { car?.disconnect() }
        car = null
    }

    companion object {
        private const val TAG = "ThruOrder.Drive"
        /** android.car.VehicleGear.GEAR_PARK */
        private const val GEAR_PARK = 0x0004
    }
}
