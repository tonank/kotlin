package foo

import foo.TypeReference.Base.Companion.FromBaseCompanion
import foo.TypeReference.CompanionSupertype.FromCompanionSupertype

object CallableReference {

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

    // Callable references
    val c = FromBaseCompanion::foo
    val d = FromCompanionSupertype::foo
}