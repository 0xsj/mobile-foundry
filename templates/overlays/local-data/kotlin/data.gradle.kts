plugins { alias(libs.plugins.android.library) }
android {
    namespace = "dev.mobilefoundry.data"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    sourceSets.getByName("androidTest").assets.srcDir("../../../../../contracts/fixtures/local-notes")
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:services"))
    implementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
