package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.justthecarbs.ocr.NormalizedRegion
import app.justthecarbs.ui.scan.CROP_READ_TAG
import app.justthecarbs.ui.scan.CropConfirmationScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CropSelectionUpdateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lateAutomaticTargetReplacesTheInitialRectangle() {
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        val initial = NormalizedRegion(0.05, 0.05, 0.95, 0.95)
        val automatic = NormalizedRegion(0.2, 0.3, 0.8, 0.7)
        val target = mutableStateOf(initial)
        var submitted: NormalizedRegion? = null
        compose.setContent {
            MaterialTheme {
                CropConfirmationScreen(bitmap, target.value, reading = false,
                    onReadTable = { submitted = it }, onRetake = {})
            }
        }
        compose.runOnIdle { target.value = automatic }
        compose.onNodeWithTag(CROP_READ_TAG).performClick()
        compose.runOnIdle { assertEquals(automatic, submitted) }
    }
}
