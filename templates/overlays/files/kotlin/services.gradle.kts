plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:http"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
tasks.test {
    inputs.file(rootProject.file("../../../contracts/fixtures/notifications/pages.json"))
    systemProperty("foundry.inbox.fixtures", rootProject.file("../../../contracts/fixtures/notifications").absolutePath)
    inputs.file(rootProject.file("../../../contracts/fixtures/notes-read-cache/snapshots.json"))
    systemProperty("notes.read.cache.fixtures", rootProject.file("../../../contracts/fixtures/notes-read-cache").absolutePath)
    inputs.file(rootProject.file("../../../contracts/fixtures/pending-notes/records.json"))
    systemProperty("foundry.pending.notes.fixtures", rootProject.file("../../../contracts/fixtures/pending-notes").absolutePath)
    systemProperty("foundry.pending.files.fixtures", rootProject.file("../../../contracts/fixtures/pending-files").absolutePath)
    systemProperty("foundry.remote.notes.fixtures", rootProject.file("../../../contracts/fixtures/remote-notes").absolutePath)
    providers.environmentVariable("GO_SESSION_TEST_BASE").orNull?.let { systemProperty("go.notes.test.base", it) }
}
