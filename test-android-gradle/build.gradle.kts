buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.0.4")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.8.2.0")
    }
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
}
