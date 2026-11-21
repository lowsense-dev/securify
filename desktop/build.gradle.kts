import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

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
    implementation("top.yukonga.miuix.kmp:miuix-ui-desktop:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-desktop:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-desktop:0.9.4")
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

// Renders screens to PNG for design review; never part of a release build.
val previewShots by tasks.registering(JavaExec::class) {
    dependsOn("testClasses")
    group = "verification"
    description = "Renders design previews into build/previews"
    mainClass.set("app.securify.ui.PreviewShotsKt")
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("java.awt.headless", "true")
    systemProperty("previewOut", layout.buildDirectory.dir("previews").get().asFile.path)
}

// ---- Windows portable build -------------------------------------------------------------
// The app is the same JVM code, but the Skiko natives must be the Windows ones, so this
// configuration resolves the Windows Compose artifacts instead of the host's.

private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

private fun fetch(url: String, file: File, sha: String) {
    if (file.exists() && sha256(file) == sha) return
    file.parentFile.mkdirs()
    URI(url).toURL().openStream().use { i -> file.outputStream().use { i.copyTo(it) } }
    val got = sha256(file)
    if (got != sha) {
        file.delete()
        throw GradleException("checksum mismatch for $url: $got")
    }
}

private fun unzip(zip: File, dest: File, stripTop: Boolean = false, filter: (String) -> Boolean = { true }) {
    ZipFile(zip).use { z ->
        for (e in z.entries()) {
            if (e.isDirectory || !filter(e.name)) continue
            val name = if (stripTop) e.name.substringAfter('/') else e.name
            if (name.isEmpty()) continue
            val out = File(dest, name)
            out.parentFile.mkdirs()
            z.getInputStream(e).use { i -> out.outputStream().use { i.copyTo(it) } }
        }
    }
}

private val WIN_JRE_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jre_x64_windows_hotspot_21.0.13.1_2.zip"
private val WIN_JRE_SHA = "e15ee1e54a5a3b38027a09cedfa87bc3348d7dd3ce50866dd3d9671d7fffc6c1"
private val WIN_XRAY_SHA = "2e04a9995a6f2ad1b28c0bb25c708ecb2695e43a1c9c7889c4b6ac2b48cf7a6a"
private val L4J_URL = "https://sourceforge.net/projects/launch4j/files/launch4j-3/3.50/launch4j-3.50-linux-x64.tgz/download"
private val L4J_SHA = "a6ad95c2fb6300410543288308b4eeae72ecd1df033928dfc524ba539bbc4d36"

val windowsRuntime: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    add(windowsRuntime.name, project(":shared"))
    add(windowsRuntime.name, compose.desktop.windows_x64)
    add(windowsRuntime.name, "top.yukonga.miuix.kmp:miuix-ui-desktop:0.9.4")
    add(windowsRuntime.name, "top.yukonga.miuix.kmp:miuix-preference-desktop:0.9.4")
    add(windowsRuntime.name, "top.yukonga.miuix.kmp:miuix-icons-desktop:0.9.4")
    add(windowsRuntime.name, "sh.calvin.reorderable:reorderable:3.1.0")
    add(windowsRuntime.name, "com.google.zxing:javase:3.5.4")
    add(windowsRuntime.name, "org.json:json:20260814")
    add(windowsRuntime.name, "com.github.hypfvieh:dbus-java-core:5.2.2")
    add(windowsRuntime.name, "com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.2")
}

val windowsJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("classes"))
    archiveFileName.set("Securify.jar")
    destinationDirectory.set(layout.buildDirectory.dir("windows"))
    manifest { attributes["Main-Class"] = "app.securify.desktop.MainKt" }
    from(sourceSets.main.get().output)
    from({ windowsRuntime.map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/*.module", "module-info.class", "META-INF/versions/*/module-info.class")
        exclude("libskiko-linux-*", "libskiko-macos-*", "skiko-windows-arm64.dll*")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val windowsDist by tasks.registering {
    dependsOn(windowsJar)
    inputs.files(windowsJar)
    inputs.file("src/main/resources/securify.svg")
    val downloads = layout.buildDirectory.dir("downloads")
    val root = layout.buildDirectory.dir("windows/Securify")
    outputs.dir(root)
    doLast {
        val dir = downloads.get().asFile
        val jreZip = File(dir, "OpenJDK21U-jre_x64_windows.zip")
        val xrayZip = File(dir, "Xray-windows-64-$xrayVersion.zip")
        val l4jTgz = File(dir, "launch4j-3.50-linux-x64.tgz")
        fetch(WIN_JRE_URL, jreZip, WIN_JRE_SHA)
        fetch("https://github.com/XTLS/Xray-core/releases/download/$xrayVersion/Xray-windows-64.zip", xrayZip, WIN_XRAY_SHA)
        fetch(L4J_URL, l4jTgz, L4J_SHA)

        val out = root.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        unzip(jreZip, File(out, "runtime"), stripTop = true)
        unzip(xrayZip, File(out, "resources"), filter = { it in setOf("xray.exe", "wintun.dll", "geoip.dat", "geosite.dat", "LICENSE", "LICENSE-Wintun") })
        val jar = windowsJar.get().archiveFile.get().asFile
        ZipFile(jar).use { check(it.getEntry("skiko-windows-x64.dll") != null) { "Windows Skiko runtime is missing" } }
        jar.copyTo(File(out, "Securify.jar"), overwrite = true)
        val manifest = File(out, "securify.manifest")
        manifest.writeText(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
              <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3">
                <security>
                  <requestedPrivileges>
                    <requestedExecutionLevel level="requireAdministrator" uiAccess="false"/>
                  </requestedPrivileges>
                </security>
              </trustInfo>
              <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1">
                <application>
                  <supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}"/>
                </application>
              </compatibility>
              <application xmlns="urn:schemas-microsoft-com:asm.v3">
                <windowsSettings>
                  <dpiAware xmlns="http://schemas.microsoft.com/SMI/2005/WindowsSettings">true/pm</dpiAware>
                  <dpiAwareness xmlns="http://schemas.microsoft.com/SMI/2016/WindowsSettings">permonitorv2,permonitor</dpiAwareness>
                </windowsSettings>
              </application>
            </assembly>
            """.trimIndent(),
        )

        val l4j = File(dir, "launch4j")
        if (!File(l4j, "launch4j.jar").exists()) {
            l4j.mkdirs()
            providers.exec {
                commandLine("tar", "xzf", l4jTgz.path, "-C", l4j.path, "--strip-components=1")
            }.result.get()
        }

        val ico = File(out, "securify.ico")
        // ImageMagick 7 calls itself magick, ImageMagick 6 (what CI runners ship) calls it convert
        val iconTool = listOf("magick", "convert").firstOrNull { tool ->
            providers.exec {
                commandLine("sh", "-c", "command -v $tool")
                isIgnoreExitValue = true
            }.result.get().exitValue == 0
        }
        if (iconTool != null) {
            providers.exec {
                commandLine(
                    iconTool, file("src/main/resources/securify.svg").path, "-background", "none",
                    "-define", "icon:auto-resize=256,128,64,48,32,16", ico.path,
                )
            }.result.get()
        }

        File(out, "launch4j.xml").writeText(
            """
            <launch4jConfig>
              <dontWrapJar>false</dontWrapJar>
              <headerType>gui</headerType>
              <jar>Securify.jar</jar>
              <outfile>Securify.exe</outfile>
              <errTitle>Securify</errTitle>
              <cmdLine></cmdLine>
              <chdir>.</chdir>
              <priority>normal</priority>
              <stayAlive>false</stayAlive>
              <restartOnCrash>false</restartOnCrash>
              <manifest>securify.manifest</manifest>
              ${if (iconTool != null) "<icon>securify.ico</icon>" else ""}
              <classPath><mainClass>app.securify.desktop.MainKt</mainClass></classPath>
              <jre>
                <path>runtime</path>
                <requires64Bit>true</requires64Bit>
                <minVersion>21</minVersion>
                <opt>-Dcompose.application.resources.dir=resources</opt>
                <opt>-Dsun.java2d.uiScale.enabled=true</opt>
                <opt>-Dfile.encoding=UTF-8</opt>
              </jre>
            </launch4jConfig>
            """.trimIndent(),
        )
        providers.exec {
            workingDir = out
            commandLine(
                File(System.getProperty("java.home"), "bin/java").path, "-Djava.awt.headless=true",
                "-jar", File(l4j, "launch4j.jar").path, "launch4j.xml",
            )
        }.result.get()
        check(File(out, "Securify.exe").isFile && File(out, "runtime/bin/javaw.exe").isFile && File(out, "resources/wintun.dll").isFile)
        File(out, "launch4j.xml").delete()
        File(out, "Securify.jar").delete()
        manifest.delete()
        ico.delete()
        File(out, "README.txt").writeText(
            "Securify $versionName for Windows\r\n\r\nRun Securify.exe. Windows asks for administrator rights: Xray needs them to create the TUN adapter.\r\nSettings and subscriptions live in %LOCALAPPDATA%\\Securify.\r\n",
        )
    }
}

val windowsZip by tasks.registering(Zip::class) {
    dependsOn(windowsDist)
    from(layout.buildDirectory.dir("windows/Securify")) { into("Securify") }
    archiveFileName.set("securify-$versionName-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("windows"))
}
