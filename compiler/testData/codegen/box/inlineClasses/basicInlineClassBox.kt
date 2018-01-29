// !LANGUAGE: +InlineClasses

inline class InlineString(private val x: String) {
    operator fun plus(other: String) = InlineString(x + other)

    fun asString(): String = x
}

fun box(): String {
    val s = InlineString("") + "O" + "K"
    return s.asString()
}