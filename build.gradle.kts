import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"

    id("com.diffplug.spotless") version "8.8.0"
    id("org.openrewrite.rewrite") version "7.37.0"
    id("net.ltgt.errorprone") version "5.1.0"
    id("com.github.spotbugs") version "6.5.9"
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

spotbugs {
    ignoreFailures = false
    effort = Effort.DEFAULT
    reportLevel = Confidence.DEFAULT
}

dependencies {
    // Web・テンプレートエンジン
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // 運用監視
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // データベース・マイグレーション
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // 認証・セッション管理
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")

    // 開発支援
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // テスト
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")

    // 静的解析・リファクタリング
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

tasks.withType<SpotBugsTask>().configureEach {
    val spotBugsTaskName = name
    reports.create("html") {
        required = true
        outputLocation = layout.buildDirectory.file("reports/spotbugs/$spotBugsTaskName.html")
    }
}
