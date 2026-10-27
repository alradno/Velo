plugins { id("com.android.application") }
android {
    namespace = "com.alberto.velo.audiolab"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.alberto.velo.audiolab"
        minSdk = 31
        targetSdk = 36
        versionName = "0.1-lab"
    }
    buildFeatures { buildConfig = true }
    flavorDimensions += "network"
    productFlavors {
        create("prepare") {
            dimension = "network"
            versionCode = 1
            buildConfigField("boolean", "ALLOW_DOWNLOADS", "true")
        }
        create("offline") {
            dimension = "network"
            versionCode = 2
            buildConfigField("boolean", "ALLOW_DOWNLOADS", "false")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("com.google.mlkit:translate:17.0.3") }
