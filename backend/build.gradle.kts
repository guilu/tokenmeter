plugins {
  java
  checkstyle
  id("org.springframework.boot") version "3.5.7"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.diffplug.spotless") version "7.2.1"
  id("org.sonarqube") version "6.3.1.5724"
}

group = "dev.diegobarrioh.tokenmeter"

version = "0.0.1-SNAPSHOT"

description = "TokenMeter backend"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")
  testRuntimeOnly("com.h2database:h2")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

checkstyle {
  toolVersion = "11.0.1"
  configFile = file("config/checkstyle/checkstyle.xml")
}

spotless {
  java {
    googleJavaFormat("1.32.0")
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

sonar {
  properties {
    property("sonar.projectKey", "guilu_tokenmeter")
    property("sonar.organization", "guilu")
    property("sonar.host.url", "https://sonarcloud.io")
  }
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.check { dependsOn("spotlessCheck") }
