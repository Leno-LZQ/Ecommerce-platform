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

    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:${property("mybatisPlusVersion")}")
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.redisson:redisson-spring-boot-starter:${property("redissonVersion")}")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery:${property("springCloudAlibabaVersion")}")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config:${property("springCloudAlibabaVersion")}")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

}
