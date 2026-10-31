package android.app

import java.lang.management.ManagementFactory

/** Tiruan ActivityManager: hanya total RAM fisik PC. */
class ActivityManager {
    class MemoryInfo {
        @JvmField var totalMem: Long = 0L
    }

    fun getMemoryInfo(info: MemoryInfo) {
        val os = ManagementFactory.getOperatingSystemMXBean()
        info.totalMem = if (os is com.sun.management.OperatingSystemMXBean) os.totalMemorySize else 0L
    }
}
