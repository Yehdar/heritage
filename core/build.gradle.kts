plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(libs.coroutines)
    implementation(libs.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
