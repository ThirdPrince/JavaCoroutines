plugins {
    kotlin("jvm") version "2.1.21"
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        java.srcDir("src")
        kotlin.srcDir("src")
    }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("Main")
}

tasks.register<JavaExec>("runKotlinDemo") {
    group = "application"
    description = "Runs the Kotlin Retrofit coroutine demo."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("WanAndroidKotlinDemoKt")
}

tasks.register<JavaExec>("runLoomBlockingQueueDemo") {
    group = "application"
    description = "Runs the Dispatchers.LOOM + BlockingQueue demo."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("LoomBlockingQueueDemoKt")
}
