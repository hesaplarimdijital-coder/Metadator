plugins {
    id("metadator.jvm.library")
    // The fakes of the ports, for the tests of the features' ViewModels as for the domain's own.
    `java-test-fixtures`
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":tags:api"))
    api(project(":library:api"))
    api(project(":lookup:api"))
    api(project(":lyrics:api"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.turbine)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
