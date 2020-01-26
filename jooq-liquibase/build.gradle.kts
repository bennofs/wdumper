plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jooq:jooq:3.12.3")
    implementation("org.jooq:jooq-meta-extensions:3.12.3")
    implementation("org.liquibase:liquibase-core:3.8.5")
}