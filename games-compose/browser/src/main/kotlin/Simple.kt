import me.simon.common.App

fun main() {
    console.log("Hello, ${greet()}")
    renderComposable(rootElementId = "root") {

    }
    App()
}

fun greet() = "world"