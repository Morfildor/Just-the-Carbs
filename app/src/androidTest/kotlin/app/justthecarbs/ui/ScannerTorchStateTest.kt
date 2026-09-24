package app.justthecarbs.ui

import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.TorchState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.MutableLiveData
import app.justthecarbs.ui.scan.rememberTorchOn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * The torch icon follows the camera's own torch state, not a remembered flag.
 *
 * A flag drifted from the hardware whenever something other than the torch button turned the torch
 * off: the label scanner rebinding its camera on Retake, or the system closing the camera while the
 * app was in the background. The icon then said the torch was on when it was not.
 *
 * No emulator camera has a flash unit, so the camera here is a stand-in whose only behaviour is the
 * torch-state stream CameraX publishes.
 */
class ScannerTorchStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val torchState = MutableLiveData(TorchState.OFF)

    private val cameraInfo = Proxy.newProxyInstance(
        CameraInfo::class.java.classLoader,
        arrayOf(CameraInfo::class.java),
    ) { proxy, method, args -> answer(proxy, method.name, args) { torchState } } as CameraInfo

    private val camera = Proxy.newProxyInstance(
        Camera::class.java.classLoader,
        arrayOf(Camera::class.java),
    ) { proxy, method, args -> answer(proxy, method.name, args) { cameraInfo } } as Camera

    /** Identity for the Object methods Compose's keys call; [only] for the one real method. */
    private fun answer(proxy: Any, name: String, args: Array<out Any?>?, only: () -> Any): Any = when (name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "stand-in"
        "getTorchState", "getCameraInfo" -> only()
        else -> error("unexpected $name")
    }

    @Test
    fun theIconFollowsTheTorchTheCameraReports() {
        var lit: Boolean? = null
        rule.setContent { lit = rememberTorchOn(camera) }
        rule.runOnIdle { assertEquals(false, lit) }

        rule.runOnUiThread { torchState.value = TorchState.ON }
        rule.runOnIdle { assertEquals(true, lit) }

        // Turned off by something other than the button: a rebind, or the camera closing.
        rule.runOnUiThread { torchState.value = TorchState.OFF }
        rule.runOnIdle { assertEquals(false, lit) }
    }

    @Test
    fun noCameraYetMeansNoTorch() {
        var lit: Boolean? = null
        rule.setContent { lit = rememberTorchOn(null) }
        rule.runOnIdle { assertEquals(false, lit) }
    }
}
