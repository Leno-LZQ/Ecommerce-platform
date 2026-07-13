plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("cn.hutool:hutool-all:${property("hutoolVersion")}")
    api("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:${property("knife4jVersion")}")
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:${property("mybatisPlusVersion")}")
}
