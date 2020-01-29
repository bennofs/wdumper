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
    id("nu.studer.jooq").version("4.1")

    // Ratpack Web Framework
    id("io.ratpack.ratpack-java").version("1.7.6")
}

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

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

dependencies {
    // This dependency is found on compile classpath of this component and consumers.
    implementation("com.google.guava:guava:27.0.1-jre")
    implementation("org.apache.commons:commons-lang3:3.9")

    // Wikidata Toolkit
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-datamodel:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-dumpfiles:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-rdf:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("info.picocli:picocli:4.0.0-alpha-3")

    // To support zstd compressed dumps
    implementation("com.github.luben:zstd-jni:1.4.0-1")

    // RDF processing
    runtime("org.eclipse.rdf4j:rdf4j-rio-ntriples:2.5.4")

    // logging implementation
    runtimeOnly("org.slf4j:slf4j-simple:1.7.26")

    // json
    implementation("com.fasterxml.jackson:jackson-bom:2.9.9")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.9.9")

    // jdbi
    implementation("org.jdbi:jdbi3-core:3.8.2")

    // mysql
    implementation("mysql:mysql-connector-java:8.0.16")

    // HTTP rest client
    implementation("com.konghq:unirest-java:2.3.08")
    implementation("com.konghq:unirest-objectmapper-jackson:2.3.08")
    implementation("org.apache.httpcomponents:httpclient:4.5.9")

    // Use JUnit test framework
    testImplementation("junit:junit:4.12")

    // JOOQ
    implementation("org.jooq:jooq")
    jooqRuntime(project(":jooq-liquibase"))
    jooqRuntime("org.liquibase:liquibase-core:3.8.5")

    // Liquibase dependencies
    liquibaseRuntime("org.liquibase:liquibase-core:3.8.5")
    liquibaseRuntime("mysql:mysql-connector-java:8.0.16")
    // Ratpack modules
    implementation(ratpack.dependency("hikari"))
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