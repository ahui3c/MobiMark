package tw.ahuimark.battery.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.ahuimark.battery.model.WorkloadType

class RunningChromeAlphaTest {
    @Test
    fun shooterChromeUsesTwentyPercentBlackOpacity() {
        assertEquals(.20f, runningChromeAlpha(WorkloadType.SHOOTER), 0f)
        assertEquals(.48f, runningChromeAlpha(WorkloadType.CAMERA), 0f)
        assertEquals(.48f, runningChromeAlpha(WorkloadType.CAMERA), 0f)
    }
}
