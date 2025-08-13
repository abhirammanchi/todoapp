plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // id("com.google.devtools.ksp")
}
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val SUPABASE_URL = (localProps.getProperty("SUPABASE_URL") ?: "")
val SUPABASE_ANON_KEY = (localProps.getProperty("SUPABASE_ANON_KEY") ?: "")


android {
    namespace = "com.example.todomoji"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.todomoji"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"$SUPABASE_URL\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$SUPABASE_ANON_KEY\"")
            buildConfigField("String", "OPENAI_API_KEY", "\"${System.getenv("OPENAI_API_KEY") ?: ""}\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            buildConfigField("String", "SUPABASE_URL", "\"$SUPABASE_URL\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$SUPABASE_ANON_KEY\"")
            buildConfigField("String", "OPENAI_API_KEY", "\"\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources {
            // what you had:
            excludes += "/META-INF/{AL2.0,LGPL2.1}"

            // add these to avoid duplicate META‑INF files:
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}
kotlin {
    // Kotlin 2.0+ recommended way
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // (optional) languageVersion, freeCompilerArgs, etc.
        // languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0) // not required
    }
}
dependencies {
val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
implementation(composeBom)
androidTestImplementation(composeBom)

implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.activity:activity-compose:1.9.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

// Compose UI
// Compose UI + Material
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")              // M3 (BOM-managed)
implementation("androidx.compose.material:material")                 // M2 (for SwipeToDismiss)
implementation("androidx.compose.material:material-icons-extended")  // icons (single line)
implementation("androidx.compose.ui:ui-tooling-preview")
debugImplementation("androidx.compose.ui:ui-tooling")
implementation("androidx.navigation:navigation-compose:2.7.7")

// (Optional) Android Views Material – only if you use classic Views anywhere
implementation("com.google.android.material:material:1.12.0")

// Animations & accompanist
implementation("com.google.accompanist:accompanist-permissions:0.34.0")

// Coil for images
implementation("io.coil-kt:coil-compose:2.6.0")

// Room (KSP)
// implementation("androidx.room:room-runtime:2.6.1")
// implementation("androidx.room:room-ktx:2.6.1")
// ksp("androidx.room:room-compiler:2.6.1")

// CameraX (using preview capture for simplicity)
implementation("androidx.camera:camera-core:1.3.4")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")

// OpenAI Java SDK (Responses + Images)
implementation("com.openai:openai-java:3.0.2")
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// --- Supabase (open-source, free) ---
implementation(platform("io.github.jan-tennert.supabase:bom:3.2.2"))
implementation("io.github.jan-tennert.supabase:auth-kt")
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:storage-kt")
implementation("io.github.jan-tennert.supabase:realtime-kt")
implementation("io.github.jan-tennert.supabase:functions-kt")

// Ktor engine for Supabase client
implementation("io.ktor:ktor-client-cio:2.3.12")

// If minSdk < 26 (you are 24): enable desugaring
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")


}



