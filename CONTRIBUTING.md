# Contributing

## Development workflow

1. Create a branch from `main`.
2. Keep Gradle parsing and rendering logic independent from IntelliJ UI classes.
3. Add regression tests for every supported syntax or fixed failure mode.
4. Run `./gradlew check buildPlugin verifyPlugin` with JDK 21.
5. Open a pull request describing the affected Gradle syntax and safety behavior.

## Safety rules

- Never turn a read or parse error into empty file content.
- Prepare and validate all changes before mutating an IDE document.
- Preserve files byte-for-byte when a declaration is unsupported.
- Normalize and compare complete Gradle module paths; do not use substring matching.
- Keep file I/O away from the Swing event-dispatch thread.

## Tests

Parser fixtures should cover both Kotlin and Groovy DSL when applicable. Include cases for comments, multiline declarations, nested modules, empty dynamic-feature sets, and repeated application of the renderer.
