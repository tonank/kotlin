package foo

import foo.TypeReference.Base.Companion.FromBaseCompanion
import foo.TypeReference.CompanionSupertype.FromCompanionSupertype

object TypeReference {

    open class Base {
        companion object {
            class FromBaseCompanion {
                fun foo() = 42
            }
        }
    }

    open class CompanionSupertype {
        class FromCompanionSupertype {
            fun foo() = 42
        }
    }
}

class Derived : Base() {
    companion object : CompanionSupertype() {
    }

    // Type references
    val a: FromBaseCompanion? = null
    val b: FromCompanionSupertype? = null
}
