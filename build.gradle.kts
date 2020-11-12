import com.moowork.gradle.node.yarn.YarnTask
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig

plugins {
    // Apply the java plugin to add support for Java
    java

    // Apply the application plugin to add support for building an application
    application

    // Run node tasks with gradle
    id("com.github.node-gradle.node").version("2.2.3")

    // Daemons for gradle continuous build
    id("io.github.bennofs.continuous-exec").version("0.1.1")

    // Compute version name from git
    id("me.qoomon.git-versioning").version("3.0.0")
}

java.sourceCompatibility = JavaVersion.toVersion("11")
java.targetCompatibility = JavaVersion.toVersion("11")

val changeLogFile = "${project.rootDir}/src/main/resources/db/changelog.xml"
val jooqGenerateDir = "${project.rootDir}/src/generated/jooq"

val buildsupport: SourceSet by sourceSets.creating {}

val jooqGenerate by tasks.registering(JavaExec::class) {
    classpath = buildsupport.runtimeClasspath
    main = "io.github.bennofs.wdumper.JooqGenerate"
    args = listOf(changeLogFile, jooqGenerateDir)
    inputs.file(changeLogFile)
    outputs.dir(jooqGenerateDir)
}

sourceSets {

    main.configure {
        resources {
            output.dir(project.buildDir.resolve("generated/resources"), "builtBy" to "generateResources")
        }
        java {
            srcDir(jooqGenerateDir)
        }
    }
}

repositories {
    // Use jcenter for resolving your dependencies.
    // You can declare any Maven/Ivy/file repository here.
    mavenCentral()
    jcenter()
    maven { url = uri("https://jitpack.io") }
}

val autoValueVersion = "1.7"
dependencies {
    // This dependency is found on compile classpath of this component and consumers.
    implementation("com.google.guava:guava:27.0.1-jre")
    implementation("org.apache.commons:commons-lang3:3.9")

    // Dependencies for the web part
    implementation("io.undertow:undertow-core:2.1.0.Final")

    // Wikidata Toolkit
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-datamodel:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-dumpfiles:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("com.github.Wikidata.Wikidata-Toolkit:wdtk-rdf:0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
    implementation("info.picocli:picocli:4.0.0-alpha-3")

    // Template rendering
    implementation("com.samskivert:jmustache:1.15")

    // To support zstd compressed dumps
    implementation("com.github.luben:zstd-jni:1.4.0-1")

    // RDF processing
    runtimeOnly("org.eclipse.rdf4j:rdf4j-rio-ntriples:2.5.4")

    // logging implementation
    runtimeOnly("org.slf4j:slf4j-simple:1.7.26")

    // json
    implementation(enforcedPlatform("com.fasterxml.jackson:jackson-bom:2.10.3"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // mysql
    implementation("mysql:mysql-connector-java:8.0.16")
    implementation("org.mariadb.jdbc:mariadb-java-client:2.6.0")
    implementation("org.liquibase:liquibase-core:3.8.5")
    implementation("org.testcontainers:testcontainers:1.14.3")
    implementation("org.testcontainers:mariadb:1.14.3")
    implementation("com.zaxxer:HikariCP:3.4.5")

    // web
    implementation("org.jboss.resteasy:resteasy-core:4.5.8.Final")
    implementation("org.jboss.resteasy:resteasy-core-spi:4.5.8.Final")
    implementation("org.jboss.resteasy:resteasy-undertow:4.5.8.Final")
    implementation("org.jboss.resteasy:resteasy-jackson2-provider:4.5.8.Final")
    implementation("io.undertow:undertow-core:2.2.0.Final")

    // HTTP rest client
    implementation("org.apache.httpcomponents:httpclient:4.5.9")

    // configuration
    implementation("org.cfg4j:cfg4j-core:4.4.1")

    // Use JUnit test framework and assert libraries
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.assertj:assertj-core:3.16.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
    testImplementation("org.testcontainers:testcontainers:1.14.3")
    testImplementation("org.testcontainers:junit-jupiter:1.14.3")
    testImplementation("org.testcontainers:mariadb:1.14.3")


    // JOOQ
    implementation("org.jooq:jooq:3.13.2")
    "buildsupportImplementation"("org.jooq:jooq:3.13.2")
    "buildsupportImplementation"("org.jooq:jooq-meta:3.13.2")
    "buildsupportImplementation"("org.jooq:jooq-codegen:3.13.2")
    "buildsupportImplementation"("org.liquibase:liquibase-core:3.8.5")
    "buildsupportImplementation"("org.mariadb.jdbc:mariadb-java-client:2.6.0")
    "buildsupportImplementation"("mysql:mysql-connector-java:8.0.16")
    "buildsupportImplementation"("org.testcontainers:testcontainers:1.14.3")
    "buildsupportImplementation"("org.testcontainers:mariadb:1.14.3")
    "buildsupportImplementation"(platform("com.fasterxml.jackson:jackson-bom:2.10.3"))
    "buildsupportImplementation"("com.fasterxml.jackson.core:jackson-core")
    "buildsupportImplementation"("com.fasterxml.jackson.core:jackson-databind")
    "buildsupportImplementation"("io.vertx:vertx-core:3.9.3")
    "buildsupportRuntimeOnly"("org.slf4j:slf4j-simple:1.7.26")

    // Dependency injection
    implementation("com.google.inject:guice:4.2.3")

    // AutoValue
    implementation("com.google.auto.value:auto-value-annotations:${autoValueVersion}")
    annotationProcessor("com.google.auto.value:auto-value:${autoValueVersion}")
}

application {
    // Define the main class for the application
    mainClassName = "io.github.bennofs.wdumper.Api"
}

// additional start script for backend
val startScriptsBackend by tasks.registering(CreateStartScripts::class) {
    applicationName = "wdumper-backend"
    mainClass.set("io.github.bennofs.wdumper.Backend")

    outputDir = file("$buildDir/scripts")
    classpath = project.tasks.startScripts.get().classpath
    modularity.inferModulePath.set(project.tasks.startScripts.get().modularity.inferModulePath)
}

// additional start script for cli
val startScriptsCli by tasks.registering(CreateStartScripts::class) {
    applicationName = "wdumper-cli"
    mainClass.set("io.github.bennofs.wdumper.Cli")

    outputDir = file("$buildDir/scripts")
    classpath = project.tasks.startScripts.get().classpath
    modularity.inferModulePath.set(project.tasks.startScripts.get().modularity.inferModulePath)
}

distributions {
    main {
        contents {
            from(startScriptsBackend) {
                into("bin/")
            }
            from(startScriptsCli) {
                into("bin/")
            }
        }
    }
}

node {
    download = false
}


// region Build tasks
val buildFrontendCss by tasks.registering(YarnTask::class) {
    inputs.dir("frontend/css")
    outputs.file("frontend/static/main.css")

    args = listOf("frontend-less")
    dependsOn(tasks["yarn"])
}

val buildFrontendJs by tasks.registering(YarnTask::class) {
    inputs.dir("frontend/ts")
    outputs.file("frontend/static/main.js")

    args = listOf("frontend-rollup")
    dependsOn(tasks["yarn"])
}

tasks.processResources.configure {
    dependsOn(buildFrontendCss)
    dependsOn(buildFrontendJs)

    from("frontend/static") { into("static/") }

    doLast {
        destinationDir.resolve(".webroot").writeText("")
    }
}

val generateToolVersion by tasks.registering {
    doLast {
        val dir = file("$buildDir/generated/resources/meta")
        dir.mkdirs()
        dir.resolve("tool-version").writeText(project.version.toString())
    }
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
// endregion

// region Test tasks
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("testIntegration") {
    useJUnitPlatform {
        includeTags("integration")
    }
}
// endregion

//region Development/run tasks
val run by tasks.named<JavaExec>("run") {
    doFirst {
        environment.put("DB_ADDRESS", file("build/run/db-url").readText())
    }
    //jvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=y")
}

task("debug-vars") {
    doLast {
        System.err.println("classpath")
        run.classpath.forEach { System.err.println(it.toString()) }

        System.err.println("resources")
        sourceSets.main.get().resources.sourceDirectories.forEach { println(it) }
    }
}

val runBackend by tasks.register<JavaExec>("run-backend") {
    classpath = sourceSets["main"].runtimeClasspath
    main = "io.github.bennofs.wdumper.Backend"
    workingDir = project.rootDir
    args = listOf(project.rootDir.resolve("data/slice.json.zst").toString())

    doFirst {
        environment.put("DB_ADDRESS", file("build/run/db-url").readText())
    }
}

val livereload by tasks.register<io.github.bennofs.gradle.continuous.ContinuousJavaExec>("livereload") {
    watch.from(run.inputs.files)
    javaExec {
        main = "io.github.bennofs.wdumper.LiveReloadServer"
        classpath = buildsupport.runtimeClasspath
    }
}
run.dependsOn(livereload)

val devdb by tasks.register<io.github.bennofs.gradle.continuous.ContinuousJavaExec>("devdb") {
    watch.from(changeLogFile)
    javaExec {
        main = "io.github.bennofs.wdumper.DevDbServer"
        classpath = buildsupport.runtimeClasspath
        args = listOf(changeLogFile)
    }
}
run.dependsOn(devdb)
runBackend.dependsOn(devdb)
//endregion

//region Deployment tasks
tasks.named("distZip") { enabled = false }
tasks.named("distTar") { enabled = false }

gitVersioning {
    val config = GitVersioningPluginConfig()
    config.commitVersionDescription = GitVersioningPluginConfig.CommitVersionDescription()
    config.commitVersionDescription.versionFormat = "\${commit}"
    apply(config)
}
//endregion
