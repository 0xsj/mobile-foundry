plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:http"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
tasks.test { systemProperty("foundry.http.fixtures", rootProject.file("../../../contracts/fixtures/http").absolutePath) }
