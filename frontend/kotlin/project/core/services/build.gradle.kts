plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:http"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
tasks.test {
    inputs.file(rootProject.file("../../../contracts/fixtures/notes-read-cache/snapshots.json"))
    systemProperty("notes.read.cache.fixtures", rootProject.file("../../../contracts/fixtures/notes-read-cache").absolutePath)
    inputs.file(rootProject.file("../../../contracts/fixtures/pending-notes/records.json"))
    systemProperty("foundry.pending.notes.fixtures", rootProject.file("../../../contracts/fixtures/pending-notes").absolutePath)
    inputs.file(rootProject.file("../../../contracts/fixtures/system-notifications/remote-payloads.json"))
    inputs.file(rootProject.file("../../../contracts/fixtures/system-notifications/payloads.json"))
    systemProperty("system.notification.fixtures", rootProject.file("../../../contracts/fixtures/system-notifications").absolutePath)
    inputs.file(rootProject.file("../../../contracts/fixtures/installations/records.json"))
    systemProperty("installation.fixtures", rootProject.file("../../../contracts/fixtures/installations").absolutePath)
    inputs.file(rootProject.file("../../../contracts/fixtures/notifications/pages.json"))
    systemProperty("foundry.inbox.fixtures", rootProject.file("../../../contracts/fixtures/notifications").absolutePath)
    systemProperty("foundry.remote.notes.fixtures", rootProject.file("../../../contracts/fixtures/remote-notes").absolutePath)
    providers.environmentVariable("GO_SESSION_TEST_BASE").orNull?.let { systemProperty("go.notes.test.base", it) }
    systemProperty("foundry.http.fixtures", rootProject.file("../../../contracts/fixtures/http").absolutePath)
    systemProperty("foundry.notes.fixtures", rootProject.file("../../../contracts/fixtures/notes").absolutePath)
}
