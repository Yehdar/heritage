plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "com.app.familytree"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.app.familytree"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    implementation(libs.activity)
    implementation(libs.lifecycle)
    implementation(libs.coil)
    implementation(libs.coil.network)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test)
}
