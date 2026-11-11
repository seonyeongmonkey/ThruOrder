package ai.thruorder.app

import ai.thruorder.app.navi.NaviRepository
import ai.thruorder.app.ui.AppRoot
import ai.thruorder.app.ui.AppViewModel
import ai.thruorder.app.ui.ThruOrderTheme
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** Pleos 내비 권한(NAVI_ROUTE, NAVI_ROUTE_SEARCH) 요청. 결과는 ViewModel이 내비 상태에 반영 */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermissionResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThruOrderTheme {
                AppRoot(vm, onRequestPermission = ::requestNaviPermissions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 설정 앱에서 권한을 바꾸고 돌아온 경우 반영
        vm.onPermissionResult()
    }

    private fun requestNaviPermissions() {
        val missing = NaviRepository.PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) vm.onPermissionResult() else permissionLauncher.launch(missing.toTypedArray())
    }
}
