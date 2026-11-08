import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.security.KeyStore

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val runNumber =
    providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()?.takeIf { it > 0 } ?: 1

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.billvalid.qxvyz"
    minSdk = 26
    targetSdk = 36
    versionCode = runNumber
    versionName = "1.0.$runNumber"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
      val keystorePassword =
          providers.environmentVariable("KEYSTORE_PASSWORD").orNull
              ?: providers.environmentVariable("STORE_PASSWORD").orNull
      val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
      val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
      val keystoreFile = if (!keystorePath.isNullOrBlank()) file(keystorePath) else null

      val isValidReleaseKeystore =
          if (keystoreFile != null &&
              keystoreFile.exists() &&
              keystoreFile.length() > 0L &&
              !keystorePassword.isNullOrBlank() &&
              !releaseKeyAlias.isNullOrBlank() &&
              !releaseKeyPassword.isNullOrBlank()
          ) {
            runCatching {
                  val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                  keystoreFile.inputStream().use { stream ->
                    ks.load(stream, keystorePassword.toCharArray())
                  }
                  ks.getKey(releaseKeyAlias, releaseKeyPassword.toCharArray()) != null
                }
                .getOrDefault(false)
          } else {
            false
          }

      if (isValidReleaseKeystore && keystoreFile != null) {
        storeFile = keystoreFile
        storePassword = keystorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      } else {
        println(
            "WARNING: Release signing environment variables (KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) are missing or keystore file was not found/valid. Falling back to debug signing config."
        )
        val debugKeystore = file("${rootDir}/debug.keystore")
        if (debugKeystore.exists()) {
          storeFile = debugKeystore
          storePassword = "android"
          keyAlias = "androiddebugkey"
          keyPassword = "android"
        }
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        val localRobolectricDir =
            file(
                "/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7"
            )
        if (localRobolectricDir.exists()) {
          it.systemProperty("robolectric.offline", "true")
          it.systemProperty("robolectric.dependency.dir", localRobolectricDir.absolutePath)
        }
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.security.crypto)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
