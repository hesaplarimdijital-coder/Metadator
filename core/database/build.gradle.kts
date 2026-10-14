plugins {
    id("metadator.android.library")
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":tags:api"))
    implementation(project(":library:api"))
    implementation(project(":core:common"))
    // The cover checks' store is a port of the domain's own, as the lossless verdicts' is.
    implementation(project(":core:domain"))
    // MetadatorDatabase is a RoomDatabase, so whoever builds it sees the type.
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
