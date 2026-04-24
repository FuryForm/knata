plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "com.github.FuryForm"
version = "0.1.0"

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.kotlinx.serialization.json)
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
            }
        }
        val androidMain by getting
    }
}

android {
    namespace = "com.furyform.knata"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ── JitPack / Maven publishing ────────────────────────────────────────────────
afterEvaluate {
    publishing {
        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set("Knata")
                description.set("Pure Kotlin Multiplatform JSONata expression engine")
                url.set("https://github.com/FuryForm/knata")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}

