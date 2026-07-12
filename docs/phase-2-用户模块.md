# 阶段 2：用户模块 + 安全体系实施方案

> **目标：** 实现注册/登录/JWT双Token/权限控制/AOP日志，完成 user-service 全部功能。  
> **预计工时：** 20~25 小时  
> **前置：** 阶段 1 基础设施全部就绪  

---

## 2.1 模块目录结构

```
user-service/
├── build.gradle.kts
└── src/main/
    ├── java/com/ecommerce/user/
    │   ├── UserApplication.java              # 启动类
    │   │
    │   ├── controller/
    │   │   ├── AuthController.java           # 登录/注册/刷新Token
    │   │   └── UserController.java           # 用户信息CRUD
    │   │
    │   ├── service/
    │   │   ├── UserService.java
    │   │   └── impl/UserServiceImpl.java
    │   │
    │   ├── mapper/
    │   │   ├── UserMapper.java
    │   │   └── RoleMapper.java
    │   │
    │   ├── entity/
    │   │   ├── User.java
    │   │   └── Role.java
    │   │
    │   ├── dto/
    │   │   ├── LoginRequest.java
    │   │   ├── RegisterRequest.java
    │   │   ├── LoginResponse.java
    │   │   └── TokenRefreshRequest.java
    │   │
    │   └── security/
    │       ├── JwtTokenProvider.java         # JWT 生成/验证/刷新
    │       ├── JwtAuthenticationFilter.java  # 拦截器解析Token
    │       ├── UserDetailsServiceImpl.java   # 用户加载
    │       └── SecurityConfig.java           # Spring Security 配置
    │
    └── resources/
        ├── application.yml
        └── bootstrap.yml
```

---

## 2.2 启动类

```java
// UserApplication.java
package com.ecommerce.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.ecommerce.user.mapper")
@ComponentScan(basePackages = {"com.ecommerce.user", "com.ecommerce.common"})
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
```

---

## 2.3 配置文件

### bootstrap.yml（先加载）

```yaml
# user-service/src/main/resources/bootstrap.yml
spring:
  application:
    name: user-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: ecommerce-dev
      config:
        server-addr: localhost:8848
        namespace: ecommerce-dev
        file-extension: yaml
        shared-configs:
          - data-id: ecommerce-common.yml
            group: DEFAULT_GROUP
            refresh: true
```

### application.yml

```yaml
# user-service/src/main/resources/application.yml
server:
  port: 8081

jwt:
  access-expiration: 3600000    # 1小时（毫秒）
  refresh-expiration: 604800000 # 7天
  secret: "YOUR_JWT_SECRET_KEY_MUST_BE_AT_LEAST_256_BITS_LONG_USE_A_STRONG_RANDOM_STRING_HERE_32_CHARS"

spring:
  main:
    allow-circular-references: true
```

---

## 2.4 实体类

```java
// entity/User.java
package com.ecommerce.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private String password;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    private Integer status;
}
```

```java
// entity/Role.java
package com.ecommerce.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("role")
public class Role {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createTime;
}
```

---

## 2.5 Mapper 层

```java
// mapper/UserMapper.java
package com.ecommerce.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT r.name FROM role r " +
            "INNER JOIN user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectRolesByUserId(Long userId);

    @Select("SELECT p.name FROM permission p " +
            "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectPermissionsByUserId(Long userId);
}
```

---

## 2.6 DTO 类

```java
// dto/LoginRequest.java
package com.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

```java
// dto/RegisterRequest.java
package com.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度 4~20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6~32 位")
    private String password;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    private String email;
    private String nickname;
}
```

```java
// dto/LoginResponse.java
package com.ecommerce.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String username;
    private String nickname;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
}
```

---

## 2.7 JWT 工具类（核心）

```java
// security/JwtTokenProvider.java
package com.ecommerce.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretString;

    @Getter
    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (secretString.length() < 32) {
            secretString = secretString + "padding_to_ensure_minimum_256_bits_length";
        }
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    // ... generateAccessToken / generateRefreshToken / parseClaims / validateToken 同上 ...

    /**
     * 获取 Token 剩余有效时间（毫秒），用于登出时将 Token 加入黑名单
     */
    public long getRemainingTTL(String token) {
        try {
            Claims claims = parseClaims(token);
            long now = System.currentTimeMillis();
            long exp = claims.getExpiration().getTime();
            return Math.max(0, exp - now);
        } catch (Exception e) {
            return 0;
        }
    }
}
```

---

## 2.8 Spring Security 集成

### JWT 认证过滤器（含黑名单校验）

```java
// security/JwtAuthenticationFilter.java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;  // ⚡ 新增：Redis 依赖

    private static final String BLACKLIST_PREFIX = "blacklist:token:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            // ⚡ 黑名单校验：登出后 Token 失效
            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token))) {
                log.warn("Token 已被加入黑名单: {}", token.substring(0, 20) + "...");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"Token 已失效，请重新登录\"}");
                return;
            }

            String username = jwtTokenProvider.getUsernameFromToken(token);
            List<String> roles = jwtTokenProvider.getRolesFromToken(token);

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // ⚡ MDC 写入 TraceId（从请求头获取，Gateway 已生成）
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId != null) MDC.put("traceId", traceId);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}

### Security 配置

```java
// security/SecurityConfig.java
package com.ecommerce.user.security;

import com.ecommerce.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)   // 开启 @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/doc.html").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    new ObjectMapper().writeValue(res.getOutputStream(),
                        Result.error(401, "未登录或 Token 已过期"));
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    new ObjectMapper().writeValue(res.getOutputStream(),
                        Result.error(403, "没有访问权限"));
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### 获取当前用户工具

```java
// security/CurrentUserHolder.java
package com.ecommerce.user.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUserHolder {

    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return null;
    }
}
```

---

## 2.9 Service 层

```java
// service/impl/UserServiceImpl.java
package com.ecommerce.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.common.constant.ErrorCode;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.mapper.RoleMapper;
import com.ecommerce.user.mapper.UserMapper;
import com.ecommerce.user.security.JwtTokenProvider;
import com.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;  // ⚡ 新增

    private static final String BLACKLIST_PREFIX = "blacklist:token:";

    // ========== 注册 ==========
    @Override
    @Transactional
    public void register(RegisterRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATE);  // ⚡ 使用 ErrorCode
        }

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, request.getPhone());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.PHONE_DUPLICATE);
        }

        User user = BeanUtil.copyProperties(request, User.class);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null
                ? request.getNickname() : "用户" + System.currentTimeMillis() % 10000);
        user.setStatus(1);
        userMapper.insert(user);

        roleMapper.insertUserRole(user.getId(), 2L);
        log.info("用户注册成功: username={}, userId={}", user.getUsername(), user.getId());
    }

    // ========== 登录 ==========
    @Override
    public LoginResponse login(LoginRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);  // 模糊提示
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        List<String> roles = userMapper.selectRolesByUserId(user.getId());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessExpiration())
                .build();
    }

    // ⚡ ========== 登出（JWT 黑名单机制） ==========
    @Override
    public void logout(String token) {
        // 去掉 "Bearer " 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        long ttl = jwtTokenProvider.getRemainingTTL(token);
        if (ttl > 0) {
            // 将 Token 加入 Redis 黑名单，过期时间 = Token 剩余有效期
            redisTemplate.opsForValue()
                    .set(BLACKLIST_PREFIX + token, "1", ttl, TimeUnit.MILLISECONDS);
            log.info("用户登出，Token 已加入黑名单: ttl={}ms", ttl);
        }
    }
}

---

## 2.10 Controller 层

```java
// controller/AuthController.java
package com.ecommerce.user.controller;

import com.ecommerce.common.result.Result;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        userService.logout(token);
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestBody TokenRefreshRequest req) {
        return Result.success(jwtTokenProvider.refreshToken(req.getRefreshToken()));
    }
}
```

```java
// controller/UserController.java
package com.ecommerce.user.controller;

import com.ecommerce.common.result.Result;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.security.CurrentUserHolder;
import com.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<User> getCurrentUser() {
        String username = CurrentUserHolder.getCurrentUsername();
        User user = userService.lambdaQuery()
                .eq(User::getUsername, username)
                .one();
        // 脱敏
        user.setPassword(null);
        return Result.success(user);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:list')")
    public Result<List<User>> listUsers() {
        List<User> users = userService.lambdaQuery()
                .select(User.class, u -> !"password".equals(u.getProperty()))
                .list();
        return Result.success(users);
    }

    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) user.setPassword(null);
        return Result.success(user);
    }
}
```

---

## 2.11 AOP 操作日志（含 TraceId）

```java
// security/LogAspect.java（放在 user-service 或 common 模块均可）
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final ObjectMapper objectMapper;
    private final HttpServletRequest request;

    @Pointcut("execution(* com.ecommerce..controller.*.*(..))")
    public void controllerLog() {}

    @Around("controllerLog()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName()
                + "." + signature.getName();

        // ⚡ 从请求头获取 TraceId 并写入 MDC（此时 %X{traceId} 自动生效）
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }

        String args = objectMapper.writeValueAsString(pjp.getArgs());
        String ip = getClientIP(request);

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[API] {} | {}ms | IP:{} | args:{} | OK",
                    methodName, elapsed, ip, args);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[API] {} | {}ms | IP:{} | args:{} | ERROR:{}",
                    methodName, elapsed, ip, args, e.getMessage());
            throw e;
        } finally {
            MDC.clear();  // ⚡ 清理 MDC，防止 ThreadLocal 污染线程池
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
```

---

## 2.12 验证测试

### 启动服务

```bash
# 确保 Docker 中间件已运行
docker ps

# 启动 user-service
./gradlew :user-service:bootRun
```

### API 测试

```bash
# 1. 注册
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","phone":"13800138000","nickname":"管理员"}'

# 2. 登录
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 3. 使用返回的 Token 访问受保护接口
curl -X GET http://localhost:8081/api/users/me \
  -H "Authorization: Bearer <access_token>"

# 4. 测试权限（普通用户无 user:list 权限会返回 403）
curl -X GET http://localhost:8081/api/users \
  -H "Authorization: Bearer <access_token>"

# 5. 登出
curl -X POST http://localhost:8081/api/auth/logout \
  -H "Authorization: Bearer <access_token>"

# 6. 验证黑名单（登出后用旧 Token 访问 → 401）
curl -X GET http://localhost:8081/api/users/me \
  -H "Authorization: Bearer <access_token>"
```

---

## 2.13 生产级安全加固（核心新增）

> 以下增强将用户模块从"能跑通"提升为"生产级安全标准"。

### 13.1 JWT 升级：从 HS256 到 RS256（非对称加密）

**为什么升级**：HS256 使用同一个密钥签名和验签，所有微服务共享 secret。RS256 使用公私钥对——网关用公钥验签，认证服务用私钥签发，密钥泄露影响面更小。

生成 RSA 密钥对：

```bash
# 生成私钥
openssl genrsa -out jwt-private.pem 2048

# 导出公钥
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

更新 JWT 工具类：

```java
// JwtUtil.java —— RS256 版本
@Component
public class JwtUtil {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    @Value("${jwt.access-expiration:900000}")    // 15 分钟（生产级缩短）
    private long accessExpiration;                // 默认 900000ms = 15 分钟

    @Value("${jwt.refresh-expiration:604800000}") // 7 天
    private long refreshExpiration;

    public JwtUtil(@Value("${jwt.private-key}") String privateKeyPem,
                   @Value("${jwt.public-key}") String publicKeyPem) throws Exception {
        this.privateKey = parsePrivateKey(privateKeyPem);
        this.publicKey = parsePublicKey(publicKeyPem);
    }

    // 签发时使用私钥
    public String generateAccessToken(Long userId, List<String> roles) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("roles", roles)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessExpiration))
            .signWith(privateKey)                         // RS256 私钥签发
            .compact();
    }

    // 验签时使用公钥（所有微服务只需公钥）
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)                        // RS256 公钥验签
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
```

### 13.2 Refresh Token 轮换（Rotation）

每次使用 Refresh Token 刷新时，旧的 Refresh Token 立即失效，生成新的 Refresh Token：

```java
@Service
public class AuthService {

    public LoginResult refresh(String refreshToken) {
        // 1. 解析 Refresh Token
        Claims claims = jwtUtil.parseToken(refreshToken);
        Long userId = Long.valueOf(claims.getSubject());
        String tokenId = claims.getId();

        // 2. 检查是否已被使用过（轮换防护）
        String usedKey = "token:used:" + tokenId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(usedKey))) {
            // Token 已被使用 → 可能是 Refresh Token 泄露
            // 立即失效该用户所有 Token，强制重新登录
            redisTemplate.delete("token:whitelist:" + userId);
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        // 3. 标记旧 Token 为已使用
        redisTemplate.opsForValue().set(usedKey, "1",
            jwtUtil.getRefreshExpiration(), TimeUnit.MILLISECONDS);

        // 4. 生成新的 Token 对
        String newAccess = jwtUtil.generateAccessToken(userId, getUserRoles(userId));
        String newRefresh = jwtUtil.generateRefreshToken(userId);

        return new LoginResult(newAccess, newRefresh);
    }
}
```

### 13.3 登录频率限制（防暴力破解）

```java
@Component
public class LoginRateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_ATTEMPTS = 5;        // 最大尝试次数
    private static final int LOCK_DURATION = 15;       // 锁定分钟数

    public boolean isBlocked(String username) {
        String key = "login:blocked:" + username;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void recordFailure(String username) {
        String key = "login:attempts:" + username;
        Long attempts = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(LOCK_DURATION));

        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(
                "login:blocked:" + username, "1", Duration.ofMinutes(LOCK_DURATION));
            log.warn("用户 {} 登录失败超过 {} 次，已锁定 {} 分钟", username, MAX_ATTEMPTS, LOCK_DURATION);
        }
    }

    public void clearOnSuccess(String username) {
        redisTemplate.delete("login:attempts:" + username);
        redisTemplate.delete("login:blocked:" + username);
    }
}
```

### 13.4 细粒度数据权限

确保用户只能操作自己的数据：

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/my")
    public Result<Page<OrderVO>> listMyOrders(PageQuery query) {
        // 从 JWT 获取当前用户 ID，而非从请求参数获取
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.success(orderService.listByUserId(currentUserId, query));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderDetailVO> getDetail(@PathVariable String orderNo) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        OrderDetailVO detail = orderService.getDetail(orderNo);
        // 校验：只能看自己的订单
        if (!detail.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return Result.success(detail);
    }
}
```

### 13.5 密码复杂度校验

```java
// 注册时校验密码强度
public void validatePassword(String password) {
    if (password == null || password.length() < 8) {
        throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
    }
    // 必须包含大小写字母和数字
    if (!password.matches(".*[A-Z].*") 
        || !password.matches(".*[a-z].*") 
        || !password.matches(".*[0-9].*")) {
        throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
    }
}
```

### 13.6 日志自动脱敏

```java
// Logback MessageConverter —— 自动替换敏感信息
// 效果：13812345678 → 138****5678
@Slf4j
public class SensitiveDataConverter extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        return message
            .replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");  // 手机号
    }
}
```

### 13.7 Secrets 管理

> **硬性规则**：JWT 私钥、数据库密码等敏感信息禁止写在 `application.yml` 中。

```yaml
# application-dev.yml —— 通过环境变量注入
jwt:
  private-key: ${JWT_PRIVATE_KEY}
  public-key: ${JWT_PUBLIC_KEY}
spring:
  datasource:
    password: ${DB_PASSWORD:root}    # 默认值仅用于本地开发
```

### 13.8 单元测试最低要求

> 从本阶段开始，每个 Service 必须有配套的单元测试。

```java
// user-service/src/test/java/.../service/AuthServiceTest.java
@ExtendWith(SpringExtension.class)
class AuthServiceTest {

    @Test
    @DisplayName("登录成功应返回双 Token")
    void loginSuccess() { /* ... */ }

    @Test
    @DisplayName("密码错误应抛出 PASSWORD_ERROR")
    void loginFailWrongPassword() { /* ... */ }

    @Test
    @DisplayName("连续 5 次失败后应锁定账号")
    void loginBlockedAfterMaxAttempts() { /* ... */ }

    @Test
    @DisplayName("Refresh Token 轮换后旧 Token 应失效")
    void refreshTokenRotation() { /* ... */ }

    @Test
    @DisplayName("用户只能查看自己的信息")
    void dataScopeIsolation() { /* ... */ }
}
```

> **目标**：user-service 测试覆盖率 ≥ 80%，CI 流水线中 JaCoCo 自动检查。

---

## 2.14 检查点

- [ ] 注册接口正常，**密码复杂度校验生效**（长度 ≥8 + 大小写+数字）
- [ ] 登录返回 accessToken + refreshToken（**RS256 非对称签名**）
- [ ] 带 Token 访问 `/api/users/me` 正常返回用户信息
- [ ] 无 Token 访问受保护接口返回 401
- [ ] Token 过期后返回 401
- [ ] 登出后 Token 加入黑名单，再次使用返回 401
- [ ] **Refresh Token 轮换：旧 Refresh Token 复用被拒绝**
- [ ] **连续 5 次登录失败后账号被锁定 15 分钟**
- [ ] 权限不足接口返回 403
- [ ] **用户只能操作自己的数据**（数据权限隔离）
- [ ] AOP 日志含 TraceId 字段，**手机号自动脱敏**
- [ ] 密码用 BCrypt 加密存储
- [ ] ErrorCode 枚举全部替换硬编码数字
- [ ] **JWT 密钥通过环境变量注入，不硬编码在 yml 中**
- [ ] **单元测试 ≥ 5 个场景，JaCoCo 报告输出**

---

## 2.15 下一步

用户模块完成 → **[阶段 3：商品模块 + 缓存体系](./phase-3-商品模块.md)**
