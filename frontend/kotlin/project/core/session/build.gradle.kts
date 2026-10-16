plugins { alias(libs.plugins.android.library) }
android {
    namespace = "dev.mobilefoundry.session"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:kernel"))
    api(project(":core:http"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
tasks.withType<Test>().configureEach {
    systemProperty("session.fixtures", rootProject.file("../../../contracts/fixtures/session").absolutePath)
    systemProperty("account.fixtures", rootProject.file("../../../contracts/fixtures/account-access").absolutePath)
    systemProperty("go.session.fixtures", rootProject.file("../../../contracts/fixtures/go-session").absolutePath)
    systemProperty("go.session.test.base", providers.environmentVariable("GO_SESSION_TEST_BASE").getOrElse(""))
}
