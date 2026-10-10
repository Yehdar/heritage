plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
val firebaseConfig = file("google-services.json")
if (firebaseConfig.exists()) apply(plugin = "com.google.gms.google-services")
val shareHost = if (firebaseConfig.exists()) {
    val config = groovy.json.JsonSlurper().parse(firebaseConfig) as Map<*, *>
    "${(config["project_info"] as Map<*, *>)["project_id"]}.web.app"
} else "heritage.invalid"
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
        manifestPlaceholders["shareHost"] = shareHost
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines)
    implementation(libs.serialization)
    implementation(libs.coroutines.tasks)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.qr)
    implementation(libs.qr.scanner)
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
    androidTestImplementation(libs.espresso)
}
