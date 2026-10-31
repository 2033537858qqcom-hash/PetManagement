# PAW & PURE 智慧宠物洗护系统 —— 后端「功能完整性」审查报告

> 审计对象：D:/PetManagement 后端（Spring Boot 3.2.5 / JDK17 / JdbcTemplate，非 JPA）
> 审计方式：只读静态阅读 + 已有构建/测试产物（未运行构建；使用 backend/target 下 2026-10-31 12:23 的 surefire 报告、2026-10-30 16:13 的 JaCoCo 报告与前端 e2e 产物）
> 范围：backend/src/main/java（41 个类）、backend/src/main/resources（schema.sql / data.sql / application*.yml / db/migration/mysql V1–V19 / db/demo/mysql V8）、backend/src/test/java（27 个测试类）、sql/pet_grooming_mysql8.sql（仅一致性对比）
> 结论以代码为准。README.md 已被代码超越（部分“未实现”已实现，部分“已完成”仍是演示），本报告不照抄 README。

---

## 0. 结论摘要

| 维度 | 结论 |
|---|---|
| 代码规模 | 41 个主类；12 个 Controller；82 个 HTTP 端点（81 个注解端点 + 1 个 Spring Security /api/auth/logout）；30 张 demo_* 表（H2 schema.sql 与 MySQL Flyway V1–V19 静态一致） |
| 演示可用性 | 高。单店/单机/演示数据场景下：注册登录、账号管理、预约→签到→护理阶段→健康评估→结算→交宠、到店开单、加项审批计价、库存扣减、零售订单、会员/次卡、图片管理均有真实落库逻辑，H2 测试 97 项通过 |
| 生产完整性 | 不完整。支付为本地伪造（无真实网关/验签/退款/关单闭环），通知/门店设置/审计/报表/多宠一单缺失，MySQL 生产路径默认测试全部跳过 |
| 关键数字 | 97 passed / 7 skipped / 0 failed（H2）；MySQL 集成测试 7 项全部 skipped；旧 JaCoCo 行覆盖 78.5%、分支 58.5%（报告滞后、不含 payment 包）；前端 e2e 最新产物 status=failed |
| 判定 | 可交付“演示/课程设计”级完整度，不具备真实门店上线条件。最严重的是支付链路的「伪集成 + 无验签回调 + 假退款」三连，其次是通知/门店配置/多宠一单缺失与 MySQL 未验证 |

---

## 1. 接口总览与完整接口清单

### 1.0 总览

- Controller（12）：AuthController、AccountController、CareController、DemoController、MembershipController、InventoryController、ScheduleController、PricingController、PhotoController、ProductPhotoController、StoreSetupController、payment/PaymentController。
- 鉴权模型：Spring Security 会话登录，/api/** 一律 authenticated，anyRequest denyAll（SecurityConfig.java:49-60）；角色 ADMIN / MANAGER / RECEPTION / GROOMER / OWNER 硬编码在各 Controller（AccountAccess.java:14-37），无方法级 RBAC、无权限表、无操作审计。
- requireStaff() = MANAGER/ADMIN/RECEPTION；OWNER/GROOMER 数据隔离靠 requirePet()、customerId()、employeeId()（AccountAccess.java:18-37）。
- CSRF 全校验，仅 /api/payment/notify/** 豁免（SecurityConfig.java:55）；登录防会话固定（AuthController.java:83）；账号停用/改密通过 account_version 使旧会话 401（ActiveAccountFilter.java:17-32）。

### 1.1 认证 / 注册 / 账号（11 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 1 | GET | /api/auth/csrf | 无 | 匿名 | 返回 CSRF token + header 名 | AuthController.java:58；SecurityConfig.java:50 |
| 2 | GET | /api/auth/me | 无 | 已登录 | 按 ROLE 返回 username/role/name/title/customerId/employeeId；未知角色 403 | AuthController.java:59,35-57 |
| 3 | PUT | /api/auth/password | {oldPassword,newPassword} | 已登录 | BCrypt 校验原密码；新密码≥12 字符且 UTF-8≤72 字节；account_version+1 并刷新当前 session | AuthController.java:60-75 |
| 4 | POST | /api/auth/register | {username,password,name,phone}+CSRF | 匿名 | 账号 3–32 位字母数字下划线；密码策略；姓名≤60；电话 6–20 位可带 +；手机号已存在且未绑号则复用客户档案；事务内建客户+OWNER 账号；重复 409；注册后自动登录 | RegistrationService.java:26-79；AuthController.java:76-79 |
| 5 | POST | /api/auth/login | {username,password}+CSRF | 匿名 | AuthenticationManager+BCrypt；失败 401；changeSessionId 防固定；写入 account_version | AuthController.java:80-93 |
| 6 | POST | /api/auth/logout | 无 | 已登录+CSRF | Spring Security logout，成功 204 | SecurityConfig.java:61 |
| 7 | GET | /api/accounts | 无 | ADMIN/MANAGER | MANAGER 列表排除 ADMIN | AccountController.java:24-30 |
| 8 | POST | /api/accounts | {username,password,role,customerId?,employeeId?,displayName?,title?} | ADMIN/MANAGER | 账号/密码规则；角色枚举 5 种；MANAGER 不可建 ADMIN/MANAGER；OWNER 需绑定未绑定客户、GROOMER 需绑定未绑定员工；重名 409 | AccountController.java:31-61 |
| 9 | PUT | /api/accounts/{username}/status | {enabled} | ADMIN/MANAGER | 不能停用自己；至少保留 1 个启用 ADMIN；MANAGER 不可操作 ADMIN/MANAGER；account_version+1 强制下线 | AccountController.java:62-76 |
| 10 | PUT | /api/accounts/{username}/password | {password} | ADMIN/MANAGER | 密码策略；同样角色限制；account_version+1 强制下线 | AccountController.java:77-89 |
| 11 | DELETE | /api/accounts/{username} | 无 | ADMIN/MANAGER | 不能删自己；最后一个 ADMIN 禁删；删账号不级联业务数据 | AccountController.java:90-107 |

### 1.2 客户 / 会员 / 次卡 / 余额（7 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 12 | GET | /api/customers | 无 | OWNER 仅本人；GROOMER 仅服务过的客户（id/name/level）；staff 全部 | 联表展示；Groomer 不返回手机号 | DemoController.java:42-47 |
| 13 | POST | /api/customers | {name,phone} | requireStaff | 姓名≤60；电话正则；重复电话 409 | StoreSetupController.java:46-53 |
| 14 | PUT | /api/customers/{id} | {name,phone} | requireStaff | 同上；同步账号 display_name | StoreSetupController.java:54-63 |
| 15 | POST | /api/customers/{id}/recharge | {amount,requestId} | requireStaff | 金额>0 且≤9999999.99、≤2 位小数；UUID 幂等（同 requestId 返回原记录，不一致 409）；demo_member_lock 行锁串行；余额上限 99,999,999.99；写 balance_flow+充值记录 | MembershipController.java:37-51 |
| 16 | POST | /api/customers/{id}/cards | {name,total,price,serviceIds[],requestId} | requireStaff | 名称≤100；total 1–1000；服务 1–100 个去重且必须启用；价格有效；UUID 幂等；行锁；建卡+卡服务关联+销售记录 | MembershipController.java:53-73 |
| 17 | GET | /api/customers/{id}/history | 无 | OWNER 仅本人；staff | 返回余额流水/次卡销售/次卡核销记录 | MembershipController.java:75-82 |
| 18 | GET | /api/members/cards | 无 | OWNER 本人；GROOMER 空数组；staff 全部 | 附 service_ids | DemoController.java:244-261 |

### 1.3 宠物档案（4 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 19 | GET | /api/pets | ?customerId 可选 | OWNER 本人（校验参数）；GROOMER 服务过的宠物；staff 全部/按客户 | 输出中文标签、健康摘要、洗护前风险提示 | DemoController.java:48-55 |
| 20 | POST | /api/pets | PetForm{customerId?,name,species,breed,ageBand,sizeType,weight,coat,allergy,skinHistory,groomingHistory} | OWNER 本人；staff | 固定词表校验：昵称 1–12、品种词表、年龄/体型/毛发/过敏/病史/洗护史枚举、体重 0.1–99.99 且≤2 位小数；客户存在 | CareController.java:300-327；CareTerms.java:70-95 |
| 21 | PUT | /api/pets/{id} | PetForm | OWNER 仅本人；staff 可改归属 | 同上；体重未变时放行 | CareController.java:329-351 |
| 22 | GET | /api/pets/{id}/records | 无 | requirePet：OWNER 本人 / GROOMER 该宠物有工单或预约 / staff | 健康记录按日期升序+中文标签；无分页、无编辑删除 | CareController.java:353-360 |

### 1.4 服务项目与定价（6 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 23 | GET | /api/services | 无 | 任意已登录 | OWNER 只看到 enabled；其余看全部 | DemoController.java:56-63 |
| 24 | POST | /api/services | {name,category,price,duration,species} | MANAGER/ADMIN | 名称 2–20；分类固定 4 种；价格 1–2000（≤2 位）；时长枚举 45/60/75/90/120；适用 ALL/DOG/CAT | CareController.java:537-549；CareTerms.java:84-91 |
| 25 | PUT | /api/services/{id} | {name?,category?,price,duration?,species?,enabled} | MANAGER/ADMIN | price/enabled 必填；价格 1–2000；其余字段回退原值 | CareController.java:551-566 |
| 26 | GET | /api/services/recommendations/{petId} | 无 | requirePet | 硬编码推荐：默认 75 分，猫项目 96、毛发含“毛”+深层护理 91；按分排序 | DemoController.java:64-78 |
| 27 | GET | /api/services/{id}/prices | 无 | 5 种角色之一 | 返回体型价目/时长 | PricingController.java:8；ServicePricing.java:17-20 |
| 28 | PUT | /api/services/{id}/prices | {items:[{sizeType,price,duration}]} | MANAGER/ADMIN | ≤4 条；体型 SMALL/MEDIUM/LARGE/GIANT 唯一；价格 1–2000；时长 1–705；booking lock；服务存在；全量替换 | PricingController.java:9；ServicePricing.java:28-40 |

### 1.5 员工与工位（6 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 29 | GET | /api/employees | 无 | 任意已登录 | OWNER 仅启用子集（不暴露敏感列）；GROOMER 仅本人；staff 全部 | DemoController.java:79-94 |
| 30 | POST | /api/employees | {name,title,skills,species,sizes[],enabled?} | MANAGER/ADMIN | 名称/职位≤60、技能≤200；species DOG/CAT；sizes 1–4 个去重且属于 4 种体型 | StoreSetupController.java:64-70 |
| 31 | PUT | /api/employees/{id} | 同上 | MANAGER/ADMIN | 同上；同步账号 display_name/title；可启停 | StoreSetupController.java:71-81 |
| 32 | GET | /api/resources | 无 | 已登录（无角色收敛） | OWNER 返回空数组；其余（含 GROOMER）返回全部工位 | DemoController.java:95-100 |
| 33 | POST | /api/resources | {name,species} | MANAGER/ADMIN | 名称≤80；物种枚举 | StoreSetupController.java:82-87 |
| 34 | PUT | /api/resources/{id} | {name,species,enabled?} | MANAGER/ADMIN | 同上；可启停 | StoreSetupController.java:88-95 |

### 1.6 预约与排班（7 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 35 | GET | /api/appointments | 无 | OWNER 本人 / GROOMER 本人 / staff 全部 | 联表 pet/service/employee/resource；无日期过滤、无分页、无单条详情 | DemoController.java:101-107 |
| 36 | POST | /api/appointments | {customerId,petId,serviceId,date,startTime,preferredEmployeeId?} | OWNER 本人 / MANAGER/ADMIN/RECEPTION | 全局 demo_booking_lock+synchronized；宠物属客户；项目启用；allocate()：物种、时间格式、未来时段、09:00 起、结束+15min≤21:00、同宠重叠、员工启用/物种/体型、休假 OFF、员工与工位 15min 缓冲；插入 CONFIRMED | DemoController.java:111-121,135-178 |
| 37 | POST | /api/appointments/{id}/reschedule | {date,startTime} | OWNER 本人 / MANAGER/ADMIN/RECEPTION | booking lock；仅 CONFIRMED；保持原时长与原价格；排除自身再查冲突 | DemoController.java:122-134 |
| 38 | POST | /api/appointments/{id}/cancel | 无 | OWNER 本人 / MANAGER/ADMIN/RECEPTION | 仅 CONFIRMED 可取消 | CareController.java:362-374 |
| 39 | POST | /api/appointments/{id}/check-in | 无 | MANAGER/ADMIN/RECEPTION（OWNER 403） | 仅 CONFIRMED 且只能当天；生成工单（stage=CHECK_IN / status=GROOMING / 6 位接宠码 / base_price=预约价）+事件；预约置 IN_SERVICE | CareController.java:57-79 |
| 40 | GET | /api/schedules?date | date | GROOMER 本人 / staff 全部 | 日期解析；无分页 | ScheduleController.java:18-22 |
| 41 | PUT | /api/schedules/{employeeId}/{date} | {status,startTime,endTime} | MANAGER/ADMIN | 不能改过去；WORK/OFF；工作时间须在 09:00–21:00 且 end>start；booking lock；员工存在；已有预约须落在新班次内；upsert | ScheduleController.java:23-44 |

### 1.7 工单 / 护理流程（9 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 42 | GET | /api/grooming | 无 | OWNER 本人 / GROOMER 本人 / staff 全部 | 联表；无分页/日期过滤 | DemoController.java:179-185 |
| 43 | POST | /api/grooming | {petId,serviceId,employeeId,resourceId?} | MANAGER/ADMIN/RECEPTION | 到店开单：booking lock；项目/员工启用；物种体型匹配；size 价+时长；营业时间可容纳；班次校验；自动搜索可用工位（宠 0/员工 15/工位 15 缓冲）；同时建预约(IN_SERVICE)+工单；唯一 6 位接宠码；事件 | CareController.java:376-499 |
| 44 | POST | /api/grooming/{id}/advance | {precheckNote?} | GROOMER 本人 / MANAGER/ADMIN | 仅 GROOMING；CHECK_IN→BATHING→DRYING→TRIMMING；接宠备注≤300；事件 | CareController.java:81-102 |
| 45 | POST | /api/grooming/{id}/rollback | 无 | GROOMER 本人 / MANAGER/ADMIN | 仅 GROOMING；回退一格；事件 | CareController.java:104-118 |
| 46 | PUT | /api/grooming/{id}/reassign | {employeeId} | MANAGER/ADMIN | booking lock；仅 GROOMING；目标员工启用、物种/体型匹配、当日非休假；同步预约 employee_id；事件 | CareController.java:120-144 |
| 47 | POST | /api/grooming/{id}/void | {reason?} | MANAGER/ADMIN/RECEPTION | 仅 GROOMING/WAITING_PICKUP；已 PAID 须先退款；工单+预约 CANCELLED；事件 | CareController.java:146-164 |
| 48 | POST | /api/grooming/{id}/handover | {pickupCode} | MANAGER/ADMIN/RECEPTION | 仅 WAITING_PICKUP；接宠码匹配；结算须 PAID 或 REFUNDED；工单+预约 COMPLETED；事件 | CareController.java:166-178 |
| 49 | GET | /api/grooming/{id}/events | 无 | OWNER 本人 / GROOMER 本人 / staff | 按 id 升序返回操作履历；无分页 | CareController.java:180-187 |
| 50 | POST | /api/grooming/{id}/assessment | {weight,skin,coat,ear,groomerNote?} | GROOMER 本人 / MANAGER/ADMIN | 仅 GROOMING 且 stage=TRIMMING；每单一次；体重 0–999.99 且≤2 位；寄语≤300；CareTerms 词典评分（皮 40%/毛 35%/耳 25%）；扣耗材；写健康记录+更新宠物体重；stage=DONE / status=WAITING_PICKUP；事件 | CareController.java:501-535；CareTerms.java:60-68 |

### 1.8 加项（3 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 51 | GET | /api/grooming/addons | 无 | OWNER 本人 / GROOMER 本人 / staff 全部 | 联表宠物名；无分页 | DemoController.java:186-192 |
| 52 | POST | /api/grooming/{id}/addons | {name,reason,amount,requestId} | GROOMER 本人 / MANAGER/ADMIN（前台不可） | 名称≤100、原因≤300、金额 0<≤2000 且≤2 位；UUID 幂等；仅 GROOMING 状态；写 PENDING | DemoController.java:195-215 |
| 53 | POST | /api/grooming/addons/{id}/decision | {decision:APPROVED 或 REJECTED} | OWNER 本人客户 / GROOMER 本人且只能 REJECT / staff | 仅 PENDING 可处理，二次 409；写 ADDON_APPROVED/ADDON_REJECTED 事件；无工单状态/结算状态校验 | DemoController.java:216-233 |

### 1.9 健康 / 体重（0 个独立端点）

健康记录只在“护理完成评估”内创建（第 50 项），读取在第 22 项；无独立称重、疫苗、病历、体检、编辑/删除、趋势统计接口。证据：CareController.java:501-535（唯一写入）、CareController.java:353-360（唯一读取）。

### 1.10 库存 / 耗材（9 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 54 | GET | /api/inventory | 无 | OWNER 仅 price>0 展示列；GROOMER 仅 id/name/unit；staff 全部 | 按角色裁剪列 | DemoController.java:234-243 |
| 55 | POST | /api/inventory（JSON） | {name,unit,threshold} | MANAGER/ADMIN | 名称≤100、单位≤20、阈值 0–1e6；新耗材 stock=0 price=0 sale_units=1 | StoreSetupController.java:103-105,110-121 |
| 56 | POST | /api/inventory（multipart） | product(JSON part)+image(file) | MANAGER/ADMIN | 图片：真 JPG/PNG、≤5MB、≤6000px/1600 万像素；事务回滚删除已上传文件 | StoreSetupController.java:106-109；PhotoStorageService.java:107-127 |
| 57 | PUT | /api/inventory/{id} | {name,unit,threshold} | MANAGER/ADMIN | 校验同上；不改价格/库存/图片 | StoreSetupController.java:96-102 |
| 58 | POST | /api/inventory/{id}/adjust | {kind:IN 或 OUT 或 SET,quantity,reason,requestId} | MANAGER/ADMIN | 数量 0–1e6（IN/OUT 需>0）、原因≤255、UUID 幂等、库存不能为负/溢出、写 inventory_flow | InventoryService.java:37-57 |
| 59 | GET | /api/inventory/{id}/flows | 无 | MANAGER/ADMIN | 流水倒序 | InventoryService.java:71 |
| 60 | PUT | /api/inventory/{id}/retail | {saleUnits,price} | MANAGER/ADMIN | saleUnits 1–1e6、price 0–2000 且≤2 位 | InventoryService.java:72-78 |
| 61 | GET | /api/services/{id}/consumption | 无 | MANAGER/ADMIN | 耗材规则+名称/单位 | InventoryService.java:70 |
| 62 | PUT | /api/services/{id}/consumption | {items:[{productId,quantity}]} | MANAGER/ADMIN | ≤100 条、耗材存在、数量 1–1e6、去重；booking lock；事务全量替换 | InventoryService.java:58-69 |

### 1.11 零售订单（5 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 63 | GET | /api/orders | 无 | GROOMER 空数组；OWNER 本人；staff 全部 | 联表客户名/电话；无分页 | CareController.java:568-578 |
| 64 | POST | /api/orders | {customerId?,productId,quantity,requestId} | OWNER 本人 / staff | 数量 1–3；UUID 幂等（demo_sale_lock + request_id 唯一）；price>0；库存按 sale_units 折算并条件扣减；写订单+SALE 流水+事件 | CareController.java:580-628 |
| 65 | POST | /api/orders/{id}/settle | {method?} | OWNER 本人仅 BALANCE / staff | 仅 SUBMITTED；只支持 CASH/BALANCE；BALANCE 条件扣减+流水；置 PAID 并写 pay_method；事件 | CareController.java:630-652 |
| 66 | POST | /api/orders/{id}/cancel | 无 | OWNER 本人 / staff | 仅 SUBMITTED；回补库存+流水；CANCELLED+事件 | CareController.java:653-660 |
| 67 | POST | /api/orders/{id}/refund | {reason} | MANAGER/ADMIN | 仅 PAID；原因≤255；BALANCE 退回余额；回补库存；REFUNDED+事件 | CareController.java:661-682 |

### 1.12 结算与收款（3 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 68 | GET | /api/grooming/{id}/settlement | 无 | OWNER 本人 / MANAGER/ADMIN/RECEPTION | 已结算返回记录；未结算返回报价：base 取 base_price→预约价→当前体型价，addon=APPROVED 加项合计，status=UNPAID | CareController.java:193-216 |
| 69 | POST | /api/grooming/{id}/settle | {method,cardId?,addonMethod?,tradeNo?} | OWNER 本人仅 BALANCE / MANAGER/ADMIN/RECEPTION | WAITING_PICKUP；无 PENDING 加项；结算存在且非 REFUNDED 拒绝；method 5 种；CARD 校验卡归属/适用服务/剩余次数，加项部分 CASH 或 BALANCE；余额条件扣减；写结算+流水 | CareController.java:218-271 |
| 70 | POST | /api/grooming/{id}/refund | {reason} | MANAGER/ADMIN | 仅 PAID；原因必填≤255；CARD 回补次数+加项余额；BALANCE 回补余额；置 REFUNDED；WECHAT/ALIPAY 无真实退款动作 | CareController.java:273-298 |

### 1.13 支付集成（4 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 71 | POST | /api/payment/create | {groomingId 或 orderId,channel} | OWNER 本人 / MANAGER/ADMIN/RECEPTION | 至少一个 id；channel 非空；工单存在且属本人；WAITING_PICKUP；无待确认加项；未结算；金额=base+APPROVED addons；生成 PAY+时间+随机号；调用 handler 本地伪造二维码；写 demo_payment_order PENDING（15 分钟）；orderId 分支被当作 groomingId 处理 | PaymentController.java:30-50；PaymentService.java:41-130 |
| 72 | GET | /api/payment/{paymentNo} | 无 | OWNER 本人 / staff | 不存在 404；本人校验；PENDING 且过期则惰性 CLOSED；返回状态/金额/tradeNo/二维码/宠物/服务/接宠码/sandbox | PaymentController.java:52-63；PaymentService.java:132-179 |
| 73 | POST | /api/payment/{paymentNo}/mock-pay | 无 | OWNER 本人 / staff | CLOSED→409；SUCCESS 幂等；生成 SIM_渠道_时间戳 → completePayment（结算 PAID、支付单 SUCCESS、写 SETTLED 事件）；不判断 sandboxEnabled，生产同样可调 | PaymentController.java:65-77；PaymentService.java:228-252 |
| 74 | POST | /api/payment/notify/{channel} | 原始 body/query 参数 | 匿名（permitAll + CSRF 豁免） | 仅解析 out_trade_no/trade_no/amount；无签名、证书、来源、金额比对校验；解析成功即 completePayment；返回渠道格式 ack | PaymentController.java:79-96；PaymentService.java:254-265；WechatPayChannelHandler.java:69-119；AlipayChannelHandler.java:68-115 |

### 1.14 评价（2 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 75 | GET | /api/reviews | 无 | GROOMER 本人工单；OWNER 按 customer_name 或工单客户；MANAGER/ADMIN/RECEPTION 全部 | OWNER 过滤依赖评价快照中的姓名（改名/重名会漏查或越权看到他人评价） | DemoController.java:263-273 |
| 76 | POST | /api/reviews | {groomingId,rating,content} | 仅 OWNER | 1–5 星；内容≤500；工单 COMPLETED 且属本人；每单一次；快照客户/宠物名 | DemoController.java:274-298 |

### 1.15 图片（5 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 77 | GET | /api/media/photos | 无 | 匿名（SecurityConfig 显式放行） | 返回固定 13 个图片位目录（封面/宠物/项目/商品/会员） | PhotoController.java:20；PhotoStorageService.java:26-40；SecurityConfig.java:51 |
| 78 | PUT | /api/media/photos/{key} | multipart file | 仅 ADMIN | key 必须命中 13 个固定位；非空、≤5MB、真实 JPG/PNG、≤6000px/1600 万像素；原子替换+SHA-256 版本号 | PhotoController.java:21-25；PhotoStorageService.java:72-92,145-160 |
| 79 | DELETE | /api/media/photos/{key} | 无 | 仅 ADMIN | 删除自定义图恢复默认；未知 key 404 | PhotoController.java:26-29 |
| 80 | GET | /api/media/photos/{key}/image | key | 匿名 | ETag/304；未自定义 404 | PhotoController.java:30-36 |
| 81 | GET | /api/media/products/{key}/image | key | 匿名 | key 必须 UUID.(png 或 jpg)；≤5MB；1 年 immutable 缓存 | ProductPhotoController.java:15-22；PhotoStorageService.java:128-144 |

### 1.16 总览 / 驾驶舱（1 个）

| # | 方法 | 路径 | 入参 | 鉴权 | 关键校验 / 行为 | 证据 |
|---|---|---|---|---|---|---|
| 82 | GET | /api/analytics/dashboard | 无 | MANAGER/ADMIN/RECEPTION（OWNER/GROOMER 403） | 仅 4 个计数：今日预约（DB current_date）、护理中、待确认加项、低库存；无营收/核销/趋势/员工业绩；前端 src 中检索不到该接口调用 | DemoController.java:33-41 |

> 合计：11+7+4+6+6+7+9+3+0+9+5+3+4+2+5+1 = 82 个端点。

---

## 2. 模块功能状态表

状态：✅ 完整可用 / ⚠️ 部分实现 / ❌ 缺失 / 🔍 仅演示或硬编码

| 模块 | 状态 | 缺什么 / 说明 | 证据 |
|---|---|---|---|
| 认证 / 注册 / 账号管理 | ✅ | 登录、会话、CSRF、注册、改密、账号 CRUD、启停、重置密码、account_version 强制下线均可用。缺：登录限流/锁定、忘记密码、操作审计、单账号多角色 | SecurityConfig.java:49-62；AuthController.java:58-93；AccountController.java:24-107；ActiveAccountFilter.java:17-32 |
| 宠物档案 | ⚠️ | 建档/编辑/列表/健康记录可闭环；无删除/过户、无宠物照片上传、无分页 | CareController.java:300-360；DemoController.java:48-55 |
| 预约 / 排班 | ✅（核心） | 预约、改期、取消、当天签到、员工排班、休假、清洁缓冲、自动分配已实现。缺：周期预约/候补、日期区间查询、手动 NO_SHOW（仅定时任务）、单条预约详情 | DemoController.java:101-178；ScheduleController.java:18-44；LifecycleScheduler.java:29-39 |
| 工单 / 护理流程 | ✅ | 签到→4 阶段推进/回退→评估→待接宠→交接闭环，含改派/作废/操作履历、并发行锁。缺：接宠检查照片/风险告知书、阶段级备注/独立质检阶段、工单详情单查 | CareController.java:57-535 |
| 加项 | ⚠️ | 申请→审批→计入结算金额已闭环（README 说“未关联订单计价”已过时）。缺：审批无工单/结算状态校验（结算后批准无法补收）、无加项字典、无照片凭证、无时长/库存联动、无通知、前台不能代客发起 | DemoController.java:195-233；CareController.java:211-214；SettlementTest.java；AddonRequestTest.java |
| 健康 / 体重 | ⚠️ | 只有护理完成评估一个写入口；无独立称重、疫苗、病历、体检、编辑/删除、趋势 API；宠物档案体重可直接改，绕过健康记录 | CareController.java:501-535,353-360 |
| 库存 / 耗材 | ✅（核心） | 建档、出入库调整（幂等+流水）、零售包装、服务耗材规则、护理自动扣减、低库存计数可用。缺：采购单/供应商/批次/效期、负库存欠账（缺料静默钳制到 0）、流水仅店长/管理员可查 | InventoryService.java:37-99,79-89 |
| 零售订单 | ✅（核心） | 下单（库存锁+幂等）、现金/余额收款、取消、退款回补库存、30 分钟超时自动释放可用。缺：在线支付未接（orderId 错绑工单）、配送/物流/地址、发票 | CareController.java:568-682；LifecycleScheduler.java:44-61 |
| 会员 / 次卡 / 余额 | ⚠️ | 充值、办卡、余额/次卡流水、history 查询可用。缺：充值/办卡无支付方式字段（demo_member_recharge/demo_card_sale 只有金额，无法对账）、次卡有效期/退卡、积分只存不用、等级仅文本无权益规则、宠主无自助充值/购卡 | MembershipController.java:37-82；schema.sql:23-24；V10 migration |
| 服务定价 | ✅ | 体型价目 CRUD + 缺省规则（LARGE/GIANT +50）+ 预约价格快照已实现 | ServicePricing.java:17-40；DemoController.java:140,173 |
| 结算与收款 | ⚠️ | 现金/余额/次卡收银、组合支付、退款（次卡/余额）、报价齐全。缺：线上支付真实退款、部分退款、发票/凭证、退款审批流、结算后加项补收 | CareController.java:193-298 |
| 支付集成 | 🔍 | 本地伪造：createOrder 直接拼 weixin:// 或 https://qr.alipay.com 假串；queryOrder 永远返回 PENDING 且从未被调用；isLiveConfigured 从未被调用；回调无验签且匿名可达；mock-pay 无沙箱开关；商品订单支付未接 | WechatPayChannelHandler.java:39-66,121-126；AlipayChannelHandler.java:38-65,117-122；PaymentChannelHandler.java:14-16；PaymentService.java:88-130,181-265；SecurityConfig.java:52,55 |
| 评价 | ⚠️ | 创建（完成交宠后、每单一次）+ 分角色查看可用。缺：申诉/回复/审核/删除、评分聚合进员工/门店统计、照片评价；OWNER 姓名快照过滤有隐私风险 | DemoController.java:263-298 |
| 图片 | ⚠️ | 管理员可维护 13 个固定图片位（上传/恢复默认/公开读取/ETag），耗材创建时可上传图片。缺：耗材图片不可替换/删除、宠物照片/护理前后对比照不可上传、图片位固定不可扩展 | PhotoController.java:20-36；PhotoStorageService.java:26-40,107-144；StoreSetupController.java:96-121 |
| 门店设置 | ❌ | 后端无任何门店设置接口/表；店名、地址、营业时间、收银配置仅在 application.yml 静态配置，且 Java 代码从不读取 pet-grooming.store（grep 0 命中）；营业时间 09:00–21:00 硬编码在预约/开单逻辑 | application.yml:45-53；DemoController.java:143；CareController.java:399-407 |
| 总览 / 驾驶舱 | ⚠️/🔍 | 仅 4 个计数接口，无营收/核销/复购/趋势；使用 DB current_date 而非注入 Clock；前端未调用（概览数字由列表聚合） | DemoController.java:33-41；App.vue:184 |
| 通知 / 消息 | ❌ | 后端无通知、短信、站内信、WebSocket/SSE；DomainModels.NotificationItem 为死代码（0 覆盖）；前端“待办提醒”是前端本地推导 | DomainModels.java:556-569；backend/src/main/java 无 notification 实现 |
| 操作审计 | ❌ | 无 audit/operation_log 表与接口；仅工单/订单有业务事件流水（demo_grooming_event、demo_order_event），账号、价格、库存等敏感变更无审计 | schema.sql（无 audit 表）；DomainModels.java:552 为死字段 |
| 多宠一单 | ❌ | 工单与预约均为单宠（pet_id 单列），无工单-宠物关联表，无法一单多宠/合并结算 | schema.sql:10-11；V1 migration:8-9 |
| 预约转工单 | ✅（当天） | 通过签到把当天 CONFIRMED 预约转成工单；到店开单可同时建预约+工单。缺：未来预约不能提前转单、无批量/自动转单、无独立 NO_SHOW 处理接口 | CareController.java:57-79,376-499 |
| 报表 / 导出 | ❌ | 无任何报表、导出、打印、对账接口 | backend/src/main/java 无相关 Controller |

---

## 3. 数据层完整性

### 3.1 双数据库路径

- H2（默认 profile）：jdbc:h2:mem:pet_grooming;MODE=MySQL 内存库（application.yml:30-35），每次启动执行 schema.sql（30 张表）+ data.sql（演示数据），重启即丢数据；Flyway 显式关闭（application.yml:24-25）。
- MySQL profile：application-mysql.yml 开启 Flyway（locations=classpath:db/migration/mysql，application-mysql.yml:2-6），spring.sql.init.mode=never；application-mysql-demo.yml 追加 db/demo/mysql 并打开 demo 种子（application-mysql-demo.yml:2-5）。
- 表数量一致性：H2 schema.sql 30 张表；MySQL 迁移 V1(15)+V2(4)+V3(1)+V5(2)+V10(3)+V12(2)+V14(1)+V15(1)+V19(1) = 30 张；表名、关键列、唯一键静态比对一致（request_id 唯一 V11/V12/V13、trade_no V19、image_key V16、groomer_note V17 在 H2 schema 中均有对应）。
- 无外键、无 CHECK：30 张表只有主键/唯一键，0 条 FOREIGN KEY（schema.sql 与 V1–V19 均无）；跨表一致性完全依赖代码，删账号/客户不级联，存在孤儿数据风险。
- 演示表与业务表不分离：生产库同样使用 demo_* 前缀表；非 demo 的 MySQL 启动仅靠 Flyway V4 建 admin 账号 + PET_BOOTSTRAP_ADMIN_PASSWORD 初始化（SecurityConfig.java:29-34），否则启动 IllegalStateException。

### 3.2 迁移风险

1. demo 种子与结构迁移共用版本号序列：V8__demo_seed.sql 位于 db/demo/mysql，但版本 8 会被 Flyway 在 V7 与 V9 之间执行（全局版本序）。当前 SQL 恰好兼容（显式列名、旧列数），但“种子先于 V9–V19 结构变更执行”是脆弱设计，未来改表需人工核对顺序依赖。
2. profile 切换可能导致 Flyway 校验失败：同一 MySQL 库先以 mysql-demo 迁移（应用过 V8）再以 mysql（不加载 demo location）启动时，Flyway 默认 validate-on-migrate=true 会检测到“已应用但本地缺失”的 V8，静态推断存在启动失败风险，需显式 ignoreMigrationPatterns 或固定 profile（未在测试中覆盖）。
3. MySQL 生产路径无有效验证产物：MySQL 集成测试 3 个类全部由 @EnabledIfSystemProperty 门控（MySqlPersistenceTest.java:20、MySqlBootstrapTest.java:17、MySqlRestartTest.java:18），默认 mvn test 全部 skipped（surefire：MySqlPersistence 4 skipped、MySqlBootstrap 2 skipped、MySqlRestart 1 skipped）。
4. H2 与 MySQL 行为差异未验证：demo_payment_order.grooming_id NOT NULL（schema.sql:30 / V19:4）意味着零售订单支付无法落库；DB current_date 与注入 Clock 混用（DemoController.java:36）；H2 无 performance_schema，并发测试只能跑 MySQL（MySqlPersistenceTest.java:81）。

### 3.3 遗留 SQL 草稿（sql/pet_grooming_mysql8.sql）

- 39 张表的旧设计草稿，与代码实际使用的 30 张 demo_* 表完全脱节；除 README 外全仓库无任何引用（grep 仅 README 命中）。
- README 指出的草稿问题仍然存在：grooming_order 建表只有 grooming_no（sql:432），DML 却插入 order_no（sql:1062）；grooming_record 建表只有 process_note（sql:480），DML 却插入 remarks（sql:1070）；另有列名拼写错误 skin_ coat_feedback（sql:953，含空格）；脚本含 39 条 DROP TABLE。
- 结论：该文件仍是不可执行的历史草稿，不应作为交付物；真实 MySQL 结构以 db/migration/mysql 为准。

### 3.4 README 过时点（数据层，代码对照）

| README 说法 | 代码事实 | 判定 |
|---|---|---|
| “MySQL profile 当前尚未与演示数据层打通，不能认为可切换使用”（README:51） | application-mysql-demo.yml + V8 demo seed + MySql 测试已存在 | 过时（已打通，但未验证） |
| “账号仍在内存中配置”（README:22） | 账号在 demo_account_binding 表，有完整 CRUD/启停/改密/version 失效 | 过时 |
| “加项尚未关联订单计价”（README:49） | 结算/线上支付均含 APPROVED 加项金额 | 过时 |
| “排班不含员工排班/请假/宠物重复预约约束”（README:50） | V14 排班表、OFF 班次、同宠重叠校验均已实现 | 过时 |
| “预约转工单/收银/次卡核销退还/退款尚未实现”（README:50） | 均已实现（除线上退款） | 部分过时 |
| “SQL 39 表草稿未验证、多组 INSERT 列不存在”（README:51） | 仍然成立（见 3.3） | 准确 |
| “通知、评价申诉、多宠一单、营收趋势尚无”（README:50） | 仍然成立 | 准确 |

---

## 4. 测试覆盖

### 4.1 测试资产与实测结果

- 27 个测试类，104 个测试用例；最近一次 surefire 产物（2026-10-31 12:23）：97 passed / 7 skipped / 0 failed。
- 除 SchedulingRulesTest（纯单元）外全部 MockMvc + @SpringBootTest + 独立 H2 内存库（每类一个 jdbc:h2:mem:xxx），多数 @Transactional 回滚；不走真实 HTTP 服务、不连 MySQL；身份多用 user()/@WithMockUser 伪造（绕过真实登录/会话/账号版本过滤器），仅 AuthTest、RegistrationTest、AccountManagementTest 部分用例使用真实 session+CSRF。
- MySQL 真实集成测试全部 skipped（7 项）：MySqlPersistenceTest(4)、MySqlBootstrapTest(2)、MySqlRestartTest(1)，需 -Dmysql.integration=true / -Dmysql.bootstrap.integration=true / -Dmysql.browser.db=... 且本机 MySQL，默认构建不会执行。

### 4.2 测试类覆盖矩阵

| 测试类 | 覆盖业务 | 方式 | 产物结果 |
|---|---|---|---|
| AuthTest | CSRF 获取→真实登录、会话/登出、5 角色登录画像 | MockMvc+H2，真实 session | 3/3 通过 |
| RegistrationTest | 注册事务、隔离、重复账号/电话、并发注册、CSRF、参数化非法输入 | MockMvc+H2 | 23/23 通过 |
| AccountManagementTest | 建号绑定、改密/停用后旧会话失效、删除后重新绑定 | MockMvc+H2，真实 session | 3/3 通过 |
| AccessIsolationTest | 跨宠主隔离、Groomer 仅本人数据、未知角色 403 | MockMvc+H2 | 4/4 通过 |
| CareFlowTest | 宠主建档/改档、取消预约、下单、经理取消/开单、价格与可用性、Groomer 评分 | MockMvc+H2 | 6/6 通过 |
| AppointmentTest | 猫咪工位、清洁缓冲、过去时段、同宠重叠 | MockMvc+H2 | 3/3 通过 |
| RescheduleTest | 改期权限+价格保持 | MockMvc+H2 | 1/1 通过 |
| EmployeeScheduleTest | 休假/班次约束、已有预约禁止危险排班 | MockMvc+H2 | 1/1 通过 |
| ServicePricingTest | 体型价/时长生效、改期保价 | MockMvc+H2 | 1/1 通过 |
| VisitWorkflowTest | 签到→阶段推进→交接全链路+owner 签到 403 | MockMvc+H2 | 1/1 通过 |
| WeightRecordingTest | 评估记录小数体重 | MockMvc+H2 | 1/1 通过 |
| WalkInLateNightTest | 深夜到店开单被拒 | MockMvc+H2 | 1/1 通过 |
| AddonRequestTest | 加项申请+宠主确认+计入结算金额 | MockMvc+H2 | 1/1 通过 |
| ReassignAndVoidTest | 改派（休假/可用）、作废联动预约、Groomer 看本人工单评价 | MockMvc+H2 | 4/4 通过 |
| SettlementTest | 次卡归属/适用服务/退款一次、组合次卡+余额、余额不足回滚、退款后可交宠、宠主余额自付 | MockMvc+H2 | 6/6 通过 |
| MembershipTest | 充值幂等/权限、办卡记录与适用服务 | MockMvc+H2 | 2/2 通过 |
| InventoryFlowTest | 调整权限/幂等/流水、护理耗材一次扣减、零售包装折算、短缺钳制 | MockMvc+H2 | 4/4 通过 |
| RetailOrderTest | 下单幂等、退款回补库存、余额结算、超时订单定时释放 | MockMvc+H2 | 2/2 通过 |
| PaymentIntegrationTest | 微信/支付宝下单二维码、状态查询、mock 支付、匿名回调（微信 JSON/支付宝表单）、过期关闭、越权支付 | MockMvc+H2，mock 渠道 | 9/9 通过 |
| WorkflowTest | 驾驶舱有数、加项只能处理一次 | MockMvc+H2 | 2/2 通过 |
| PhotoManagementTest | 目录公开、上传/替换/重置、ETag、权限、假图/尺寸/大小、路径穿越 | MockMvc+H2+临时目录 | 8/8 通过 |
| ProductPhotoTest | 耗材+图片事务、回滚删图、JSON 建品、JPG、权限、未知 key | MockMvc+H2+临时目录 | 8/8 通过 |
| StoreSetupTest | 客户/员工/工位/耗材创建、账号绑定、宠主建档 | MockMvc+H2 | 1/1 通过 |
| SchedulingRulesTest | 缓冲与重叠纯函数 | 纯 JUnit | 2/2 通过 |
| MySqlPersistenceTest | 迁移+真实登录+签到生成键+充值/办卡/结算/退款/库存/订单+并发不超卖 | 真实 MySQL（门控） | 4/4 skipped |
| MySqlBootstrapTest | 生产 bootstrap 无演示数据、空店可运营 | 真实 MySQL（门控） | 2/2 skipped |
| MySqlRestartTest | 浏览器写入跨重启持久化 | 真实 MySQL（门控） | 1/1 skipped |

### 4.3 完全无测试的功能（按端点/能力）

- 无任何测试的端点：GET /api/grooming/{id}/settlement（报价分支）、POST /api/grooming/{id}/rollback、GET /api/inventory/{id}/flows、GET /api/customers/{id}/history、PUT /api/auth/password（自助改密）、PUT /api/employees/{id}、PUT /api/resources/{id}、PUT /api/customers/{id}、PUT /api/inventory/{id}、GET /api/services/{id}/consumption、GET /api/accounts。
- 无测试的能力：真实网关下单/查单/关闭/退款、回调验签与金额比对、商品订单在线支付（orderId 分支）、加项在结算后批准补收、部分退款、通知、评价申诉/回复、门店设置、多宠一单、登录限流、审计日志、跨实例并发预约（仅零售并发 1 例且 MySQL-only）。
- 测试把不安全行为固化为期望：PaymentIntegrationTest.java:150-174 在无鉴权、无签名的前提下 POST notify 并断言结算变 PAID；PaymentIntegrationTest.java:98-147 断言 mock-pay 可把工单结算掉。

### 4.4 覆盖率（旧报告，需注意时效）

backend/target/site/jacoco/jacoco.csv（2026-10-30 16:13，早于 payment 包与 V19 引入，不含 PaymentService/Controller/Handler）：

- 整体行覆盖 78.5%（1086/1384），分支覆盖 58.5%（661/1129），指令覆盖 81.0%。
- 主要类：CareController 行 90%/分支 55%；DemoController 75%/56%；CareTerms 99%/51%；InventoryService 95%/60%；MembershipController 80%/55%；ScheduleController 83%/56%；SecurityConfig 93%/44%；DomainModels（567 行死代码）0%。
- 因报告滞后，payment 包与最新测试（PaymentIntegrationTest 9 条）覆盖情况无有效产物。

### 4.5 “测试全绿但功能未接 UI / 权限 / 真实环境”的证据

1. 支付：后端 PaymentIntegrationTest 9/9 绿，但渠道是本地 mock，真实微信/支付宝从未被调用；产物无法证明网关可用。
2. 驾驶舱：GET /api/analytics/dashboard 仅 WorkflowTest 冒烟一次，frontend/src 检索 0 次调用，该接口在 UI 中不可达。
3. 商品订单在线支付：/api/payment/create 的 orderId 分支无任何测试，前端也只传 groomingId（App.vue:295），该分支不可达且实现错误。
4. MySQL 持久化：真实 MySQL 测试全 skipped，mvn test 全绿不代表 MySQL profile 可用；README 却把 mvn test 描述为后端验证方式。
5. 端到端：frontend/test-results/.last-run.json 明确 status=failed（1 条失败：payment.spec.ts 收银台二维码 .qr-svg 不可见，见 error-context.md），根目录 test-results/.last-run.json 亦为 failed（failedTests 为空）。当前不满足前后端全绿。

---

## 5. “宣布完成但未闭环”功能点（结合代码判定）

| # | 功能 | 表面完成证据 | 实际未闭环 | 严重度 |
|---|---|---|---|---|
| 5.1 | 支付集成（微信/支付宝） | V19 迁移 + demo_payment_order 表 + 前端完整收银台/二维码/倒计时 UI + 9 条支付测试全绿 | 二维码是本机伪造串（WechatPayChannelHandler.java:41-43、AlipayChannelHandler.java:40-42）；queryOrder/isLiveConfigured 从未被调用（PaymentChannelHandler.java:14-16，全库无调用方）；无真实网络请求/证书；回调无验签且匿名可达（SecurityConfig.java:52,55）；mock-pay 无沙箱开关（PaymentService.java:228-252）；无关闭/退款接口 | P0 |
| 5.2 | 线上支付退款 | POST /grooming/{id}/refund 可把结算置 REFUNDED，SettlementTest 覆盖次卡/余额退款 | WECHAT/ALIPAY 结算只改数据库状态（CareController.java:283-296），不调用任何退款，demo_payment_order 不更新，PaymentStatus.REFUNDED 全库无使用（仅定义于 PaymentStatus.java:7），线上已收款退款是假退款 | P0 |
| 5.3 | 零售订单线上支付 | /api/payment/create 表单声明 orderId（PaymentController.java:28,32），收银台 UI 存在 | orderId 被直接当作 groomingId 传入 PaymentService.createPayment（PaymentController.java:32,48），而 demo_payment_order.grooming_id NOT NULL（V19:4 / schema.sql:30）；UI 只传 groomingId（App.vue:295）→ 商品订单永远无法在线支付，且 orderId 与某工单 id 相同时会给错误工单发起支付 | P0 |
| 5.4 | 加项计入订单金额 | 已实现：结算报价与线上支付均累加 APPROVED 加项（CareController.java:211-214；PaymentService.java:82-84），AddonRequestTest 验证 | 审批接口不校验工单状态/是否已结算（DemoController.java:216-233）：工单已交宠/已收款后仍可批准加项，金额不会再被收取；加项为自由文本，无字典/时长/库存联动，无通知 | P1 |
| 5.5 | 次卡/余额收银闭环 | 结算/退款/流水/幂等全部实现，6 条 SettlementTest 绿 | 次卡销售与充值记录没有支付方式字段（V10 表结构、MembershipController.java:19-20），现金与余额无法对账；无退卡/有效期；宠主无自助充值/购卡 | P1 |
| 5.6 | 评价 | 创建 + 分角色查看可用，前端有评价弹窗 | 无申诉（全库无 endpoint/表）、无商家回复、无审核/删除、无评分聚合；OWNER 过滤用姓名快照（DemoController.java:268-271）有重名越权/改名漏查风险 | P1 |
| 5.7 | 图片管理 | 13 个固定位可替换/重置、耗材创建可带图 | 耗材图片无替换/删除接口（ProductForm 无 image 字段，StoreSetupController.java:96-102）；宠物照片/护理前后对比照无处上传；PhotoStorageService 只认固定 key | P2 |
| 5.8 | 健康记录 | 评估即生成健康评分并更新体重，前端有健康趋势图 | 无独立称重/病历/疫苗/编辑/删除/趋势接口；趋势图由前端从 records 计算；demo_health_record 只增不改 | P1 |
| 5.9 | 通知 / 待办提醒 | 前端有“查看待办提醒”按钮与通知面板 | 后端无通知数据/推送；DomainModels.NotificationItem 是 0 覆盖死代码；加项待确认、预约提醒、支付结果均无通知 | P1 |
| 5.10 | 门店设置 | 前端有门店设置/门店维护面板（员工/工位/商品/客户维护） | 后端无门店设置接口与表；店名/地址/营业时间/支付配置仅 yml 静态值且代码不读取（application.yml:45-53）；营业时间硬编码 09:00–21:00（DemoController.java:143；CareController.java:399-407） | P1 |
| 5.11 | MySQL 持久化 | Flyway V1–V19 + demo 种子 + 3 个 MySQL 测试类 + README 启动说明 | 默认 mvn test 全部 skipped，无运行产物；无 CI/脚本证据证明迁移可跑通；H2 默认内存重启丢数据；profile 混用有 Flyway 校验风险 | P1 |
| 5.12 | 驾驶舱 | 有 /api/analytics/dashboard 与前端概览页 | 只有 4 个计数、无营收/核销/趋势；前端未调用该接口 | P2 |

---

## 6. Top 缺口清单（按严重度）

### P0 — 阻断真实上线 / 资金与安全风险

1. 支付回调可匿名伪造：/api/payment/notify/** permitAll + CSRF 豁免（SecurityConfig.java:52,55），两个渠道 handler 只解析字段、无签名/证书/来源/金额校验（WechatPayChannelHandler.java:69-119；AlipayChannelHandler.java:68-115）；任何人都能 POST 商户单号把工单标记为已支付（PaymentService.java:254-265）。
2. 支付网关是假的：下单返回本地伪造二维码（WechatPayChannelHandler.java:41-43；AlipayChannelHandler.java:40-42）；queryOrder()/isLiveConfigured() 是死接口（PaymentChannelHandler.java:14-16，全库 0 调用），配置项 notifyBaseUrl/notifyUrl 无人读取（PaymentProperties.java:11,19,36）。
3. mock-pay 无环境开关：任何登录用户（宠主/前台）都可对自己/任意待收款支付单调 /api/payment/{paymentNo}/mock-pay 直接完成支付（PaymentController.java:65-77；PaymentService.java:228-252 无 sandbox 判断）。
4. 线上支付退款假闭环：退款只处理 CARD/BALANCE，WECHAT/ALIPAY 仅改状态（CareController.java:283-296），不触达支付渠道、不更新 demo_payment_order；真实商户会出现“系统显示已退、钱未退”。
5. 商品订单在线支付错绑工单：orderId 被当 groomingId 使用（PaymentController.java:32,48），且 demo_payment_order.grooming_id NOT NULL（schema.sql:30 / V19:4）——零售订单在线支付功能不存在，且有给错误工单发起支付的风险。

### P1 — 功能完整性硬缺口

6. 通知能力整体缺失：后端无通知/短信/站内信/推送/WebSocket 代码；加项待确认、预约提醒、支付结果、支付超时全部无通知（全库 grep 仅死代码 DomainModels.java:556）。
7. 门店设置缺失、营业时间硬编码：无门店设置接口/表（application.yml:45-53 配置无代码读取）；09:00–21:00 与 15 分钟缓冲硬编码（DemoController.java:143；CareController.java:399-407），运营参数无法配置。
8. 多宠一单 / 合并结算缺失：预约与工单均为单 pet_id（schema.sql:10-11；V1:8-9），无一单多宠关联表。
9. 加项审批无状态守卫：结算后仍可批准加项但不补收（DemoController.java:216-233），金额完整性漏洞。
10. 审计日志缺失：无 audit 表/接口，账号启停、改密、价格修改、库存调整、退款等敏感操作无不可篡改留痕（仅业务事件流）。
11. MySQL 生产路径未验证：MySqlPersistenceTest/MySqlBootstrapTest/MySqlRestartTest 全部 skipped（MySqlPersistenceTest.java:20 等；surefire 7 skipped）；README 的 mvn test 验证口径会让人误以为已覆盖持久化。
12. 评价申诉/回复缺失 + 姓名快照过滤：无申诉/回复/审核接口；OWNER 查询用 customer_name 匹配（DemoController.java:268-271），改名或重名会造成漏查/越权。

### P2 — 体验/可维护性/数据质量

13. 驾驶舱 API 形同虚设：仅 4 个计数、无营收/核销/趋势、前端未调用（DemoController.java:33-41）。
14. 列表无分页：/grooming、/orders、/appointments、/reviews、/members/cards、/inventory 全部全量返回（CareController.java:568-578；DemoController.java:101-107,179-185）。
15. 库存缺料静默钳制：consumeCare 把库存压到 0 并在 reason 里写差额（InventoryService.java:79-89），没有欠账/待补记录，账实不符且无告警。
16. DomainModels 567 行死代码：包含 NotificationItem（:556）、申诉状态（:552）、PaymentFlow（:447）等未实现设计残留，JaCoCo 0% 覆盖，误导后续开发。
17. 图片位固定不可扩展：13 个固定 key（PhotoStorageService.java:26-40）；耗材图不可替换/删除；宠物照无上传。
18. 遗留 39 表 SQL 草稿仍随仓库交付且错误依旧（sql/pet_grooming_mysql8.sql:432 vs 1062；480 vs 1070；953），无任何代码引用，易误导入。

---

## 7. 审计物料索引（可复核）

| 证据 | 位置 |
|---|---|
| Spring 安全/角色策略 | backend/src/main/java/com/petcare/grooming/config/SecurityConfig.java:48-62；AccountAccess.java:14-37；ActiveAccountFilter.java:17-32 |
| 业务主链路 | CareController.java:57-682；DemoController.java:33-298；MembershipController.java:37-82；InventoryService.java:37-99；PaymentService.java:41-265 |
| H2 结构/种子 | backend/src/main/resources/schema.sql（30 表）；data.sql |
| MySQL 迁移 | backend/src/main/resources/db/migration/mysql/V1–V19；db/demo/mysql/V8__demo_seed.sql |
| 多数据源配置 | application.yml:24-43；application-mysql.yml:1-20；application-mysql-demo.yml:1-5 |
| 测试源码 | backend/src/test/java（27 类） |
| 测试结果产物 | backend/target/surefire-reports/*.txt（97 passed/7 skipped/0 failed，2026-10-31 12:23） |
| 覆盖率产物（滞后） | backend/target/site/jacoco/jacoco.csv（2026-10-30 16:13，不含 payment 包） |
| 前端 e2e 产物 | frontend/test-results/.last-run.json（status=failed）+ payment-pet-owner-settles--44f9b-t-payment-via-cashier-modal/error-context.md |
| 遗留 SQL 草稿 | sql/pet_grooming_mysql8.sql（39 表/38 INSERT/39 DROP，仅 README 引用） |

> 说明：本审计未执行任何构建或测试命令，所有“通过/跳过”数字均来自上述现有产物；启动验证与最终测试结论以 verify-runner 为准。
