buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.snakeyaml:snakeyaml-engine:2.9") }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

compose.resources {
    publicResClass = true
}

val companyIdentifiersUrl = "https://bitbucket.org/bluetooth-SIG/public/raw/main/assigned_numbers/company_identifiers/company_identifiers.yaml"

tasks.register("updateCompanyIdentifiers") {
    description = "Downloads the latest Bluetooth SIG company identifiers YAML"
    doLast {
        val dest = rootProject.file("scripts/company_identifiers.yaml")
        uri(companyIdentifiersUrl).toURL().openStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        println("Downloaded company identifiers to ${dest.absolutePath}")
    }
}

val generateCompanyDatabase by tasks.registering {
    val inputFile = rootProject.file("scripts/company_identifiers.yaml")
    val outputDir = layout.buildDirectory.dir("generated/companydb")
    inputs.file(inputFile)
    outputs.dir(outputDir)

    doLast {
        val yaml = org.snakeyaml.engine.v2.api.Load(
            org.snakeyaml.engine.v2.api.LoadSettings.builder().build()
        )
        val data = yaml.loadFromReader(inputFile.reader()) as Map<*, *>
        val rawEntries = data["company_identifiers"] as List<*>
        val entries = rawEntries.map { entry ->
            val map = entry as Map<*, *>
            val hexValue = map["value"] as String  // e.g. "0x108A"
            val code = Integer.decode(hexValue)
            val name = map["name"] as String
            code to name
        }

        val maxCode = entries.maxOf { it.first }
        val chunkSize = 500
        val chunks = entries.chunked(chunkSize)
        val lines = buildString {
            appendLine("package com.makeramen.around.ble")
            appendLine()
            appendLine("// Generated from scripts/company_identifiers.yaml — do not edit manually.")
            appendLine("// Update source: ./gradlew updateCompanyIdentifiers")
            appendLine("// Regenerate:    ./gradlew :shared:generateCompanyDatabase")
            appendLine("object CompanyDatabase {")
            appendLine("    val companies: Array<String?> = arrayOfNulls(${maxCode + 1})")
            appendLine()
            appendLine("    init {")
            for (i in chunks.indices) {
                appendLine("        init$i(companies)")
            }
            appendLine("    }")
            appendLine()
            // Split into chunks to avoid JVM 64KB method size limit
            for ((i, chunk) in chunks.withIndex()) {
                appendLine("    private fun init$i(a: Array<String?>) {")
                for ((code, name) in chunk) {
                    val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
                    appendLine("        a[$code] = \"$escaped\"")
                }
                appendLine("    }")
                appendLine()
            }
            appendLine("}")
            appendLine()
        }
        val outFile = outputDir.get().file(
            "com/makeramen/around/ble/CompanyDatabase.kt"
        ).asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(lines)
        println("Generated ${outFile.absolutePath} with ${entries.size} entries (array size ${maxCode + 1})")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "com.makeramen.around.shared"
        compileSdk = 37
        minSdk = 31
        androidResources.enable = true
    }

    jvm("desktop")

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateCompanyDatabase.map { it.outputs.files.single() })
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.material.icons.core)
                implementation(libs.compose.ui)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.compose.components.resources)
                implementation(libs.lifecycle.viewmodel.compose)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.compose.ui.tooling)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kable.core)
            }
        }
    }
}
