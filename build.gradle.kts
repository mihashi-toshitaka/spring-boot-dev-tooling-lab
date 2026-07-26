import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"

    id("com.diffplug.spotless") version "8.8.0"
    id("org.openrewrite.rewrite") version "7.37.0"
    id("net.ltgt.errorprone") version "5.1.0"
    id("checkstyle")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat()
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        ktlint()
    }
}

rewrite {
    // activeRecipe("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
    activeRecipe("org.openrewrite.java.migrate.UpgradeToJava21")
    activeRecipe("org.openrewrite.staticanalysis.CommonStaticAnalysis")
}

checkstyle {
    toolVersion = "13.7.0"
    configDirectory = file("config/checkstyle")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    compileOnly("com.google.errorprone:error_prone_annotations:2.50.0")
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    rewrite("org.openrewrite.recipe:rewrite-spring:6.34.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
