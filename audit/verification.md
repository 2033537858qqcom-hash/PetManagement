# PAW & PURE 宠物洗护系统 — 真实构建 / 测试 / 角色权限冒烟验证报告

- 审计角色：verify-runner（task-4：真实构建与测试验证）
- 审计对象：`D:\PetManagement`（Windows，非 git 仓库；只读源码，本报告为唯一交付物）
- 验证时间：2026-10-31 12:22–12:31（Asia/Shanghai）
- 结论口径：**以本次真实运行证据为准**，不采信 README.md 的旧描述。结论可信度：**高**（后端 104 用例、前端 73 用例、Mock E2E 23 例、真实后端 E2E 4 例、真实 HTTP 角色矩阵均在本机独立重跑）。

## 0. 结论摘要

| 验证项 | 结果 | 关键数字 |
|---|---|---|
| 后端 `mvn -B test` | ✅ 通过 | 27 套件 / 104 tests / 0 failures / 0 errors / 7 skipped（MySQL 集成类按设计跳过）/ BUILD SUCCESS / 69s |
| 后端 `mvn -B -DskipTests package` | ✅ 通过 | BUILD SUCCESS / 9.4s / `smart-pet-grooming-1.0.0.jar` 32.0 MB |
| 前端 `npm test` | ✅ 通过 | node:test 15 + vitest 58 = **73** tests 全过 / exit 0 / 16s |
| 前端 `npm run build`（vue-tsc + vite） | ✅ 通过 | vue-tsc 0 error；vite 1633 modules，dist JS 292.39 kB(gzip 97.24)、CSS 84.19 kB / exit 0 / 18s |
| Playwright Mock E2E `npm run test:e2e` | ❌ 1/23 失败 | 22 passed / 1 failed（`payment.spec.ts:44`）/ 54s；**定位为测试数据“时间炸弹”，非产品缺陷** |
| Playwright Real E2E `npm run test:e2e:real` | ✅ 通过 | 4/4 passed（真实后端 8181 + 真实前端 5194）/ 32s |
| 真实服务冒烟（jar + H2 种子库） | ✅ 通过 | 5 个演示账号登录 200；角色×接口矩阵见 §5；越权写入全部 403 |
| MySQL 集成套件（13318） | ⚠️ 未验证（环境缺失） | 7 个用例 skip；本机仅 3306 实例，未用于隔离验证 |

**总体判断**：构建、单元/组件、Mock E2E、真实后端 E2E、真实 HTTP 角色隔离**全部可用**；唯一红灯是 Mock E2E 的支付收银台用例因**固定过期时间早于系统日期**而失败（见 §4.2）。角色权限为“认证后可访问全部 `/api/**`，再在各端点手工 require 角色/属主”的模型，本次对 5 角色的 GET/POST 矩阵实测**未发现越权放行**，但该模型存在结构脆弱性（§6.2）。

## 1. 验证环境（实测）

| 项目 | 实测值 | 证据 |
|---|---|---|
| OS / 工作目录 | Windows / `D:\PetManagement` | `working_directory` |
| JDK | `java -version` = **23.0.2**（PATH），Maven 实际使用 `JAVA_HOME=C:\Program Files\Java\jdk-17` = **17.0.12** | `mvn -v` |
| Maven | Apache Maven **3.9.11** | `mvn -v` |
| Node / npm | **v25.9.0 / 11.12.1** | `node -v`、`npm -v` |
| Chrome（Playwright channel） | `C:\Program Files\Google\Chrome\Application\chrome.exe` 存在 | Test-Path |
| 初始端口占用 | 5173（Vite dev，PID 19420）、36090（DSH GUI，PID 25344）、3306（MySQL，PID 7300）；**8000 / 5181 / 5193 / 5194 / 8181 空闲** | Get-NetTCPConnection |
| 系统日期 | 2026-10-31（直接影响 §4.2 的 E2E 失败归因） | Get-Date |
| 端口清理 | 验证结束后仅剩 5173、36090；本次启动的 8000/8181/5193/5194 均已释放，**未 kill 他人进程** | Get-NetTCPConnection |

## 2. 命令 × 结果矩阵

| # | 命令（工作目录） | 退出码 | 墙钟耗时 | 结果摘要 |
|---|---|---|---|---|
| 1 | `mvn -B test`（backend） | 0 | 69s（mvn 自报 01:05） | BUILD SUCCESS；104 tests / 0 fail / 0 err / 7 skip；27 套件 |
| 2 | `mvn -B -DskipTests package`（backend） | 0 | 13s（mvn 自报 9.4s） | BUILD SUCCESS；产出 `target/smart-pet-grooming-1.0.0.jar` 32,011,819 B |
| 3 | `npm test`（frontend） | 0 | 16s | node --test 4 文件 15 tests；vitest 7 文件 58 tests；全过，0 fail |
| 4 | `npm run build`（frontend） | 0 | 18s | `vue-tsc --noEmit` 通过；`vite build` 7.43s，1633 modules，dist/index-*.js 292.39 kB、index-*.css 84.19 kB |
| 5 | `npm run test:e2e`（frontend，Mock API） | 1 | 54s（play 自报 49.3s） | 23 tests：22 passed / **1 failed**（`payment.spec.ts:44`） |
| 6 | `npm run test:e2e:real`（frontend，真实后端） | 0 | 34s（play 自报 32.0s） | 4/4 passed：registration / photo-management / product-photo / visit |
| 7 | `java -jar target/smart-pet-grooming-1.0.0.jar`（backend，后台） | —（手动停止） | 启动 **19.4s** 就绪 | Tomcat 8000；H2 内存库 + data.sql 种子；`/api/auth/csrf` 200 |
| 8 | MySQL 集成套件（13318） | 未执行 | — | 需 13318 实例 + `-Dmysql.integration=true` 等属性；本机无实例 → 明确记为“未验证（环境缺失）” |

> 说明：命令 1/2 使用 `JAVA_HOME` 下的 JDK 17（Spring Boot 3.2.5 要求）；日志全文见 §9 证据清单。

## 3. 后端测试套件明细（本次运行，surefire 实测）

| 套件 | tests | skipped | 用时 |
|---|---|---|---|
| AccessIsolationTest | 4 | 0 | 17.63s |
| AccountManagementTest | 3 | 0 | 3.99s |
| AddonRequestTest | 1 | 0 | 2.12s |
| AppointmentTest | 3 | 0 | 1.65s |
| AuthTest | 3 | 0 | 4.38s |
| CareFlowTest | 6 | 0 | 2.39s |
| EmployeeScheduleTest | 1 | 0 | 1.87s |
| InventoryFlowTest | 4 | 0 | 1.64s |
| MembershipTest | 2 | 0 | 1.16s |
| PaymentIntegrationTest | 9 | 0 | 1.52s |
| PhotoManagementTest | 8 | 0 | 2.23s |
| ProductPhotoTest | 8 | 0 | 1.13s |
| ReassignAndVoidTest | 4 | 0 | 1.00s |
| RegistrationTest | 23 | 0 | 2.87s |
| RescheduleTest | 1 | 0 | 0.74s |
| RetailOrderTest | 2 | 0 | 1.28s |
| SchedulingRulesTest | 2 | 0 | 0.003s |
| ServicePricingTest | 1 | 0 | 1.05s |
| SettlementTest | 6 | 0 | 1.01s |
| StoreSetupTest | 1 | 0 | 1.16s |
| VisitWorkflowTest | 1 | 0 | 1.34s |
| WalkInLateNightTest | 1 | 0 | 1.38s |
| WeightRecordingTest | 1 | 0 | 1.61s |
| WorkflowTest | 2 | 0 | 1.50s |
| MySqlBootstrapTest | 2 | **2** | 0.004s |
| MySqlPersistenceTest | 4 | **4** | 0s |
| MySqlRestartTest | 1 | **1** | 0.001s |
| **合计** | **104** | **7** | 校验：97 executed + 7 skipped = 104 ✅ |

跳过归因（代码级）：`MySqlPersistenceTest.java:20` `@EnabledIfSystemProperty(named="mysql.integration") `、`MySqlBootstrapTest.java:17` `mysql.bootstrap.integration`、`MySqlRestartTest.java:18` `mysql.browser.db`；默认 `mvn test` 下三套件整体 skip，**不是失败**。

## 4. 前端与端到端验证

### 4.1 前端单元/组件测试（`npm test`，exit 0）

- node --test：`dialog/booking/catalog/workspace` 4 个 `.test.mjs`，**15 tests / 15 pass**（含 “five-stage view marks the current stage without inventing timestamps”“login identity chooses a distinct workspace”）。
- vitest：7 文件 **58 tests / 58 pass** —— app-time-picker(3)、app-select(7)、app-date-picker(3)、photos(14)、store-setup-panel(13)、entry-page(12)、payment-modal(6)。
- 合计 73 个前端用例全过；无 skip / 无 todo。

### 4.2 Mock E2E（`npm run test:e2e`，日志 79 行，1 红）

- 23 tests：**22 passed / 1 failed**；失败唯一用例：
  `tests/e2e/payment.spec.ts:44 › pet owner settles waiting-pickup order with wechat payment via cashier modal`
  断言 `payment.spec.ts:172`：`expect(modal.locator('.qr-svg')).toBeVisible()` → 5s 超时“element(s) not found”（弹窗与支付单号 `PAY20261030999901` 已可见，说明收银台已打开）。
- **根因定位（非产品缺陷，测试数据时间炸弹）**：
  - 用例 fixture 写死 `expireAt: '2026-10-30 23:59:59'`（`frontend/tests/e2e/payment.spec.ts:75`），而当前系统日期为 **2026-10-31**；
  - 组件 `PaymentModal.vue:61-71` `computeRemaining()` 发现过期 → `status='CLOSED'`；模板 `PaymentModal.vue:236-250` 在 `isExpired` 时渲染“二维码已过期”覆盖层，`v-else` 分支的 `.qr-svg`（`PaymentModal.vue:252`）不渲染；
  - 即：**产品行为正确**（过期二维码不应展示），失败来自用例固定日期已过期。修复建议：fixture 改为相对当前时间（如 `Date.now()+15*60*1000`）。
  - 该失败会长期阻塞“发布级 E2E 全绿”，属**测试维护缺陷（中）**；与后端/前端实现无关（同功能单测 `payment-modal.test.ts` 6 例全过，真实后端 real-e2e `visit` 通过）。

### 4.3 Real E2E（`npm run test:e2e:real`，4/4 通过）

配置 `playwright.real.config.ts` 自动拉起 `mvn spring-boot:run --server.port=8181` + Vite 5194，真实前后端联调：

- `visit.spec.ts` ✅ real backend: accounts, check-in, grooming, settlement and handover（16.7s）
- `registration.spec.ts` ✅ new owner registers, keeps the session, adds a pet, and logs in again（7.7s）
- `photo-management.spec.ts` ✅ administrator replaces project photos and the replacement is visible after logout and reload（11.5s）
- `product-photo.spec.ts` ✅ manager creates a stock item with a photo shown in inventory, retail and its order（8.0s）

> 结论：**真实链路（含会话保持、照片上传落盘、到店→护理→结算→交宠主流程）可用**，非仅 mock。

## 5. 真实服务冒烟：角色 × 接口状态码矩阵

启动方式：`java -jar backend/target/smart-pet-grooming-1.0.0.jar`（H2 内存库 + `data.sql` 种子；Tomcat 8000；19.4s 就绪）。
会话方式：PowerShell `WebRequestSession` 维持 Cookie；`GET /api/auth/csrf` 取 token → `POST /api/auth/login`（JSON + `X-CSRF-TOKEN`）。5 个演示账号密码均为 `petcare123`（`backend/src/main/resources/data.sql:2` + `SecurityConfig.java:25-28` 注入 hash），**全部登录 200**，`/api/auth/me` 角色正确：manager=店长、reception=前台、groomer=洗护师(employeeId=1)、owner=宠主(customerId=1)、admin=管理员。

### 5.1 只读接口矩阵（HTTP 状态码，匿名列含未登录行为）

| 接口（GET） | 匿名 | manager | reception | groomer | owner | admin |
|---|---|---|---|---|---|---|
| `/api/auth/me` | **401** | 200 | 200 | 200 | 200 | 200 |
| `/api/pets` | **401** | 200 | 200 | 200 | 200 | 200 |
| `/api/analytics/dashboard` | **401** | 200 | 200 | **403** | **403** | 200 |
| `/api/media/photos` | **200（公开）** | 200 | 200 | 200 | 200 | 200 |
| `/api/customers` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/appointments` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/grooming` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/grooming/addons` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/inventory` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/employees` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/services` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/accounts` | - | 200 | **403** | **403** | **403** | 200 |
| `/api/schedules?date=…` | - | 200 | 200 | 200 | **403** | 200 |
| `/api/members/cards` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/reviews` | - | 200 | 200 | 200 | 200 | 200 |
| `/api/orders` | - | 200 | 200 | 200 | 200 | 200 |

> “200”不代表看到全量数据：读接口按角色**行级过滤**（§5.3）。匿名统一 401 由 `SecurityConfig.java:57-60` 定制 JSON 入口返回（`{"message":"请先登录"}`），与实测一致。

### 5.2 跨角色越权写入矩阵（全部 403，未发生数据变更）

| 角色 | 请求 | 结果 |
|---|---|---|
| reception | `POST /api/inventory/1/adjust` | **403** 没有操作权限 |
| reception | `PUT /api/schedules/1/2026-11-02` | **403** |
| groomer | `POST /api/inventory/1/adjust` | **403** |
| groomer | `POST /api/grooming/1/advance`（非本人工单，员工2的工单） | **403** |
| groomer | `POST /api/accounts` | **403** |
| owner | `PUT /api/services/1/prices` | **403** |
| owner | `POST /api/grooming/3/advance`（他人宠物的工单） | **403** |
| owner | `GET /api/grooming/1/events`（他人工单） | **403** |
| owner | `POST /api/appointments/3/cancel`（他人预约） | **403** “只能取消自己的预约” |
| owner | `POST /api/customers/2/recharge` | **403** |
| manager | `POST /api/inventory/1/adjust`（权限通过，payload 非法） | 400 校验失败（证明 manager 被放行到业务校验） |

### 5.3 数据行级隔离实测（各列表接口返回条数；manager 列为种子库全量基线）

| 接口（GET） | manager | reception | groomer(employeeId=1) | owner(customerId=1) | admin |
|---|---|---|---|---|---|
| `/api/pets` | 4 | 4 | **1** | **2**（customer_id=1） | 4 |
| `/api/customers` | 3 | 3 | **1**（仅关联客户） | **1**（仅本人） | 3 |
| `/api/appointments` | 4 | 4 | **1** | **3**（仅本人） | 4 |
| `/api/grooming` | 4 | 4 | **2**（仅 employee_id=1 工单） | **3**（仅 customer_id=1） | 4 |
| `/api/grooming/addons` | 1 | 1 | **0** | **1**（仅本人工单） | 1 |
| `/api/inventory` | 3 | 3 | 3（**仅 id/name/unit 字段**） | 3（无 threshold，含 stock/price） | 3 |
| `/api/employees` | 3 | 3 | **1**（仅本人） | 3（字段裁剪，无 skills） | 3 |
| `/api/services` | 4 | 4 | 4 | 4 | 4 |
| `/api/reviews` | 2 | 2 | **0** | **1**（仅本人相关） | 2 |
| `/api/members/cards` | 2 | 2 | **0**（按设计返回空） | **1**（仅本人） | 2 |
| `/api/orders` | 1 | 1 | **0** | **1**（仅本人） | 1 |

> 计数来自真实服务（`counts.ps1` → `test-results/verify/counts.txt`）；同一 URL 对不同角色返回不同行集与不同字段集，说明行级隔离与字段裁剪均在控制器内实现。

补充实测：
- 宠主访问 `GET /api/services/recommendations/{petId}`：本人宠物(1)=200；他人宠物(3)=**403**（`AccountAccess.requirePet`，`DemoController.java:57-66`）。
- 洗护师访问 `GET /api/grooming/{id}/events`：本人工单(2)=200；他人工单(1)=**403**（`CareController.java:183-185`）。
- 洗护师访问 `GET /api/grooming/{id}/settlement`：即使本人工单也=**403**——这是**设计如此**（结算仅宠主本人/前台/店长/管理员，`CareController.java:196-197`），不构成缺陷。
- 洗护师 `/api/members/cards` 返回 `[]`、`/api/reviews` 返回与本人相关评价（`DemoController.java:249-250, 264-265`）。
- 洗护师 `/api/analytics/dashboard` = **403**：`DemoController.java:34` `access.requireStaff()` 未包含 GROOMER；**需与前端“洗护师工作台”首屏接口核对**（本次 Mock E2E 22/23 通过、real E2E 4/4 通过，说明当前洗护师工作台未调用该接口，暂无用户可见问题；若后续复用仪表盘会 403）。

## 6. 角色权限实现证据与缺口

### 6.1 实现位置（代码级）

- `SecurityConfig.java:48-63`：URL 级只做 `/api/**` → `authenticated()`；仅 `/api/auth/csrf|login|register`、`GET /api/media/photos*`、`/api/payment/notify/**` 放行；`anyRequest().denyAll()`；401 定制 JSON；CSRF 默认开启（登录实测必须带 `X-CSRF-TOKEN`）。
- `AccountAccess.java:14-37`：`role()`、`requireStaff()`、`requirePet()`（OWNER 校验 customer_id、GROOMER 校验工单/预约归属、其余按 staff）、`customerId()/employeeId()` 从 `demo_account_binding` 绑定取。
- 网关无 URL 级角色规则 → **每个端点自行 require**（共 105 处 `AccountAccess` 调用 / 92 条映射注解（含 12 条类级 `@RequestMapping`）），本次抽测的读写端点均有对应校验。

### 6.2 缺口与风险（按严重度）

| 严重度 | 缺口 | 证据 | 影响/建议 |
|---|---|---|---|
| **中** | 授权模型为“先认证后逐端点手工 require”，SecurityConfig 不做 URL 级角色约束；漏写一处 `require` 即横向越权 | `SecurityConfig.java:48-63`（`/api/**` 仅 authenticated）+ `AccountAccess.java:27-37` | 本次矩阵未发现放行，但结构性脆弱。建议按角色/资源分组 URL 规则 + 方法级注解兜底 |
| **中** | Mock E2E 支付用例时间炸弹，发布级 E2E 不稳定 | `frontend/tests/e2e/payment.spec.ts:75`（`expireAt: '2026-10-30 23:59:59'`）+ 断言 :172；组件逻辑 `PaymentModal.vue:61-71,236-250` | 非产品缺陷；改用相对当前时间即可恢复全绿 |
| **中** | 洗护师被 `/api/analytics/dashboard` 拒绝（403），若工作台复用仪表盘会缺数据 | `DemoController.java:34` `requireStaff()` 不含 GROOMER | 当前前端未调用；建议明确该接口对 GROOMER 的数据视图（本人指标）或文档化 |
| 低 | 宠主可读取商品库存量 `stock`（不含 threshold），属内部经营数据 | `DemoController.java:235-237`（owner 分支显式 select `stock`；groomer 分支仅 id/name/unit） | 业务若将库存视为内部信息则应收窄 |
| 低 | 照片目录（`GET /api/media/photos` 与图片流）匿名可访问 | `SecurityConfig.java:51` | 品牌封面/商品图公开符合展示需要；上传/替换仅 ADMIN（`PhotoController.java:23,27`），风险可控 |
| 低 | 默认 profile 为 H2 内存库（数据不持久），生产需 mysql profile | `application.yml:29-35`；MySQL 路径未验证（§7） | 演示/测试可用；生产切换需验证 Flyway/mysql 迁移脚本 |

## 7. 历史基线 / 本次差异 / 环境阻塞

1. **历史 surefire 目录**：本次运行前 `backend/target/surefire-reports` 存在 **171** 个文件（历史轮次产物）；本次 `mvn test` 重写后为 **172** 个文件 / 27 个套件 TXT。历史汇总文件未单独留存，故无法逐项 diff；但本次为**同套件独立重跑且 104 tests 全绿**，未出现历史新增失败。
2. **历史 Maven 启动失败与本次无关**：`backend/backend-run.log:54-66` 记录 2026-10-29 的一次 `spring-boot:run` 失败，原因是 `Port 8000 was already in use`（环境冲突），非代码缺陷；本次 8000 空闲，jar 启动 19.4s 正常。
3. **历史 Playwright 残留**：根目录 `test-results/.last-run.json` 为 2026-10-30 23:04 的旧产物（status=failed，failedTests=[]，信息不完整）；本次 Mock/Real E2E 产物写入 `frontend/test-results/.last-run.json`（real 运行后 status=passed）。两者不可混用。
4. **MySQL 集成（13318）**：本机 3306 有 MySQL 8.0.34 实例，但测试要求 127.0.0.1:**13318** 且需 `-Dmysql.integration=true` / `-Dmysql.bootstrap.integration=true` / `-Dmysql.browser.db=codex_pet_browser_*`；未在 15 分钟窗口内构建隔离实例 → **未验证（环境缺失）**，7 个用例 skip。这是本次“不能证明”的主要项。
5. **端口/进程**：全程未 kill 5173（Vite dev）、36090（DSH GUI）；本次自启服务均在结束后释放。

## 8. 自动化验证能证明什么 / 不能证明什么

**能证明（本次实测）**
- 后端 27 套件 97 个可运行用例全过（权限隔离、到店/护理/结算/交宠、预约、改约、加项、库存、会员、零售、照片、账号、支付沙箱、注册 23 例等）。
- 前端类型检查与生产构建通过；73 个组件/工具单测全过。
- Mock E2E 覆盖 12 个 spec / 23 例：22 例全绿（角色工作台、controls、photos、inventory、accounts、store-setup、membership、retail、schedules、care 流程等）。
- 真实后端 E2E 4 例全绿：注册、照片替换（含退出重登可见）、商品照片、完整到店→护理→结算→交宠。
- 真实 HTTP 冒烟：5 角色登录、401/403 边界、行级过滤、越权写入拒绝均符合设计。

**不能证明**
- MySQL/生产数据库路径（7 skip），mysql profile 迁移脚本未实跑。
- 真实微信/支付宝支付（`sandbox-enabled:true`，`/mock-pay` 模拟；真实证书/回调未验证）。
- 性能/并发/容量；仅功能正确性。
- 视觉一致性/响应式/多浏览器：Playwright 仅 `channel:'chrome'`、1440×900；未跑 Firefox/WebKit/移动端。
- 上传文件的持久化/清理策略（H2 内存库 + 本地 `uploads/photos`、`target/browser-photos`，重启即失效/不清理）。
- 生产安全基线（默认演示密码 `petcare123` 由 `demo-seed-enabled` 控制；H2 console 默认开启 `application.yml:36-39`，生产需关闭）。
- 前端对 groomer/owner 各自工作台的**全部**接口契约（仅覆盖 22+4 个 E2E 用例）。

## 9. 证据文件（本次生成）

| 文件 | 内容 |
|---|---|
| `test-results/verify/mvn-test.log` | 后端完整测试日志（97.9 KB，含每套件 Tests run 行、BUILD SUCCESS） |
| `test-results/verify/mvn-package.log` | 打包日志（BUILD SUCCESS / jar 路径） |
| `test-results/verify/npm-test.log` | 前端 node:test + vitest 输出（73 tests） |
| `test-results/verify/npm-build.log` | vue-tsc + vite build 输出与 dist 体积 |
| `test-results/verify/playwright-e2e.log` | Mock E2E 23 例结果与 `payment.spec.ts:172` 失败栈 |
| `test-results/verify/playwright-real.log` | Real E2E 4 例结果（4 passed） |
| `test-results/verify/backend-smoke.log` | 真实服务启动日志（Tomcat 8000 / Started 19.4s / H2 种子） |
| `test-results/verify/smoke-rows.txt` | 角色×接口矩阵原始行（5 角色 × 16 接口 + 隔离探针） |
| `test-results/verify/smoke-denials.txt` | 跨角色越权写入 403 原始行 |
| `test-results/verify/counts.txt` | 5 角色 × 11 个列表接口的返回条数（§5.3 数据来源） |
| `backend/target/surefire-reports/` | 27 套件 surefire TXT/XML（本次运行覆盖） |
| `frontend/dist/` | 生产构建产物 |

## 10. 复现命令（Windows PowerShell，均在 `D:\PetManagement`）

```powershell
# 后端测试 / 打包
cd backend
mvn -B test
mvn -B -DskipTests package
# 前端单测 / 构建
cd ..\frontend
npm test
npm run build
# Mock E2E（需要本机 Chrome；自起 5193）
npm run test:e2e
# Real E2E（自起真实后端 8181 + 前端 5194）
npm run test:e2e:real
# 真实冒烟（8000）
cd ..\backend
java -jar target\smart-pet-grooming-1.0.0.jar
# 另开终端：脚本见 test-results/verify/smoke2.ps1 / smoke-denials.ps1
```

> 备注：任务书里提到的 `/api/demo/overview` 等路由**不存在**；后端实际由 `DemoController` 提供 `/api/analytics/dashboard`、`/api/customers`、`/api/pets`、`/api/appointments`、`/api/grooming`、`/api/inventory`、`/api/employees`、`/api/services`、`/api/reviews`、`/api/members/cards` 等（已按实际路由完成矩阵）。
