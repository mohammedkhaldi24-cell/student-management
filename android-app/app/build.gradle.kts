plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mobileBaseUrl = (project.findProperty("MOBILE_BASE_URL") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "http://10.0.2.2:8081/"

val mobileLanBaseUrl = (project.findProperty("MOBILE_LAN_BASE_URL") as String?)
    ?.trim()
    ?: ""

android {
    namespace = "com.pfe.gestionetudiantmobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pfe.gestionetudiantmobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"${mobileBaseUrl}\"")
        buildConfigField("String", "LAN_BASE_URL", "\"${mobileLanBaseUrl}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-ktx:1.9.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
