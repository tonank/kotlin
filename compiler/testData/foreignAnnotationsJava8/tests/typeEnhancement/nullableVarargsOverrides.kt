// JAVAC_EXPECTED_FILE
// SKIP_COMPILED_JAVA
// FILE: BaseClass.java
import org.checkerframework.checker.nullness.qual.*;

public class BaseClass {
    public void loadCache(@Nullable Object... args) {}
}

// FILE: main.kt

class A : BaseClass() {
    override fun loadCache(vararg args: Any?) {
        super.loadCache(*args)
    }
}

class B : BaseClass() {
    <!NOTHING_TO_OVERRIDE!>override<!> fun loadCache(vararg args: Any) {
        super.loadCache(*args)
    }
}
