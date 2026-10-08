plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "dev.mobilefoundry.ui"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:query"))
    api(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
}
