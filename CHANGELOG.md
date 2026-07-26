# Changelog

All notable changes to this project are documented in this file.

## 1.0.0 - Unreleased

### Added

- Safe parsing for Groovy and Kotlin Gradle settings.
- Dynamic-feature synchronization for Groovy and Kotlin application build files.
- Project configuration through `.android-dynamic-modules.properties`.
- Required and hidden modules, filterable UI, and light/default presets.
- Unit and regression tests plus GitHub Actions verification.

### Changed

- Migrated to IntelliJ Platform Gradle Plugin 2.x, Gradle 9.5, and JDK 21.
- Replaced placeholder plugin metadata and package names.
- Applied module changes as one validated IDE write command.

### Fixed

- Prevented missing or unreadable Gradle files from being replaced with empty content.
- Added consistent support for `build.gradle.kts` and `build.gradle`.
- Removed substring-based module matching and destructive comment replacement.
