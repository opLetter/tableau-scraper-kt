plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "io.github.opletter.tableau"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.coroutines)
    implementation(libs.serialization)
    implementation(libs.bundles.ktor)
    implementation(libs.jsoup)
    api(libs.jb.dataframe)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
//    exclude("**/liveExamples/**")
}

kotlin {
    jvmToolchain(11)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing.publications {
    create<MavenPublication>("tableau-scraper-kt") {
        from(components["java"])
        artifact(sourcesJar)
    }
}