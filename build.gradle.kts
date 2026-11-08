// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false

    // Hilt: inyección de dependencias.
    id("com.google.dagger.hilt.android") version "2.60.1" apply false

    // KSP: procesador de anotaciones, actualizado para AGP 9.
    alias(libs.plugins.ksp) apply false
}