pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AutoBuyAssistant"

// App module
include(":app")

// Core modules
include(":core:common")
include(":core:data")
include(":core:security")
include(":core:accessibility")

// Feature modules
include(":feature:config")
include(":feature:dashboard")
include(":feature:recorder")
