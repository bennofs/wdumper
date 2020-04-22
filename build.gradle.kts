plugins {
    // Apply the java plugin to add support for Java
    java

    // Apply the application plugin to add support for building an application
    application

    // Shadow is a plugin to generate a self-contained jar with all dependencies included ("fatJar")
    id("com.github.johnrengelman.shadow").version("5.0.0")

    // Liquibase plugin for database migrations
    id("org.liquibase.gradle").version("2.0.2")

    // JOOQ code generation plugin for database access
    id("nu.studer.jooq").version("4.2")

    // Ratpack Web Framework
    id("io.ratpack.ratpack-java").version("1.7.6")

    // Generate type script definitions from Java POJOs
    id("cz.habarta.typescript-generator") version "2.22-SNAPSHOT"
}

java.sourceCompatibility = JavaVersion.toVersion("11")
java.targetCompatibility = JavaVersion.toVersion("11")

sourceSets.main.configure {
    resources {
        output.dir(project.buildDir.resolve("generated/resources"), "builtBy" to "generateResources")
    }
}

repositories {
    // Use jcenter for resolving your dependencies.
    // You can declare any Maven/Ivy/file repository here.
    mavenCentral()
    jcenter()
    maven { url = uri("https://jitpack.io") }
}

val autoValueVersion = "1.7";
dependencies {
    // This dependency is found on compile classpath of this component and consumers.
    implementation("com.google.guava:guava:27.0.1-jre")
    implementation("org.apache.commons:commons-lang3:3.9")

    // Wikidata Toolkit
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-datamodel:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-dumpfiles:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-rdf:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("info.picocli:picocli:4.0.0-alpha-3")

    // Template rendering
    implementation("com.github.spullara.mustache.java:compiler:0.9.6")

    // To support zstd compressed dumps
    implementation("com.github.luben:zstd-jni:1.4.0-1")

    // RDF processing
    runtimeOnly("org.eclipse.rdf4j:rdf4j-rio-ntriples:2.5.4")

    // logging implementation
    runtimeOnly("org.slf4j:slf4j-simple:1.7.26")

    // json
    implementation("com.fasterxml.jackson:jackson-bom:2.9.9")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.9.9")

    // mysql
    implementation("mysql:mysql-connector-java:8.0.16")

    // HTTP rest client
    implementation("org.apache.httpcomponents:httpclient:4.5.9")

    // Use JUnit test framework and assert libraries
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0");
    testImplementation("org.assertj:assertj-core:3.11.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0");

    // JOOQ
    implementation("org.jooq:jooq")
    jooqRuntime("org.jooq:jooq-meta-extensions:3.13.1")
    jooqRuntime("org.liquibase:liquibase-core:3.8.5")

    // Liquibase dependencies
    liquibaseRuntime("org.liquibase:liquibase-core:3.8.5")
    liquibaseRuntime("mysql:mysql-connector-java:8.0.16")

    // Ratpack modules
    implementation(ratpack.dependency("hikari"))

    // AutoValue
    implementation("com.google.auto.value:auto-value-annotations:${autoValueVersion}")
    annotationProcessor("com.google.auto.value:auto-value:${autoValueVersion}")
}

application {
    // Define the main class for the application
    mainClassName = "io.github.bennofs.wdumper.Api"
}

task<JavaExec>("run-backend") {
    classpath = sourceSets["main"].runtimeClasspath
    main = "io.github.bennofs.wdumper.Backend"
    workingDir = project.rootDir
    args = listOf(project.rootDir.resolve("data/slice.json.zst").toString())
}

val changeLogFile by project.extra { project.rootDir.resolve("src/main/resources/db/changelog.xml").absolutePath }
apply(from = "./jooq.gradle")

// Development defaults. Make sure these match the defaults in the README
val dbHost: String = System.getenv("DB_HOST") ?: "localhost"
val dbName: String = System.getenv("DB_NAME") ?: "wdumper"
val dbUser: String = System.getenv("DB_USER") ?: "root"
val dbPassword: String = System.getenv("DB_PASSWORD") ?: ""

// Liquibase tasks for easy execution during development
liquibase {
    activities.register("dev") {
        arguments = mapOf(
                "changeLogFile" to changeLogFile,
                "url" to "jdbc:mysql://${dbHost}/${dbName}",
                "username" to dbUser,
                "password" to dbPassword,
                "driver" to "com.mysql.cj.jdbc.Driver"
        )
    }
}

val generateToolVersion by tasks.registering(Exec::class) {
    doFirst {
        val outputDir = project.buildDir.resolve("generated/resources/meta")
        outputDir.mkdirs()
        standardOutput = outputDir.resolve("tool-version").outputStream()
    }
    commandLine("git", "rev-parse", "HEAD")
}

val generateWDTKVersion by tasks.registering {
    doLast {
        val outputDir = project.buildDir.resolve("generated/resources/meta")
        outputDir.mkdirs()

        // if the build is using a local, substituted version of wdtk, get the git revision
        val wdtkBuild = gradle.includedBuilds.find {
            it.name == "wdtk-parent"
        }

        if (wdtkBuild != null) {
            project.exec {
                commandLine("git", "rev-parse", "HEAD")
                standardOutput = outputDir.resolve("wdtk-version").outputStream()
                workingDir = wdtkBuild.projectDir
            }
            return@doLast
        }

        val wdtkRdf = configurations.runtimeClasspath.get().resolvedConfiguration.firstLevelModuleDependencies.find {
            it.moduleName == "wdtk-rdf"
        }!!

        // if this dependency does not come from jitpack, then prefix "release-" to the version
        val releasePrefix = if (wdtkRdf.moduleGroup == "com.github.Wikidata.Wikidata-Toolkit") {
            ""
        } else {
            "release-"
        }
        outputDir.resolve("wdtk-version").writeText(releasePrefix + wdtkRdf.moduleVersion)
    }
}

tasks.register("generateResources") {
    dependsOn(generateToolVersion)
    dependsOn(generateWDTKVersion)
}

tasks.generateTypeScript {
    jsonLibrary = cz.habarta.typescript.generator.JsonLibrary.jackson2
    outputFileType = cz.habarta.typescript.generator.TypeScriptFileType.declarationFile
    outputFile = project.rootDir.resolve("src/web/types/generated-json.d.ts").absolutePath
    outputKind = cz.habarta.typescript.generator.TypeScriptOutputKind.global
    classPatterns = listOf("io.github.bennofs.wdumper.spec.DumpSpecJson", "io.github.bennofs.wdumper.api.**Request")
}
