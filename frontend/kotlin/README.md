# Kotlin foundation

The Android Gradle build is initialized in `project` with a Compose application
and a Kotlin/JVM kernel module. Build and open it using
[Setup](../../docs/SETUP.md).

`project/app` holds the Compose application scaffold and depends on
`project/core/kernel`. The kernel implements typed outcomes, failures, and
public projection under [its shared contract](../../contracts/behavior/kernel.md).
Reusable behavior and catalog examples grow with each slice. Use Kotlin/JVM libraries for
platform-independent code and Android libraries for Android APIs or Compose.

Keep one Gradle build, its wrapper, and a shared version catalog. Features
and catalog examples compose reusable modules rather than defining duplicate
versions of their components.

Keep shader code and native rendering integration with their graphics module.
Shared graphics fixtures need an explicit Android resource or asset bundling
step. Module ownership is described in [Organization](../../docs/ORGANIZATION.md).

[Learning notes](notes/README.md) provide the source reading order, Kotlin
mechanics, framework findings, and verification limits for implemented slices.
