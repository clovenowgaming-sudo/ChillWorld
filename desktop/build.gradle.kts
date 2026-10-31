// Modul Windows PC: Kotlin/JVM + LWJGL (GLFW + OpenGL 4.3 core).
// Kode render (Renderer, World, shader, dll.) dipakai bareng :app lewat folder sumber bersama,
// jadi tidak diduplikasi. Hanya kelas "android.*" dan UI/Input yang punya versi desktop sendiri.
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

val lwjglVersion = "3.3.4"
val lwjglNatives = "natives-windows"

kotlin {
    jvmToolchain(17)
}

// Salin kode bersama dari :app (kecuali UI/Input Android) ke folder build, lalu ikut dikompilasi
val sharedDir = layout.buildDirectory.dir("generated/shared")
val syncShared by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        exclude("**/MainActivity.kt", "**/Hud.kt", "**/Input.kt")
    }
    into(sharedDir)
}

kotlin.sourceSets.getByName("main").kotlin.srcDir(sharedDir)

// shader + texture ikut masuk jar sebagai resource (dibaca lewat AssetManager tiruan)
sourceSets.getByName("main").resources.srcDir("../app/src/main/assets")

tasks.named("compileKotlin") { dependsOn(syncShared) }

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
}

application {
    mainClass.set("com.vinz.chillworld.desktop.MainKt")
}
