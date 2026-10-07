plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    systemProperty("foundry.fixtures", rootProject.file("../../../contracts/fixtures/kernel").absolutePath)
}
