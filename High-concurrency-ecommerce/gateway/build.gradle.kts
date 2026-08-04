plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {

    // Spring Cloud Gateway（Reactive 栈）
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    // Nacos 服务发现 + 配置中心
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config")

    // Sentinel 限流（Gateway 适配版）
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel")
    implementation("com.alibaba.cloud:spring-cloud-alibaba-sentinel-gateway")

    // Redis（Token 黑名单 + WebSocket 会话）
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")


    // 链路追踪
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")

    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")

}
