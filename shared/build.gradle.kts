plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Android's D8 and the desktop JRE both take Java 17 bytecode
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Android ships org.json in the platform, the desktop app adds the library itself
    compileOnly("org.json:json:20260814")
    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20260814")
}
