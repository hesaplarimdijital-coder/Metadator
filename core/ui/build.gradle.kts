plugins {
    id("metadator.android.library")
    id("metadator.android.compose")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:designsystem"))
    // The cover finder's sheet shows what the domain found: CoverSuggestion is in its signature.
    api(project(":core:domain"))
    api(project(":tags:api"))
    implementation(project(":core:common"))
    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette)
    api(libs.bundles.lifecycle)
    implementation(libs.kotlinx.coroutines.android)
}
