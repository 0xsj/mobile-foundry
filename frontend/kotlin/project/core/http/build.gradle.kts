plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:kernel"))
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
tasks.test { systemProperty("foundry.http.fixtures", rootProject.file("../../../contracts/fixtures/http").absolutePath) }
