plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.5"
    application
}

group = "dev.fritzctl"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("com.github.ajalt.mordant:mordant:2.7.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.1.0")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("fritzctl.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("fritzctl")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}
