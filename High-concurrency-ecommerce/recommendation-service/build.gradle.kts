plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // MyBatis-Plus + MySQL
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:${property("mybatisPlusVersion")}")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Redis + Redisson
    implementation("org.redisson:redisson-spring-boot-starter:${property("redissonVersion")}")

    // Nacos
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery:${property("springCloudAlibabaVersion")}")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config:${property("springCloudAlibabaVersion")}")

    // OpenFeign
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")


    // Sentinel（按项目统一规范引入）
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel:${property("springCloudAlibabaVersion")}")
}
