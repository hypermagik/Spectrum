// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.benchmark) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

val javaToolchainVersion: Provider<String> = providers.gradleProperty("java.toolchain.version")
if (javaToolchainVersion.isPresent) {
    configure(subprojects) {
        plugins.withType<com.android.build.gradle.BasePlugin>().configureEach {
            extensions.findByType<com.android.build.gradle.BaseExtension>()?.apply {
                javaToolchainVersion.map { JavaVersion.toVersion(it) }.orNull?.let {
                    compileOptions {
                        sourceCompatibility = it
                        targetCompatibility = it
                    }

                }
            }
            extensions.findByType<JavaPluginExtension>()?.apply {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(javaToolchainVersion.get())
                }
            }
        }
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
            compilerOptions {
                jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaToolchainVersion.get())
            }
        }
    }
}