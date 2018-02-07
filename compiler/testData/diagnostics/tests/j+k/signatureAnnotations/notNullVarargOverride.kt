// FILE: BaseClass.java
import org.jetbrains.annotations.NotNull;

public class BaseClass {
    public void loadCache(@NotNull Object... args) {}
}

// FILE: main.kt

class A : BaseClass() {
    override fun loadCache(vararg args: Any?) {
        super.loadCache(*args)
    }
}

class B : BaseClass() {
    override fun loadCache(vararg args: Any) {
        super.loadCache(*args)
    }
}
