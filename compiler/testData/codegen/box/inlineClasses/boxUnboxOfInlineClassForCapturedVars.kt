// !LANGUAGE: +InlineClasses

inline class UInt(private val value: Int) {
    operator fun plus(other: UInt): UInt = UInt(value + other.asValue())

    fun asValue(): Int = value
}

val Int.u get() = UInt(this)

var global = 0.u

fun test(x: UInt?, withAssert: Boolean) {
    x?.myLet {
        takeUInt(it)
        takeUInt(x)
    }

    x?.myLet {
        takeNullableUInt(it)
        takeNullableUInt(x)
    }

    if (withAssert) {
        x!!.myLet {
            takeUInt(it)
            takeUInt(x)
        }
    }
}

fun takeUInt(y: UInt) {
    global += y
}

fun takeNullableUInt(y: UInt?) {
    if (y != null) {
        global += y
    }
}

inline fun <T> T.myLet(f: (T) -> Unit) = f(this)

fun box(): String {
    val u = 1.u
    test(u, true)
    if (global.asValue() != 6) return "fail"

    global = 0.u
    test(null, false)
    if (global.asValue() != 0) return "fail"

    return "OK"
}