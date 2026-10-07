.DEFAULT_GOAL := help

FOUNDRY_ANDROID_JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
FOUNDRY_ANDROID_SDK ?= $(HOME)/Library/Android/sdk
FOUNDRY_PYTHON ?= python3

.PHONY: help kernel-build kernel-test http-test ios-generate ios-build android-build android-test android-ui-test notes-check

help:
	@echo 'make kernel-build    Build the Swift kernel package'
	@echo 'make kernel-test     Run Swift and Kotlin kernel behavioral tests'
	@echo 'make http-test       Run Swift and Kotlin HTTP and health service tests'
	@echo 'make ios-generate    Regenerate the iOS project from project.yml'
	@echo 'make ios-build       Build the iOS catalog for the simulator'
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

ios-generate:
	cd frontend/swift/apps/FoundryCatalog && xcodegen generate

ios-build:
	xcodebuild -project frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj \
		-scheme FoundryCatalog -destination 'generic/platform=iOS Simulator' \
		-derivedDataPath .cache/ios -configuration Debug CODE_SIGNING_ALLOWED=NO -quiet build

android-build:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:kernel:build :core:http:build :core:services:build :app:assembleDebug

android-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:kernel:test :core:http:test :core:services:test :app:testDebugUnitTest

android-ui-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:connectedDebugAndroidTest

notes-check:
	$(FOUNDRY_PYTHON) -B scripts/check_notes.py
