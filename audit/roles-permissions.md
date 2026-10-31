# 角色分工与权限完整性审查报告

- 项目：PAW & PURE 智慧宠物洗护系统（D:\PetManagement）
- 审查维度：功能完整性中的「角色分工 + 权限完整性」，覆盖 5 角色 × 服务端/前端双层矩阵
- 审查方式：只读源码静态审查 + 已有测试报告 artifact（未运行构建/测试，遵守分工）
- 审查人：role-auditor（task-3）；审查对象基线：当前工作区源码（非 git）
- 重要更正：**代码中“宠主”的角色码是 `OWNER`，不是任务描述里的 `CUSTOMER`**（见 `backend/src/main/resources/schema.sql:2`、`backend/src/main/resources/data.sql:2`、`AuthController.java:44-48`、`frontend/src/lib/workspace.ts:1`）。本报告统一用 OWNER=宠主。

---

## 0. 结论摘要（TL;DR）

1. **角色分工“双层实现”总体成立且质量高于 README 自述**：URL 级只要求“已登录”，角色与数据归属检查全部手写在 Controller/Service 方法内；5 个角色的核心读写路径基本都有服务端复核，前端隐藏按钮与后端拒绝在**关键业务写操作上高度一致**，未发现典型的“按钮只在前端隐藏、后端裸奔”的伪权限（反向不一致更多，见 §3.3）。
2. **账号体系已从“内存演示账号”升级为数据库账号表**（demo_account_binding），支持创建/停用/改密/删除、禁用账号即时失效、改密踢下线；但**缺少角色变更、绑定变更、本人资料修改、登录限流/锁定、账号操作审计、会话管理**。
3. **最高风险不在“角色矩阵”，而在“认证边界”**：
   - 自助注册按手机号**自动认领已有宠主档案**（无短信验证）→ 账号接管/跨家庭越权（P1）；
   - 支付回调 `/api/payment/notify/**` 公网可达且**不验签名**→ 任意人可把任意支付单置为成功（P0，权限/完整性）；
   - `mock-pay` 服务端**不检查沙箱开关**→ 宠主可自行把线上支付标记成功（P1）；
   - 店内角色可直接用 `WECHAT/ALIPAY` 标记洗护已收款而不产生第三方流水（P1）。
4. **角色闭环缺口**集中在：管理员无工单/排班/宠物入口（后端却允许其操作）、店长/管理员无营收与对账、所有角色无审批链/审计、前台可越权范围偏大（作废、代客同意加项）、洗护师无排班申请与耗材入口。
5. **可扩展性**：绑定存于数据库种子表（非内存硬编码），但**全系统没有门店/租户维度**，且前端演示身份列表与 `?? 1` 兜底仍是硬编码；多门店、多店长、员工转岗换绑均不可用，属演示级单店模型。

---

## 1. 角色定义与账号体系

### 1.1 角色码与前端标签

| 角色 | 后端 role_code | 前端 Role 标签 | 业务绑定字段 | 证据 |
|---|---|---|---|---|
| 系统管理员 | `ADMIN` | 管理员 | 无（display_name/title） | `schema.sql:2`、`AuthController.java:49-50`、`workspace.ts:1,9` |
| 店长 | `MANAGER` | 店长 | 无（display_name/title） | `data.sql:2`、`AuthController.java:50`、`workspace.ts:1,5` |
| 前台 | `RECEPTION` | 前台 | 无（display_name/title） | `data.sql:2`、`AuthController.java:38` |
| 洗护师 | `GROOMER` | 洗护师 | `demo_account_binding.employee_id` | `data.sql:2`、`AuthController.java:39-43` |
| 宠主 | **`OWNER`**（非 CUSTOMER） | 宠主 | `demo_account_binding.customer_id` | `data.sql:2`、`AuthController.java:44-48` |

角色码是 `demo_account_binding.role_code VARCHAR(20)` 自由字符串，无外键/约束（`schema.sql:2`、`V4__database_accounts.sql:1`）；后端以字符串硬编码判断（`AccountAccess.role("OWNER")` 等），前端以中文标签联合类型约束（`workspace.ts:1`）。

### 1.2 演示账号与绑定（5 个账号）

| 账号 | 角色 | 绑定 | 密码/启用 | 证据 |
|---|---|---|---|---|
| `admin` | ADMIN | 无 | 演示模式统一 `petcare123`（BCrypt，仅 `password_hash is null` 时写入）；`enabled=1` 默认 | `V4__database_accounts.sql:2`、`SecurityConfig.java:25-28`、`schema.sql:2` |
| `manager` | MANAGER | 无 | 同上 | `data.sql:2`、`SecurityConfig.java:28` |
| `reception` | RECEPTION | 无 | 同上 | `data.sql:2`、`SecurityConfig.java:28` |
| `groomer` | GROOMER | `employee_id=1`（周凯） | 同上 | `data.sql:2`、`workspace.ts:7`、`AuthTest.java:43` |
| `owner` | OWNER | `customer_id=1`（林知夏） | 同上 | `data.sql:2`、`workspace.ts:8`、`AuthTest.java:44` |

- 演示密码可通过 `pet-grooming.demo-password`（环境变量 `PET_GROOMING_DEMO_PASSWORD`）配置（`SecurityConfig.java:25`）。
- **生产/MySQL 安全兜底**：`application-mysql.yml:18-20` 默认 `demo-seed-enabled=false`，要求提供 `PET_BOOTSTRAP_ADMIN_PASSWORD`（≥12 字符、≤72 字节），否则应用**启动失败**；无管理员账号时同样启动失败（`SecurityConfig.java:29-34`）。这是较好的设计，但**首位管理员只能靠环境变量初始化，没有 UI**。
- 启用/账号版本：`enabled` 默认 1、`account_version` 默认 0（`schema.sql:2`、`V7__account_version.sql:1`）。

### 1.3 注册与开户流程

| 流程 | 谁能做 | 实现细节 | 证据 |
|---|---|---|---|
| 宠主自助注册 | 匿名 | `POST /api/auth/register` permitAll；角色**固定 OWNER**，忽略请求里的 role/customerId/employeeId/balance；注册即自动登录；账号 3-32 位、密码 ≥12 字符且 UTF-8 ≤72 字节、手机号 `\+?[0-9]{6,20}` | `SecurityConfig.java:50`、`RegistrationService.java:30-37,73`、`AuthController.java:76-78`、`RegistrationTest.java:76-86` |
| 手机号命中已有客户 | 匿名 | 若手机号命中 `demo_customer` **且该客户尚未绑账号**：直接绑定该 customer_id，并**覆盖客户姓名**；若已绑账号 → 409 | `RegistrationService.java:42-55` |
| 新客户 | 匿名 | 同一事务内建 `demo_customer`（普通、余额 0、积分 0）+ OWNER 账号 | `RegistrationService.java:56-77` |
| 员工/管理员账号 | ADMIN 建全部；MANAGER 仅 OWNER/GROOMER/RECEPTION | 角色白名单 5 个；OWNER 必须绑定已有客户、GROOMER 必须绑定已有员工；同一客户/员工只能绑一个账号（应用层 count 校验）；RECEPTION/MANAGER/ADMIN 只需姓名+职位 | `AccountController.java:31-61`、`AccountController.java:35-54` |
| 首位管理员 | 运维 | 环境变量口令 + 数据库无 admin 账号时初始化；无 UI | `SecurityConfig.java:29-34`、`application-mysql.yml:18-20` |

**风险点（P1，账号接管）**：自助注册对“已有客户但未绑账号”的手机号**自动认领**，无短信/验证码/门店确认。种子数据中客户 3（王小雨 13800138003）无账号（`data.sql:1-2`），任何知道该手机号的人可注册进她的档案，从而查看/操作其宠物、历史护理与余额（余额为 0，但同样可消费其名下资产/次卡）。README:28 声称“不会自动绑定已有客户资料”，**与实现相反**；`RegistrationTest.java:97-104` 只覆盖了“该客户已有账号”的场景，未覆盖“未绑账号被认领”。

### 1.4 会话与 CSRF 机制

| 项目 | 实现 | 证据 |
|---|---|---|
| 登录 | 手写 `AuthenticationManager` 认证，成功后保存 SecurityContext 到 HttpSession；登录时 `changeSessionId()` 防会话固定 | `AuthController.java:80-92`（changeSessionId 在 :83，保存上下文 :85-86） |
| 会话 | 30 分钟超时；Cookie `http-only`、`same-site=lax`，**未设置 Secure** | `application.yml:5-11` |
| CSRF | Spring Security CSRF 默认开启，仅 `/api/payment/notify/**` 豁免；token 由 `GET /api/auth/csrf` 下发，前端对非 GET 请求自动加 header；登录/注册前先初始化 | `SecurityConfig.java:55`、`AuthController.java:58`、`frontend/src/lib/api.ts:6-13`、`Root.vue:25-26` |
| 登出 | `POST /api/auth/logout` 需 CSRF，成功 204 | `SecurityConfig.java:61`、`AuthTest.java:29-38` |
| 未登录 | 401 + `{"message":"请先登录"}` | `SecurityConfig.java:57-60` |
| 禁用/改密即时失效 | `ActiveAccountFilter` 每请求校验 `enabled` 与 `account_version`；停用/重置密码会 `account_version+1` 使旧会话失效并 invalidate | `ActiveAccountFilter.java:17-29`、`AccountController.java:74,86`、`AuthController.java:70,87`、`AccountManagementTest.java:34-46` |

**缺口**：无在线会话列表/强制下线 UI；**角色直接改库不会使在线会话刷新权限**（过滤器只校验 enabled/version，而角色变更既无接口也不会 bump version，且无角色变更功能本身）；Cookie 无 Secure（本地 HTTP 可接受，HTTPS 部署需补）。

### 1.5 账号管理实现程度

| 能力 | 状态 | 证据 |
|---|---|---|
| 账号列表 | ✅ 店长看不到 ADMIN 账号，管理员看全部 | `AccountController.java:24-30` |
| 创建+绑定 | ✅ 管理员可建 5 角色；店长只能建 OWNER/GROOMER/RECEPTION；绑定唯一性校验 | `AccountController.java:31-61` |
| 启用/停用 | ✅ 不能停用自己、至少保留 1 个启用管理员、bump version 踢下线 | `AccountController.java:62-76` |
| 重置密码 | ✅ 管理员/店长（店长不能重置 ADMIN/MANAGER）；bump version | `AccountController.java:77-89` |
| 删除账号 | ✅ 不能删自己、至少保留 1 个管理员；删除后客户/员工可重新绑定 | `AccountController.java:90-107`、`AccountManagementTest.java:49-66` |
| 本人改密 | ✅ 校验原密码；改完保留当前会话（刷新 version） | `AuthController.java:60-75` |
| 角色变更 | ❌ 无任何接口/UI | `AccountController.java` 全文无 role 更新端点 |
| 绑定变更 | ❌ 只能“删号重建” | `AccountController.java` 无 rebind 端点 |
| 本人资料修改 | ❌ 姓名/职位由客户/员工档案或开户时固定 | `AccountController.java:39-56` |
| 登录限流/失败锁定 | ❌ 仅密码策略，无锁定/验证码/限流 | `AuthController.java:80-92`、`PasswordPolicy.java` |
| 账号操作审计 | ❌ 无账号操作日志表/页面 | 全库无对应表（`schema.sql`） |
| 会话管理 | ❌ 无列表/踢出按钮（靠 version 间接失效） | `ActiveAccountFilter.java` |

**安全敏感点（P1，内部越权/冒充）**：店长/管理员可**重置任意宠主密码**并借此登录宠主账号（`AccountController.java:77-89` 对 OWNER 无额外限制），也可为任意未绑客户新建 OWNER 账号（`AccountController.java:41-47`）；这些操作无二次确认、无审计、无需客户本人确认。

---

## 2. 服务端权限矩阵

### 2.1 鉴权机制说明

- **URL 级**：`SecurityConfig.java:49-54` 仅配置 `permitAll`（auth/csrf、login、register、图片读取、支付回调）与 `/api/**` 必须认证，**没有任何 URL 角色规则**。
- **方法内手写判断**：全部角色/归属判断在 Controller/Service 内（`AccountAccess` 提供 `role()/require()/requireStaff()/customerId()/employeeId()/requirePet()`，`AccountAccess.java:14-37`）。**全仓库无 `@PreAuthorize/@Secured/@RolesAllowed`（grep 0 命中）**，无权限表、无菜单表。
- **失败语义**：匿名→401 JSON（`SecurityConfig.java:57-60`）；已登录但无权限/无绑定→403 + JSON message（`ResponseStatusException` 经 `ApiExceptionHandler.java:15-21`）；业务冲突→409，校验→400；登录失败→401（`AuthController.java:89-92`）。**未发现 200+错误码反模式**，前端 `api.ts:16-17` 也按 HTTP 状态处理。

### 2.2 按模块权限矩阵（★=有数据归属校验）

| 模块 | 端点（方法） | 允许角色 / 范围 | 鉴权位置 | 备注 |
|---|---|---|---|---|
| 认证 | GET /auth/csrf；POST /auth/login、/auth/register | 匿名 | `SecurityConfig.java:50` | 注册固定 OWNER |
| 认证 | GET /auth/me；PUT /auth/password；POST /auth/logout | 任意登录 | `AuthController.java:58-75` | `/me` 对未知角色 403（:51） |
| 账号 | GET/POST /accounts；PUT /accounts/{u}/status、/password；DELETE /accounts/{u} | ADMIN；MANAGER（仅非 ADMIN/MANAGER 目标，且不能建/管 ADMIN/MANAGER） | `AccountController.java:25,32,63,78,91` | 店长看不到 ADMIN 列表（:26-29） |
| 预约 | GET /appointments | OWNER★本人；GROOMER★本人；MANAGER/ADMIN/RECEPTION 全店 | `DemoController.java:101-107` | |
| 预约 | POST /appointments | OWNER★（customerId 必须本人）、MANAGER、ADMIN、RECEPTION | `DemoController.java:111-121` | GROOMER 被拒 |
| 预约 | POST /appointments/{id}/reschedule | OWNER★本人；MANAGER/ADMIN/RECEPTION | `DemoController.java:122-134` | |
| 预约 | POST /appointments/{id}/cancel | OWNER★本人；MANAGER/ADMIN/RECEPTION | `CareController.java:362-374` | |
| 预约 | POST /appointments/{id}/check-in | MANAGER/ADMIN/RECEPTION | `CareController.java:57-79` | 仅当天、CONFIRMED |
| 排班 | GET /schedules?date | GROOMER★本人；MANAGER/ADMIN/RECEPTION 全店 | `ScheduleController.java:18-22` | OWNER 403 |
| 排班 | PUT /schedules/{employeeId}/{date} | MANAGER/ADMIN | `ScheduleController.java:23-44` | 不能改过去、冲突校验 |
| 资源 | GET /resources | 非 OWNER 的所有登录角色 | `DemoController.java:95-100` | **未 requireStaff**（见 §2.4） |
| 工单 | GET /grooming | OWNER★本人；GROOMER★本人；MANAGER/ADMIN/RECEPTION 全店 | `DemoController.java:179-185` | |
| 工单 | GET /grooming/addons | OWNER★本人；GROOMER★本人；MANAGER/ADMIN/RECEPTION 全店 | `DemoController.java:186-192` | |
| 工单 | POST /grooming（到店开单） | MANAGER/ADMIN/RECEPTION | `CareController.java:376-499` | |
| 工单 | POST /grooming/{id}/advance、/rollback | GROOMER★本人；MANAGER/ADMIN | `CareController.java:81-118` | GROOMER 归属校验 :86,:109 |
| 工单 | POST /grooming/{id}/assessment | GROOMER★本人；MANAGER/ADMIN | `CareController.java:501-535` | 需 TRIMMING 阶段 |
| 工单 | PUT /grooming/{id}/reassign | MANAGER/ADMIN | `CareController.java:120-144` | 前台不能改派 |
| 工单 | POST /grooming/{id}/void | MANAGER/ADMIN/**RECEPTION** | `CareController.java:146-164` | 已收费需先退款 |
| 工单 | POST /grooming/{id}/handover | MANAGER/ADMIN/RECEPTION | `CareController.java:166-178` | 需已结算(PAID/REFUNDED) |
| 工单 | GET /grooming/{id}/events | OWNER★本人；GROOMER★本人；MANAGER/ADMIN/RECEPTION | `CareController.java:180-187` | |
| 加项 | POST /grooming/{id}/addons | GROOMER★本人；MANAGER/ADMIN | `DemoController.java:195-215` | 仅护理中 |
| 加项 | POST /grooming/addons/{id}/decision | OWNER★本人；GROOMER★本人且仅可 REJECTED；MANAGER/ADMIN/**RECEPTION 可代客同意/拒绝** | `DemoController.java:216-233` | 代客确认 = 宠主授权旁路，见风险 |
| 结算 | GET /grooming/{id}/settlement | OWNER★本人；MANAGER/ADMIN/RECEPTION | `CareController.java:193-200` | GROOMER 不可见 |
| 结算 | POST /grooming/{id}/settle | OWNER★本人且仅 BALANCE；MANAGER/ADMIN/RECEPTION 可选 CASH/BALANCE/CARD/WECHAT/ALIPAY | `CareController.java:218-271` | 店内角色可“无流水记 WECHAT/ALIPAY”，见风险 |
| 退款 | POST /grooming/{id}/refund | MANAGER/ADMIN | `CareController.java:273-298` | 前台不可退款 |
| 支付 | POST /payment/create；GET /payment/{no}；POST /payment/{no}/mock-pay | OWNER★本人（PaymentService 复核 customerId）；MANAGER/ADMIN/RECEPTION 全店 | `PaymentController.java:30-77`、`PaymentService.java:41-50,132-141,228-237` | **mock-pay 不校验沙箱开关** |
| 支付 | POST /payment/notify/{channel} | **匿名**（permitAll + CSRF 豁免） | `SecurityConfig.java:52,55`、`PaymentController.java:79-96` | **不验签名**，见风险 |
| 宠物 | GET /pets | OWNER★本人（可用 customerId 参数但被限定本人）；GROOMER★本人经手工单/预约；MANAGER/ADMIN/RECEPTION 全店 | `DemoController.java:48-55` | |
| 宠物 | POST /pets；PUT /pets/{id} | OWNER★本人；MANAGER/ADMIN/RECEPTION（可改归属客户） | `CareController.java:300-351` | 前台可把宠物改到别的客户（店级权限） |
| 宠物 | GET /pets/{id}/records | `requirePet`：OWNER★本人/GROOMER★本人经手/MANAGER/ADMIN/RECEPTION | `CareController.java:353-360`、`AccountAccess.java:29-37` | |
| 会员 | GET /customers | OWNER★本人；GROOMER★仅经手客户；MANAGER/ADMIN/RECEPTION 全店 | `DemoController.java:42-47` | |
| 会员 | POST /customers；PUT /customers/{id} | **MANAGER/ADMIN/RECEPTION**（requireStaff） | `StoreSetupController.java:46-63` | 前台可改任意客户姓名/电话 |
| 会员 | POST /customers/{id}/recharge、/{id}/cards | MANAGER/ADMIN/RECEPTION | `MembershipController.java:37-73` | 前台可充值/办卡（现金） |
| 会员 | GET /customers/{id}/history | OWNER★本人；MANAGER/ADMIN/RECEPTION | `MembershipController.java:75-82` | |
| 会员 | GET /members/cards | OWNER★本人；GROOMER→空数组；MANAGER/ADMIN/RECEPTION 全店 | `DemoController.java:244-261` | |
| 服务 | GET /services | 5 个角色均可（OWNER 只见启用项） | `DemoController.java:56-63` | |
| 服务 | POST /services；PUT /services/{id} | MANAGER/ADMIN | `CareController.java:537-566` | |
| 价格 | GET /services/{id}/prices | 5 个角色均可 | `ServicePricing.java:17-20`、`PricingController.java:8` | |
| 价格 | PUT /services/{id}/prices | MANAGER/ADMIN（Service 内判断） | `ServicePricing.java:28-39`、`PricingController.java:9` | Controller 无注解，靠 service |
| 库存 | POST /inventory/{id}/adjust；GET .../flows；PUT .../retail；GET/PUT /services/{id}/consumption | MANAGER/ADMIN（Service 内判断） | `InventoryService.java:24,37-78`、`InventoryController.java:9-13` | Controller 层无角色注解 |
| 库存 | GET /inventory | OWNER★仅在售限量字段；GROOMER★仅 id/name/unit；MANAGER/ADMIN/RECEPTION 全量 | `DemoController.java:234-243` | |
| 库存 | POST/PUT /inventory[/{id}] | MANAGER/ADMIN | `StoreSetupController.java:96-121` | |
| 评价 | GET /reviews | GROOMER★本人经手工单；OWNER★本人（按 customer_name 或本人工单）；MANAGER/ADMIN/RECEPTION 全店 | `DemoController.java:263-273` | OWNER 的 name 匹配是弱过滤（同名可见） |
| 评价 | POST /reviews | **仅 OWNER**★本人且工单 COMPLETED、一单一评 | `DemoController.java:274-298` | |
| 概览 | GET /analytics/dashboard | MANAGER/ADMIN/RECEPTION | `DemoController.java:33-41` | 仅 4 个计数；**前端未调用** |
| 图片 | GET /media/photos（目录）；GET /media/photos/{k}/image；GET /media/products/{k}/image | **匿名可读**（SecurityConfig GET permitAll） | `SecurityConfig.java:51`、`PhotoController.java:20,30-36` | 目录含 key 元数据，无敏感信息 |
| 图片 | PUT/DELETE /media/photos/{key} | **ADMIN** | `PhotoController.java:21-29` | 店长也不可管理图片 |

### 2.3 越权 / IDOR 评估

- **宠主侧越权（结论：防护较完整）**：宠物、预约、工单、加项确认、结算、支付、订单、评价创建、会员历史均有 customer_id 归属复核（证据见上表 ★）；`AccessIsolationTest.java:34-49` 覆盖“跨家庭读/改/预约”4 类，均 403。
- **洗护师侧越权（结论：防护较完整）**：列表按 employee_id 过滤；推进/回退/评分/加项申请校验本人；加项决定仅允许拒绝；结算/交工/改派均被拒（`AccessIsolationTest.java:51-59` 覆盖列表与加项越权；`CareFlowTest.java` 等覆盖）。
- **店级角色（MANAGER/ADMIN/RECEPTION）**：设计上就是全店权限，无门店隔离；**RECEPTION 的权限面偏大**：可作废工单、代客同意加项、改任意客户资料、充值/办卡、以“WECHAT/ALIPAY 已收款”结算，但不可改派/退款/管账号。
- **发现的具体越权风险**：
  1. `RegistrationService.java:42-55`：手机号认领未绑账号客户（P1，见 §1.3）。
  2. `DemoController.java:268-271`：OWNER 评价按 `customer_name` 匹配，同名宠主可能互相看到评价（P3）。
  3. `CareController.java:329-351`：RECEPTION 可把宠物 `customer_id` 改到其他客户（店级权限过宽，缺审批，P2）。
  4. `CareController.java:149`：RECEPTION 可作废进行中/待接宠工单（P2，建议只留店长/管理员或需审批）。
  5. `AccountController.java:77-89`：店长/管理员可重置任意宠主密码 → 可登录宠主账号（P1，见 §1.5）。
  6. 无“角色变更”，故不存在角色提升接口；但 role_code 可被直接 SQL 修改，且在线会话不刷新（P3）。

### 2.4 “任何登录用户可调用”的接口清单

| 端点 | 实际可达角色 | 是否合理 | 证据 |
|---|---|---|---|
| `GET /api/resources` | 任何已登录且非 OWNER（含 GROOMER） | 基本合理（资源名/物种），但**缺少 requireStaff 显式声明** | `DemoController.java:95-100` |
| `GET /api/media/photos`（目录） | **匿名**（GET permitAll） | 可接受（仅图片 key/元数据），但“未登录可枚举”需知悉 | `SecurityConfig.java:51`、`PhotoController.java:20` |
| `GET /api/media/photos/{key}/image`、`/api/media/products/{key}/image` | **匿名** | 有意公开的展示图片 | `SecurityConfig.java:51` |
| `POST /api/payment/notify/**` | **匿名**（且免 CSRF） | **不合理：无签名校验**（P0） | `SecurityConfig.java:52,55`、`PaymentController.java:79-96`、`AlipayChannelHandler.java:68-115`、`WechatPayChannelHandler.java:69-119` |
| `GET /api/services`、`GET /api/employees` | 5 个角色全部（=任何有效登录账号） | 只读目录，可接受 | `DemoController.java:56-63,79-94` |
| `GET /api/auth/me`、`PUT /api/auth/password`、`POST /api/auth/logout` | 任意登录 | 合理 | `AuthController.java:58-75` |
| 其余业务读写 | 均有角色分支（见 §2.2） | 合理 | — |

### 2.5 支付/结算的权限与完整性缺陷（重点）

1. **P0 回调无签名**：`/api/payment/notify/{channel}` 匿名 + 免 CSRF；两个 handler 的 `parseAndVerifyNotify` 只解析字段、**不做任何签名/证书校验**，直接返回成功；`PaymentService.java:254-265` 拿到 paymentNo 就 `completePayment`。任何外部请求 `{"out_trade_no":"PAY..."}` 即可把支付单置 SUCCESS 并写结算/事件。`PaymentIntegrationTest.java:149-200` 恰好证明“无鉴权无 CSRF 也可完成支付”。
2. **P1 mock-pay 无服务端沙箱开关**：`PaymentController.java:65-77` + `PaymentService.java:228-252` 未读取 `sandboxEnabled`；该变量只用于返回给前端和 `isLiveConfigured`（`PaymentService.java:128,177`、`PaymentProperties.java:9`）。即使部署时 `PET_PAYMENT_SANDBOX=false`，OWNER 仍可调用 mock-pay 把自己的支付单标记成功（前端仅隐藏按钮：`PaymentModal.vue:99-118,282`）。
3. **P1 店内角色可伪造线上收款**：`CareController.java:238,262-269` 允许 RECEPTION/MANAGER/ADMIN 用 `WECHAT/ALIPAY` 直接插入 `PAID` 结算并自动生成 `POS_WECHAT_*` 流水号，**不校验是否存在对应 paymentNo**；可绕开扫码收银直接“确认收款”。
4. **P3 退款后仍可交宠**：`CareController.java:173` handover 允许结算状态 `REFUNDED` 也可交宠（服务已退款仍给宠物，需要业务确认）。

---

## 3. 前端角色体验矩阵

### 3.1 各角色可进入视图与入口

| 角色 | 导航（`workspace.ts:23-29`） | 首屏 | 主要可用操作（前端） | 证据 |
|---|---|---|---|---|
| 管理员 | 总览、员工、库存、会员、服务、订单、图片 | 总览（系统总览） | 建号/停用/重置/删号、员工/工位/耗材维护、库存调整与耗材规则、会员充值/办卡、服务增改价/停用、商品订单收款/取消/退款、图片管理、从总览“到店开单” | `App.vue:456,471-475,512,515,517-522`、`AccountManager.vue:15-17` |
| 店长 | 总览、排班、工单、宠物、会员、服务、员工、库存、订单 | 总览 | 预约/改期/取消、签到、到店开单、护理推进/评分、改派、作废、交宠、收退款、排班编辑、客户/员工/服务/价格/库存维护、账号管理（限 OWNER/GROOMER/RECEPTION）、商品退款 | `App.vue:458-463,483,490,502,512-522,540-568` |
| 前台 | 总览、排班、工单、宠物、会员、订单 | 总览（今日接待） | 预约/改期/取消、签到、到店开单、作废、交宠、结算（现金/余额/次卡/微信/支付宝）、代客加项同意/拒绝、客户建改、宠物建改、充值/办卡、商品收款/取消 | `App.vue:465-469,483,490,502,512,522,539-568` |
| 洗护师 | 工单、排班、宠物 | 工单 | 推进/回退护理、健康评分、申请加项（仅本人工单）、拒绝自己的加项申请、查看本人排班与经手宠物档案 | `App.vue:486-490,537,540-551`、`workspace.ts:25,30` |
| 宠主 | 总览、宠物、排班（我的预约）、工单、订单、会员、服务 | 总览（我的主页） | 自助预约/改期/取消、加宠/改档案、加项同意/拒绝、自助结算（余额/微信/支付宝）、取宠码展示、评价、购买商品（余额支付/取消）、查看会员流水与健康记录 | `App.vue:476-481,509,512,514,529-568`、`workspace.ts:24` |

### 3.2 按钮 / 操作与后端一致性（伪权限排查）

**结论：核心业务写操作未发现“仅前端隐藏、后端不校验”的伪权限。** 抽查到的“前端显示但后端拒绝”与“前端隐藏但后端允许”如下：

| 场景 | 前端 | 后端 | 判定 | 证据 |
|---|---|---|---|---|
| 管理员的预约/工单/排班/宠物 | 无入口（`canBook('管理员')=false`；导航无工单/排班/宠物） | 允许 ADMIN（book/check-in/advance/assess/reassign/settle/handover/pets 等） | ⚠️ **后端宽、前端无入口**（能力倒挂，非越权） | `workspace.ts:27,31`、`workspace.test.mjs:38,42`、`entry.spec.ts:93-94`、`DemoController.java:112`、`CareController.java:60,84,123,169,230` |
| 管理员结算/退款/改派/作废/交宠（洗护） | 无工单视图，无法操作 | 允许 ADMIN | ⚠️ 同上 | `CareController.java:123,149,169,230,276` |
| 管理员到店开单 | 总览按钮可见（`isAdmin && view==='总览'`） | 允许 ADMIN | ✅ 一致 | `App.vue:456`、`CareController.java:379` |
| 管理员商品退款 | 订单页“退款”按钮 | 允许 MANAGER/ADMIN | ✅ 一致 | `App.vue:502`、`CareController.java:662` |
| 店长管理 ADMIN/MANAGER 账号 | 角色下拉过滤 + “受限” | 403 | ✅ 一致 | `AccountManager.vue:15,17,66`、`AccountController.java:36-38,69-71,83-85,95-97` |
| 前台改派/退款 | 无按钮（改派仅店长/管理员；退款仅店长/管理员） | 403 | ✅ 一致 | `App.vue:547,565`、`CareController.java:123,276` |
| 前台作废/代客加项同意 | 有按钮 | 允许 | ✅ 一致（但业务权限偏宽，见风险） | `App.vue:490,552-554`、`CareController.java:149`、`DemoController.java:222-224` |
| 洗护师结算/交宠/签到/改派/作废 | 无按钮 | 403 | ✅ 一致 | `App.vue:539,552,555-568`、`CareController.java:60,123,149,169,230` |
| 洗护师编辑宠物/新增宠物 | 无按钮（`canManagePets` 不含洗护师） | 403（仅 requireStaff/OWNER） | ✅ 一致 | `App.vue:80,509`、`CareController.java:304-313,334-339` |
| 洗护师改价/库存/账号 | 无入口 | 403 / 仅 MANAGER+ADMIN | ✅ 一致 | `CareController.java:539,553`、`InventoryService.java:24`、`AccountController.java:25,32` |
| 宠主现金/次卡结算、改派、退款、作废、交宠、管账号 | 无按钮 | 403 | ✅ 一致 | `App.vue:560-563,547-568`、`CareController.java:224-231,123,149,169,276` |
| 宠主线上支付 | 有按钮 | 允许本人（但沙箱 mock 见风险） | ⚠️ 功能一致、安全性不足 | `App.vue:559-563`、`PaymentController.java:65-77` |
| 宠主商品退款 | 无按钮（仅店长/管理员） | 403 | ✅ 一致 | `App.vue:502`、`CareController.java:662` |
| 库存页组件 | 库存页内 `StoreSetupPanel/InventoryManager` **无角色判断**，仅靠导航隔离 | 后端 MANAGER/ADMIN 复核 | ⚠️ 前端守卫依赖“导航未出现”，非组件级；当前无实际漏洞 | `App.vue:521-522`、`InventoryService.java:24` |
| 管理员的资源/工位维护 | 无排班视图 → 无 `StoreSetupPanel kind=resource` 入口 | 允许 ADMIN | ⚠️ 后端宽/前端无入口 | `App.vue:483`、`StoreSetupController.java:83,89` |

**前端隐藏、后端也拒绝（一致）的敏感项**：退款、改派、账号管理、图片管理、价格/服务/库存维护、排班编辑、护理推进、结算/交宠——均已服务端复核，属正确实现。

### 3.3 各角色缺失入口（本职必需能力缺口）

| 角色 | 缺失/不足 | 证据 |
|---|---|---|
| 管理员 | ①无工单/排班/宠物视图，但后端允许其运营操作 → “管理员无法在 UI 处理护理/交宠”；②无营收/对账/审计；③无角色变更/权限配置；④工位（resource）维护无入口 | `workspace.ts:27`、`entry.spec.ts:93`、`App.vue` 无对应视图、`DemoController.java:33-41`、`AccountController.java` 无 role 更新 |
| 店长 | ①无营收/毛收/对账/提成报表（后端也没有聚合接口）；②无退款/改派/作废的审批流（直接执行）；③无评价处理/回复；④无“图片管理”（ADMIN 专属，设计如此） | 全库无营收 API；`CareController.java:273-298`、`DemoController.java:263-273` |
| 前台 | ①不能改派洗护师（仅店长/管理员）；②不能退款；③不能管账号；④无实时交接通知（前后台/洗护师靠刷新）；⑤作废权限偏大且无审批 | `CareController.java:123,276`、`ScheduleController.java:23`、`App.vue:490,552-554` |
| 洗护师 | ①只能看自己排班，不能请假/申请调班；②无耗材规则/库存入口（接口也仅店长/管理员）；③无法直接向宠主发起确认（仅提交加项申请）；④无评价汇总/绩效；⑤不能查看结算金额（仅店长/前台/宠主） | `ScheduleController.java:18-22`、`InventoryService.java:70`、`DemoController.java:195-215`、`CareController.java:193-200` |
| 宠主 | ①不能删除/转移宠物档案；②无发票、退款申请、投诉/评价修改入口；③无消息通知/护理报告下载（健康记录只读）；④线上自付依赖 mock 沙箱（生产不可用）；⑤不能查看服务评价汇总 | `CareController.java:300-351`（无 delete）、`App.vue:537`（评价只读+新建）、`PaymentProperties.java:9` |

---

## 4. 角色闭环评估（预约—到店—护理—加项—交宠—结算—评价）

| 流程环节 | 宠主 OWNER | 前台 RECEPTION | 洗护师 GROOMER | 店长 MANAGER | 管理员 ADMIN | 闭环问题 |
|---|---|---|---|---|---|---|
| 1 预约 | ✅ 自助（新约/改期/取消） | ✅ 代客预约/改期/取消 | ❌（设计） | ✅ | ⚠️ 后端可、前端无入口 | 无“预约提醒/确认通知” |
| 2 到店 | ✅ 查看状态 | ✅ 签到+到店开单 | ❌ | ✅ 签到+开单 | ⚠️ 开单可（总览）、签到无入口 | 签到仅限当天 CONFIRMED，无迟到/未到（NO_SHOW）操作 UI |
| 3 护理 | ✅ 查看进度 | ✅ 只读 | ✅ 推进/回退/评分（仅本人） | ✅ | ⚠️ 无工单视图 | 无实时通知，前台/宠主需刷新 |
| 4 加项 | ✅ 同意/拒绝 | ✅ **可代客同意/拒绝** | ✅ 申请；仅能撤回/拒绝 | ✅ 代客 | ⚠️ 后端可、无入口 | 前台代客确认绕过宠主授权（P2）；无短信/推送确认 |
| 5 交宠 | ✅ 出示取宠码 | ✅ 核验凭证+交宠 | ❌ | ✅ | ⚠️ 无入口 | 需先结算；退款后仍可交宠（P3）；无宠物交接照片/签字 |
| 6 结算 | 🔒 余额/微信/支付宝自付；不能现金/次卡 | ✅ 现金/余额/次卡/微信/支付宝 | ❌ | ✅ | ⚠️ 洗护无入口；商品/会员有 | 线上支付链路有 P0/P1 缺陷；店内可“无流水记线上已收”；无退款审批流 |
| 7 评价 | ✅ 完成交宠后 1 单 1 评 | 🔒 只读（工单详情内） | 🔒 只读本人（工单详情内） | 🔒 只读 | 🔒 只读 | 无评价汇总页、无回复/申诉/低分干预 |
| 交接 | 前台↔洗护师 | 依赖页面刷新 / 待办提醒（登录后拉取） | 同左 | 可在总览看进度 | 无入口 | **无 WebSocket/站内信/移动端提醒**，交接靠人工刷新；客服/宠主无法主动确认 |

**结论**：主流程 7 个环节在“任选一个前台 + 一个洗护师 + 一个宠主”的单店演示场景下可走通（并有 e2e/后端用例覆盖，见 §8）；但**跨角色协同仍是“拉取式”而非“推送式”**，敏感操作（退款/改派/作废/加项代确认/账号）缺少二级审批与审计，管理员在 UI 上被排除在运营闭环之外。

---

## 5. 演示绑定与硬编码 / 可扩展性

| 项目 | 现状 | 证据 |
|---|---|---|
| 演示账号绑定存储 | **数据库种子表**（非内存）：`demo_account_binding` 行；H2 用 `data.sql`，MySQL 用 V1/V4/V8 迁移 | `schema.sql:2`、`data.sql:2`、`V4__database_accounts.sql:2`、`V8__demo_seed.sql:2` |
| 运行时绑定变更 | ✅ 通过 `/api/accounts` 创建/删除账号间接改绑定；**无直接改绑接口** | `AccountController.java:31-107` |
| 前端硬编码 | 5 个演示身份（用户名/姓名/角色/customerId/employeeId）硬编码：`demoAccounts`、入口角色单选、`workspace.test.mjs` 断言、`App.vue` 的 `ownerId/staffId ?? 1` 兜底 | `workspace.ts:4-10,11-22`、`EntryPage.vue:16,60-68`、`workspace.test.mjs:34-47`、`App.vue:35-36` |
| 演示密码 | SecurityConfig 在演示模式为 5 个固定用户名写 BCrypt 密码；生产 MySQL 默认关闭并强制引导管理员口令 | `SecurityConfig.java:25-34`、`application-mysql.yml:18-20`、`application-mysql-demo.yml:4-5` |
| 门店/多门店 | **全系统无 store_id / tenant / store_code**（Java 与 SQL grep 均 0 命中），无门店表；店长/前台/管理员权限是“全库”级 | `schema.sql` 全文、grep 结果；`application.yml:47-52` 只有单店配置 |
| 多员工 | 可新增员工+配套账号（employee_id 绑定，应用层唯一性校验）；但**数据库无唯一约束**，并发建号可产生重复绑定 | `AccountController.java:45-46,52-53`、`schema.sql:2` |
| 角色可扩展性 | role_code 为自由字符串可加值，但代码在 ~30 处硬编码 5 个角色字符串 + 前端 `Role` 联合类型 + 测试/种子；无角色/权限/菜单表，无授权模型 | `AccountAccess.java:14-37`、`AuthController.java:37-52`、`workspace.ts:1` |
| 真实门店上线所需改造 | ①加门店表与所有业务表 store_id；②账号加 store_id/角色-权限表；③加角色变更加绑定变更 API + 会话刷新；④首位管理员初始化 UI；⑤清空/禁用演示账号与前端演示身份 | 综合 §1.5、§5 |

**判定**：演示绑定**已不是“内存硬编码”**（这点比 README:22 的“账号仍在内存中配置”更新），但**仍是单店、固定 5 角色、单人单绑**的演示模型；换多门店/多员工/员工转岗需要改代码 + 迁移 + 前端改造，不能仅靠配置。

---

## 6. 角色 × 关键能力大矩阵

图例：✅ 服务端+前端一致 ｜ ⚠️ 前后端不一致（备注方向：后端宽/前端宽）｜ ❌ 未实现或双方均拒绝 ｜ 🔒 仅部分角色或条件

| 能力 \ 角色 | 管理员 ADMIN | 店长 MANAGER | 前台 RECEPTION | 洗护师 GROOMER | 宠主 OWNER |
|---|---|---|---|---|---|
| 查看本域数据 | ✅（账号/员工/库存/会员/服务/订单/图片；⚠️无工单/排班/宠物视图） | ✅（全店，除图片） | ✅（总览/排班/工单/宠物/会员/订单） | 🔒 仅本人经手数据 ✅ | 🔒 仅本家庭数据 ✅ |
| 宠物档案查看 | ⚠️ 后端✅/前端无入口 | ✅ | ✅ | 🔒 本人经手 ✅ | 🔒 本人 ✅ |
| 宠物新增/编辑 | ⚠️ 后端✅/前端无入口 | ✅ | ✅（可改归属客户） | ❌ | 🔒 本人 ✅ |
| 预约/改期/取消 | ⚠️ 后端✅/前端无入口 | ✅ | ✅ | ❌ | 🔒 本人 ✅ |
| 到店签到 | ⚠️ 后端✅/前端无入口 | ✅ | ✅ | ❌ | ❌ |
| 到店开单（walk-in） | ✅（总览按钮，后端✅） | ✅ | ✅ | ❌ | ❌ |
| 护理推进/回退 | ⚠️ 后端✅/前端无入口 | ✅ | ❌ | 🔒 本人 ✅ | ❌ |
| 健康评分 | ⚠️ 后端✅/前端无入口 | ✅ | ❌ | 🔒 本人 ✅ | ❌ |
| 加项申请 | ⚠️ 后端✅/前端无入口 | ✅ | ❌ | 🔒 本人 ✅ | ❌ |
| 加项确认 | ✅（代客） | ✅（代客） | ✅（代客，**权限偏宽**） | 🔒 仅撤回/拒绝本人 ✅ | 🔒 本人 ✅ |
| 洗护收款/结算 | ⚠️ 后端✅/前端无入口 | ✅ | ✅（全方式） | ❌ | 🔒 余额/线上自付 ✅ |
| 商品订单收款/取消 | ✅ | ✅ | ✅ | ❌ | 🔒 余额支付/取消本人 ✅ |
| 洗护退款 | ⚠️ 后端✅/前端无入口 | ✅ | ❌ | ❌ | ❌ |
| 商品退款 | ✅ | ✅ | ❌ | ❌ | ❌ |
| 改派洗护师 | ⚠️ 后端✅/前端无入口 | ✅ | ❌ | ❌ | ❌ |
| 作废/终止工单 | ⚠️ 后端✅/前端无入口 | ✅ | ✅（**权限偏宽**） | ❌ | ❌ |
| 交宠核销 | ⚠️ 后端✅/前端无入口 | ✅ | ✅ | ❌ | 🔒 仅出示取宠码 |
| 排班查看 | ⚠️ 后端✅/前端无入口 | ✅ | 🔒 只读 | 🔒 仅本人 | ❌ |
| 排班编辑 | ⚠️ 后端✅/前端无入口 | ✅ | ❌ | ❌ | ❌ |
| 服务/价格管理 | ✅ | ✅ | ❌ | ❌ | ❌ |
| 库存/耗材规则 | ✅ | ✅ | ❌ | ❌（接口亦受限） | ❌ |
| 图片管理 | ✅ | ❌ | ❌ | ❌ | ❌ |
| 评价 | 🔒 只读 | 🔒 只读 | 🔒 只读 | 🔒 只读本人 | 🔒 提交/查看本人 ✅ |
| 会员充值/办卡 | ✅ | ✅ | ✅ | ❌ | ❌ |
| 账号管理 | ✅（全角色，无角色变更） | 🔒 OWNER/GROOMER/RECEPTION（无角色变更） | ❌ | ❌ | ❌ |
| 本人改密 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 角色变更 | ❌ | ❌ | ❌ | ❌ | ❌ |
| 营收/对账报表 | ❌ | ❌ | ❌ | ❌ | ❌ |
| 多门店隔离 | ❌ | ❌ | ❌ | ❌ | ❌ |

> 说明：本表“❌”对 GROOMER/OWNER 表示“设计上不应有 + 服务端确实拒绝（一致）”；对 ADMIN 的“⚠️”不构成越权，而是**能力倒挂**（后端放行、前端无入口）。

---

## 7. 风险清单（按严重度）

> 说明：P0 = 生产阻断/资金安全；P1 = 高风险越权或完整性问题；P2 = 中风险权限过宽/治理缺失；P3 = 低风险/健壮性。前 4 条偏“认证/资金边界”，因它们直接决定角色权限是否可信，故列入。

| # | 级别 | 风险 | 证据（文件:行） | 建议 |
|---|---|---|---|---|
| 1 | **P0** | 支付回调匿名可达且不验签，任意人可把任意支付单置成功并写结算 | `SecurityConfig.java:52,55`、`PaymentController.java:79-96`、`AlipayChannelHandler.java:68-115`、`WechatPayChannelHandler.java:69-119`、`PaymentService.java:254-265`、`PaymentIntegrationTest.java:149-200` | 真实实现签名/证书校验，校验金额/商户号；沙箱回调改内网/开关关闭 |
| 2 | **P1** | 自助注册按手机号自动认领未绑账号的宠主档案并覆盖姓名，无短信验证 → 账号接管/跨家庭越权 | `RegistrationService.java:42-55,72-77`、`data.sql:1-2`、README:28（描述相反）、`RegistrationTest.java:97-104`（未覆盖未绑场景） | 注册前短信验证码/门店审核；或只允许前台建号；禁止覆盖客户姓名 |
| 3 | **P1** | mock-pay 服务端不受沙箱开关约束，宠主可自标记线上支付成功 | `PaymentController.java:65-77`、`PaymentService.java:228-252`、`PaymentProperties.java:9`、`PaymentModal.vue:99-118,282` | mock-pay 加 `sandboxEnabled` 服务端校验；生产构建删除该端点 |
| 4 | **P1** | 店内角色可无第三方流水地把洗护结算标记为 WECHAT/ALIPAY 已收款 | `CareController.java:238,262-269` | settle 的线上方式必须关联 paymentNo 且状态 SUCCESS；否则仅允许 CASH/BALANCE/CARD |
| 5 | **P1** | 店长/管理员可重置任意宠主密码并建/删宠主账号 → 可登录宠主账号 | `AccountController.java:41-47,77-89` | 二次确认 + 审计日志；重置宠主密码需本人验证或管理员复核 |
| 6 | **P1** | 角色判断全部手写、无方法级安全注解/权限表；URL 级仅“已登录” | `SecurityConfig.java:49-54`、grep 无 `@PreAuthorize/@Secured` | 引入方法级鉴权/权限表；新增端点加测试基线 |
| 7 | **P1** | 前台可“代客同意”加项，绕过宠主费用授权 | `DemoController.java:216-233`、`App.vue:490` | 代客确认需店长权限或记录电话授权凭证 |
| 8 | **P2** | 前台可作废进行中/待接宠工单（与改派/退款仅店长不对称） | `CareController.java:146-164` | 作废收归店长/管理员，或需原因+复核 |
| 9 | **P2** | 角色不可变更、绑定不可变更；直接改库角色不影响在线会话权限（过滤器只查 enabled/version） | `AccountController.java`（无 role/rebind 端点）、`ActiveAccountFilter.java:20-23` | 增加角色变更 API + bump account_version + 审计 |
| 10 | **P2** | 无门店/租户隔离；店长/前台/管理员为全库权限，多门店不可用 | `schema.sql`（无 store 表/字段）、grep `store_id/tenant` 0 命中 | 增加 store 维度与所有查询过滤；账号绑门店 |
| 11 | **P2** | 管理员后端可运营（工单/排班/宠物）但前端无入口；管理员无营收/审计 | `workspace.ts:27`、`entry.spec.ts:93`、`DemoController.java:33-41`（且前端未调用 dashboard） | 明确 ADMIN 职责：补运营视图或收窄后端权限；补报表 |
| 12 | **P2** | 演示绑定/前端身份仍硬编码，`?? 1` 兜底可掩盖未绑定账号 | `workspace.ts:4-10`、`EntryPage.vue:16,60-68`、`App.vue:35-36` | 改为完全由 /auth/me 驱动；删除兜底 1 |
| 13 | **P3** | OWNER 评价按 customer_name 匹配，同名宠主可能互见评价 | `DemoController.java:268-271` | 仅按 grooming 归属查询 |
| 14 | **P3** | 客户/员工与账号绑定唯一性仅应用层 count，DB 无唯一约束，并发可重复绑定 | `schema.sql:2`、`AccountController.java:45-46,52-53` | 加 UNIQUE 索引 |
| 15 | **P3** | 前台可改任意客户姓名/电话；改宠物归属客户；无审计 | `StoreSetupController.java:54-63`、`CareController.java:329-351` | 限制字段、记录操作人、必要时店长复核 |
| 16 | **P3** | 退款后仍允许交宠 | `CareController.java:173` | 业务确认是否允许；建议仅 PAID |

---

## 8. 验证证据与局限

### 8.1 已读取的验证 artifact（未自行运行测试）

- 后端 surefire 报告（读取时 `backend/target/surefire-reports` 共 27 个测试类）：**0 失败 0 错误**；其中 `MySqlBootstrapTest/MySqlPersistenceTest/MySqlRestartTest` 共 7 个用例 skipped（无 MySQL 环境）。与角色/权限直接相关的：`AccessIsolationTest`（4）、`AccountManagementTest`（3）、`AuthTest`（3）、`RegistrationTest`（23）、`PaymentIntegrationTest`（9）、`ReassignAndVoidTest`（4 参与后端全绿）。
- 前端 e2e 为 **mock API 交互测试**（`frontend/tests/e2e/*.spec.ts`）：`entry.spec.ts:33-95` 断言各角色导航与按钮显隐（前台无库存、管理员无工单/新建预约等）；`accounts.spec.ts:3-38` 覆盖管理员建号/停用/重置密码 UI；`care-additions.spec.ts:180-204` 覆盖前台先结算后交宠、未结算无交宠按钮。
- **局限**：①前端 e2e 全部 `page.route` mock，不验证真实后端拒绝；②后端测试**没有任何 `RECEPTION` 角色的权限用例**（grep `roles = "RECEPTION"` 0 命中），前台权限面（作废/代客加项/改客户/充卡）缺回归保护；③`D:\PetManagement\test-results\.last-run.json` 记录 `status=failed` 且 `failedTests=[]`，该 artifact 自相矛盾，需 verify-runner 复核最终 e2e 结论；④本次为静态审查，未实测并发/会话失效/支付回调攻击面。

### 8.2 与 README 的主要出入（README 已过时）

| README 说法 | 代码事实 | 证据 |
|---|---|---|
| “账号仍在内存中配置”“完整账号管理和权限隔离尚未完成”（:22） | 账号在数据库表，已有账号 CRUD/停用/改密、角色+归属双层校验 | `schema.sql:2`、`AccountController.java`、`AccessIsolationTest.java` |
| “已登记电话需门店处理，不会自动绑定已有客户资料”（:28） | 未绑账号的既有客户会被手机号注册认领 | `RegistrationService.java:42-55` |
| “其余业务接口仍需完善权限”“收银、次卡核销/退还、退款、通知等尚未实现”（:49-50） | 收银（现金/余额/次卡/微信/支付宝）、退款、次卡核销/退还、支付单均以实现（但有 §2.5 缺陷）；通知确实未实现 | `CareController.java:218-298`、`MembershipController.java`、`PhotoController.java`、无消息表 |
| “数据驾驶舱尚无营收或核销趋势”（:50） | 仍然属实，且 `/analytics/dashboard` 前端未使用 | `DemoController.java:33-41`、前端 grep 0 命中 |

---

## 9. 总体判定

- **角色分工完整性**：架构方向正确（5 角色、双层校验、数据库账号、绑定唯一性、会话失效、CSRF），**核心业务写操作的服务端复核覆盖率高**，可评为“演示级完整 / 生产级未完成”。
- **主要缺口不是“角色矩阵没写”，而是**：认证与资金的边界（注册认领、回调验签、mock-pay、线上收款）尚未达到可运营标准；账号治理（角色变更/绑定/审计/审批）缺失；门店维度与报表缺失；管理员/前台/洗护师的 UI 闭环与权限面需要再平衡。
- **建议优先级**：P0 回调签名 → P1 注册认领/mock-pay/线上收款/宠主密码重置 → P2 前台敏感操作收权与审批、角色变更+审计、门店维度 → P3 评价匹配、DB 唯一约束、演示硬编码清理。
