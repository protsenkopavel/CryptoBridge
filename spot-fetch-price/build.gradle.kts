plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "net.protsenko.cryptobridge"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.liquibase:liquibase-core")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    implementation("org.knowm.xchange:xchange-core:5.2.2")
    implementation("org.knowm.xchange:xchange-bybit:5.2.2")
    implementation("org.knowm.xchange:xchange-binance:5.2.2")
    implementation("org.knowm.xchange:xchange-mexc:5.2.2")
    implementation("org.knowm.xchange:xchange-gateio-v4:5.2.2")
    implementation("org.knowm.xchange:xchange-kucoin:5.2.2")
    implementation("org.knowm.xchange:xchange-bitget:5.2.2")
    implementation("org.knowm.xchange:xchange-coinex:5.2.2")
    implementation("org.knowm.xchange:xchange-huobi:5.2.2")
    implementation("org.knowm.xchange:xchange-deribit:5.2.2")
    implementation("org.knowm.xchange:xchange-bitfinex:5.2.2")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("org.json:json:20240303")
    implementation("org.mapstruct:mapstruct:1.6.3")

    compileOnly("org.projectlombok:lombok")

    runtimeOnly("org.postgresql:postgresql")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok")

}

tasks.withType<Test> {
    useJUnitPlatform()
}
