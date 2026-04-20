# Knata

Knata is a Kotlin Multiplatform implementation of a JSONata-compatible expression engine. It includes a Kotlin library module and an Android sample app demonstrating dynamic JSON query and transformation usage.

## Repository Structure

- `knata/` - Kotlin Multiplatform library module
- `sample/` - Android Compose sample app using the Knata library
- `testdata/` - JSON test files used for validation and test coverage
- `jitpack.yml` - JitPack build configuration

## Getting Started

### Requirements

- JDK 17
- Android SDK 34
- Gradle 8.9 (wrapper included)

### Build

From the repository root on macOS/Linux:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

### Install Android Sample

```powershell
.\gradlew.bat installDebug
```

## Publish on JitPack

This repository includes the required `jitpack.yml` configuration. To use Knata from JitPack, add the following to your Gradle project:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.FuryForm:knata:main'
}
```

Replace `main` with a release tag once you create one in the GitHub repository.

## License

MIT License
