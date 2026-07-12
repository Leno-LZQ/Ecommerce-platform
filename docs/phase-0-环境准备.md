# 阶段 0：环境准备实施方案

> **目标：** 在本地搭建完整开发环境，确保所有工具链可用，中间件可通过 Docker 一键启动。  
> **预计工时：** 3~5 小时（含下载等待时间）  
> **前置：** 无  

---

## 0.1 检查当前环境

在终端依次执行，记录结果：

```bash
# Java 版本（需 ≥ 17）
java -version

# Git 版本
git --version

# Node 版本（需 ≥ 18）
node --version
npm --version

# 内存大小（记下数值）
# Windows：任务管理器 → 性能 → 内存
```

**参考结果：**

| 工具 | 版本 | 状态 |
|:-----|:-----|:----:|
| JDK | 21.0.5 | ✅ |
| Git | 2.54.0 | ✅ |
| Node | 22.22.2 | ✅ |
| npm | 10.9.7 | ✅ |

---

## 0.2 安装 Docker Desktop

> ⚠️ **这是整个项目最关键的一步**。所有中间件（MySQL、Redis、RabbitMQ、Nacos、ES）都靠 Docker 运行。

### 2.1 下载安装

```
1. 打开 https://www.docker.com/products/docker-desktop/
2. 下载 Docker Desktop for Windows
3. 运行安装程序，全部默认选项
4. 安装完成后重启电脑
```

### 2.2 启用 WSL2（Windows 必须）

```powershell
# 以管理员身份打开 PowerShell，执行：
wsl --install
wsl --set-default-version 2
```

### 2.3 验证

```bash
# 确保右下角 Docker 图标是绿色/白色（正在运行）
docker --version
docker compose version
docker run hello-world
```

若 `hello-world` 输出 `Hello from Docker!` 则表示安装成功。

### 2.4 配置 Docker 国内镜像加速（可选但推荐）

1. 打开 Docker Desktop → Settings → Docker Engine
2. 在 JSON 中添加：

```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

3. Apply & Restart

---

## 0.3 安装 JetBrains IntelliJ IDEA

### 3.1 下载

```
1. 打开 https://www.jetbrains.com/idea/download/
2. 下载 Ultimate 版（推荐，功能全）
   或 Community 版（免费，够用）
3. 如果是学生：https://www.jetbrains.com/community/education/#students
   用学校邮箱注册免费 Ultimate 许可证
```

### 3.2 必装插件

打开 IDEA → File → Settings → Plugins → Marketplace，搜索并安装：

| 插件 | 用途 | 必须 |
|:----|:----|:---:|
| **Lombok** | `@Data` `@Slf4j` 自动生成 | ✅ |
| **MyBatisX** | MyBatis-Plus 代码生成 + XML 跳转 | ✅ |
| **Gradle** | 内置，确保启用 | ✅ |
| **Spring Assistant** | `.properties`/`.yml` 配置提示 | 推荐 |
| **Rainbow Brackets** | 括号着色，调试友好 | 推荐 |
| **GitToolBox** | Git 状态行内显示 | 推荐 |

### 3.3 IDE 基础设置

```
File → Settings：
├── Editor → File Encodings → 全部设为 UTF-8
├── Build → Build Tools → Gradle → Build and run using: IntelliJ IDEA
└── Editor → Code Style → Java → Tab size: 4, Indent: 4
```

---

## 0.4 安装 JMeter（压测用）

```
1. 打开 https://jmeter.apache.org/download_jmeter.cgi
2. 下载 apache-jmeter-5.6.x.zip
3. 解压到 D:\tools\apache-jmeter
4. 验证：
   D:\tools\apache-jmeter\bin\jmeter.bat --version
```

---

## 0.5 安装 Gradle（可选，通过 Wrapper 可跳过）

如果不打算全局安装 Gradle，项目会通过 `gradlew` 自举，无需额外操作。

如需全局安装：

```bash
# 使用 Chocolatey（推荐）
choco install gradle

# 或手动下载
# https://gradle.org/releases/ → 下载 8.7+ → 配置 PATH
```

---

## 0.6 验证本地数据库（可选）

如果 Docker 未启动或你更习惯本地安装 MySQL，可以安装 MySQL 8.0：

```
https://dev.mysql.com/downloads/mysql/8.0.html
```

但 **强烈建议用 Docker**，因为项目还依赖 Redis/RabbitMQ/Nacos 等组件，Docker 一键全搞定。

---

## 0.7 创建项目目录

```bash
cd D:\study\java全栈开发\Java全栈技术项目
mkdir -p docker/mysql
mkdir -p docs
```

---

## 0.8 环境检查清单

逐项确认后打勾：

```
[ ] JDK 17+ 已安装
[ ] Git 已配置（git config user.name / user.email）
[ ] Node.js 18+ 已安装
[ ] Docker Desktop 已安装并运行
[ ] IDEA 已安装 + 必要插件
[ ] JMeter 已安装
[ ] 内存 ≥ 8GB（最好 ≥ 12GB）
[ ] 国内镜像加速已配置（Docker 拉取镜像）
```

---

## 0.9 创建 GitHub 仓库并配置工程规范

### 9.1 创建仓库

```bash
# 方式一：GitHub 网页创建后 clone
# 1. 打开 https://github.com/new
# 2. Repository name: ecommerce-platform
# 3. 选择 Public（免费使用 GitHub Actions）
# 4. 不要勾选 "Add a README file"（之后我们在本地写）

# 方式二：gh CLI（推荐）
gh repo create ecommerce-platform --public --clone
cd ecommerce-platform
```

### 9.2 Git 全局配置

```bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
git config --global init.defaultBranch main
```

### 9.3 初始化仓库并首次推送

```bash
cd D:\code\MyProject\Ecommerce-platform
git init
git add .
git commit -m "chore: 初始化项目（Gradle多模块骨架 + 文档）"
git remote add origin https://github.com/你的用户名/ecommerce-platform.git
git branch -M main
git push -u origin main
```

### 9.4 创建 .editorconfig（编辑器统一配置）

项目根目录创建 `.editorconfig`：

```ini
# .editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true

[*.{yml,yaml}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false

[*.{js,ts,vue,json,css,html}]
indent_size = 2
```

### 9.5 配置 Git 分支策略

```bash
# 创建 develop 分支
git checkout -b develop
git push -u origin develop

# 后续开发流程：
# main     ← 稳定分支（通过 PR 合并）
# develop  ← 日常开发
# feature/xxx ← 新功能从 develop 分叉
# hotfix/xxx  ← 紧急修复从 main 分叉
```

### 9.6 GitHub 仓库设置

在 GitHub 仓库页面 → Settings：

```
General:
├── Default branch: main
└── [ ] Allow merge commits（关闭，只用 Squash 或 Rebase）

Branches → Add branch protection rule (main):
├── Branch name pattern: main
├── [✓] Require a pull request before merging
│   └── [✓] Require approvals (1)
├── [✓] Require status checks to pass before merging
│   └── [✓] Require branches to be up to date before merging
└── [✓] Do not allow bypassing the above settings
```

### 9.7 PR 模板

创建 `.github/PULL_REQUEST_TEMPLATE.md`：

```markdown
## 变更说明
<!-- 简要描述本次变更 -->


## 变更类型
- [ ] Bug 修复
- [ ] 新功能
- [ ] 代码重构
- [ ] 性能优化
- [ ] 文档更新
- [ ] CI/CD 变更

## 测试
- [ ] 单元测试已通过
- [ ] 集成测试已通过
- [ ] 本地 docker compose 验证通过

## Checklist
- [ ] 代码通过 Checkstyle
- [ ] 新增测试覆盖率 > 80%
- [ ] 无 SonarQube Blocker / Critical
- [ ] 敏感信息未硬编码（使用环境变量）
```

---

## 0.10 环境检查清单（更新）

逐项确认后打勾：

```
[ ] JDK 17+ 已安装 ✓
[ ] Git 已配置（user.name / user.email）
[ ] Node.js 18+ 已安装
[ ] Docker Desktop 已安装并运行
[ ] IDEA 已安装 + 必要插件
[ ] JMeter 已安装
[ ] 内存 ≥ 8GB（最好 ≥ 12GB）
[ ] 国内镜像加速已配置
[ ] GitHub 仓库已创建
[ ] develop 分支已创建
[ ] main 分支保护规则已配置
[ ] .editorconfig 已添加
[ ] PR 模板已创建
```

---

## 0.11 下一步

所有检查项打勾后，进入 **[阶段 1：项目脚手架与基础设施](./phase-1-项目脚手架.md)**。
