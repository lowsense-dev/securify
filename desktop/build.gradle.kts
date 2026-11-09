import java.net.URI
import java.security.MessageDigest

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val versionName = (findProperty("versionName") as String?) ?: "1.0.0"
val versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.13.0-beta02")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("com.materialkolor:material-kolor:5.0.1")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.google.zxing:javase:3.5.4")
    implementation("org.json:json:20260814")
    implementation("com.github.hypfvieh:dbus-java-core:5.2.2")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.2")
}

// The UI reads the Android app's translations, so both apps share one set of strings.
val generated = layout.buildDirectory.dir("generated/securify")
val androidRes = rootProject.file("app/src/main/res")
val genSources by tasks.registering {
    val sources = listOf("values", "values-ru").flatMap { listOf(File(androidRes, "$it/strings.xml"), file("src/main/strings/$it/strings.xml")) }
    inputs.files(sources)
    inputs.property("version", "$versionName/$versionCode")
    outputs.dir(generated)
    doLast {
        val out = generated.get().asFile
        out.deleteRecursively()
        val kt = File(out, "kotlin/app/securify/ui").apply { mkdirs() }
        val names = Regex("""<(string|plurals) name="([a-z0-9_]+)"""")
            .findAll(sources.filter { it.parentFile.name == "values" }.joinToString("\n") { it.readText() })
            .map { it.groupValues[1] to it.groupValues[2] }.distinct().toList()
        File(kt, "R.kt").writeText(buildString {
            appendLine("package app.securify.ui\n\nobject R {")
            for (kind in listOf("string", "plurals")) {
                appendLine("    object $kind {")
                names.filter { it.first == kind }.forEach { appendLine("        const val ${it.second} = \"${it.second}\"") }
                appendLine("    }")
            }
            appendLine("}")
        })
        File(kt, "BuildInfo.kt").writeText(
            "package app.securify.ui\n\nobject BuildInfo {\n    const val VERSION_NAME = \"$versionName\"\n    const val VERSION_CODE = $versionCode\n}\n",
        )
        for (src in sources.filter { it.exists() }) {
            val name = if (src.path.startsWith(androidRes.path)) "android.xml" else "desktop.xml"
            src.copyTo(File(out, "res/strings/${src.parentFile.name}/$name"))
        }
        File(androidRes, "drawable/ic_shield.xml").copyTo(File(out, "res/ic_shield.xml"))
    }
}
kotlin.sourceSets.main { kotlin.srcDir(genSources.map { generated.get().dir("kotlin") }) }
sourceSets.main { resources.srcDir(genSources.map { generated.get().dir("res") }) }

// Xray and the geo files ship next to the app; the root helper copies them out of the read-only AppImage.
val xrayVersion = "v26.10.31"
val xrayZip = layout.buildDirectory.file("downloads/Xray-linux-64-$xrayVersion.zip")
val downloadXray by tasks.registering {
    outputs.file(xrayZip)
    doLast {
        val f = xrayZip.get().asFile
        if (!f.exists()) {
            f.parentFile.mkdirs()
            URI("https://github.com/XTLS/Xray-core/releases/download/$xrayVersion/Xray-linux-64.zip").toURL().openStream()
                .use { i -> f.outputStream().use { i.copyTo(it) } }
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        if (sha != "e4bd9119f50ae7c41658b1abd0679ae06f98bd864a2907ff33f2f2cf7081b214") {
            f.delete()
            throw GradleException("Xray-linux-64.zip checksum mismatch: $sha")
        }
    }
}
val appResources = layout.buildDirectory.dir("appResources")
val prepareCore by tasks.registering(Copy::class) {
    dependsOn(downloadXray)
    from(zipTree(xrayZip)) { include("xray") }
    from(zipTree(rootProject.file("app/libs/libv2ray.aar"))) {
        include("assets/geoip.dat", "assets/geosite.dat")
        eachFile { path = name }
    }
    includeEmptyDirs = false
    into(appResources.map { it.dir("linux-x64") })
}

compose.desktop {
    application {
        mainClass = "app.securify.desktop.MainKt"
        jvmArgs += listOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED", "-Dsun.java2d.uiScale.enabled=true")
        nativeDistributions {
            packageName = "Securify"
            packageVersion = versionName
            appResourcesRootDir.set(appResources)
            modules("java.desktop", "java.xml", "java.naming", "jdk.crypto.ec", "jdk.unsupported", "jdk.localedata", "jdk.charsets", "jdk.zipfs", "jdk.security.auth")
        }
    }
}
afterEvaluate {
    tasks.named("prepareAppResources") { dependsOn(prepareCore) }
}

// appimagetool and its runtime are pinned to tagged releases and checked like Xray
val appImage by tasks.registering(Exec::class) {
    dependsOn("createDistributable")
    val dist = layout.buildDirectory.dir("compose/binaries/main/app/Securify")
    val out = layout.buildDirectory.file("appimage/securify-$versionName-x86_64.AppImage")
    inputs.dir(dist)
    inputs.files("appimage.sh", "securify.desktop")
    outputs.file(out)
    commandLine(
        "bash", file("appimage.sh").path,
        dist.get().asFile.path,
        layout.buildDirectory.dir("appimage").get().asFile.path,
        out.get().asFile.path,
    )
}
