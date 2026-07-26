# Android Dynamic Modules

An IntelliJ Platform plugin that lets Android developers enable or disable Gradle modules from the IDE. When selected modules are Android dynamic features, the plugin also keeps the base application's `dynamicFeatures` declaration aligned and starts a Gradle sync.

This repository accompanies [How I Reduced Android Compilation Time by 70%](https://medium.com/@fazi.ruben/from-frustration-to-efficiency-how-i-reduced-android-compilation-time-by-70-e0a57fd0bb09), but the implementation has been hardened for use outside the original sample project.

## What it supports

- `settings.gradle.kts` and `settings.gradle`.
- Kotlin and Groovy `include(...)` syntax.
- Multiple and multiline module declarations.
- Nested paths such as `:feature:payments`.
- Kotlin and Groovy Android `dynamicFeatures` declarations.
- Required modules, hidden modules, custom application directories, and configurable dynamic-feature prefixes.
- A fail-closed change plan: every target is parsed and validated before any IDE document is changed.
- One grouped IDE write command followed by Gradle sync only when files changed.

## Usage

1. Open a Gradle project in Android Studio or IntelliJ IDEA with the Gradle plugin installed.
2. Select **Tools → Manage Android Modules**.
3. Choose the modules to keep enabled, or apply one of the presets.
4. Select **Apply and Sync**.

The plugin normalizes module paths to Gradle's `:module:path` form. Required modules cannot be deselected. Hidden modules preserve their current state and are not shown in the dialog.

The Gradle files are version-controlled project files. Review or revert their changes with the IDE or Git before committing them.

## Project configuration

Add an optional `.android-dynamic-modules.properties` file to the project root:

```properties
applicationModule=:app
# Optional when the application module has a custom projectDir.
# applicationModuleDirectory=applications/mobile
requiredModules=:app,:core
hiddenModules=:build-logic
dynamicFeaturePrefixes=df_,dynamic_
```

All module lists are comma-separated. Without this file the defaults are:

- application and required module: `:app`;
- no hidden modules;
- dynamic-feature prefix: `df_`.

A module already listed in the application's `dynamicFeatures` declaration is recognized as dynamic even when it does not match a configured prefix.

## Safe editing model

The plugin never writes an empty fallback after a read error. Its workflow is:

1. resolve exactly one settings file and, when required, exactly one application build file;
2. parse all literal module declarations;
3. prepare the complete changes in memory;
4. verify that open IDE documents still match the loaded snapshot;
5. update the documents as one IDE command;
6. save and start Gradle sync.

If a file changed while the dialog was open, a dynamic-feature declaration is missing, both Groovy and Kotlin variants exist, or a statement is structurally invalid, the operation stops without applying the plan.

## Supported declaration examples

```kotlin
include(":app", ":feature:login")
// include(":optional")

android {
    dynamicFeatures += setOf(":df_payments", ":df_profile")
}
```

```groovy
include ':app', ':feature:login'

android {
    dynamicFeatures = [':df_payments', ':df_profile']
}
```

Only literal quoted module paths are managed. Computed includes, version-catalog logic, custom functions, and arbitrary Gradle expressions are intentionally left untouched.

## Development

Requirements:

- JDK 21;
- Gradle Wrapper included in this repository.

Run the quality gates:

```shell
./gradlew check buildPlugin verifyPlugin
```

Useful tasks:

```shell
./gradlew runIde
./gradlew test
./gradlew buildPlugin
```

The `app`, `optional`, and `hiddenmodule` subprojects are lightweight sandbox fixtures. Their behavior is described by the repository's `.android-dynamic-modules.properties` file.

## Compatibility

The plugin is built with IntelliJ Platform Gradle Plugin 2.x, targets IntelliJ Platform build `242` or newer, and declares a required dependency on the bundled Gradle plugin. Compatibility is checked by CI with the IntelliJ Plugin Verifier.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Please include tests for parser, renderer, or file-planning changes.
