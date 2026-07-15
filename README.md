# NeuroStats

![Passing](https://img.shields.io/badge/build-passing-brightgreen)

NeuroStats is a professional Android application designed for neuro-scientific EEG data ingestion, real-time monitoring, and analysis. It serves as a mobile, clinical-grade biofeedback tool based on the core logic previously developed in Python.

## System Overview

This application leverages modern Android development best practices to ensure high performance, maintainability, and data security:

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose for declarative and reactive UI.
- **Design System**: Material Design 3 (M3) for a modern, accessible interface.
- **Data Persistence**: Room Database for secure, local storage of EEG data and analysis results, ensuring compliance with clinical standards.
- **Asynchronous Processing**: Kotlin Coroutines and Flow for handling real-time data streams efficiently.

## Mathematical Specification

The system implements core logic for:
- **Cognitive System Load W(t)**: Real-time calculation and monitoring of cognitive load during EEG tasks.
- **Trajectory Validation**: Continuous tracking and validation of neural signal trajectories to ensure data integrity during clinical recording sessions.

## Build and Test Instructions

This project uses the Gradle build system.

1. Clone the repository.
2. Ensure you have the Android SDK configured.
3. Build the application using:
   ```bash
   ./gradlew assembleDebug
   ```

4. Run the validation tests using:
   ```bash
   ./gradlew testDebugUnitTest
   ```

## Copyright and License

Copyright (c) 2026 Patrick Frank Buckreus (MMSI).

All rights reserved.

This software is provided for academic peer-review and validation purposes only.
Any commercial use, modification, or redistribution of this source code, in whole or in part,
without prior written agreement is strictly prohibited.
