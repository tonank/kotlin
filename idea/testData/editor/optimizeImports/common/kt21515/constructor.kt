package foo

import foo.TypeReference.Base.Companion.FromBaseCompanion
import foo.TypeReference.CompanionSupertype.FromCompanionSupertype

object Constructors {

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

    // Constructors
    val e = FromBaseCompanion()
    val f = FromCompanionSupertype()
}