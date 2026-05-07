// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.4.4" apply false
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "UWP-Bonfire_Bonfire-Android")
        property("sonar.organization", "uwp-bonfire")
        property("sonar.projectName", "Bonfire")
        property("sonar.projectVersion", "1.0")
        property("sonar.host.url", "http://localhost:9000")
        property("sonar.token", "your-sonar-token")
        property("sonar.sourceEncoding", "UTF-8")
        // These paths point to the :app module's outputs
        property("sonar.androidLint.reportPaths", "app/build/reports/lint-results-debug.xml")
        property("sonar.coverage.jacoco.xmlReportPaths", "app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
    }
}
