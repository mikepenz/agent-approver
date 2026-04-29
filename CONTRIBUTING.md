# Contributing to Agent Belay

Thank you for your interest in contributing! This guide will help you get started.

## Development Setup

### Prerequisites

- JDK 21 or later
- Git

### Building

```bash
# Clone the repository
git clone https://github.com/mikepenz/agent-belay.git
cd agent-belay

# Build the project
./gradlew build

# Run the application
./gradlew :composeApp:jvmRun

# Run tests
./gradlew :composeApp:jvmTest
```

## How to Contribute

### Reporting Bugs

- Use the [GitHub Issues](https://github.com/mikepenz/agent-belay/issues) page
- Include steps to reproduce, expected behavior, and actual behavior
- Include your OS and Java version

### Suggesting Features

- Open a [GitHub Issue](https://github.com/mikepenz/agent-belay/issues) describing the feature
- Explain the use case and why it would be valuable

### Submitting Changes

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Run tests (`./gradlew :composeApp:jvmTest`)
5. Commit your changes with a descriptive message
6. Push to your fork and open a Pull Request

## Dependency Verification

This project uses [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html) (`gradle/verification-metadata.xml`) to ensure all dependencies match known SHA-256 checksums. This protects against supply-chain attacks.

**When adding or updating dependencies**, regenerate the verification metadata:

```bash
./gradlew --write-verification-metadata sha256 resolveDependencies
```

The `resolveDependencies` task walks every project and forces resolution of every resolvable configuration (buildscript + project), so the run captures dependencies that wouldn't otherwise be touched by a normal `jvmJar`/`jvmTest` build. This makes the generated metadata stable across host systems (Linux/macOS/Windows). **Commit the updated file** alongside your dependency changes.

> **Note:** Platform-specific artifacts (Compose Desktop runtimes, Skiko) and CI-injected dependencies are covered by `<trusted-artifacts>` rules in the config since they vary by OS/arch and can't be captured from a single machine.

If the build fails with a verification error, it means a dependency's checksum doesn't match what's recorded. This could indicate:
- A dependency was updated without regenerating the metadata (run the command above)
- A dependency artifact was tampered with (investigate before proceeding)

## Code Guidelines

- Follow Kotlin coding conventions
- Write tests for new functionality
- Keep commits focused and atomic
- Use conventional commit messages (`feat:`, `fix:`, `chore:`, `docs:`)

## Data Model Compatibility

When modifying serializable data models (in `model/`), you **must** maintain backward and forward compatibility. See [AGENTS.md](AGENTS.md) for the full rules:

- New fields must have default values
- Fields cannot be removed or renamed
- Enum values can be added but not removed

## Project Structure

See [CLAUDE.md](CLAUDE.md) for a detailed architecture overview including module structure, core flow, and key packages.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
