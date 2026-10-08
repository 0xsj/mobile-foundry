plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:kernel"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
tasks.test {
    systemProperty("foundry.query.fixtures", rootProject.file("../../../contracts/fixtures/query").absolutePath)
}
