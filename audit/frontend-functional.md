# PAW & PURE 前端功能完整性审计报告

> 审计对象：`D:\PetManagement`（Windows，非 git 仓库）
> 审计范围：`frontend/src/App.vue`、`Root.vue`、`components/*.vue`（24 个）、`lib/*.ts`（5 个）、`tests/*`、`playwright*.config.ts`、`frontend/artifacts/*`、`test-results/verify/*` 日志。
> 方法：静态阅读为主（未运行构建/测试，避免与 verify-runner 抢资源），量化数据全部取自仓库内既有 artifact 与 verify 日志。
> 审计时间：2026-10-31。代码规模实测：src 下 32 个 .vue/.ts 文件共 2824 行（不含 style.css 851 行），其中 24 个组件 1947 行；`App.vue` 实际 581 行（任务简报中“2000+ 行”已过时）。
> 结论口径：以代码为准；README.md 未作为依据。

## 0. 总体结论

**前端功能覆盖度较高：10 个视图、5 套角色导航、绝大多数后端写操作都有真实 UI 入口并调用真实 API（没有“前端假写”的模块），页面级加载/空态/错误重试齐全。** 主要问题集中在四类：

1. **角色工作台完整性**：管理员角色缺少“排班/工单/宠物”三个视图入口，但后端与前端抽屉代码都给管理员开放了护理推进、改派、作废、退款、结算等能力 → 这些能力对管理员“写得到、点不到”（死代码/死权限）。
2. **无前端路由**：视图只存在内存 `view` ref 中，刷新/后退/分享 URL 全部丢失当前页。
3. **演示残留**：支付收银台内置“模拟扫码支付成功”，且后端 mock-pay 不校验 sandbox 开关；演示账号快捷身份、固定宠物头像、商品演示图按名称正则套图。
4. **测试覆盖偏科**：`App.vue` 无单元测试；coverage artifact 只覆盖 4 个文件；唯一失败的 E2E 是测试数据里的“过期时间炸弹”，不是产品缺陷但会让验收变红。

前后端接口对账：后端 81 个方法级端点中，前端实际使用约 67 个；**未发现“前端调用、后端无对应实现”的接口（0 条）**；“后端有、前端无入口”主要是 `GET /analytics/dashboard`、`GET /services/{id}/prices`、`POST /payment/notify/{channel}`（外部回调）等非关键项。

## 1. 导航与视图对应关系

导航由 `src/lib/workspace.ts:23-30` 的 `navigationFor(role)` 与 `homeView(role)` 决定；`App.vue:34` 用内存 `view` ref 承载当前视图；`App.vue:160` 的 `go()` 只改本地状态；`App.vue:130-131` 把导航项映射到标题与图标。**全仓没有 vue-router、没有 history/hash 用法**（grep 确认），即没有真实路由。

| 角色 | 首屏 homeView | 导航项（label / 文案） | 视图数 |
|---|---|---|---|
| 店长（默认分支） | 总览 / 门店总览 | 总览·排班·工单·宠物·会员·服务·员工·库存·订单 | 9 |
| 前台 | 总览 / 今日接待 | 总览·排班·工单·宠物·会员·订单 | 6 |
| 洗护师 | 工单 / 我的护理工单 | 工单·排班·宠物 | 3 |
| 宠主 | 总览 / 我的主页 | 总览·宠物·排班·工单·订单·会员·服务 | 7 |
| 管理员 | 总览 / 系统总览 | 总览·员工·库存·会员·服务·订单·图片 | 7 |

### 1.1 视图 × 角色访问矩阵（● 可访问 / — 无入口）

| 视图 | 店长 | 前台 | 洗护师 | 宠主 | 管理员 | 模板分支证据 |
|---|---|---|---|---|---|---|
| 总览 | ● 门店版 | ● 接待版 | — | ● 宠主版 | ● 系统版 | `App.vue:458-481`（4 个 v-if 分支） |
| 排班 | ● | ● | ● 只读 | ● 仅我的预约表 | — | `App.vue:482-485` |
| 工单 | ● | ● 到店与接宠 | ● 我的工单 | ● 护理进度 | **—（缺口）** | `App.vue:486-491` |
| 宠物 | ● | ● | ● 只读 | ● | — | `App.vue:508-510` |
| 会员 | ● | ● | — | ● | ● | `App.vue:511-513` |
| 服务 | ● | — | — | ● 探索护理 | ● | `App.vue:514-516` |
| 员工 | ● | — | — | — | ● 员工与权限 | `App.vue:517-519` |
| 库存 | ● | — | — | — | ● | `App.vue:521-523` |
| 订单 | ● | ● | — | ● | ● | `App.vue:492-507` |
| 图片 | — | — | — | — | ● 仅 admin | `App.vue:520`（`v-if="view==='图片' && isAdmin"`） |

说明：管理员缺少排班/工单/宠物入口，不是模板没写，而是 `workspace.ts:27` 的导航数组没有这三项；`App.vue:486-568` 中管理员分支大量存在（见 §7）。

## 2. 各视图档案（组件 / API / 操作 / 状态处理）

> 数据加载统一在 `App.vue:179-193`：一次性并行拉取 pets/services/employees/appointments/grooming/addons/customers/cards/inventory/orders/resources/reviews；失败进 `App.vue:453-455` 的整页错误态并支持“重新连接”。全局 toast：`App.vue:157-159, 528`。

| 视图 | 主要组件 | 调用 API（除初始 load） | 已实现操作 | 加载/空态/错误 |
|---|---|---|---|---|
| 总览 | PetPortrait、StageTimeline 卡片入口 | 无（复用 load 数据） | 门店/接待/管理员/宠主四套首页；指标、今日 agenda、待办、护理预览、推荐（宠主）；“到店开单”（管理员/店长/前台） | 骨架屏+整页错误重试；各区块 empty-state |
| 排班 | AppDatePicker、ScheduleManager、GanttSchedule | GET/PUT `/schedules` | 员工班次 WORK/OFF、上下班时间、甘特图+15 分钟清洁缓冲（`workspace.ts:87-93`）、状态筛选、预约明细、改期/取消/签到入口 | ScheduleManager 有 error/notice；无排班时空态 |
| 工单 | StageTimeline、AddonRequest、HealthScoreDialog、Review 表单 | POST `/grooming/{id}/advance|rollback|reassign|void|handover|assessment`、POST `/grooming/addons/{id}/decision`、GET `/grooming/{id}/events` | 推进/回退/改派/作废/加项申请与审批/健康评分/评价/接宠凭证/结算/退款 | 抽屉内 loading/error、操作 toast；空工单/无加项提示 |
| 宠物 | PetPortrait、PetDialog、HealthTrend | GET `/pets/{id}/records`、POST/PUT `/pets` | 筛选、新增、编辑（固定词条：年龄/过敏/皮肤/既往洗护）、风险提示、健康趋势图、关联工单 | HealthTrend 空态；get records 失败静默为空 |
| 会员 | MembershipManager、AppSelect | POST `/customers/{id}/recharge|cards`、GET `/customers/{id}/history` | 现金充值、办次卡（选服务）、余额流水、次卡流水；宠主只读流水 | 空态齐全；错误行内提示 |
| 服务 | ServiceDialog | POST `/services`、PUT `/services/{id}`、PUT `/services/{id}/prices` | 新增项目、调整价格/时长/体型差异价、启用/停用、宠主浏览+预约入口 | 表格空态；无删除（后端也无接口） |
| 员工 | StoreSetupPanel(employee)、AccountManager | GET/POST/PUT `/employees`、`/accounts` 全套 | 洗护师档案、账号创建/停用/重置密码/删除、权限受限提示（“受限”） | 行内错误/成功提示；无员工删除（后端无接口） |
| 库存 | StoreSetupPanel(product)、InventoryManager | POST `/inventory/{id}/adjust`、PUT `/inventory/{id}/retail`、GET `/inventory/{id}/flows`、GET/PUT `/services/{id}/consumption`、POST/PUT `/inventory` | 耗材建档（含创建时上传图片）、入库/出库/盘点、零售包装价、耗材规则、流水、预警筛选 | 预警条、空态、行内错误 |
| 订单 | RetailOrderActions、AppSelect | POST `/orders`、POST `/orders/{id}/settle|cancel|refund` | 商品浏览+下单（数量）、会员代客下单、现金/余额收款、取消、退款退货 | 空态；二次确认取消（armed） |
| 图片 | PhotoManager | GET `/media/photos`、PUT/DELETE `/media/photos/{key}` | 13 个固定图片位替换/恢复默认、分类筛选 | loading/错误重试/上传格式校验 |

全局能力：搜索（`App.vue:153-156, 579`，仅宠物+预约，Ctrl/Cmd+K）、待办托盘（`App.vue:113, 451`，加项+低库存）、修改密码（`App.vue:402-411, 571-578`）、401 会话过期处理（`lib/api.ts:16`、`Root.vue:13`）、键盘可达性（DialogShell 焦点陷阱 `components/DialogShell.vue:11-30`）。

## 3. 业务模块前端状态矩阵

| # | 模块 | 状态 | 依据/证据 | 关键缺口 |
|---|---|---|---|---|
| 1 | 登录/注册/会话 | ✅ 完整 | `Root.vue:14-37`（CSRF、restore、登录注册、退出）；`EntryPage.vue:20-47`（12 位密码、手机号校验、busy 防重、错误聚焦）；`api.ts:6-19` | 登录页“选择登录身份”是演示账号快捷选择（见 §5） |
| 2 | 角色工作台/导航 | ⚠️ 部分 | `workspace.ts:23-32`；`App.vue:72-80` 角色能力位 | 管理员缺排班/工单/宠物入口，但 `canManagePets` 包含管理员（`App.vue:80`）、抽屉里 `isAdmin` 分支存在（`App.vue:540-568`）→ 不可达；无路由导致刷新丢页 |
| 3 | 预约（建/改/取消/签到） | ✅ 完整 | `BookingDialog.vue:47-62`、`RescheduleDialog.vue:14-22`、取消 `App.vue:343-348`、签到 `App.vue:207-216`；时段校验 `workspace.ts:71-86` | 无预约删除（设计上取消代替） |
| 4 | 护理工单（推进/回退/改派/作废/交宠） | ⚠️ 部分 | `App.vue:217-272` 全部真实 API；后端权限与前端一致（`CareController.java:84,107,123,149,169`） | 管理员不可达（见上）；工单列表无分页/状态筛选（只有汇总） |
| 5 | 加项申请/审批 | ✅ 完整 | `AddonRequest.vue:8-26`、`App.vue:368-382`；宠主/洗护师/店长/前台/管理员分支齐全（`App.vue:490, 537`）；requestId 幂等（sessionStorage） | 无加项历史独立页面（在工单抽屉内） |
| 6 | 结算/支付 | ⚠️ 部分 | `App.vue:281-317, 555-568`；现金/余额/次卡/微信/支付宝；`PaymentModal.vue:79-143` 轮询与倒计时 | 收银台含“模拟扫码支付”演示按钮且后端 mock-pay 无 sandbox 校验（`PaymentModal.vue:282-294`、`PaymentService.java:229-252`、`application.yml:54`）；零售订单无线上支付；宠主不能用次卡自付（仅 BALANCE，`CareController.java:224-228`） |
| 7 | 会员/次卡 | ✅ 完整（门店侧） | `MembershipManager.vue:14-45`；后端 `MembershipController.java:37-90` | 宠主侧只读（无自助充值，符合设计）；无次卡退卡（后端无接口） |
| 8 | 宠物档案 | ⚠️ 部分 | `PetDialog.vue:32-45`（新增/编辑）、风险词条 `lib/catalog.ts`、风险面板 `App.vue:535` | 无删除（后端无）；无宠物照片上传；id>4 的新宠物只能显示爪印占位（`workspace.ts:50`、`PetPortrait.vue:9`） |
| 9 | 服务项目 | ⚠️ 部分 | `ServiceDialog.vue:30-53`、启停 `App.vue:334-342` | 无删除（后端无）；新增项目无 E2E 覆盖 |
| 10 | 员工/账号权限 | ✅ 完整 | `AccountManager.vue:19-51`、`StoreSetupPanel.vue`（employee）；与后端 `AccountController.java:25-103` 的角色限制一致 | 无员工删除（后端无） |
| 11 | 库存/耗材 | ✅ 完整 | `InventoryManager.vue:15-46`；预警 `App.vue:112, 521-523` | 真实后端路径无 E2E 覆盖（只有 mock E2E） |
| 12 | 商品订单 | ⚠️ 部分 | `App.vue:349-367, 492-507`、`RetailOrderActions.vue:11-21` | 无在线扫码支付；无订单详情页；商品图无图时按名称正则套演示图（`App.vue:62-67`） |
| 13 | 图片管理 | ⚠️ 部分 | `PhotoManager.vue:26-44`；仅 13 个固定图片位（`PhotoStorageService.java:26-39`） | 仅 ADMIN（与后端一致 `PhotoController.java:23,27`），店长无入口；不能给新宠物/商品换图；商品图仅创建时上传（`StoreSetupPanel.vue:59`），`PUT /inventory/{id}` 不接收图片（`StoreSetupController.java:96-102`） |
| 14 | 搜索/待办/通知 | ⚠️ 部分 | 搜索 `App.vue:153-156`；待办 `App.vue:111-113, 451` | 搜索只覆盖宠物+预约（无会员/订单/工单/服务）；无通知中心、无操作日志页面（后端有模型无接口） |
| 15 | 健康记录/趋势 | ✅ 完整 | `HealthScoreDialog.vue:20-31`、`HealthTrend.vue:7-36` | 记录读取失败被静默吞掉（`App.vue:170`），表现为“趋势空白” |
| 16 | 加载/空态/错误 | ✅ 基本完整 | 整页 `App.vue:453-455`；各视图 empty-state 齐全 | 少量静默失败：`App.vue:170,177,182-183`；root 图片加载 `Root.vue:36` 静默 |
| 17 | 权限提示 | ⚠️ 部分 | 按钮级隐藏（`App.vue:78-80, 456, 509, 515`） | 无“当前角色无权限”的显式说明；只读视角（洗护师排班、前台服务）无提示文案 |

## 4. 前后端接口对接差异

对账口径：后端 `backend/src/main/java/**` 共 81 个方法级端点（GET 29 / POST 35 / PUT 15 / DELETE 2，另有 11 个类级 `@RequestMapping` 前缀）；前端所有调用集中在 `lib/api.ts:10-19`（只有这一处 fetch）。逐条比对结果：

### 4.1 前端调用、后端无对应实现：**0 条**
包括容易误判的 `POST /api/auth/logout`——它由 Spring Security 过滤器提供（`config/SecurityConfig.java:61`），并非 Controller。

### 4.2 后端有、前端未接（有意义项）

| 后端端点 | 后端证据 | 前端现状/影响 | 严重度 |
|---|---|---|---|
| `GET /api/analytics/dashboard` | `DemoController.java:33-41` | 前端首页指标全部用 load 数据自行计算（`App.vue:458-475`），未接该聚合接口；统计口径可能与后端不一致 | 低 |
| `GET /api/services/{id}/prices` | `PricingController.java:8` | 前端仅 PUT 该路径（`ServiceDialog.vue:50`），读取靠 `GET /services` 内嵌的 `size_rules`（`DemoController.java:27`） | 低 |
| `POST /api/payment/notify/{channel}` | `PaymentController.java:79-96` | 外部支付渠道回调，非前端职责；但该路径 `permitAll`（`SecurityConfig.java:52`），建议安全审计关注 | 低（设计如此） |
| `GET /api/media/photos/{key}/image`、`GET /api/media/products/{key}/image` | `PhotoController.java:30`、`ProductPhotoController.java:15` | 前端通过返回的 URL 由浏览器直接加载，属“间接接入”，无按钮入口 | 无 |
| `POST /api/payment/create` 的 `orderId` 参数；`WalkIn.resourceId`；`Settlement.tradeNo` | `PaymentController.java:28,32`；`CareController.java:44,51` | 前端均未使用：零售订单不能线上支付（后端 `PaymentService.createPayment` 也只接收 groomingId，`PaymentService.java:41`）；到店开单不能指定工位（`WalkInDialog.vue:31` 只传 petId/serviceId/employeeId）；POS 流水号无输入入口 | 中（功能缺口，部分属后端半成品） |

### 4.3 前端权限矩阵与后端一致性抽查（结论：基本一致）

- 签到：前端 `App.vue:539` 限店长/管理员/前台 = 后端 `CareController.java:60` ✅
- 推进/回退/健康评分：前端 `App.vue:540-546, 537` = 后端 `CareController.java:84,107,504` ✅
- 改派/退款：前端 `App.vue:547, 565` 限店长/管理员 = 后端 `CareController.java:123,276` ✅
- 作废/交宠/现场结算：前端 `App.vue:552, 568, 558` 限店长/管理员/前台 = 后端 `CareController.java:149,169,230` ✅
- 宠主结算：前端只给 BALANCE/WECHAT/ALIPAY（`App.vue:560`），后端 owner 仅允许 BALANCE 走 `/settle`、线上支付走 `/payment/create`（`CareController.java:224-228`、`PaymentController.java:40-49`）✅
- 排班编辑：前端 `:can-edit="isManager || isAdmin"`（`App.vue:483`）= 后端 `ScheduleController.java:24` ✅
- 账号管理：前端 `AccountManager.vue:13-15` 的角色过滤与受限行 = 后端 `AccountController.java:25-36,63-73` ✅
- **唯一明显不一致**：管理员在前端没有工单/排班/宠物入口，后端却对管理员开放上述大部分能力（见 §7-1）。

## 5. 演示 / 模拟残留清单

| # | 残留 | 证据 | 说明/风险 |
|---|---|---|---|
| 1 | 登录页“选择登录身份”5 个演示账号快捷选择 | `lib/workspace.ts:4-10`、`EntryPage.vue:4,16,63-67` | 只是把 seed 账号（`backend/src/main/resources/data.sql:2`）填进账号框，密码仍需手输；seed 改名/删号后该快捷区会误导 |
| 2 | `sessionAccount()` 未知账号/角色回退到演示档案（含 customerId=1/employeeId=1） | `workspace.ts:11-22` | 后端异常或新增角色时可能静默套用“林知夏/金卡会员”等身份 |
| 3 | 固定宠物头像映射（id 1-4） | `workspace.ts:50`、`PetPortrait.vue:9,15`（alt 文案明确标注“演示照片”） | 新宠物永远爪印占位；图片管理也不能新增宠物头像 |
| 4 | 商品/服务/会员配图用演示图；无商品图时按名称正则套图 | `App.vue:55-71`（含注释“illustrative stock photographs”）、`App.vue:524` 页脚“照片均为演示配图” | 新建无图商品会显示香波/精华/泡腾演示图，可能让用户误以为是真实商品图 |
| 5 | 支付收银台“⚡ 模拟扫码支付成功（演示环境）” | `PaymentModal.vue:99-118, 282-294` | 后端 `sandboxEnabled` 默认 true（`application.yml:54`），且 `mockPay` 未校验该开关（`PaymentService.java:229-252`），仅校验角色；生产环境若沿默认配置，任何有权角色可“假支付”结清工单 |
| 6 | 门店信息（店名、营业时间 09:00–21:00、15 分钟缓冲）硬编码 | `App.vue:439`、`workspace.ts:71-93`；后端同名配置在 `application.yml:45-52` 但无查询接口 | 改后端配置前端不会同步 |
| 7 | `ownerId ?? 1` / `staffId ?? 1` 兜底 | `App.vue:35-36` | 店长/前台/管理员（customerId 为 null）打开“新建预约”时默认客户是 id=1（`App.vue:529` → `BookingDialog.vue:14`）；新库无 id=1 时该默认无意义 |
| 8 | 初始推荐宠物 id 固定为 1 | `App.vue:48` | 无 id=1 宠物时依赖 `recommend()` 回退到第一只（`App.vue:173-178`） |
| 9 | 服务/宠物演示数据本身 | 后端 `DemoController` 命名 + `data.sql` seed | 前端展示的是种子演示数据（宠物、会员、工单、评价、次卡等），生产需替换 seed |
| 10 | 前端本地 mock 数据 | —— | **未发现**：前端无本地假数据仓库，所有写操作都打真实 API；仅 sessionStorage 用于幂等重试缓存（`App.vue:352-362`、`AddonRequest.vue:11,16-19` 等） |

## 6. 测试覆盖

### 6.1 分层测试资产

| 层 | 文件/命令 | 规模 | 覆盖内容 | 最近结果（仓库内 artifact） |
|---|---|---|---|---|
| 纯函数单测（node --test） | `tests/*.test.mjs`（4 文件） | 15 用例 | `lib/workspace.ts`（bookingTiming、scheduleBlock、estimatePrice、careStages、scopedPets、登录身份导航）、`lib/catalog.ts` healthScore、`lib/dialogLock.ts` | 15/15 通过（`test-results/verify/npm-test.log`） |
| 组件单测（vitest） | `tests/*.test.ts`（7 文件） | 58 用例 | AppSelect、AppDatePicker、AppTimePicker、EntryPage、photos 仓库+PhotoManager、StoreSetupPanel、PaymentModal | 7 文件 58/58 通过（同上日志） |
| 模拟 API E2E（Playwright） | `tests/e2e/*.spec.ts`（12 文件）+ `playwright.config.ts` | 23 用例 | 每个用例 `page.route('**/api/**')` 手工 mock 全部响应（如 `tests/e2e/accounts.spec.ts:5-21`）；覆盖登录/多角色工作台/预约/工单/结算/加项/会员/库存/订单/排班/图片展示/控件可达性 | **22 通过 / 1 失败**（`playwright-e2e.log`；`test-results/.last-run.json` status=failed） |
| 真实后端 E2E（Playwright） | `tests/real-e2e/*.spec.ts`（4 文件）+ `playwright.real.config.ts`（H2）/`playwright.mysql.config.ts`（MySQL 隔离库） | 4 用例 | 注册→建宠→复登；账号创建→签到→推进→加项→评分→前台结算→交宠全链路；项目图片替换持久化；商品图创建后展示 | **本次 verify 日志未包含该套件**（需后端+浏览器+MySQL 环境，由 verify-runner 执行） |
| 构建 | `npm run build`（vue-tsc + vite） | —— | 类型检查 + 打包 | 通过，dist 292.39 kB JS / 84.19 kB CSS（`npm-build.log`） |

### 6.2 量化覆盖率证据（`frontend/artifacts/*/coverage-summary.json`）

| 覆盖对象 | lines | branches | functions | 备注 |
|---|---|---|---|---|
| `EntryPage.vue` | 90/90 = 100% | 95.71% | 100% | registration-coverage |
| `PhotoManager.vue` | 64/64 = 100% | 93.18% | 100% | photo-coverage |
| `lib/photos.ts` | 18/18 = 100% | 100% | 100% | photo-coverage |
| `StoreSetupPanel.vue` | 63/63 = 100% | 98.96% | 95% | product-photo-coverage |
| **合计覆盖 4 个文件 / 235 行** | 100% | ≈95.7-98.96% | ≈95-100% | 仅针对单测文件；**不代表整仓覆盖率** |

**全局口径**：src 共 32 个 .vue/.ts 文件、2824 行；有覆盖率 artifact 的仅 4 个文件（12.5% 文件）、235 行（8.3% 行）。`App.vue`（581 行，承载约 80% 交互）**没有任何单测覆盖**，仅靠 E2E 间接覆盖。

### 6.3 唯一失败的 E2E：测试数据“时间炸弹”，不是产品缺陷

- 失败用例：`tests/e2e/payment.spec.ts:44` “pet owner settles waiting-pickup order with wechat payment via cashier modal”，断言 `.qr-svg` 可见（`payment.spec.ts:172`）失败。
- 根因：mock 支付单把 `expireAt` 写死为 `'2026-10-30 23:59:59'`（`tests/e2e/payment.spec.ts:75`）。当前日期 2026-10-31，前端 `PaymentModal.vue:61-71` 在挂载时算出已过期 → 渲染“二维码已过期”分支（`PaymentModal.vue:243-247`），二维码 SVG 不渲染。**功能实现本身是正确的，测试数据已过期。**
- 影响：`test-results/verify/playwright-e2e.log` 与 `test-results/.last-run.json` 均为 failed，验收会被这条红测阻塞；建议把 expireAt 改为运行时 +15 分钟。
- 另有历史红测日志 `frontend/artifacts/product-photo-test-red.log`（StoreSetupPanel 图片上传用例 6 failed），对应当前 `product-photo-coverage` 100% 的修复后状态，属 TDD 留档。

### 6.4 E2E 覆盖盲区（无任何 E2E 断言的页面/流程）

- 视图：**图片管理**只有 real-e2e（无 mock E2E）；**管理员总览**仅有标题级断言（`tests/e2e/entry.spec.ts:90`）。
- 流程：修改密码、搜索弹层（Ctrl+K 与结果打开）、待办提醒托盘、服务新增/调价（ServiceDialog 全流程）、工单改派/作废/回退/退款、洗护评价提交、宠物新增（只有编辑）、账号删除、宠主余额支付/次卡核销、通知与操作日志。
- 真实后端 E2E 只覆盖 4 条主干，库存/会员/订单/排班/服务定价均无真实后端 E2E。

## 7. “看起来存在、实际不可用 / 只对演示场景生效”的功能

1. **管理员护理动作**：`App.vue:540-568` 有完整的 `isAdmin` 判断（推进、回退、改派、作废、结算、退款、交宠），但管理员导航没有“工单/排班/宠物”（`workspace.ts:27`），管理员的“总览”也没有任何护理卡片（`App.vue:471-475`）→ 管理员实际无法打开护理抽屉，这些能力是死代码。
2. **管理员“到店开单”单行道**：`App.vue:456` 允许管理员在总览点“到店开单”，`WalkInDialog` 会创建工单（`WalkInDialog.vue:31`）并提示成功，但管理员没有任何列表能看到它（无工单视图）→ 创建后无法跟踪/推进。
3. **`canManagePets` 的管理员分支**：`App.vue:80` 包含 `isAdmin`，用于“添加爱宠/编辑档案”（`App.vue:509, 535`），但管理员没有宠物视图 → 不可达。
4. **收银台“模拟支付”**：仅演示可用（见 §5-5）；同时 `PaymentModal.vue:282` 用 `payment.sandbox !== false` 判定，后端若漏传该字段也会误显示演示按钮。
5. **新宠物/新商品图片**：图片管理只能改 13 个固定 key（`PhotoStorageService.java:26-39`）；`PetDialog` 无照片字段；商品图只在“新建商品”时可上传（`StoreSetupPanel.vue:59`），已建商品无法换图/删图（后端 PUT 无 image 字段，`StoreSetupController.java:96-102`）→ 只能在创建那一刻“看起来支持图片”。
6. **商品无图时的演示图**：按名称正则套 `product-shampoo/serum/bath` 演示图（`App.vue:62-67`），任何新商品都会显示一张“看似官方”的图。
7. **搜索的完整感**：标题写“搜索宠物与预约”（`App.vue:448`），实际只搜宠物与预约两类（`App.vue:153-156`）；帮助文案“Enter 打开”（`App.vue:579`）需先用方向键把焦点移到结果按钮才生效。
8. **健康趋势**：抽屉标题“健康趋势”总在（`HealthTrend.vue:38-40`），但 `/pets/{id}/records` 失败会被静默置空（`App.vue:170`），表现为“一直空白/只有空态”，无错误提示。
9. **门店营业时间/缓冲期**：前端硬编码并用于预约校验（`workspace.ts:71-93`），后端配置（`application.yml:45-52`）无查询接口 → “按门店配置校验”实际只是演示值。
10. **演示身份快捷登录**：只看得到 5 个 seed 身份（`EntryPage.vue:63-67`），换 seed 或生产环境后该区域无意义。

## 8. Top 缺口清单（按严重度）

> 严重度定义：P0=阻断验收/可造成资金或安全错误；P1=角色/流程完整性问题，用户会明显感知；P2=体验/测试/维护性缺口。

| 级别 | # | 缺口 | 证据 | 影响 |
|---|---|---|---|---|
| **P0** | 1 | 支付演示路径在默认配置下可用：收银台内置“模拟扫码支付成功”，后端 `mock-pay` 不校验 sandbox 开关，仅校验角色 | `PaymentModal.vue:99-118, 282-294`；`PaymentService.java:229-252`；`application.yml:54`；`PaymentController.java:65-77` | 任何店长/前台/管理员/宠主可绕过真实支付把工单置为已结算（资金/审计风险）；验收时“支付成功”无法区分真假 |
| **P0** | 2 | E2E 支付用例因固定过期时间必红，最新 Playwright 结果为 failed | `tests/e2e/payment.spec.ts:75, 172`；`test-results/verify/playwright-e2e.log`；`test-results/.last-run.json` | 阻塞“全绿”验收；需修测试数据（建议运行时刻 +15min） |
| **P1** | 3 | 管理员角色缺“排班/工单/宠物”三个视图入口，但抽屉代码与后端都给管理员开放护理/结算/退款/改派能力 | `workspace.ts:27`；`App.vue:471-475, 540-568`；`CareController.java:60,84,107,149,169,230,276` | 管理员实际无法履行这些职责；权限矩阵“写得到、点不到” |
| **P1** | 4 | 无前端路由：视图/抽屉/筛选全部内存态，刷新回到 homeView，浏览器后退无效，无法分享/直达 | `App.vue:34,160`；`package.json`（无 vue-router） | 页面结构完整性缺口；深链、刷新保持、运营入口不可用 |
| **P1** | 5 | 新增宠物/商品无法维护图片：图片位固定 13 个，宠物无上传入口，商品图仅创建时可传，之后不能换/删 | `PhotoStorageService.java:26-39`；`workspace.ts:50`；`PetPortrait.vue:9`；`PetDialog.vue`（无照片字段）；`StoreSetupPanel.vue:59`；`StoreSetupController.java:96-102` | “图片管理”看似完整，实际只覆盖演示固定素材 |
| **P1** | 6 | 零售订单无线上扫码支付入口；后端 `/payment/create` 的 `orderId` 参数未实现；到店开单不能指定工位 | `App.vue:290-299`（只传 groomingId）、`RetailOrderActions.vue:11-21`（仅现金/余额）；`PaymentController.java:28,32` vs `PaymentService.java:41`；`WalkInDialog.vue:31` vs `CareController.java:44` | 商品订单只能线下收款；工位分配不可控 |
| **P1** | 7 | 宠主无法用次卡自付，只能余额/线上支付；次卡核销只能由店长/前台操作 | `App.vue:560`；`CareController.java:224-228` | 会员“次卡”权益在宠主自助流程中不完整 |
| **P2** | 8 | 后端有、前端未接：`GET /analytics/dashboard`、`GET /services/{id}/prices`（前端首页指标自行计算，可能与后端口径漂移） | `DemoController.java:33-41`；`PricingController.java:8`；`App.vue:458-475` | 指标一致性风险、冗余实现 |
| **P2** | 9 | 覆盖盲区：`App.vue` 无单测；coverage artifact 仅 4 文件；无 E2E 的关键流程：修改密码、搜索、待办、服务新增/调价、改派/作废/回退/退款、评价、宠物新增、账号删除 | §6.2-6.4；`test-results/verify/npm-test.log`；`artifacts/*coverage-summary.json` | 回归风险集中在承载 80% 交互的 App.vue 与高风险资金流程 |
| **P2** | 10 | 演示残留与硬编码：demoAccounts 回退身份、`customerId/employeeId ?? 1`、固定宠物头像、门店营业时间硬编码、商品正则演示图、footer 演示图声明 | `workspace.ts:4-22,50`；`App.vue:35-36,55-71,439,524`；`application.yml:45-52` 无读取接口 | 生产数据替换不彻底，易造成误解与错误默认值 |
| **P2** | 11 | 静默失败：宠物健康记录、推荐、资源/评价加载失败都被 `catch` 吞掉，只显示空态 | `App.vue:170,177,182-183`；`Root.vue:36` | 故障不可见，用户以为“没有数据” |
| **P2** | 12 | 权限提示与不可达分支：管理员 `canManagePets` 死分支；只读视角（洗护师排班/宠物）无“只读/无权限”说明；操作错误多为 toast 无字段级定位 | `App.vue:80,509,535`；`ScheduleManager.vue`（canEdit 只隐藏表单）；`App.vue:528` | 角色工作台的可理解性不足 |

## 9. 审计方法、限制与证据索引

- **只读审计**：未运行 `npm test/build`、`mvn`、`playwright`；量化结论全部来自仓库内既有日志/覆盖率 artifact：`test-results/verify/npm-test.log`、`npm-build.log`、`playwright-e2e.log`、`frontend/test-results/.last-run.json`、`frontend/artifacts/{registration,photo,product-photo}-coverage/coverage-summary.json`。
- **阅读清单**：`frontend/src/App.vue`（全 581 行）、`Root.vue`、24 个组件、5 个 lib ts、`index.html`、`vite.config.ts`、3 个 playwright 配置、全部 27 个测试文件（grep/抽读）、`package.json`；后端 `AuthController`、`CareController`、`DemoController`、`PaymentController/Service`、`PhotoController/StorageService`、`StoreSetupController`、`ScheduleController`、`AccountController`、`AccountAccess`、`SecurityConfig` 的端点与权限做实例对账。
- **限制**：
  1. 未做真实浏览器复跑，凡标注“通过/失败”均引用仓库日志（时间为 2026-10-31 12:23 前后），可能随代码变更过期；
  2. `tests/real-e2e`（4 用例）本次未执行，其有效性依赖 verify-runner；
  3. 组件样式/CSS（851 行）与视觉验收不在本报告范围；
  4. 后端业务正确性由后端审计负责，本报告只用到其接口/权限作为前端完整性的参照。

### 附：关键文件行号速查

| 主题 | 位置 |
|---|---|
| 导航定义 | `frontend/src/lib/workspace.ts:23-32` |
| 演示账号 | `frontend/src/lib/workspace.ts:4-10`；`components/EntryPage.vue:63-67` |
| 会话回退 | `frontend/src/lib/workspace.ts:11-22` |
| 视图状态/跳转 | `frontend/src/App.vue:34, 130-131, 160` |
| 初始数据加载/错误态 | `frontend/src/App.vue:179-193, 453-455` |
| 工单抽屉动作 | `frontend/src/App.vue:534-568` |
| 结算表单 | `frontend/src/App.vue:555-568` |
| 支付收银台 | `frontend/src/components/PaymentModal.vue:79-143, 236-294` |
| 模拟支付 | `frontend/src/components/PaymentModal.vue:99-118, 282-294` |
| 通知/待办 | `frontend/src/App.vue:111-113, 451` |
| 搜索 | `frontend/src/App.vue:153-156, 579` |
| 图片上传（商品仅创建） | `frontend/src/components/StoreSetupPanel.vue:56-63` |
| 图片位定义 | `backend/src/main/java/com/petcare/grooming/PhotoStorageService.java:26-39` |
| mock-pay 无 sandbox 校验 | `backend/src/main/java/com/petcare/grooming/payment/PaymentService.java:229-252` |
| 支付沙箱默认开启 | `backend/src/main/resources/application.yml:53-56` |
| 管理员无工单/排班/宠物 | `frontend/src/lib/workspace.ts:27`；`frontend/src/App.vue:471-475, 540-568` |
| 失败的 E2E | `frontend/tests/e2e/payment.spec.ts:75, 172` |
| 覆盖率 artifact | `frontend/artifacts/*/coverage-summary.json` |
