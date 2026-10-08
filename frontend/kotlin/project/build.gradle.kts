// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}

// fwcd's Kotlin language server adds these tasks through an init script and
// reads live Project state in their actions. Keep normal builds cached while
// allowing the editor to resolve its dependency classpaths.
allprojects {
  tasks.matching { it.name.startsWith("kotlinLSP") }.configureEach {
    notCompatibleWithConfigurationCache("Kotlin language server tasks inspect Project state at execution time")
  }
}
