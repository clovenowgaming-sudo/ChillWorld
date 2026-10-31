package android.content.res

import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Tiruan AssetManager: membaca file dari resource di dalam jar (folder assets Android
 * disalin ke resource lewat build.gradle.kts desktop). Kalau ada folder "assets_pc" di samping
 * program, isinya diutamakan (dipakai untuk paket texture 4K PC nanti).
 */
class AssetManager {
    private val overrideDir: java.io.File? =
        java.io.File("assets_pc").takeIf { it.isDirectory }

    fun open(path: String): InputStream {
        overrideDir?.let {
            val f = java.io.File(it, path)
            if (f.isFile) return f.inputStream()
        }
        return AssetManager::class.java.getResourceAsStream("/$path")
            ?: throw FileNotFoundException("asset tidak ada: $path")
    }
}
