# Tasks+

Tasks+ is an Android task-management application built with Kotlin and Jetpack Compose. It uses Firebase services for cloud features and Room for local data storage.

## Features

- Native Android interface built with Jetpack Compose and Material 3
- Firebase Authentication
- Cloud data storage with Cloud Firestore
- Local persistence with Room and DataStore
- Firebase AI Logic integration
- Firebase App Check support
- Firebase emulator configuration for local development
- Unit and Android instrumentation tests

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- Room
- DataStore Preferences
- Firebase Authentication
- Cloud Firestore
- Firebase AI Logic
- Firebase App Check
- Gradle Kotlin DSL
- Java 11 compatibility

## Requirements

- Android Studio with Android SDK 36
- JDK 11 or newer
- An Android device or emulator running API 23 or newer
- A Firebase project for cloud and authentication features
- Firebase CLI for emulator-based development

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/fju3616372-maker/tasksplus.git
cd tasksplus
```

### 2. Configure Firebase

Place your Android app's `google-services.json` in the `app/` directory, or keep the existing project configuration in place.

To copy the example environment file:

```bash
cp .env.example .env
```

The Firebase AI Logic integration does not require a client-side Gemini API key.

### 3. Open and build the app

Open the repository in Android Studio and let Gradle sync the project.

Build the app from a terminal:

```bash
./gradlew assembleDebug
```

Install it on a connected device or emulator:

```bash
./gradlew installDebug
```

## Firebase Emulators

The repository includes Firebase emulator configuration for local development:

- Authentication: `127.0.0.1:9099`
- Firestore: `127.0.0.1:8085`

Start the emulator suite with:

```bash
firebase emulators:start
```

## Testing

Run unit tests:

```bash
./gradlew test
```

Run instrumentation tests on a connected device or emulator:

```bash
./gradlew connectedAndroidTest
```

Run one test task for the app module:

```bash
./gradlew :app:test
```

## Project Structure

```text
.
├── app/                   # Main Tasks+ Android application
│   ├── src/main/          # Application source and resources
│   ├── src/test/          # Local unit tests
│   └── src/androidTest/   # Instrumentation tests
├── docs/                  # Project documentation
├── public/                # Public project assets
├── firebase.json          # Firebase emulator configuration
├── firestore.rules        # Firestore security rules
├── firestore.test.js      # Firestore rules tests
└── victoria/              # Separate Victoria Launcher project
```

## Victoria Launcher

The repository also contains a separate Android project under `victoria/`. Its Gradle project is independent of the main Tasks+ project. See [`victoria/README.md`](victoria/README.md) for details.

## Contributing

Contributions are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make your changes and add tests where appropriate.
4. Open a pull request.

## License

No license is currently declared for the root Tasks+ project. The separate `victoria/` directory contains its own license file.
