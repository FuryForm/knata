// Root project IS the library — consumed as com.github.FuryForm:knata
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    // Sub-project plugins (applied in :sample only)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}

group = "com.github.FuryForm"
version = "0.4.0"

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDirs("knata/src/commonMain/kotlin")
    }
    test {
        kotlin.srcDirs("knata/src/jvmTest/kotlin")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Knata")
                description.set("Pure Kotlin JSONata expression engine")
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
