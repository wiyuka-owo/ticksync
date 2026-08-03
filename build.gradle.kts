plugins {
    alias(libs.plugins.fabric.loom)
    `maven-publish`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName = project.property("archives_base_name") as String
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.parchmentmc.org") {
        content { includeGroup("org.parchmentmc.data") }
    }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(@Suppress("UnstableApiUsage") loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${libs.versions.minecraft.get()}:${libs.versions.parchment.get()}@zip")
    })
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
}

loom {
    // Client-only code lives in src/client, so the compiler — not convention — is what stops
    // server-side code from reaching into the HUD.
    splitEnvironmentSourceSets()

    // Both source sets are one mod; without this Loom would treat them as two.
    mods {
        register("ticksync") {
            sourceSet("main")
            sourceSet("client")
        }
    }

    runs {
        // The dev client would otherwise share `run/` with the server and fight it for
        // the log file.
        named("client") {
            runDir = "run-client"
            // Vanilla defaults this to "Player" + millis % 1000, which changes every
            // launch and so can never be pre-opped.
            programArgs("--username", "rayneko4")
        }
    }
}

tasks {
    processResources {
        inputs.property("version", version)
        inputs.property("minecraft_version", libs.versions.minecraft.get())
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(
                "version" to inputs.properties["version"] as String,
                "minecraft_version" to inputs.properties["minecraft_version"] as String,
            )
        }
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
    }
    val runDir = project.projectDir.resolve("run")
    named("runServer") {
        (this as JavaExec).defaultCharacterEncoding = "UTF-8"
        doFirst {
            val eula = file("$runDir/eula.txt")
            if (!eula.exists()) {
                eula.parentFile.mkdirs()
                eula.bufferedWriter().use { it.write("eula=true\n") }
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    // Consumers compile against com.wiyuka.ticksync.api, whose signatures do not separate the
    // two rewind variants; the sources jar is the only place that distinction is visible.
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mod") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
    // Local only. Consumers add mavenLocal() and depend on
    // com.wiyuka:ticksync:<version>, having run `gradlew publishToMavenLocal` here.
    repositories { mavenLocal() }
}
