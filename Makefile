.DEFAULT_GOAL := help

FOUNDRY_ANDROID_JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
FOUNDRY_ANDROID_SDK ?= $(HOME)/Library/Android/sdk
FOUNDRY_PYTHON ?= python3

.PHONY: help kernel-build ios-generate ios-build android-build android-test notes-check

help:
	@echo 'make kernel-build    Build the Swift kernel package'
	@echo 'make ios-generate    Regenerate the iOS project from project.yml'
	@echo 'make ios-build       Build the iOS catalog for the simulator'
	@echo 'make android-build   Build the Kotlin kernel and Android debug APK'
	@echo 'make android-test    Run Android starter unit tests'
	@echo 'make notes-check     Check learning notes and documentation links'

kernel-build:
	swift build --package-path frontend/swift/packages/FoundryKernel

ios-generate:
	cd frontend/swift/apps/FoundryCatalog && xcodegen generate

ios-build:
	xcodebuild -project frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj \
		-scheme FoundryCatalog -destination 'generic/platform=iOS Simulator' \
		-derivedDataPath .cache/ios -configuration Debug CODE_SIGNING_ALLOWED=NO -quiet build

android-build:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :core:kernel:build :app:assembleDebug

android-test:
	cd frontend/kotlin/project && \
		JAVA_HOME="$(FOUNDRY_ANDROID_JAVA_HOME)" ANDROID_HOME="$(FOUNDRY_ANDROID_SDK)" \
		./gradlew :app:testDebugUnitTest

notes-check:
	$(FOUNDRY_PYTHON) -B scripts/check_notes.py
