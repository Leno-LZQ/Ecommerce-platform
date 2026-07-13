plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")

//    // Security（仅 user-service 需要）
//    implementation("org.springframework.boot:spring-boot-starter-security")
//
//    // JWT
//    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
//    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
//    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")
//
//    // MyBatis-Plus + MySQL
//    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:${property("mybatisPlusVersion")}")
//    runtimeOnly("com.mysql:mysql-connector-j")
//
//    // Redis + Redisson
//    implementation("org.springframework.boot:spring-boot-starter-data-redis")
//    implementation("org.redisson:redisson-spring-boot-starter:${property("redissonVersion")}")
//
//    // Nacos
//    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery:${property("springCloudAlibabaVersion")}")
//    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config:${property("springCloudAlibabaVersion")}")
//
//    // Sentinel（按需添加）
//    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel:${property("springCloudAlibabaVersion")}")
}
