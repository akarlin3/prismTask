plugins {
    kotlin("jvm") version "2.3.20"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.6")
}
