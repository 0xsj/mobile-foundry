.DEFAULT_GOAL := help

FOUNDRY_ANDROID_JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
FOUNDRY_ANDROID_SDK ?= $(HOME)/Library/Android/sdk
FOUNDRY_PYTHON ?= python3
FOUNDRY_IOS_TEST_DESTINATION ?= platform=iOS Simulator,name=iPhone 17 Pro
CLI_PREFIX ?= $(HOME)/.local

.PHONY: cli-test cli-install
.PHONY: physics-test physics-ui-test physics-ios-ui-test
.PHONY: go-dev go-check go-test
.PHONY: installations-ui-test
.PHONY: system-notifications-ui-test
.PHONY: push-tokens-ui-test
.PHONY: remote-notifications-ios-test remote-notifications-android-test

remote-notifications-ios-test: ios-generate
	python3 -B scripts/remote_notifications_ios_test.py

remote-notifications-android-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.systemnotifications.RemoteNotificationTest,dev.mobilefoundry.catalog.systemnotifications.SystemNotificationTest

push-tokens-ui-test: ios-generate
	xcodebuild -project frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj \
		-scheme FoundryCatalog -destination '$(FOUNDRY_IOS_TEST_DESTINATION)' \
		-derivedDataPath .cache/ios -configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- \
		-quiet -only-testing:FoundryCatalogTests/PushTokenTests \
		-only-testing:FoundryCatalogUITests/PushTokenUITests test
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.installations.PushTokenTest,dev.mobilefoundry.catalog.installations.PushTokenUITest

system-notifications-ui-test: ios-generate
	xcodebuild -project frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj \
		-scheme FoundryCatalog -destination '$(FOUNDRY_IOS_TEST_DESTINATION)' \
		-derivedDataPath .cache/ios -configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- \
		-quiet -only-testing:FoundryCatalogTests/SystemNotificationAdmissionTests \
		-only-testing:FoundryCatalogUITests/SystemNotificationUITests test
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.systemnotifications.SystemNotificationTest

installations-ui-test: ios-generate
	xcodebuild -project frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj \
		-scheme FoundryCatalog -destination '$(FOUNDRY_IOS_TEST_DESTINATION)' \
		-derivedDataPath .cache/ios -configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- \
		-quiet -only-testing:FoundryCatalogTests/InstallationVaultTests test
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.installations.InstallationVaultTest

go-dev:
	$(MAKE) -C backend/go dev

go-check:
	$(MAKE) -C backend/go check

go-test:
	$(MAKE) -C backend/go test

cli-test:
	$(FOUNDRY_PYTHON) -B -m unittest discover -s cli/tests -v

cli-install:
	$(FOUNDRY_PYTHON) -B scripts/install_cli.py --prefix "$(CLI_PREFIX)"

.PHONY: product-ios-generate product-ios-build product-ios-test product-android-build product-android-ui-test starter-check

.PHONY: help kernel-build kernel-test http-test query-test data-test data-ui-test observation-test observation-ui-test session-test session-ui-test account-test account-ui-test shell-ui-test ui-test graphics-test assets-sync assets-check ios-generate ios-build ios-test android-build android-test android-ui-test notes-check

help:
	@echo 'make remote-notifications-ios-test Verify injected remote and local iOS opens'
	@echo 'make remote-notifications-android-test Verify remote/local Android notification opens'
	@echo 'make push-tokens-ui-test Verify native token bridges and missing-setup UI'
	@echo 'make go-dev          Run the Go runtime and health endpoints'
	@echo 'make go-check        Check Go formatting, architecture, vet and build'
	@echo 'make go-test         Run Go foundation tests with the race detector'
	@echo 'make cli-test        Check native/Go CLI admission, copying and cleanup'
	@echo 'make cli-install     Link mobile-foundry into CLI_PREFIX/bin (default ~/.local)'
	@echo 'make product-ios-generate Regenerate the clean iOS product project'
	@echo 'make product-ios-build Build the clean iOS product for the simulator'
	@echo 'make product-ios-test Run the clean iOS product native checks'
	@echo 'make product-android-build Build the clean Android product APK'
	@echo 'make product-android-ui-test Run clean product interactions on a device'
	@echo 'make starter-check   Check candidate extraction inventory and closure'
	@echo 'make kernel-build    Build the Swift kernel package'
	@echo 'make kernel-test     Run Swift and Kotlin kernel behavioral tests'
	@echo 'make http-test       Run Swift and Kotlin HTTP and domain service tests'
	@echo 'make query-test      Run Swift and Kotlin query/mutation value tests'
	@echo 'make data-test       Run Swift storage and native service/state-owner tests'
	@echo 'make data-ui-test    Run Android SQLite and Local notes UI tests on a device'
	@echo 'make observation-test Run local repository observation and reader unit checks'
	@echo 'make observation-ui-test Run Android live readers and account lifecycle checks'
	@echo 'make session-test    Run Swift and Kotlin session lifecycle tests'
	@echo 'make go-native-test  Verify native sessions/Notes/Files against an owned Go API'
	@echo 'make installations-ui-test Run native installation Keychain/Keystore checks'
	@echo 'make system-notifications-ui-test Run native notification/permission/open checks'
	@echo 'make go-native-device-test Add iOS/Android Go catalog and native-vault checks'
	@echo 'make session-ui-test Run Android Keystore and session UI tests on a device'
	@echo 'make account-test    Run session/HTTP, account storage and feature unit checks'
	@echo 'make account-ui-test Run Android account, credential and Notes native checks'
	@echo 'make shell-ui-test   Run Android product shells and shared shell UI checks'
	@echo 'make ui-test         Run Swift and Kotlin token/material contract tests'
	@echo 'make graphics-test   Run graphics policies and native Metal shader tests'
	@echo 'make physics-test    Run Swift and Kotlin fixed-step toss physics tests'
	@echo 'make physics-ui-test Run Android toss controls and pause/reset checks'
	@echo 'make physics-ios-ui-test Run iPhone toss gestures and pause/reset checks'
	@echo 'make assets-sync     Author the lamp and synchronize bundled graphics assets'
	@echo 'make assets-check    Check asset hashes and both catalog copies'
	@echo 'make ios-generate    Regenerate the iOS project from project.yml'
	@echo 'make ios-build       Build the iOS catalog for the simulator'
	@echo 'make ios-test        Run iOS catalog state-owner tests on a simulator'
	@echo 'make android-build   Build the native core modules and Android debug APK'
	@echo 'make android-test    Run Kotlin core and Android unit tests'
	@echo 'make android-ui-test Run Compose tests on a booted emulator/device'
	@echo 'make notes-check     Check learning notes and documentation links'

kernel-build:
	swift build --package-path frontend/swift/packages/FoundryKernel

kernel-test:
	swift test --package-path frontend/swift/packages/FoundryKernel
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:kernel:test

http-test:
	swift test --package-path frontend/swift/packages/FoundryHTTP
	swift test --package-path frontend/swift/packages/FoundryServices
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:http:test :core:services:test

query-test:
	swift test --package-path frontend/swift/packages/FoundryQuery
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:query:test

data-test:
	swift test --package-path frontend/swift/packages/FoundryData
	swift test --package-path frontend/swift/packages/FoundryServices
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:services:test :app:testDebugUnitTest

observation-test: data-test

observation-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.data.NotesObservationTest,dev.mobilefoundry.catalog.ui.notes.LiveNotesScreenTest,dev.mobilefoundry.catalog.data.AccountNotesRepositoryTest,dev.mobilefoundry.catalog.data.LocalNotesRepositoryTest,dev.mobilefoundry.catalog.session.KeystoreCredentialVaultTest,dev.mobilefoundry.catalog.ui.session.SessionCatalogScreenTest,dev.mobilefoundry.catalog.ui.notes.LocalNotesScreenTest,dev.mobilefoundry.catalog.ui.shell.AppShellTest

data-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.data.LocalNotesRepositoryTest,dev.mobilefoundry.catalog.ui.notes.LocalNotesScreenTest

ui-test:
	swift test --package-path frontend/swift/packages/FoundryUI
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:ui:testDebugUnitTest

session-test:
	swift test --package-path frontend/swift/packages/FoundrySession
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:session:testDebugUnitTest

.PHONY: go-native-test go-native-device-test
go-native-test:
	python3 -B scripts/go_native_test.py

go-native-device-test:
	python3 -B scripts/go_native_test.py --devices

session-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.session.KeystoreCredentialVaultTest,dev.mobilefoundry.catalog.ui.session.SessionCatalogScreenTest

account-test:
	swift test --package-path frontend/swift/packages/FoundrySession
	swift test --package-path frontend/swift/packages/FoundryData
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:session:testDebugUnitTest :app:testDebugUnitTest

account-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.data.AccountNotesRepositoryTest,dev.mobilefoundry.catalog.data.LocalNotesRepositoryTest,dev.mobilefoundry.catalog.session.KeystoreCredentialVaultTest,dev.mobilefoundry.catalog.ui.session.SessionCatalogScreenTest,dev.mobilefoundry.catalog.ui.notes.LocalNotesScreenTest

shell-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.ui.shell.patterns.ProductShellCatalogTest,dev.mobilefoundry.catalog.ui.shell.AppShellTest,dev.mobilefoundry.catalog.ui.components.ShellCatalogTest,dev.mobilefoundry.catalog.ui.components.ShellComponentTest

graphics-test:
	swift test --package-path frontend/swift/packages/FoundryGraphics
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:graphics:testDebugUnitTest

physics-test:
	swift test --package-path frontend/swift/packages/FoundryPhysics
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:physics:test

physics-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.ui.physics.PhysicsPlaygroundTest

physics-ios-ui-test:
	xcodebuild -project frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj \
		-scheme FoundryCatalog -destination '$(FOUNDRY_IOS_TEST_DESTINATION)' \
		-derivedDataPath .cache/ios -configuration Debug -parallel-testing-enabled NO \
		-only-testing:FoundryCatalogUITests/PhysicsPlaygroundUITests \
		CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- -quiet test

ios-generate:
	cd frontend/swift/apps/FoundryCatalog && xcodegen generate

ios-build:
	xcodebuild -project frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj \
		-scheme FoundryCatalog -destination 'generic/platform=iOS Simulator' \
		-derivedDataPath .cache/ios -configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- -quiet build

ios-test:
	xcodebuild -project frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj \
		-scheme FoundryCatalog -destination '$(FOUNDRY_IOS_TEST_DESTINATION)' \
		-derivedDataPath .cache/ios -configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- -quiet test

android-build:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:kernel:build :core:http:build :core:services:build :core:query:build :core:data:assembleDebug :core:session:assembleDebug :core:ui:assembleDebug :core:graphics:assembleDebug :app:assembleDebug

android-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:kernel:test :core:http:test :core:services:test :core:query:test :core:physics:test :core:session:testDebugUnitTest :core:ui:testDebugUnitTest :core:graphics:testDebugUnitTest :app:testDebugUnitTest

android-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest

assets-sync:
	$(FOUNDRY_PYTHON) -B scripts/sync_graphics_assets.py

assets-check:
	$(FOUNDRY_PYTHON) -B scripts/sync_graphics_assets.py --check

notes-check:
	$(FOUNDRY_PYTHON) -B scripts/check_notes.py

starter-check:
	$(FOUNDRY_PYTHON) -B scripts/check_starter_profile.py

product-ios-generate:
	cd frontend/swift/apps/ProductApp && xcodegen generate

product-ios-build:
	xcodebuild -project frontend/swift/apps/ProductApp/ProductApp.xcodeproj \
		-scheme ProductApp -destination 'generic/platform=iOS Simulator' \
		-derivedDataPath .cache/product-ios -configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- -quiet build

product-ios-test:
	xcodebuild -project frontend/swift/apps/ProductApp/ProductApp.xcodeproj \
		-scheme ProductApp -destination '$(FOUNDRY_IOS_TEST_DESTINATION)' \
		-derivedDataPath .cache/product-ios -configuration Debug CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- -quiet test

product-android-build:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :product:assembleDebug

product-android-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :product:connectedDebugAndroidTest
