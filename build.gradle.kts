plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
}

val pluginGroup: String by project
val pluginVersion: String by project
val pluginDescription: String by project
val serverDir: String by project
// val javaVersionProp: String by project 

group = pluginGroup
version = pluginVersion
description = pluginDescription

val javaVersion = 21

repositories {
    mavenCentral()
    maven {
        name = "hytale-release"
        url = uri("https://maven.hytale.com/release")
    }
    maven {
        name = "hytale-pre-release"
        url = uri("https://maven.hytale.com/pre-release")
    }
}

sourceSets {
    main {
        java {
        }
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:2026.01.22-6f8bdbdc4")
    
    // Common dependencies (will be bundled in JAR)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains:annotations:24.1.0")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Storage
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.slf4j:slf4j-nop:2.0.12")
}

tasks {
    // Configure Java compilation
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(javaVersion)
    }
    
    // Configure resource processing
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        
        // Replace placeholders in manifest.json
        val props = mapOf(
            "group" to project.group,
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)
        
        filesMatching("manifest.json") {
            expand(props)
        }
    }
    
    // Configure default jar to avoid conflict with shadowJar
    jar {
        archiveClassifier.set("noshade")
    }

    // Configure ShadowJar (bundle dependencies)
    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("")
        
        // Relocate dependencies to avoid conflicts
        relocate("com.google.gson", "com.example.basicplugin.libs.gson")

    }
    
    // Configure tests
    test {
        useJUnitPlatform()
    }
    
    // Task to copy the JAR to the server plugins directory
    val copyJar by registering(Copy::class) {
        from(shadowJar)
        into("$serverDir/mods")
    }

    // Make build depend on shadowJar and run copyJar afterwards
    // Make build depend on copyJar
    build {
        dependsOn(copyJar)
    }
}

// Configure Java toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}
