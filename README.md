# PAW & PURE 智慧宠物洗护系统

这是一套给宠物洗护门店用的网页系统：可以预约、到店开单、推进护理、收银、管会员和库存。

本文专门写给**第一次在自己电脑上跑程序的人**。按顺序做即可，不需要会写代码。

> 本文以 **Windows 11** 为例（Win10 步骤相同）。Mac 用户请看文末「Mac 补充」。

---

## 目录

1. [你最终会看到什么](#1-你最终会看到什么)
2. [需要准备的三样软件](#2-需要准备的三样软件)
3. [确认软件已经装好](#3-确认软件已经装好)
4. [打开项目文件夹](#4-打开项目文件夹)
5. [第一次启动（最重要）](#5-第一次启动最重要)
6. [用演示账号登录](#6-用演示账号登录)
7. [五个角色分别能做什么](#7-五个角色分别能做什么)
8. [自己注册一个宠主账号](#8-自己注册一个宠主账号)
9. [以后每次怎么再打开](#9-以后每次怎么再打开)
10. [怎么退出 / 关掉程序](#10-怎么退出--关掉程序)
11. [数据会不会丢](#11-数据会不会丢)
12. [常见问题](#12-常见问题)
13. [可选：改用 MySQL 长期保存数据](#13-可选改用-mysql-长期保存数据)
14. [可选：接真实支付宝](#14-可选接真实支付宝)
15. [给老师 / 开发者看的说明](#15-给老师--开发者看的说明)

---

## 1. 你最终会看到什么

启动成功后，用浏览器打开：

**http://127.0.0.1:5173/**

会看到 Paw & Pure 的登录页，左边是封面图，右边是「欢迎回来」。点开「本地演示账号」，选一个角色，密码填 `petcare123`，就能进工作台。

系统其实是两段程序同时跑：

| 名称 | 通俗理解 | 地址 |
|---|---|---|
| 后端 | 负责存数据、算价格、管权限的「服务器」 | `http://127.0.0.1:8000` |
| 前端 | 你在浏览器里看到的网页 | `http://127.0.0.1:5173` |

网页会自动把请求转给后端，**你只要打开 5173 这个网址**。

---

## 2. 需要准备的三样软件

必须安装这三样，缺一不可：

| 软件 | 干什么用 | 推荐版本 |
|---|---|---|
| **JDK（Java）** | 运行后端 | 17 或更新的 17.x / 21 |
| **Maven** | 帮后端下载依赖并启动 | 3.8 或更新 |
| **Node.js** | 运行前端网页 | 18 或 20 的 LTS |

下面两种安装方式，**会用命令的选方式 A，更习惯点鼠标的选方式 B**。

### 方式 A：用 Windows 自带的 `winget`（推荐）

1. 按键盘 **Win** 键，输入 `终端` 或 `Windows Terminal` 或 `PowerShell`，打开它。
2. **一整行复制**，粘贴进去，按回车（如果弹出是否允许，选「是」）：

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements
winget install --id OpenJS.NodeJS.LTS -e --accept-package-agreements --accept-source-agreements
winget install --id Apache.Maven -e --accept-package-agreements --accept-source-agreements
```

3. **关掉这个窗口，再重新打开一个新的终端**（这一步很重要，否则系统还找不到刚装的软件）。
4. 跳到第 3 节做检查。

### 方式 B：去官网下载安装包

**① Java 17**

1. 打开：https://adoptium.net/zh-CN/temurin/releases/?version=17
2. 操作系统选 Windows，架构选 x64，包类型选 JDK，选 **.msi** 安装包下载。
3. 双击安装，一路「下一步」。遇到「Set JAVA_HOME」或「Add to PATH」请勾上。

**② Node.js**

1. 打开：https://nodejs.org/zh-cn
2. 下载 **LTS** 版本（长期支持版，按钮通常是绿色的）。
3. 双击安装，一路「下一步」，保持勾选 **Add to PATH**。

**③ Maven**

1. 打开：https://maven.apache.org/download.cgi
2. 下载 `apache-maven-3.9.x-bin.zip`（数字可能略有不同）。
3. 解压到一个好记的位置，例如 `C:\maven`。
4. 把 Maven 加到系统 PATH：
   - 按 Win 键，搜索「编辑系统环境变量」→ 打开 → 点「环境变量」。
   - 在「系统变量」里找到 `Path`，点「编辑」→「新建」，填入解压后的 `bin` 目录，例如 `C:\maven\apache-maven-3.9.9\bin`。
   - 如果没有 `JAVA_HOME`，点「新建」，变量名填 `JAVA_HOME`，变量值填 JDK 安装目录（常见类似 `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`）。
5. **确定保存后，关掉所有终端窗口，重新打开一个。**

---

## 3. 确认软件已经装好

重新打开 PowerShell / 终端，**分别**输入下面三行，每行输完按回车：

```powershell
java -version
mvn -version
node -v
npm -v
```

成功时大致会看到：

```
java version "17. ..."     （或 openjdk 17 / 21）
Apache Maven 3. ...
v18. ... 或 v20. ...       （node）
10. ... 或 9. ...          （npm）
```

如果某一条提示「不是内部或外部命令」：

- 先确认第 2 节装完后**重新打开了终端**。
- 仍不行：回到方式 B 检查 PATH。
- Java 显示 8 或 11：需要改成 17，否则后端启动会失败。

---

## 4. 打开项目文件夹

假设项目就在 `D:\PetManagement`（如果你解压到了别的盘，把下面路径改成你自己的）。

在 PowerShell 里输入：

```powershell
cd D:\PetManagement
dir
```

你应该能看到至少这两个文件夹：

- `backend`　← 后端（Java）
- `frontend`　← 前端（网页）

如果提示「找不到路径」，说明 `cd` 后面的地址写错了。可以在文件资源管理器中打开项目文件夹，点击地址栏复制路径，再写成 `cd "你复制的路径"`。

---

## 5. 第一次启动（最重要）

需要**同时开两个终端窗口**：一个跑后端，一个跑前端。两个都不要关。

### 窗口 1：启动后端

```powershell
cd D:\PetManagement\backend
mvn spring-boot:run
```

- **第一次**会下载很多文件，可能要 3～10 分钟，请耐心等，不要反复按回车。
- 成功的标志：最后出现类似

  `Started PetGroomingApplication in ... seconds`

  并且窗口**一直停着不再往下走**，这是正常的，说明服务器在运行。
- 看到红色 `ERROR` 并很快退出：请看 [第 12 节常见问题](#12-常见问题)。

默认使用 **H2 内存数据库**，不用单独安装 MySQL，演示数据会自动准备好。

### 窗口 2：启动前端

**再开一个新的** PowerShell（不要关掉窗口 1）。

```powershell
cd D:\PetManagement\frontend
npm install
npm run dev
```

- `npm install` 也是第一次最慢，同样请等它自己结束。
- 成功的标志：出现

  `Local: http://127.0.0.1:5173/`

  这一行，窗口同样要一直开着。

### 打开网页

用 Chrome / Edge 浏览器访问：

**http://127.0.0.1:5173/**

不要用 `https`，也不要写成 `localhost:8000` 当首页（8000 是接口，不是给你点的登录页）。

---

## 6. 用演示账号登录

登录页下方有一块 **「本地演示账号」**。点其中一个角色，账号会自动填上。

**所有演示账号的密码都是：**

```
petcare123
```

（注意全小写、没有空格。）

| 点哪一项 | 账号 | 进去之后像谁 |
|---|---|---|
| 店长: manager | `manager` | 门店店长林晓晴 |
| 前台: reception | `reception` | 前台陈雨桐 |
| 洗护师: groomer | `groomer` | 洗护师周凯 |
| 宠主: owner | `owner` | 会员林知夏 |
| 管理员: admin | `admin` | 系统管理员 |

点「登录」。成功后会进入对应工作台。右上角可以「修改密码」或「退出登录」。

---

## 7. 五个角色分别能做什么

可以按这个顺序体验一遍，比较好懂：

1. **宠主 `owner`**：看自己的狗「糯米 / 旺财」，预约一次护理，到「护理进度」里确认加项，用余额或支付宝（演示里可点「模拟扫码」）付钱，会员页可以给账户充值。
2. **前台 `reception`**：看今日到店，帮客人新建预约或「到店开单」，签到生成工单，收银（现金 / 余额 / 次卡 / 支付宝），用 6 位凭证交宠。
3. **洗护师 `groomer`**：只看到分配给自己的工单，一步步推进护理阶段，申请加项，给宠物打健康分；可以修改自己经手过的宠物健康档案。
4. **店长 `manager`**：看今日营收和待办，改派 / 作废工单，管员工、工位、服务价格、库存，打开「门店设置」改店名和营业时间。
5. **管理员 `admin`**：账号与权限、图片、库存、服务项目，也可以顶班做预约和开单；「门店设置」里还能看操作审计。

---

## 8. 自己注册一个宠主账号

登录页点「立即注册」：

- 账号：3～32 位字母数字下划线，**或邮箱**（例如 `xiaoming@example.com`）
- 姓名、电话
- 密码：**至少 12 位**（演示账号的 `petcare123` 是系统预置的，自己注册必须更长）
- 注册成功后自动进入宠主工作台，可以添加爱宠、预约

员工账号（店长 / 前台 / 洗护师 / 管理员）不能自助注册，要由店长或管理员在「员工」页创建。

---

## 9. 以后每次怎么再打开

电脑重启或你关掉了那两个窗口后，再按第 5 节做一遍即可。

**已经执行过 `npm install` 的话，前端第二次起可以跳过它**，只需要：

窗口 1：

```powershell
cd D:\PetManagement\backend
mvn spring-boot:run
```

窗口 2：

```powershell
cd D:\PetManagement\frontend
npm run dev
```

浏览器打开 http://127.0.0.1:5173/

建议：**先开后端，等出现 Started，再开前端。**

---

## 10. 怎么退出 / 关掉程序

1. 网页里点「退出登录」（只是退出账号，程序还在跑）。
2. 要彻底关掉系统：回到那两个黑色/蓝色终端窗口，在窗口里按 **Ctrl + C**，如果问是否终止，输入 `Y` 回车。
3. 两个窗口都结束后，网页就打不开了，这是正常的。

不要直接叉掉正在跑 `mvn` / `npm` 的窗口也行，但用 Ctrl+C 更干净。

---

## 11. 数据会不会丢

**默认情况会丢。**

现在用的是 H2「内存数据库」：后端一关，预约、新注册的账号、改过的价格都会回到最初的演示数据。这是为了方便课堂演示，每次都是干净的旗舰店。

如果希望关掉电脑后数据还在，请看 [第 13 节 MySQL](#13-可选改用-mysql-长期保存数据)。

上传的宠物照片、登录封面等文件存在 `backend/uploads/photos/`，只要你不删这个文件夹，图片文件还在；但 H2 模式下宠物记录本身仍会随重启重置。

---

## 12. 常见问题

### 网页打不开 / 一直转圈 / 「暂时无法连接门店服务」

- 两个窗口是否都还在跑，没有报错退出？
- 网址是不是 `http://127.0.0.1:5173/`（不是 8000，也不是 https）？
- 是不是先开了前端、后端还没起来？等后端出现 `Started` 再刷新网页。

### 登录提示「账号或密码错误」

- 演示密码必须是 `petcare123`，不要用大写、不要多空格。
- 刚重启过后端，数据已重置，请用上面五个演示账号，不要用你上次自己改过的密码。

### 登录提示 403 或「请求失败（403）」

- 多半是后端没启动，或前端代理不到 8000。确认窗口 1 仍在跑。
- 关掉浏览器再打开 5173，重新登录一次。

### `mvn spring-boot:run` 说端口被占用（8000）

说明上次的后端没关干净，或别的软件占用了 8000。

在 **以管理员身份打开的 PowerShell** 中：

```powershell
netstat -ano | findstr :8000
```

最后一列是 PID 数字，然后：

```powershell
taskkill /PID 这里换成数字 /F
```

再重新 `mvn spring-boot:run`。

### 前端说 5173 被占用

同样用上面的方法查 `:5173`。或者看终端里 Vite 是不是改到了 `5174`，那就用新地址打开。

### `java -version` 太旧或没有

必须是 17 及以上。装好后务必**新开终端**再试。

### `npm install` 很慢或失败

- 多半是网络问题。可换成国内镜像后再装一次：

```powershell
cd D:\PetManagement\frontend
npm config set registry https://registry.npmmirror.com
npm install
```

### `mvn` 下载依赖失败

同样是网络。可配置华为 / 阿里云 Maven 镜像（搜「Maven 阿里云镜像 settings.xml」）。公司网络有代理时，需要在 Maven 和 npm 里分别设置代理。

### 页面是英文报错一长串

把终端窗口里**从报错开始往上约 30 行**复制下来，通常能看到 `Address already in use`、`Unsupported class file`、`Port 8000` 这类关键词，对照本节处理。

---

## 13. 可选：改用 MySQL 长期保存数据

只有在你已经会装 MySQL，并且希望数据重启后还在时才需要这一步。**课堂演示用默认 H2 即可，可跳过。**

1. 安装 MySQL 8，记住 root 密码。
2. 用 MySQL 客户端执行：

```sql
CREATE DATABASE pet_grooming DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. 启动后端时加上 MySQL 配置（把密码换成你的）：

```powershell
cd D:\PetManagement\backend
$env:PET_DB_PASSWORD="你的root密码"
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

表结构由 Flyway 自动从 `backend/src/main/resources/db/migration/mysql/` 升级（V1～V22）。

若还希望自动灌入和 H2 一样的演示账号、宠物、预约，使用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql,mysql-demo"
```

也可用环境变量：

| 变量 | 含义 | 默认 |
|---|---|---|
| `PET_DB_URL` | JDBC 地址 | `jdbc:mysql://localhost:3306/pet_grooming?...` |
| `PET_DB_USER` | 用户名 | `root` |
| `PET_DB_PASSWORD` | 密码 | 空 |
| `PET_SEED_ENABLED` | 是否写演示账号 | mysql 配置下默认 false |

---

## 14. 可选：接真实支付宝

默认是**演示支付**：弹出收款码后可以点「模拟扫码支付成功」，不会真的扣钱。

若要走支付宝个人开发者（RSA2）：

1. 打开 `backend/src/main/resources/application.yml`
2. 把 `pet-grooming.payment.alipay` 下的 `app-id`、`private-key`、`alipay-public-key` 填成你在开放平台复制的内容
3. 本地演示可保持 `sandbox-enabled: true`；确认真实扣款后再改为 `false`，并把 `gateway-url` 改成 `https://openapi.alipay.com/gateway.do`
4. 公网回调地址填到 `notify-base-url`（本机 127.0.0.1 支付宝访问不到，需要内网穿透或已部署的域名）

没配密钥时不要改 `sandbox-enabled`，否则真实下单会失败。

---

## 15. 给老师 / 开发者看的说明

### 技术栈

- 后端：Spring Boot 3.2.5、Spring Security 6、JdbcTemplate、H2 / MySQL 8、Flyway
- 前端：Vue 3、TypeScript、Vite 5、Tailwind CSS
- 认证：Session + CSRF（`X-CSRF-TOKEN`）、BCrypt、`account_version` 踢下线

### 目录

```
PetManagement/
├── backend/          Java 后端（端口 8000）
├── frontend/         Vue 前端（端口 5173，/api 代理到 8000）
└── README.md         本说明
```

### 演示账号（密码 `petcare123`，可用环境变量 `PET_SEED_PASSWORD` 覆盖）

| 账号 | 角色 | 工作台要点 |
|---|---|---|
| `admin` | 管理员 | 账号权限、图片、库存、服务、门店设置与审计；可预约/开单 |
| `manager` | 店长 | 营收总览、排班、改派/退款、员工与工位、门店设置 |
| `reception` | 前台 | 预约、到店开单、建档、收银、交宠 |
| `groomer` | 洗护师 | 本人工单推进、加项、健康评分、经手宠物档案 |
| `owner` | 宠主 | 爱宠、预约、加项确认、余额/支付宝自付与充值、评价 |

### 默认端口与代理

- 后端绑定 `127.0.0.1:8000`（见 `backend/src/main/resources/application.yml`）
- 前端开发服务器默认 `5173`，`frontend/vite.config.ts` 把 `/api` 代理到 `http://127.0.0.1:8000`
- 可用环境变量 `VITE_API_PROXY_TARGET` 改代理目标

### 数据库

- H2 权威脚本：`backend/src/main/resources/schema.sql`、`data.sql`
- MySQL Flyway：`backend/src/main/resources/db/migration/mysql/`（V1～V22）

### 常用命令

```powershell
# 后端测试
mvn -f D:\PetManagement\backend\pom.xml test

# 前端单测
cd D:\PetManagement\frontend
npm test

# 前端类型检查与打包
npm run build

# Playwright（需先装浏览器：npx playwright install）
npm run test:e2e
```

改完 Java 代码后需要**停掉后端再重新 `mvn spring-boot:run`**，网页刷新即可；只改前端时 Vite 会自动热更新。

---

## Mac 补充

1. 建议用 Homebrew：

```bash
brew install temurin@17 maven node
```

2. 启动命令相同，只是路径改成你的项目目录，例如：

```bash
cd ~/PetManagement/backend && mvn spring-boot:run
cd ~/PetManagement/frontend && npm install && npm run dev
```

3. 浏览器同样打开 http://127.0.0.1:5173/

---

遇到本文没写到的报错时：把**终端里红色那一段完整复制**（不要只截最后一行），对照第 12 节，或交给会看日志的同学。大多数情况是「没装齐软件 / 没开两个窗口 / 端口被占用 / 没重新打开终端」这四类。
