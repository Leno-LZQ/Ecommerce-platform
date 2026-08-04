plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // ========== api：下游服务编译时必须看到 ==========
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.cloud:spring-cloud-starter-openfeign")
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("org.springframework.boot:spring-boot-starter-security")   // ← 从 implementation 改成 api
    api("cn.hutool:hutool-all:${property("hutoolVersion")}")

    // ========== implementation：common 自己用，不透传 ==========
    implementation("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:${property("knife4jVersion")}")
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:${property("mybatisPlusVersion")}")

    // RabbitMQ 消息转换器（compileOnly：运行时由各服务 spring-boot-starter-amqp 提供）
    compileOnly("org.springframework.amqp:spring-amqp")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

