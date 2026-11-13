plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// La cle de signature reste HORS du depot (jamais publiee). Elle est lue dans l'environnement :
// FAMILLE_KEYSTORE (chemin), FAMILLE_STOREPASS, FAMILLE_KEYALIAS, FAMILLE_KEYPASS.
val cleChemin: String? = System.getenv("FAMILLE_KEYSTORE")
val cleDispo = cleChemin != null && file(cleChemin).exists()

android {
    namespace = "fr.famille"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "role"
    productFlavors {
        create("enfant") {
            dimension = "role"
            applicationId = "fr.famille.enfant"
            resValue("string", "app_name", "Famille Enfant")
            buildConfigField("String", "ROLE", "\"enfant\"")
        }
        create("parent") {
            dimension = "role"
            applicationId = "fr.famille.parent"
            resValue("string", "app_name", "Famille Parent")
            buildConfigField("String", "ROLE", "\"parent\"")
        }
    }

    signingConfigs {
        if (cleDispo) {
            create("famille") {
                storeFile = file(cleChemin!!)
                storePassword = System.getenv("FAMILLE_STOREPASS")
                keyAlias = System.getenv("FAMILLE_KEYALIAS")
                keyPassword = System.getenv("FAMILLE_KEYPASS")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (cleDispo) signingConfig = signingConfigs.getByName("famille")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (cleDispo) signingConfig = signingConfigs.getByName("famille")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
