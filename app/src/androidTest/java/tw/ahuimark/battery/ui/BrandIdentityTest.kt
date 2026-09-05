package tw.ahuimark.battery.ui

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import tw.ahuimark.battery.R

class BrandIdentityTest {
    @Test fun launcherUsesMobiMarkWithoutChangingApplicationIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("tw.ahuimark.battery", context.packageName)
        assertEquals("MobiMark", context.getString(R.string.app_name))
        assertEquals("MobiMark", context.packageManager.getApplicationLabel(context.applicationInfo))
        assertTrue(context.packageManager.getApplicationIcon(context.packageName) is AdaptiveIconDrawable)
        assertNotNull(context.getDrawable(R.drawable.ic_mobimark_notification))
    }
}
