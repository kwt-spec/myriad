plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:engine"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
