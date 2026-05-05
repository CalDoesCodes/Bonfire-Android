# Readme

![Platform](https://img.shields.io/badge/platform-Android-brightgreen.svg?color=00ADB5&style=for-the-badge)
![Repo Size](https://img.shields.io/github/repo-size/dev-aniketj/Weather-App?color=00ADB5&style=for-the-badge)

Android build for the Bonfire app, a messaging app being developed for Software Engineering Principles and Practice.

# Installation

git clone <https://github.com/UWP-Bonfire/Bonfire-Android> 

Open project in android studio and hit 'run'.

# Project Structure 
```.
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/bonfire/
│   │   │   │   ├── ui/            # UI components (activities, fragments, screens)
│   │   │   │   └── ui/theme/      # App theming (colors, typography, styling)
│   │   │   ├── res/               # XML resources
│   │   │   │   ├── layout/        # UI layouts
│   │   │   │   ├── drawable/      # Images & shapes
│   │   │   │   ├── menu/          # Menu resources
│   │   │   │   ├── navigation/    # Navigation graph
│   │   │   │   ├── values/        # Strings, colors, styles
│   │   │   │   └── xml/           # Config files
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── androidTest/           # Instrumented tests
│   │   └── test/                  # Unit tests
│   │
│   └── build/                    # Generated build files (ignored)
│
├── gradle/
│   └── wrapper/                  # Gradle wrapper files
│
└── build.gradle / settings.gradle
```
