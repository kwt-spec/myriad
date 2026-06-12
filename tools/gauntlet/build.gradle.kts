plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.cauldron.myriad.gauntlet.MainKt")
}

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:content"))
}
