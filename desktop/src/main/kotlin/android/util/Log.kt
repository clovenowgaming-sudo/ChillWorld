package android.util

/** Tiruan android.util.Log: cetak ke konsol. */
object Log {
    @JvmStatic fun i(tag: String, msg: String): Int { println("I/$tag: $msg"); return 0 }
    @JvmStatic fun w(tag: String, msg: String): Int { System.err.println("W/$tag: $msg"); return 0 }
    @JvmStatic fun e(tag: String, msg: String): Int { System.err.println("E/$tag: $msg"); return 0 }
    @JvmStatic fun e(tag: String, msg: String, tr: Throwable?): Int {
        System.err.println("E/$tag: $msg")
        tr?.printStackTrace()
        return 0
    }
    @JvmStatic fun d(tag: String, msg: String): Int { println("D/$tag: $msg"); return 0 }
}
