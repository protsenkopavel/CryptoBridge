plugins {
    java
    id("org.springframework.boot") version "3.5.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "net.protsenko.cryptobridge"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
}
