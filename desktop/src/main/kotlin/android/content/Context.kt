package android.content

import android.app.ActivityManager
import android.content.res.AssetManager

/** Tiruan android.content.Context: cukup assets + layanan memori. */
open class Context {
    val assets = AssetManager()

    fun getSystemService(name: String): Any? =
        if (name == ACTIVITY_SERVICE) ActivityManager() else null

    companion object {
        const val ACTIVITY_SERVICE = "activity"
    }
}
