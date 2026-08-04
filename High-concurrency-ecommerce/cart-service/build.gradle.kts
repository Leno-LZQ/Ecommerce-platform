plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.51")

    // ↓ 取消注释
    implementation("org.springframework.boot:spring-boot-starter-data-redis")    // 购物车主存储
    implementation("org.redisson:redisson-spring-boot-starter:${property("redissonVersion")}")

    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery:${property("springCloudAlibabaVersion")}")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config:${property("springCloudAlibabaVersion")}")

    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")

    // 购物车 MySQL 灾备同步依赖
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:${property("mybatisPlusVersion")}")
    runtimeOnly("com.mysql:mysql-connector-j")
}
