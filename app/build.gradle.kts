plugins {
    id("com.android.application")
}

val hasGoogleServices = file("google-services.json").exists()

if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.ezcall.onetoone"
    compileSdk = 36

    buildFeatures {
        resValues = true
    }

    defaultConfig {
        applicationId = "com.ezcall.onetoone"
        minSdk = 26
        targetSdk = 36
        versionCode = 94
        versionName = "1.94"

        if (!hasGoogleServices) {
            resValue("string", "default_web_client_id", "")
        }
    }
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.core:core-telecom:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-functions")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("io.michaelrocks:libphonenumber-android:9.0.34")
    implementation("io.getstream:stream-webrtc-android:1.3.10")
    testImplementation("junit:junit:4.13.2")
}
