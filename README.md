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

## Example Usage

Use Knata directly from Kotlin to evaluate a JSONata expression against parsed JSON data.

```kotlin
import com.furyform.knata.Knata
import kotlinx.serialization.json.Json

val json = """
{
  "example": [
    { "value": 4 },
    { "value": 7 },
    { "value": 13 }
  ]
}
"""

val jsonElement = Json.parseToJsonElement(json)
val data = jsonElementToKotlin(jsonElement)
val expression = "$sum(example.value)"
val result = Knata.evaluate(expression, data)
println(result)
```

> The `jsonElementToKotlin` helper converts `JsonElement` values into native Kotlin objects that Knata can evaluate.

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
