plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
tasks.test {
    systemProperty("physics.fixtures", rootProject.file("../../../contracts/fixtures/physics").absolutePath)
}
