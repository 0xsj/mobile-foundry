plugins { alias(libs.plugins.android.library) }
android {
    namespace = "dev.mobilefoundry.data"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:services"))
    api(project(":core:session"))
    implementation(libs.kotlinx.coroutines.core)
}
