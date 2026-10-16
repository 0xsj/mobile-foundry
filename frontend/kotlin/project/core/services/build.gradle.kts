plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:http"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
tasks.test {
    systemProperty("foundry.remote.notes.fixtures", rootProject.file("../../../contracts/fixtures/remote-notes").absolutePath)
    providers.environmentVariable("GO_SESSION_TEST_BASE").orNull?.let { systemProperty("go.notes.test.base", it) }
    systemProperty("foundry.http.fixtures", rootProject.file("../../../contracts/fixtures/http").absolutePath)
    systemProperty("foundry.notes.fixtures", rootProject.file("../../../contracts/fixtures/notes").absolutePath)
}
