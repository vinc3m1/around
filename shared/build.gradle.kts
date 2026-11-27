import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

compose.resources {
    publicResClass = true
}

val generateCompanyDatabase by tasks.registering {
    val inputFile = rootProject.file("scripts/company_ids.json")
    val outputDir = layout.buildDirectory.dir("generated/companydb")
    inputs.file(inputFile)
    outputs.dir(outputDir)

    doLast {
        val entries = (JsonSlurper().parse(inputFile) as List<*>).map { entry ->
            val row = entry as Map<*, *>
            val code = (row["code"] as Number).toInt()
            require(code in 0..0xFFFF) { "Invalid Bluetooth company identifier" }
            code to (row["name"] as String)
        }.filterNot { it.first == 0xFFFF } // Reserved value, not a company.
        require(entries.map { it.first }.toSet().size == entries.size) {
            "Duplicate Bluetooth company identifier"
        }

        val maxCode = entries.maxOfOrNull { it.first } ?: -1
        val chunkSize = 500
        val chunks = entries.chunked(chunkSize)
        val lines = buildString {
            appendLine("package com.makeramen.around.ble")
            appendLine()
            appendLine("// Generated from scripts/company_ids.json — do not edit manually.")
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
                    val escaped = buildString {
                        for (ch in name) {
                            when (ch) {
                                '\\' -> append("\\\\")
                                '"' -> append("\\\"")
                                '$' -> { append('\\'); append('$') }
                                '\n' -> append("\\n")
                                '\r' -> append("\\r")
                                '\t' -> append("\\t")
                                else -> if (ch.isISOControl()) {
                                    append("\\u")
                                    append(ch.code.toString(16).padStart(4, '0'))
                                } else {
                                    append(ch)
                                }
                            }
                        }
                    }
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
