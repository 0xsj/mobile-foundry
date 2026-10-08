plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:http"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
tasks.test {
    systemProperty("foundry.http.fixtures", rootProject.file("../../../contracts/fixtures/http").absolutePath)
    systemProperty("foundry.notes.fixtures", rootProject.file("../../../contracts/fixtures/notes").absolutePath)
}
