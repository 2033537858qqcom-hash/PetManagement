-- ============================================================================
-- ⚠️ 架构与交付提示 / ARCHITECTURE & DEPLOYMENT NOTICE ⚠️
-- 本文件为早期概念设计的 39 表参考草稿（含 DROP TABLE），仅作为课程设计与设计理念参考。
-- 生产与演示的权威数据模型请使用：
-- 1. MySQL 权威版本：backend/src/main/resources/db/migration/mysql/ 下的 Flyway 迁移脚本 (V1 ~ V20)
-- 2. H2 内存与测试环境：backend/src/main/resources/schema.sql 与 data.sql
-- 请勿直接在生产环境或集成数据库中全量执行此脚本！
-- ============================================================================
-- 《基于 Spring Boot 的智慧宠物洗护预约与门店管理系统设计与实现》
-- 数据库初始化草稿 (MySQL 8.0+ 概念设计归档)
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `pet_grooming_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `pet_grooming_db`;

-- ============================================================================
-- 一、用户与角色权限模块 (3张表)
-- ============================================================================

DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录账号',
  `password` VARCHAR(128) NOT NULL COMMENT '加密密码(BCrypt)',
  `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名/昵称',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号码',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '电子邮箱',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `user_type` VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' COMMENT '账号类型: ADMIN-店长/管理员, CASHIER-前台收银, GROOMER-洗护美容师, CUSTOMER-宠主客户',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态: 1-正常, 0-禁用, 2-锁定',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_user_type_status` (`user_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统登录用户表';

DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` VARCHAR(32) NOT NULL COMMENT '角色编码: ROLE_ADMIN, ROLE_CASHIER, ROLE_GROOMER, ROLE_CUSTOMER',
  `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '角色职责描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户与角色关联表';

-- ============================================================================
-- 二、宠主管理模块 (2张表)
-- ============================================================================

DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '宠主客户ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '关联系统登录用户ID',
  `customer_no` VARCHAR(32) NOT NULL COMMENT '宠主会员编号(如 CUS20260001)',
  `name` VARCHAR(64) NOT NULL COMMENT '宠主姓名',
  `gender` VARCHAR(10) DEFAULT 'UNKNOWN' COMMENT '性别: MALE-男, FEMALE-女, UNKNOWN-未知',
  `phone` VARCHAR(20) NOT NULL COMMENT '联系手机号',
  `wechat_id` VARCHAR(64) DEFAULT NULL COMMENT '微信号',
  `address` VARCHAR(255) DEFAULT NULL COMMENT '常住地址(用于接送宠参考)',
  `member_level` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '会员等级: NORMAL-普通会员, SILVER-银卡会员(95折), GOLD-金卡会员(9折), DIAMOND-钻卡会员(85折)',
  `points` INT NOT NULL DEFAULT 0 COMMENT '当前可用积分(100积分可抵1元)',
  `total_spent` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '历史累计消费金额(元)',
  `visit_count` INT NOT NULL DEFAULT 0 COMMENT '累计到店洗护次数',
  `no_show_count` INT NOT NULL DEFAULT 0 COMMENT '累计爽约次数',
  `restrict_status` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '预约限制状态: NORMAL-正常, WARNING-爽约观察, RESTRICTED-限制线上预约(需付定金或人工审核), BLACKLIST-黑名单',
  `restrict_reason` VARCHAR(255) DEFAULT NULL COMMENT '限制或拉黑原因说明',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '门店对宠主的服务偏好备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册建档时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_no` (`customer_no`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_member_level` (`member_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠主会员档案表';

DROP TABLE IF EXISTS `customer_contact`;
CREATE TABLE `customer_contact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '联系人ID',
  `customer_id` BIGINT NOT NULL COMMENT '所属宠主ID',
  `contact_name` VARCHAR(64) NOT NULL COMMENT '联系人姓名',
  `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系人电话',
  `relationship` VARCHAR(32) NOT NULL COMMENT '与宠主关系: SPOUSE-配偶, PARENT-父母, FRIEND-朋友, ROOMMATE-室友, OTHER-其他',
  `is_emergency` TINYINT NOT NULL DEFAULT 1 COMMENT '是否为紧急联系人: 1-是, 0-否',
  `can_pickup` TINYINT NOT NULL DEFAULT 1 COMMENT '是否授权代接送宠物: 1-已授权, 0-未授权',
  `id_card_last4` VARCHAR(10) DEFAULT NULL COMMENT '身份证后四位(核验代接宠身份用)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠主紧急联系人与授权代接宠联系人表';

-- ============================================================================
-- 三、宠物档案管理模块 (4张表)
-- ============================================================================

DROP TABLE IF EXISTS `pet`;
CREATE TABLE `pet` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '宠物ID',
  `pet_no` VARCHAR(32) NOT NULL COMMENT '宠物档案编号(如 PET20260001)',
  `primary_customer_id` BIGINT NOT NULL COMMENT '主宠主ID',
  `name` VARCHAR(64) NOT NULL COMMENT '宠物昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '宠物头像照片URL',
  `species` VARCHAR(20) NOT NULL DEFAULT 'DOG' COMMENT '宠物种类: DOG-犬类, CAT-猫类',
  `breed` VARCHAR(64) NOT NULL COMMENT '具体品种(如 金毛寻回犬、比熊、布偶猫、柯基、萨摩耶)',
  `gender` VARCHAR(16) NOT NULL DEFAULT 'MALE' COMMENT '性别: MALE-公, FEMALE-母',
  `birth_date` DATE DEFAULT NULL COMMENT '出生日期',
  `size_type` VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '体型分类: SMALL-小型(≤6kg), MEDIUM-中型(6~15kg), LARGE-大型(15~30kg), GIANT-巨型(>30kg)',
  `weight_kg` DECIMAL(5,2) NOT NULL DEFAULT 5.00 COMMENT '最新体重(kg)',
  `coat_type` VARCHAR(32) NOT NULL DEFAULT 'LONG' COMMENT '毛发类型: SHORT-短毛, MEDIUM-中长毛, LONG-长毛, CURLY-卷毛, DOUBLE-双层厚毛, HAIRLESS-无毛',
  `matting_level` VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '日常打结情况: NONE-无打结, SLIGHT-轻微打结, MODERATE-中度打结, SEVERE-严重毡化打结',
  `temperament_tags` JSON DEFAULT NULL COMMENT '性格与行为标签JSON数组(如 ["温顺亲人","怕吹水机","护爪","需戴伊丽莎白圈"])',
  `grooming_preference` VARCHAR(500) DEFAULT NULL COMMENT '洗护造型偏好(如 泰迪装、留靴子、肛门腺轻挤、用低敏香波)',
  `neutered` TINYINT NOT NULL DEFAULT 0 COMMENT '绝育状态: 1-已绝育, 0-未绝育, 2-未知',
  `chip_no` VARCHAR(64) DEFAULT NULL COMMENT '电子芯片号/犬证号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '档案状态: ACTIVE-正常服务, SUSPENDED-建议暂缓洗护, ARCHIVED-已归档/转出',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建档时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_no` (`pet_no`),
  KEY `idx_primary_customer` (`primary_customer_id`),
  KEY `idx_species_size` (`species`, `size_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠物核心档案表';

DROP TABLE IF EXISTS `pet_owner_relation`;
CREATE TABLE `pet_owner_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `pet_id` BIGINT NOT NULL COMMENT '宠物ID',
  `customer_id` BIGINT NOT NULL COMMENT '关联宠主ID',
  `role_type` VARCHAR(20) NOT NULL DEFAULT 'PRIMARY' COMMENT '主人角色: PRIMARY-主主人, CO_OWNER-家庭共同照料人/副主人, FORMER-历史前主人(已转移)',
  `relation_label` VARCHAR(32) DEFAULT '本人' COMMENT '身份称谓(如 男主人、女主人、父母代管)',
  `can_book` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许代预约: 1-允许, 0-不允许',
  `can_pickup` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许代接宠: 1-允许, 0-不允许',
  `use_shared_card` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许共享主主人次卡/储值: 1-允许, 0-不允许',
  `transfer_note` VARCHAR(255) DEFAULT NULL COMMENT '归属权绑定或转移说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_customer` (`pet_id`, `customer_id`),
  KEY `idx_customer_id` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠物与宠主多对多关联及授权表(支持一人多宠与家庭共管)';

DROP TABLE IF EXISTS `pet_health_note`;
CREATE TABLE `pet_health_note` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '健康记录ID',
  `pet_id` BIGINT NOT NULL COMMENT '宠物ID',
  `allergens` VARCHAR(255) DEFAULT NULL COMMENT '已知过敏源(如 某种香波成分、鸡肉零食、花粉)',
  `skin_condition` VARCHAR(255) DEFAULT NULL COMMENT '皮肤与耳道状况(如 趾间炎恢复期、耳螨史、腹部红疹)',
  `health_risks` VARCHAR(500) DEFAULT NULL COMMENT '已知健康风险(如 心脏杂音、髌骨脱位二级、高龄猫应激风险)',
  `last_vaccine_date` DATE DEFAULT NULL COMMENT '最近一次核心疫苗接种日期',
  `next_vaccine_date` DATE DEFAULT NULL COMMENT '下次疫苗到期提醒日期',
  `vaccine_Institution` VARCHAR(128) DEFAULT NULL COMMENT '疫苗接种机构或凭证说明',
  `last_deworm_date` DATE DEFAULT NULL COMMENT '最近一次体内外驱虫日期',
  `next_deworm_date` DATE DEFAULT NULL COMMENT '下次驱虫提醒日期',
  `suggest_suspend` TINYINT NOT NULL DEFAULT 0 COMMENT '是否建议暂缓洗护服务: 1-建议暂缓(触发预约拦截提醒), 0-可正常洗护',
  `suspend_reason` VARCHAR(255) DEFAULT NULL COMMENT '建议暂缓原因(如 刚打完疫苗未满7天、术后拆线前)',
  `recorded_by` VARCHAR(64) DEFAULT NULL COMMENT '记录人/核验洗护师',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_pet_id` (`pet_id`),
  KEY `idx_suggest_suspend` (`suggest_suspend`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠物健康、疫苗驱虫与洗护禁忌档案表';

DROP TABLE IF EXISTS `pet_service_history`;
CREATE TABLE `pet_service_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '历史档案ID',
  `pet_id` BIGINT NOT NULL COMMENT '宠物ID',
  `grooming_order_id` BIGINT DEFAULT NULL COMMENT '关联洗护工单ID',
  `service_date` DATE NOT NULL COMMENT '服务日期',
  `service_summary` VARCHAR(255) NOT NULL COMMENT '服务项目摘要(如 精致洗护套餐+去底绒)',
  `groomer_id` BIGINT DEFAULT NULL COMMENT '主洗护师ID',
  `groomer_name` VARCHAR(64) DEFAULT NULL COMMENT '洗护师姓名',
  `weight_at_service` DECIMAL(5,2) DEFAULT NULL COMMENT '当次到店称重(kg)',
  `skin_ coat_feedback` VARCHAR(500) DEFAULT NULL COMMENT '毛发与皮肤状态反馈',
  `behavior_feedback` VARCHAR(500) DEFAULT NULL COMMENT '当次洗护配合度与应激表现',
  `before_photo_url` VARCHAR(255) DEFAULT NULL COMMENT '洗护前对比照片URL',
  `after_photo_url` VARCHAR(255) DEFAULT NULL COMMENT '洗护后成品照片URL',
  `next_suggest_date` DATE DEFAULT NULL COMMENT '建议下次洗护日期',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_pet_id_date` (`pet_id`, `service_date`),
  KEY `idx_grooming_order_id` (`grooming_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠物历史洗护摘要与前后对比档案表';

-- ============================================================================
-- 四、洗护项目管理模块 (6张表)
-- ============================================================================

DROP TABLE IF EXISTS `service_category`;
CREATE TABLE `service_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_code` VARCHAR(32) NOT NULL COMMENT '分类编码: BASIC_BATH, FULL_GROOM, COAT_CARE, LOCAL_CARE',
  `category_name` VARCHAR(64) NOT NULL COMMENT '分类名称(基础洗澡、宠物美容、毛发SPA护理、局部单项护理)',
  `icon` VARCHAR(64) DEFAULT NULL COMMENT '前端展示图标标识',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号(升序)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-停用',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '分类说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护服务分类表';

DROP TABLE IF EXISTS `service_item`;
CREATE TABLE `service_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '服务项目ID',
  `category_id` BIGINT NOT NULL COMMENT '所属分类ID',
  `item_code` VARCHAR(32) NOT NULL COMMENT '服务项目编码(如 SRV_BATH_01)',
  `item_name` VARCHAR(100) NOT NULL COMMENT '服务项目名称',
  `applicable_species` VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '适用宠物种类: DOG-仅犬类, CAT-仅猫类, ALL-猫犬通用',
  `base_price` DECIMAL(10,2) NOT NULL DEFAULT 68.00 COMMENT '基准起步价格(元,默认对应小型宠)',
  `base_duration_min` INT NOT NULL DEFAULT 45 COMMENT '基准预计耗时(分钟)',
  `required_skill_level` VARCHAR(20) NOT NULL DEFAULT 'JUNIOR' COMMENT '所需洗护师最低技能等级: JUNIOR-初级洗护师, SENIOR-高级洗护师, MASTER-首席美容师',
  `process_steps` JSON DEFAULT NULL COMMENT '标准服务工序说明JSON(如 ["耳道清洁","剪指甲磨甲","挤肛门腺","两遍香波浴","护毛素","吹干拉毛"])',
  `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '项目封面图URL',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '上下架状态: 1-上架可预约, 0-下架停售',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '展示排序',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_code` (`item_code`),
  KEY `idx_category_status` (`category_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护标准服务项目表';

DROP TABLE IF EXISTS `service_price_rule`;
CREATE TABLE `service_price_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '定价规则ID',
  `service_item_id` BIGINT NOT NULL COMMENT '关联服务项目ID',
  `species` VARCHAR(20) NOT NULL DEFAULT 'DOG' COMMENT '适用种类: DOG, CAT',
  `size_type` VARCHAR(20) NOT NULL COMMENT '适用体型: SMALL, MEDIUM, LARGE, GIANT',
  `min_weight_kg` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '体重下限(kg,含)',
  `max_weight_kg` DECIMAL(5,2) NOT NULL DEFAULT 99.99 COMMENT '体重上限(kg,含)',
  `coat_type` VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '毛发类型附加条件: ALL-不限, LONG-长毛/双层厚毛',
  `final_price` DECIMAL(10,2) NOT NULL COMMENT '该规格最终核定单价(元)',
  `extra_duration_min` INT NOT NULL DEFAULT 0 COMMENT '较基准时长增加的额外分钟数(如大型犬+45分钟)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '规格说明(如 大型犬15-30kg双层毛标准价)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_item_species_size` (`service_item_id`, `species`, `size_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务项目按体型/体重/毛量差异化定价与时长规则表';

DROP TABLE IF EXISTS `service_package`;
CREATE TABLE `service_package` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
  `package_code` VARCHAR(32) NOT NULL COMMENT '套餐编码(如 PKG_DELUXE_01)',
  `package_name` VARCHAR(100) NOT NULL COMMENT '套餐名称(如 精致洗护SPA套餐、萌宠全身造型焕新套餐)',
  `applicable_species` VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '适用种类: DOG, CAT, ALL',
  `original_total_price` DECIMAL(10,2) NOT NULL COMMENT '单项原价合计(元)',
  `package_price` DECIMAL(10,2) NOT NULL COMMENT '套餐一口优惠价(元,小型宠基准)',
  `large_pet_surcharge` DECIMAL(10,2) NOT NULL DEFAULT 50.00 COMMENT '中大型宠套餐补差价(元)',
  `estimated_duration_min` INT NOT NULL DEFAULT 90 COMMENT '套餐预计总时长(分钟)',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '套餐亮点描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-上架, 0-下架',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_code` (`package_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护组合套餐表';

DROP TABLE IF EXISTS `service_package_item`;
CREATE TABLE `service_package_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `package_id` BIGINT NOT NULL COMMENT '所属套餐ID',
  `service_item_id` BIGINT NOT NULL COMMENT '包含的服务项目ID',
  `item_quantity` INT NOT NULL DEFAULT 1 COMMENT '包含次数/份数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pkg_item` (`package_id`, `service_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护套餐包含项目明细表';

DROP TABLE IF EXISTS `service_addon`;
CREATE TABLE `service_addon` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '附加项ID',
  `addon_code` VARCHAR(32) NOT NULL COMMENT '附加项编码(如 ADD_DEMAT_SEVERE)',
  `addon_name` VARCHAR(100) NOT NULL COMMENT '附加收费项名称(如 严重打结开结费、中草药药浴升级、体外驱虫滴剂护理、去底绒加收)',
  `charge_type` VARCHAR(20) NOT NULL DEFAULT 'FIXED' COMMENT '计费方式: FIXED-固定一口价, BY_LEVEL-按程度分级, BY_TIME-按耗时每30分钟',
  `default_price` DECIMAL(10,2) NOT NULL DEFAULT 30.00 COMMENT '标准参考加收金额(元)',
  `extra_duration_min` INT NOT NULL DEFAULT 20 COMMENT '预计增加服务时长(分钟)',
  `need_customer_confirm` TINYINT NOT NULL DEFAULT 1 COMMENT '洗护中临时追加是否必须经宠主确认: 1-必须确认, 0-免确认',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-停用',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '收费标准说明(向宠主展示)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_addon_code` (`addon_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护标准附加收费项字典表';

-- ============================================================================
-- 五、员工与门店资源模块 (3张表)
-- ============================================================================

DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '员工ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '关联系统登录账号ID',
  `emp_no` VARCHAR(32) NOT NULL COMMENT '员工工号(如 EMP001)',
  `name` VARCHAR(64) NOT NULL COMMENT '员工姓名',
  `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
  `position` VARCHAR(32) NOT NULL DEFAULT 'GROOMER' COMMENT '岗位: STORE_MANAGER-店长, SENIOR_GROOMER-首席美容师, GROOMER-资深洗护师, ASSISTANT-洗护助理, CASHIER-前台收银',
  `skill_level` VARCHAR(20) NOT NULL DEFAULT 'SENIOR' COMMENT '技能等级: JUNIOR-初级, SENIOR-高级, MASTER-首席A级',
  `specialty_tags` JSON DEFAULT NULL COMMENT '擅长技能标签JSON(如 ["比熊萌系造型","大型犬精洗","猫咪低应激洗护","赛级手剪"])',
  `accepted_sizes` VARCHAR(100) NOT NULL DEFAULT 'SMALL,MEDIUM,LARGE,GIANT' COMMENT '可接宠物体型范围(逗号分隔)',
  `accept_cat` TINYINT NOT NULL DEFAULT 1 COMMENT '是否接猫咪洗护: 1-可接猫, 0-仅接犬',
  `commission_type` VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE' COMMENT '提成方式: PERCENTAGE-按项目实收比例, FIXED-按单固定提成',
  `commission_rate` DECIMAL(5,2) NOT NULL DEFAULT 15.00 COMMENT '提成比例(%)或固定每单金额(元)',
  `work_status` VARCHAR(20) NOT NULL DEFAULT 'ON_DUTY' COMMENT '在职与当班状态: ON_DUTY-在岗可派单, BUSY-服务中, REST-休息中, RESIGNED-已离职',
  `hire_date` DATE DEFAULT NULL COMMENT '入职日期',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '员工展示头像',
  `intro` VARCHAR(500) DEFAULT NULL COMMENT '美容师个人简介(客户端预约展示)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_no` (`emp_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_position_status` (`position`, `work_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店员工与洗护美容师档案表';

DROP TABLE IF EXISTS `employee_schedule`;
CREATE TABLE `employee_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '排班ID',
  `employee_id` BIGINT NOT NULL COMMENT '员工ID',
  `schedule_date` DATE NOT NULL COMMENT '排班日期',
  `shift_name` VARCHAR(32) NOT NULL DEFAULT '全天班' COMMENT '班次名称: 早班(09:00-18:00), 晚班(11:00-20:00), 全天班(09:30-19:30)',
  `start_time` TIME NOT NULL DEFAULT '09:30:00' COMMENT '上班开始时间',
  `end_time` TIME NOT NULL DEFAULT '19:30:00' COMMENT '下班结束时间',
  `break_start` TIME DEFAULT '12:30:00' COMMENT '午休开始时间',
  `break_end` TIME DEFAULT '13:30:00' COMMENT '午休结束时间',
  `max_appointments` INT NOT NULL DEFAULT 8 COMMENT '当日最大可接预约单数上限',
  `booked_count` INT NOT NULL DEFAULT 0 COMMENT '当日已排预约单数',
  `status` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '排班状态: NORMAL-正常出勤, LEAVE-请假, OFF-公休调休',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '排班备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_date` (`employee_id`, `schedule_date`),
  KEY `idx_schedule_date_status` (`schedule_date`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工每日排班与接单容量表';

DROP TABLE IF EXISTS `service_resource`;
CREATE TABLE `service_resource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源工位ID',
  `resource_code` VARCHAR(32) NOT NULL COMMENT '资源编号(如 STATION_BATH_01)',
  `resource_name` VARCHAR(64) NOT NULL COMMENT '工位/设备名称(如 1号大型犬冲浪洗护池、2号猫咪独立静音洗护间、A1精修美容台、智能负离子烘干舱)',
  `resource_type` VARCHAR(32) NOT NULL DEFAULT 'BATH_TUB' COMMENT '资源类型: BATH_TUB-洗护池, GROOM_TABLE-美容修剪台, DRYING_BOX-智能烘干箱, CAT_ROOM-猫咪独立无应激洗护室, CAGE-待接宠寄养格',
  `supported_sizes` VARCHAR(100) NOT NULL DEFAULT 'SMALL,MEDIUM,LARGE,GIANT' COMMENT '支持宠物体型范围',
  `cat_exclusive` TINYINT NOT NULL DEFAULT 0 COMMENT '是否猫咪专用(犬猫分区防应激): 1-猫咪专用, 0-通用或犬用',
  `status` VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '当前状态: AVAILABLE-空闲可用, IN_USE-占用服务中, CLEANING-消毒清理中, MAINTENANCE-故障维护',
  `current_order_id` BIGINT DEFAULT NULL COMMENT '当前占用的洗护工单ID',
  `location_zone` VARCHAR(64) DEFAULT '一楼洗护区' COMMENT '所在门店区域',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '设备规格说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_code` (`resource_code`),
  KEY `idx_type_status` (`resource_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店洗护工位、美容台与设备资源表';

-- ============================================================================
-- 六、在线预约管理模块 (2张表)
-- ============================================================================

DROP TABLE IF EXISTS `appointment`;
CREATE TABLE `appointment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '预约单ID',
  `appointment_no` VARCHAR(32) NOT NULL COMMENT '预约单号(如 APT20261029001)',
  `customer_id` BIGINT NOT NULL COMMENT '预约宠主ID',
  `booker_phone` VARCHAR(20) NOT NULL COMMENT '预约人联系电话',
  `appointment_date` DATE NOT NULL COMMENT '预约到店日期',
  `start_time` TIME NOT NULL COMMENT '预约服务开始时间(如 10:00:00)',
  `end_time` TIME NOT NULL COMMENT '预计服务结束时间(如 11:30:00)',
  `buffer_clean_min` INT NOT NULL DEFAULT 15 COMMENT '工位消毒清理缓冲时长(分钟,防时段冲突计算用)',
  `source_type` VARCHAR(20) NOT NULL DEFAULT 'ONLINE' COMMENT '预约来源: ONLINE-小程序/网页自主预约, PHONE-电话预约, WALK_IN-到店直接散客登记',
  `pet_count` INT NOT NULL DEFAULT 1 COMMENT '本次预约宠物数量(支持单次预约多只宠物)',
  `estimated_total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '预约预估总金额(元)',
  `estimated_total_duration` INT NOT NULL DEFAULT 60 COMMENT '预约预估总耗时(分钟)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '预约状态: PENDING-待门店确认, CONFIRMED-已确认待到店, CHECKED_IN-已到店签到接宠, IN_SERVICE-洗护服务中, COMPLETED-服务已完成, CANCELLED-已取消, NO_SHOW-超时爽约',
  `cancel_type` VARCHAR(20) DEFAULT NULL COMMENT '取消发起方: CUSTOMER-宠主主动取消, STORE-门店协调取消, SYSTEM_TIMEOUT-系统超时未到店转爽约',
  `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '改期、取消或爽约原因记录',
  `customer_note` VARCHAR(500) DEFAULT NULL COMMENT '宠主预约备注(如 狗狗最近耳朵有点痒请重点清理)',
  `store_note` VARCHAR(500) DEFAULT NULL COMMENT '门店前台内部备注',
  `checked_in_at` DATETIME DEFAULT NULL COMMENT '实际到店签到时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预约提交时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment_no` (`appointment_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_date_status` (`appointment_date`, `status`),
  KEY `idx_date_time` (`appointment_date`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线预约与到店登记主表';

DROP TABLE IF EXISTS `appointment_item`;
CREATE TABLE `appointment_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '预约明细ID',
  `appointment_id` BIGINT NOT NULL COMMENT '关联预约单ID',
  `pet_id` BIGINT NOT NULL COMMENT '预约洗护的宠物ID',
  `pet_name` VARCHAR(64) NOT NULL COMMENT '宠物名称快照',
  `pet_size_type` VARCHAR(20) NOT NULL COMMENT '宠物预约时体型快照',
  `item_type` VARCHAR(20) NOT NULL DEFAULT 'SINGLE' COMMENT '服务类型: SINGLE-单项服务, PACKAGE-组合套餐',
  `service_item_id` BIGINT DEFAULT NULL COMMENT '关联单项服务ID(item_type=SINGLE时必填)',
  `package_id` BIGINT DEFAULT NULL COMMENT '关联洗护套餐ID(item_type=PACKAGE时必填)',
  `service_name` VARCHAR(100) NOT NULL COMMENT '预约服务或套餐名称快照',
  `preferred_groomer_id` BIGINT DEFAULT NULL COMMENT '宠主指定/偏好洗护师ID(NULL表示到店由系统分配)',
  `assigned_groomer_id` BIGINT DEFAULT NULL COMMENT '门店排班锁定的洗护师ID',
  `assigned_resource_id` BIGINT DEFAULT NULL COMMENT '预分配的洗护工位资源ID',
  `estimated_price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '按宠物体型计算的预计单价(元)',
  `estimated_duration_min` INT NOT NULL DEFAULT 60 COMMENT '按宠物体型计算的预计时长(分钟)',
  `item_status` VARCHAR(20) NOT NULL DEFAULT 'BOOKED' COMMENT '单宠明细状态: BOOKED-已预约, CONVERTED-已转洗护工单, CANCELLED-已取消',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_appointment_id` (`appointment_id`),
  KEY `idx_pet_id` (`pet_id`),
  KEY `idx_assigned_groomer` (`assigned_groomer_id`),
  KEY `idx_assigned_resource` (`assigned_resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约单宠物与服务项目排程明细表(支持一单多宠)';

-- ============================================================================
-- 七、洗护服务过程管理模块 (4张表，核心特色：服务状态机独立于订单支付)
-- ============================================================================

DROP TABLE IF EXISTS `grooming_order`;
CREATE TABLE `grooming_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '洗护工单ID',
  `grooming_no` VARCHAR(32) NOT NULL COMMENT '洗护工单编号(如 GRM20261029001)',
  `appointment_id` BIGINT DEFAULT NULL COMMENT '来源预约单ID(散客直接到店可为NULL)',
  `appointment_item_id` BIGINT DEFAULT NULL COMMENT '来源预约明细ID',
  `customer_id` BIGINT NOT NULL COMMENT '宠主ID',
  `pet_id` BIGINT NOT NULL COMMENT '服务宠物ID',
  `main_groomer_id` BIGINT NOT NULL COMMENT '主责洗护/美容师ID',
  `assistant_id` BIGINT DEFAULT NULL COMMENT '协助洗护助理ID',
  `resource_id` BIGINT NOT NULL COMMENT '占用的洗护工位/美容台资源ID',
  `cage_resource_id` BIGINT DEFAULT NULL COMMENT '暂存或待接宠寄养格资源ID',
  `service_title` VARCHAR(150) NOT NULL COMMENT '本次执行服务摘要标题',
  `service_status` VARCHAR(32) NOT NULL DEFAULT 'WAITING_CHECKIN' COMMENT '服务主状态: WAITING_CHECKIN-待接宠检查, WAITING_GROOM-已接宠待洗护, GROOMING-洗护进行中, WAITING_CONFIRM-待宠主确认加项, WAITING_PICKUP-洗护完成待交宠, COMPLETED-已交宠离店, SUSPENDED-服务暂停, ABNORMAL-异常中止',
  `current_stage` VARCHAR(32) NOT NULL DEFAULT 'WAITING' COMMENT '当前洗护子阶段: WAITING-候洗区等待, CHECK_IN-接宠体检与风险确认, BATHING-香波沐浴与SPA, DRYING-吹水拉毛烘干, TRIMMING-精修造型与局部护理, QUALITY_CHECK-店长品控复检, DONE-全部完成',
  `checkin_weight_kg` DECIMAL(5,2) DEFAULT NULL COMMENT '到店接宠实测体重(kg)',
  `checkin_inspection` JSON DEFAULT NULL COMMENT '接宠外观与健康五官检查JSON(如 {"ears":"正常","skin":"腹部轻微红点","coat":"耳后局部打结","parasites":"未见跳蚤","temperament":"轻微紧张"})',
  `stress_level` VARCHAR(20) NOT NULL DEFAULT 'LOW' COMMENT '术前应激风险评估: LOW-低风险平稳, MEDIUM-中度敏感需安抚, HIGH-高应激需专人监护或洗护袋',
  `risk_agreement_signed` TINYINT NOT NULL DEFAULT 1 COMMENT '洗护风险告知书签署状态(高龄/打结剃毛/应激免责): 1-已签署, 0-未签署',
  `checkin_time` DATETIME DEFAULT NULL COMMENT '接宠入场时间',
  `start_groom_time` DATETIME DEFAULT NULL COMMENT '实际开始洗护时间',
  `estimated_finish_time` DATETIME DEFAULT NULL COMMENT '预计洗护完成时间(动态随加项更新)',
  `actual_finish_time` DATETIME DEFAULT NULL COMMENT '实际洗护完成时间',
  `pickup_code` VARCHAR(10) DEFAULT NULL COMMENT '6位数字接宠核验码(防冒领)',
  `pickup_person_name` VARCHAR(64) DEFAULT NULL COMMENT '实际接宠交接人姓名',
  `pickup_person_relation` VARCHAR(32) DEFAULT NULL COMMENT '实际交接人与宠主关系(如 本人、授权配偶、授权朋友)',
  `pickup_time` DATETIME DEFAULT NULL COMMENT '实际接宠离店交接时间',
  `groomer_summary` VARCHAR(500) DEFAULT NULL COMMENT '洗护师完工总结报告(推送给宠主查看)',
  `before_photo` VARCHAR(255) DEFAULT NULL COMMENT '洗护前全身存证照URL',
  `after_photo` VARCHAR(255) DEFAULT NULL COMMENT '洗护后造型成品照URL',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '工单创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_grooming_no` (`grooming_no`),
  KEY `idx_appointment_id` (`appointment_id`),
  KEY `idx_customer_pet` (`customer_id`, `pet_id`),
  KEY `idx_groomer_status` (`main_groomer_id`, `service_status`),
  KEY `idx_service_status_stage` (`service_status`, `current_stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护服务全过程工单主表(核心服务履约状态机)';

DROP TABLE IF EXISTS `grooming_record`;
CREATE TABLE `grooming_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '阶段打卡ID',
  `grooming_order_id` BIGINT NOT NULL COMMENT '关联洗护工单ID',
  `stage_code` VARCHAR(32) NOT NULL COMMENT '工序阶段: CHECK_IN-接宠体检, BATHING-沐浴清洁, DRYING-吹干拉毛, TRIMMING-美容修剪, QUALITY_CHECK-完工质检, PICKUP_HANDOVER-交宠核验',
  `stage_name` VARCHAR(64) NOT NULL COMMENT '阶段中文名称',
  `operator_id` BIGINT NOT NULL COMMENT '操作员工ID',
  `operator_name` VARCHAR(64) NOT NULL COMMENT '操作员工姓名',
  `start_time` DATETIME NOT NULL COMMENT '本阶段开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '本阶段结束时间',
  `duration_min` INT DEFAULT 0 COMMENT '本阶段实际耗时(分钟)',
  `process_note` VARCHAR(500) DEFAULT NULL COMMENT '阶段过程记录(如 已完成两遍低敏燕麦香波清洁，挤出较多肛门腺分泌物，吹水时轻微抗拒已安抚)',
  `photo_urls` JSON DEFAULT NULL COMMENT '过程实拍照片URL数组JSON',
  `pet_emotion_state` VARCHAR(32) DEFAULT 'CALM' COMMENT '宠物当时情绪状态: CALM-安静配合, NERVOUS-略微紧张, RESISTANT-抗拒挣扎, HAPPY-活跃愉悦',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打卡时间',
  PRIMARY KEY (`id`),
  KEY `idx_grooming_order_stage` (`grooming_order_id`, `stage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护工单分阶段节点打卡与过程影像记录表';

DROP TABLE IF EXISTS `grooming_addon_confirm`;
CREATE TABLE `grooming_addon_confirm` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '加项确认单ID',
  `grooming_order_id` BIGINT NOT NULL COMMENT '关联洗护工单ID',
  `customer_id` BIGINT NOT NULL COMMENT '宠主ID',
  `pet_id` BIGINT NOT NULL COMMENT '宠物ID',
  `addon_id` BIGINT DEFAULT NULL COMMENT '关联标准附加项字典ID',
  `addon_name` VARCHAR(100) NOT NULL COMMENT '追加项目名称(如 严重毛发打结人工开结费、真菌药浴升级)',
  `apply_reason` VARCHAR(500) NOT NULL COMMENT '申请加项原因说明(如 接宠细检发现腋下与后腿内侧有4处严重死结，若保留毛发需耗时约25分钟精细开结，请宠主确认)',
  `evidence_photo` VARCHAR(255) DEFAULT NULL COMMENT '现场局部问题实拍凭证照片URL',
  `addon_price` DECIMAL(10,2) NOT NULL DEFAULT 30.00 COMMENT '申请加收金额(元)',
  `extra_duration_min` INT NOT NULL DEFAULT 20 COMMENT '预计延长工时(分钟)',
  `confirm_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '宠主确认状态: PENDING-待宠主确认, APPROVED-宠主已同意加项, REJECTED-宠主拒绝(按原方案或直接剃短处理)',
  `customer_reply` VARCHAR(255) DEFAULT NULL COMMENT '宠主回复意见(如 同意开结，尽量保留毛长)',
  `applied_by` BIGINT NOT NULL COMMENT '发起申请的洗护师ID',
  `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起申请时间',
  `confirmed_at` DATETIME DEFAULT NULL COMMENT '宠主确认或拒绝时间',
  PRIMARY KEY (`id`),
  KEY `idx_grooming_order_id` (`grooming_order_id`),
  KEY `idx_customer_status` (`customer_id`, `confirm_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护服务中临时追加项目与宠主线上确认记录表';

DROP TABLE IF EXISTS `grooming_incident`;
CREATE TABLE `grooming_incident` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '异常记录ID',
  `grooming_order_id` BIGINT NOT NULL COMMENT '关联洗护工单ID',
  `pet_id` BIGINT NOT NULL COMMENT '关联宠物ID',
  `incident_type` VARCHAR(32) NOT NULL COMMENT '异常类型: STRESS_REACTION-强烈应激反应, SKIN_ABRASION-皮肤红肿/刮伤, EQUIPMENT_FAULT-设备故障更换工位, GROOMER_HANDOVER-洗护师中途交接, HEALTH_ISSUE-突发身体不适',
  `severity` VARCHAR(20) NOT NULL DEFAULT 'MINOR' COMMENT '严重程度: MINOR-轻微可控, MODERATE-中度需暂停安抚, CRITICAL-严重需送医或终止服务',
  `occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `description` VARCHAR(500) NOT NULL COMMENT '异常情况详细经过描述',
  `emergency_action` VARCHAR(500) NOT NULL COMMENT '现场应急处置措施(如 立即关闭高速吹水机改用静音烘干箱，安抚10分钟后呼吸恢复平稳)',
  `handover_from_groomer` BIGINT DEFAULT NULL COMMENT '原洗护师ID(交接场景)',
  `handover_to_groomer` BIGINT DEFAULT NULL COMMENT '接手洗护师ID(交接场景)',
  `customer_notified` TINYINT NOT NULL DEFAULT 1 COMMENT '是否已主动通知宠主: 1-已通知, 0-未通知',
  `notified_at` DATETIME DEFAULT NULL COMMENT '通知宠主时间',
  `compensation_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '门店关怀减免或赔付金额(元)',
  `manager_review_status` VARCHAR(20) NOT NULL DEFAULT 'REVIEWED' COMMENT '店长复核状态: PENDING-待复核, REVIEWED-已复核结案',
  `manager_comment` VARCHAR(255) DEFAULT NULL COMMENT '店长复核批注与改进措施',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_grooming_order_id` (`grooming_order_id`),
  KEY `idx_incident_type_severity` (`incident_type`, `severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护服务异常、应激处置与中途交接记录表';

-- ============================================================================
-- 八、订单与财务结算模块 (4张表，支持现金/扫码/储值/次卡/模拟组合支付)
-- ============================================================================

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '结算订单ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '财务结算单号(如 ORD20261029001)',
  `order_type` VARCHAR(24) NOT NULL DEFAULT 'GROOMING' COMMENT '订单类型: GROOMING-洗护服务结算单, RETAIL-门店商品零售单, CARD_RECHARGE-次卡购买/储值充值单',
  `grooming_order_id` BIGINT DEFAULT NULL COMMENT '关联洗护工单ID(洗护结算时必填)',
  `appointment_id` BIGINT DEFAULT NULL COMMENT '关联预约单ID',
  `customer_id` BIGINT NOT NULL COMMENT '消费宠主ID',
  `pet_id` BIGINT DEFAULT NULL COMMENT '关联宠物ID',
  `base_service_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '基础洗护/套餐/商品原价总额(元)',
  `approved_addon_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '经宠主确认的追加项目总额(元)',
  `retail_product_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '顺带购买零售商品总额(元)',
  `member_discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '会员等级折扣减免金额(元)',
  `coupon_discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣金额(元)',
  `service_card_deduct_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '次卡划次抵扣对应金额(元)',
  `points_deduct_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '积分抵现金额(元)',
  `manual_adjust_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '店长手工抹零或关怀减免金额(元)',
  `payable_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '最终应收/待付金额(元,扣除次卡与各项优惠后需付金额)',
  `paid_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实际已支付金额(元,含储值扣款+现金扫码+模拟支付)',
  `refunded_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计已退款金额(元)',
  `pay_status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态: UNPAID-未支付, PARTIAL-部分支付(组合支付进行中), PAID-已结清, REFUNDING-退款处理中, REFUNDED-已全额退款',
  `customer_coupon_id` BIGINT DEFAULT NULL COMMENT '使用的宠主优惠券ID',
  `points_used` INT NOT NULL DEFAULT 0 COMMENT '本次消耗积分数',
  `points_earned` INT NOT NULL DEFAULT 0 COMMENT '本次结算赠送积分数',
  `cashier_id` BIGINT DEFAULT NULL COMMENT '收银确认员工ID',
  `settled_at` DATETIME DEFAULT NULL COMMENT '订单结清时间',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '结算备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开单时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_grooming_order_id` (`grooming_order_id`),
  KEY `idx_customer_pay_status` (`customer_id`, `pay_status`),
  KEY `idx_settled_at` (`settled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店综合财务结算订单主表';

DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单明细ID',
  `order_id` BIGINT NOT NULL COMMENT '所属结算订单ID',
  `item_source` VARCHAR(24) NOT NULL DEFAULT 'BASE_SERVICE' COMMENT '明细分类: BASE_SERVICE-基础洗护/套餐, ADDON_SERVICE-临时确认加项, RETAIL_PRODUCT-零售商品, CARD_PURCHASE-购卡充值',
  `ref_id` BIGINT DEFAULT NULL COMMENT '关联项目/附加项/商品ID',
  `item_name` VARCHAR(100) NOT NULL COMMENT '收费项目/商品名称',
  `unit_price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '核定单价(元)',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
  `subtotal_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '小计原价金额(元)',
  `discount_share_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '分摊优惠或次卡抵扣金额(元)',
  `net_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实收净额/计提成基数金额(元)',
  `groomer_id` BIGINT DEFAULT NULL COMMENT '绩效归属洗护师ID',
  `commission_rate` DECIMAL(5,2) NOT NULL DEFAULT 15.00 COMMENT '该项目提成比例(%)',
  `commission_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '洗护师本项应得提成金额(元)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_groomer_id` (`groomer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算订单费用明细与洗护师提成核算表';

DROP TABLE IF EXISTS `payment_record`;
CREATE TABLE `payment_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '支付流水ID',
  `payment_no` VARCHAR(32) NOT NULL COMMENT '支付流水号(如 PAY202610290001)',
  `order_id` BIGINT NOT NULL COMMENT '关联结算订单ID',
  `customer_id` BIGINT NOT NULL COMMENT '支付宠主ID',
  `pay_channel` VARCHAR(32) NOT NULL COMMENT '支付方式: CASH_OR_SCAN-到店现金/聚合码收银确认, MEMBER_BALANCE-会员储值余额抵扣, SERVICE_CARD-洗护次卡划次核销, SIMULATED-演示沙箱模拟在线支付',
  `pay_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本笔支付或抵扣金额(元)',
  `service_card_id` BIGINT DEFAULT NULL COMMENT '使用的次卡ID(pay_channel=SERVICE_CARD时记录)',
  `deducted_times` INT NOT NULL DEFAULT 0 COMMENT '本次核销次卡次数(默认0,次卡支付时为1)',
  `external_voucher_no` VARCHAR(64) DEFAULT NULL COMMENT '收银小票号/扫码凭证尾号/模拟交易流水号',
  `pay_status` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '流水状态: PENDING-处理中, SUCCESS-支付成功, FAILED-失败, REVERSED-已冲正退回',
  `operator_id` BIGINT DEFAULT NULL COMMENT '收银确认人ID(客户端自助模拟支付时可为NULL)',
  `paid_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '支付完成时间',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '支付备注(如 组合支付：次卡抵扣基础洗护88元 + 储值余额支付开结加项30元)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_customer_channel` (`customer_id`, `pay_channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单多渠道组合支付流水表';

DROP TABLE IF EXISTS `refund_record`;
CREATE TABLE `refund_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '退款记录ID',
  `refund_no` VARCHAR(32) NOT NULL COMMENT '退款单号(如 REF20261029001)',
  `order_id` BIGINT NOT NULL COMMENT '原结算订单ID',
  `payment_record_id` BIGINT DEFAULT NULL COMMENT '关联原支付流水ID',
  `customer_id` BIGINT NOT NULL COMMENT '宠主ID',
  `refund_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '退还金额(元)',
  `refund_channel` VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL' COMMENT '退回途径: ORIGINAL-原路退回, MEMBER_BALANCE-退回会员储值账户, SERVICE_CARD_RETURN-退还次卡次数, CASH-门店现金退还',
  `returned_card_times` INT NOT NULL DEFAULT 0 COMMENT '联动退还次卡次数',
  `deducted_points` INT NOT NULL DEFAULT 0 COMMENT '联动扣回原赠送积分',
  `refund_reason` VARCHAR(255) NOT NULL COMMENT '退款或反结账冲正原因',
  `status` VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT '状态: PENDING-待店长审批, COMPLETED-已退款完成, REJECTED-已驳回',
  `approved_by` BIGINT DEFAULT NULL COMMENT '审批店长ID',
  `refunded_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '退款完成时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_customer_id` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单退款、次卡退回与反结账冲正记录表';

-- ============================================================================
-- 九、会员储值、洗护次卡与优惠券模块 (6张表)
-- ============================================================================

DROP TABLE IF EXISTS `member_account`;
CREATE TABLE `member_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '储值账户ID',
  `customer_id` BIGINT NOT NULL COMMENT '所属宠主ID(一对一)',
  `balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '当前可用储值余额(元,本金+赠送金合计)',
  `principal_balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '其中充值本金余额(元)',
  `gift_balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '其中充值活动赠送金余额(元)',
  `frozen_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '预约担保冻结金额(元)',
  `total_recharged` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '历史累计充值总金额(元)',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(防并发扣款异常)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态: ACTIVE-正常, FROZEN-冻结',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_id` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠主会员储值钱包账户表';

DROP TABLE IF EXISTS `member_balance_flow`;
CREATE TABLE `member_balance_flow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '储值流水ID',
  `flow_no` VARCHAR(32) NOT NULL COMMENT '流水号(如 BAL20261029001)',
  `account_id` BIGINT NOT NULL COMMENT '储值账户ID',
  `customer_id` BIGINT NOT NULL COMMENT '宠主ID',
  `flow_type` VARCHAR(24) NOT NULL COMMENT '变动类型: RECHARGE-充值入账, GIFT-充值满赠入账, CONSUME-洗护/商品消费扣款, REFUND-退款回充',
  `change_amount` DECIMAL(10,2) NOT NULL COMMENT '变动金额(元,正数为增加,负数为扣减)',
  `before_balance` DECIMAL(10,2) NOT NULL COMMENT '变动前余额(元)',
  `after_balance` DECIMAL(10,2) NOT NULL COMMENT '变动后余额(元)',
  `related_order_no` VARCHAR(32) DEFAULT NULL COMMENT '关联结算单号或充值单号',
  `operator_id` BIGINT DEFAULT NULL COMMENT '经办员工ID',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '流水说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_no` (`flow_no`),
  KEY `idx_customer_created` (`customer_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员储值余额变动流水表';

DROP TABLE IF EXISTS `service_card`;
CREATE TABLE `service_card` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '次卡账户ID',
  `card_no` VARCHAR(32) NOT NULL COMMENT '次卡编号(如 CARD20260001)',
  `customer_id` BIGINT NOT NULL COMMENT '持卡宠主ID',
  `card_name` VARCHAR(100) NOT NULL COMMENT '次卡名称(如 萌宠10次精致畅洗卡、大型犬5次深度洗护季卡)',
  `applicable_species` VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '适用宠物种类: DOG, CAT, ALL',
  `applicable_sizes` VARCHAR(100) NOT NULL DEFAULT 'SMALL,MEDIUM,LARGE' COMMENT '适用体型范围',
  `bound_pet_id` BIGINT DEFAULT NULL COMMENT '指定绑定宠物ID(NULL表示该宠主名下同体型宠物可共享使用)',
  `single_value` DECIMAL(10,2) NOT NULL DEFAULT 88.00 COMMENT '单次核销折算价值(元)',
  `purchase_amount` DECIMAL(10,2) NOT NULL DEFAULT 680.00 COMMENT '购卡实付总金额(元)',
  `total_times` INT NOT NULL DEFAULT 10 COMMENT '购卡初始总次数(含赠送次数)',
  `used_times` INT NOT NULL DEFAULT 0 COMMENT '累计已核销使用次数',
  `remaining_times` INT NOT NULL DEFAULT 10 COMMENT '当前剩余可用次数',
  `valid_from` DATE NOT NULL COMMENT '生效开始日期',
  `valid_until` DATE NOT NULL COMMENT '有效期截止日期',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(防止高并发下重复扣减次卡次数)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '次卡状态: ACTIVE-有效可用, EXHAUSTED-次数已用完, EXPIRED-已过期, REFUNDED-已退卡',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开卡时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`),
  KEY `idx_customer_status` (`customer_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠主洗护次卡账户表(支持乐观锁防重复扣减)';

DROP TABLE IF EXISTS `service_card_usage`;
CREATE TABLE `service_card_usage` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '次卡核销流水ID',
  `usage_no` VARCHAR(32) NOT NULL COMMENT '核销流水号(如 SCU20261029001)',
  `card_id` BIGINT NOT NULL COMMENT '关联次卡ID',
  `customer_id` BIGINT NOT NULL COMMENT '宠主ID',
  `pet_id` BIGINT NOT NULL COMMENT '本次享受洗护的宠物ID',
  `grooming_order_id` BIGINT DEFAULT NULL COMMENT '关联洗护工单ID',
  `order_id` BIGINT DEFAULT NULL COMMENT '关联财务结算订单ID',
  `change_type` VARCHAR(20) NOT NULL DEFAULT 'CONSUME' COMMENT '变动类型: CONSUME-到店洗护核销扣次, RETURN-订单冲正/取消退回次数',
  `change_times` INT NOT NULL DEFAULT -1 COMMENT '变动次数(-1表示核销1次, +1表示退回1次)',
  `before_times` INT NOT NULL DEFAULT 0 COMMENT '变动前剩余次数',
  `after_times` INT NOT NULL DEFAULT 0 COMMENT '变动后剩余次数',
  `deducted_value` DECIMAL(10,2) NOT NULL DEFAULT 88.00 COMMENT '本次核销抵扣的工单金额(元)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'USED' COMMENT '状态: USED-已正常核销, RETURNED-已退还冲正',
  `operator_id` BIGINT DEFAULT NULL COMMENT '核销收银员/洗护师ID',
  `verify_code` VARCHAR(16) DEFAULT NULL COMMENT '宠主出示的核销动态码或签字确认标识',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '核销说明(如 糯米精致洗护核销1次，剩余6/10次)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_usage_no` (`usage_no`),
  KEY `idx_card_id` (`card_id`),
  KEY `idx_grooming_order_id` (`grooming_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护次卡划次核销与退还流水表';

DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '优惠券模板ID',
  `coupon_code` VARCHAR(32) NOT NULL COMMENT '券模板编码(如 CPN_NEW_30)',
  `coupon_name` VARCHAR(100) NOT NULL COMMENT '优惠券名称(如 新客首次洗护立减20元券、满150减30元精修美容券、爱宠生日月5折SPA券)',
  `coupon_type` VARCHAR(20) NOT NULL DEFAULT 'FULL_REDUCE' COMMENT '券类型: FULL_REDUCE-满减券, DISCOUNT-折扣券, CASH-无门槛立减券',
  `min_spend_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用最低消费门槛(元)',
  `discount_value` DECIMAL(10,2) NOT NULL DEFAULT 20.00 COMMENT '立减金额(元)或折扣率(如0.85)',
  `total_quota` INT NOT NULL DEFAULT 500 COMMENT '发行总张数',
  `claimed_count` INT NOT NULL DEFAULT 0 COMMENT '已领取张数',
  `valid_days` INT NOT NULL DEFAULT 30 COMMENT '领取后有效天数',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-生效中, 0-已停发',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_code` (`coupon_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店营销优惠券模板表';

DROP TABLE IF EXISTS `customer_coupon`;
CREATE TABLE `customer_coupon` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '宠主领券ID',
  `customer_id` BIGINT NOT NULL COMMENT '所属宠主ID',
  `coupon_id` BIGINT NOT NULL COMMENT '关联优惠券模板ID',
  `coupon_name` VARCHAR(100) NOT NULL COMMENT '优惠券名称快照',
  `discount_amount` DECIMAL(10,2) NOT NULL COMMENT '可抵扣面值(元)',
  `min_spend_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛(元)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'UNUSED' COMMENT '状态: UNUSED-未使用, USED-已使用, EXPIRED-已过期',
  `used_order_id` BIGINT DEFAULT NULL COMMENT '核销使用的结算订单ID',
  `used_at` DATETIME DEFAULT NULL COMMENT '使用时间',
  `expire_at` DATETIME NOT NULL COMMENT '过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领券时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_status` (`customer_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠主账户优惠券持有与核销表';

-- ============================================================================
-- 十、商品与洗护耗材库存模块 (2张表)
-- ============================================================================

DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品/耗材ID',
  `sku_code` VARCHAR(32) NOT NULL COMMENT '商品/耗材编码(如 SKU_SHAMPOO_01)',
  `product_name` VARCHAR(100) NOT NULL COMMENT '商品或耗材名称',
  `category` VARCHAR(32) NOT NULL DEFAULT 'CLEANING' COMMENT '分类: CLEANING-洗护香波耗材, CARE-医疗驱虫护理, FOOD-主粮零食, TOY_SUPPLY-玩具日用百货',
  `product_type` VARCHAR(20) NOT NULL DEFAULT 'CONSUMABLE' COMMENT '属性类型: CONSUMABLE-门店内部洗护耗材, RETAIL-对外零售商品, BOTH-既可零售也可内部领用',
  `spec` VARCHAR(64) NOT NULL DEFAULT '500ml/瓶' COMMENT '规格型号(如 3.8L/桶、500ml/瓶、3支/盒)',
  `unit` VARCHAR(16) NOT NULL DEFAULT '瓶' COMMENT '计量单位',
  `retail_price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '门店零售单价(元)',
  `cost_price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '采购成本均价(元)',
  `stock_qty` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '当前库存数量(支持小数按升/次扣减)',
  `warning_qty` DECIMAL(10,2) NOT NULL DEFAULT 5.00 COMMENT '最低安全库存预警阈值(低于等于此值触发补货预警)',
  `expiry_date` DATE DEFAULT NULL COMMENT '批次保质期截止日期',
  `supplier_name` VARCHAR(100) DEFAULT NULL COMMENT '供应商名称',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常启用, 0-下架停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_type_stock` (`product_type`, `stock_qty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店零售商品与洗护耗材库存表';

DROP TABLE IF EXISTS `inventory_transaction`;
CREATE TABLE `inventory_transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存流水ID',
  `trans_no` VARCHAR(32) NOT NULL COMMENT '出入库单号(如 INV20261029001)',
  `product_id` BIGINT NOT NULL COMMENT '关联商品/耗材ID',
  `trans_type` VARCHAR(24) NOT NULL COMMENT '异动类型: PURCHASE_IN-采购入库, SALE_OUT-门店零售出库, GROOM_CONSUME-洗护工单耗材领用出库, ADJUST-盘点损益调整',
  `change_qty` DECIMAL(10,2) NOT NULL COMMENT '变动数量(正数为入库,负数为出库消耗)',
  `before_qty` DECIMAL(10,2) NOT NULL COMMENT '变动前库存',
  `after_qty` DECIMAL(10,2) NOT NULL COMMENT '变动后库存',
  `unit_cost` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '当次单位成本(元)',
  `related_grooming_id` BIGINT DEFAULT NULL COMMENT '关联洗护工单ID(GROOM_CONSUME时记录)',
  `related_order_id` BIGINT DEFAULT NULL COMMENT '关联零售结算单ID(SALE_OUT时记录)',
  `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '出入库备注說明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trans_no` (`trans_no`),
  KEY `idx_product_created` (`product_id`, `created_at`),
  KEY `idx_related_grooming` (`related_grooming_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品与洗护耗材出入库及工单消耗流水表';

-- ============================================================================
-- 十一、服务评价、站内通知与操作审计模块 (3张表)
-- ============================================================================

DROP TABLE IF EXISTS `service_review`;
CREATE TABLE `service_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `grooming_order_id` BIGINT NOT NULL COMMENT '关联洗护工单ID',
  `order_id` BIGINT DEFAULT NULL COMMENT '关联结算订单ID',
  `customer_id` BIGINT NOT NULL COMMENT '评价宠主ID',
  `pet_id` BIGINT NOT NULL COMMENT '关联宠物ID',
  `groomer_id` BIGINT NOT NULL COMMENT '被评价主洗护师ID',
  `overall_rating` TINYINT NOT NULL DEFAULT 5 COMMENT '综合星级评分(1~5星)',
  `skill_rating` TINYINT NOT NULL DEFAULT 5 COMMENT '造型与洗护技术评分(1~5星)',
  `attitude_rating` TINYINT NOT NULL DEFAULT 5 COMMENT '服务态度与耐心评分(1~5星)',
  `env_rating` TINYINT NOT NULL DEFAULT 5 COMMENT '门店环境卫生与无异味评分(1~5星)',
  `content` VARCHAR(1000) NOT NULL COMMENT '宠主评价文字内容',
  `review_images` JSON DEFAULT NULL COMMENT '宠主晒图URL数组JSON',
  `store_reply` VARCHAR(500) DEFAULT NULL COMMENT '门店/店长官方回复内容',
  `replied_at` DATETIME DEFAULT NULL COMMENT '回复时间',
  `appeal_status` VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '异常差评申诉状态: NONE-正常无需申诉, APPEALING-洗护师申诉复核中, UPHELD-维持原评, EXEMPTED-不可抗力免于绩效扣分',
  `is_public` TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开展示在门店口碑墙: 1-展示, 0-隐藏',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价提交时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_grooming_order` (`grooming_order_id`),
  KEY `idx_groomer_rating` (`groomer_id`, `overall_rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洗护服务多维星级评价与门店回复表';

DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息通知ID',
  `receiver_user_id` BIGINT NOT NULL COMMENT '接收人系统用户ID(宠主或门店员工)',
  `receiver_role` VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' COMMENT '接收人角色类型: CUSTOMER, GROOMER, ADMIN',
  `notify_type` VARCHAR(32) NOT NULL COMMENT '通知类型: BOOKING_REMIND-预约到店提醒, ADDON_CONFIRM-洗护中加项待确认提醒, PICKUP_READY-洗护完成待接宠通知, VACCINE_REMIND-疫苗驱虫到期提醒, STOCK_WARNING-耗材低库存预警',
  `title` VARCHAR(100) NOT NULL COMMENT '消息标题',
  `content` VARCHAR(500) NOT NULL COMMENT '消息正文内容',
  `related_biz_type` VARCHAR(32) DEFAULT NULL COMMENT '关联业务类型: APPOINTMENT, GROOMING_ORDER, PRODUCT',
  `related_biz_id` BIGINT DEFAULT NULL COMMENT '关联业务主键ID(点击跳转详情用)',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '阅读状态: 0-未读, 1-已读',
  `read_at` DATETIME DEFAULT NULL COMMENT '阅读时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_receiver_read` (`receiver_user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内消息推送与业务提醒通知表';

DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `module_name` VARCHAR(64) NOT NULL COMMENT '业务模块(如 预约调度、洗护工单、次卡核销、财务结算、库存管理)',
  `action_type` VARCHAR(32) NOT NULL COMMENT '操作类型(如 CHECK_IN, STAGE_UPDATE, ADDON_APPLY, CARD_DEDUCT, ORDER_SETTLE)',
  `biz_no` VARCHAR(64) DEFAULT NULL COMMENT '关联业务单号(如 GRM20261029001)',
  `operator_id` BIGINT NOT NULL COMMENT '操作人用户ID',
  `operator_name` VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  `operator_role` VARCHAR(32) NOT NULL COMMENT '操作人角色',
  `ip_address` VARCHAR(64) DEFAULT '127.0.0.1' COMMENT '操作终端IP地址',
  `request_summary` VARCHAR(500) NOT NULL COMMENT '关键业务变更摘要描述',
  `before_snapshot` JSON DEFAULT NULL COMMENT '变更前关键状态快照JSON',
  `after_snapshot` JSON DEFAULT NULL COMMENT '变更后关键状态快照JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_module_biz` (`module_name`, `biz_no`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统关键业务操作审计日志表';

-- ============================================================================
-- 十二、全链路高仿真初始演示数据 (DML)
-- 提示: 所有日期采用基于 CURDATE() / NOW() 的动态相对时间，保证任意日期导入后
--       “今日预约看板”、“今日洗护工单状态机”、“近7日营收趋势”均有最新鲜的数据展示！
-- 默认登录密码统一为: 123456 (BCrypt加密哈希值)
-- ============================================================================

-- 1. 系统角色初始数据
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `status`) VALUES
(1, 'ROLE_ADMIN', '门店店长/超级管理员', '拥有全店运营数据看板、排班调度、工单监控、商品库存、财务冲正与系统设置全部权限', 1),
(2, 'ROLE_CASHIER', '前台接待与收银员', '负责预约确认、到店签到接宠、开单收银、次卡核销、会员充值与交宠核验', 1),
(3, 'ROLE_GROOMER', '洗护师/宠物美容师', '负责接宠五官体检、分阶段洗护打卡、上传前后对比照、发起临时加项申请与异常上报', 1),
(4, 'ROLE_CUSTOMER', '注册宠主会员', '支持在线管理爱宠档案、多宠预约、实时查看洗护进度、在线确认加项、次卡查询与服务评价', 1);

-- 2. 系统用户初始数据 (1名店长 + 1名前台收银 + 4名洗护美容师 + 6名宠主会员)
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `email`, `avatar`, `user_type`, `status`, `last_login_time`) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '林晓晴(店长)', '13800138000', 'manager@pawspa.cn', '/avatars/staff_admin.png', 'ADMIN', 1, NOW()),
(2, 'cashier01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '陈雨桐(前台)', '13800138001', 'cashier@pawspa.cn', '/avatars/staff_cashier.png', 'CASHIER', 1, NOW()),
(3, 'groomer01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '周凯(首席美容师)', '13800138002', 'zhoukai@pawspa.cn', '/avatars/groomer_zhou.png', 'GROOMER', 1, NOW()),
(4, 'groomer02', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张伟(资深洗护师)', '13800138003', 'zhangwei@pawspa.cn', '/avatars/groomer_zhang.png', 'GROOMER', 1, NOW()),
(5, 'groomer03', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '苏婉(猫咪洗护专家)', '13800138004', 'suwan@pawspa.cn', '/avatars/groomer_su.png', 'GROOMER', 1, NOW()),
(6, 'groomer04', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李铭(造型护理师)', '13800138005', 'liming@pawspa.cn', '/avatars/groomer_li.png', 'GROOMER', 1, NOW()),
(7, 'cus_shen', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '沈逸飞', '13910002001', 'shenyifei@qq.com', '/avatars/cus_shen.png', 'CUSTOMER', 1, NOW()),
(8, 'cus_lin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '林诗涵', '13910002002', 'linshihan@qq.com', '/avatars/cus_lin.png', 'CUSTOMER', 1, NOW()),
(9, 'cus_xu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '许星河', '13910002003', 'xuxinghe@163.com', '/avatars/cus_xu.png', 'CUSTOMER', 1, NOW()),
(10, 'cus_zhao', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵明轩', '13910002004', 'zhaomx@qq.com', '/avatars/cus_zhao.png', 'CUSTOMER', 1, NOW()),
(11, 'cus_wang', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王梓轩', '13910002005', 'wangzx@qq.com', '/avatars/cus_wang.png', 'CUSTOMER', 1, NOW()),
(12, 'cus_chen', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '陈思远', '13910002006', 'chensy@qq.com', '/avatars/cus_chen.png', 'CUSTOMER', 1, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- 3. 用户角色关联初始数据
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1), (2, 2), (3, 3), (4, 3), (5, 3), (6, 3),
(7, 4), (8, 4), (9, 4), (10, 4), (11, 4), (12, 4);

-- 4. 宠主会员档案初始数据
INSERT INTO `customer` (`id`, `user_id`, `customer_no`, `name`, `gender`, `phone`, `wechat_id`, `address`, `member_level`, `points`, `total_spent`, `visit_count`, `no_show_count`, `restrict_status`, `restrict_reason`, `remark`) VALUES
(1, 7, 'CUS20260001', '沈逸飞', 'MALE', '13910002001', 'wx_shenyf88', '翠湖天地御苑 6栋1202室', 'GOLD', 1680, 3280.00, 14, 0, 'NORMAL', NULL, '养有金毛“旺财”与柯基“豆包”两只狗狗，常由妻子叶知秋代接宠，偏爱茶树精油香波'),
(2, 8, 'CUS20260002', '林诗涵', 'FEMALE', '13910002002', 'wx_linshihan', '万科翡翠滨江 3栋801室', 'DIAMOND', 2850, 5620.00, 22, 0, 'NORMAL', NULL, '比熊“糯米”的主人，持有10次畅洗卡，对造型圆润度要求较高，指定首席美容师周凯'),
(3, 9, 'CUS20260003', '许星河', 'MALE', '13910002003', 'wx_starxu', '华润橡树湾 11栋504室', 'SILVER', 620, 1180.00, 6, 0, 'NORMAL', NULL, '布偶猫“雪球”主人，猫咪胆小易应激，每次必须安排独立猫屋和静音烘干箱'),
(4, 10, 'CUS20260004', '赵明轩', 'MALE', '13910002004', 'wx_zhaomx', '保利天悦 2栋1903室', 'GOLD', 1120, 2450.00, 9, 0, 'NORMAL', NULL, '萨摩耶“棉花糖”主人，狗狗周末常去草坪疯跑，耳后与腋下易打结'),
(5, 11, 'CUS20260005', '王梓轩', 'MALE', '13910002005', 'wx_wangzx', '绿城玫瑰园 8栋301室', 'NORMAL', 320, 480.00, 3, 0, 'NORMAL', NULL, '猫犬双全家庭，柴犬“赤丸”+英短蓝猫“煤球”'),
(6, 12, 'CUS20260006', '陈思远', 'MALE', '13910002006', 'wx_chensy99', '金地自在城 5栋1101室', 'NORMAL', 50, 128.00, 1, 2, 'RESTRICTED', '近30天内连续2次预约黄金时段未到店且未提前取消，系统自动标记为受限，线上预约需人工审核确认', '多次临时爽约，需电话二次核实');

-- 5. 宠主紧急联系人与授权代接宠联系人数据
INSERT INTO `customer_contact` (`id`, `customer_id`, `contact_name`, `contact_phone`, `relationship`, `is_emergency`, `can_pickup`, `id_card_last4`, `remark`) VALUES
(1, 1, '叶知秋', '13910002991', 'SPOUSE', 1, 1, '4821', '沈逸飞妻子，旺财与豆包的共同女主人，长期授权直接凭接宠码接送宠物'),
(2, 2, '周欣怡', '13910002992', 'FRIEND', 1, 1, '3109', '林诗涵同小区闺蜜，周五下午常帮忙代接比熊“糯米”回家'),
(3, 3, '许母(张阿姨)', '13910002993', 'PARENT', 1, 1, '7752', '许星河母亲，住在同小区，可代接送布偶猫“雪球”'),
(4, 4, '刘思琪', '13910002994', 'SPOUSE', 1, 1, '6018', '赵明轩未婚妻，授权代接萨摩耶“棉花糖”');

-- 6. 宠物核心档案数据 (含金毛“旺财”、比熊“糯米”、布偶猫“雪球”、柯基“豆包”、萨摩耶“棉花糖”等7只萌宠)
INSERT INTO `pet` (`id`, `pet_no`, `primary_customer_id`, `name`, `avatar`, `species`, `breed`, `gender`, `birth_date`, `size_type`, `weight_kg`, `coat_type`, `matting_level`, `temperament_tags`, `grooming_preference`, `neutered`, `chip_no`, `status`) VALUES
(1, 'PET20260001', 1, '旺财', '/pets/golden_wangcai.jpg', 'DOG', '金毛寻回犬', 'MALE', '2023-04-15', 'LARGE', 28.50, 'DOUBLE', 'SLIGHT', '["温顺亲人", "喜欢玩水", "吹肚子会痒乱动"]', '使用茶树控油香波，胸毛和尾毛自然修齐，趾间毛剃干净，耳道重点清洁', 1, 'CHIP3101150001', 'ACTIVE'),
(2, 'PET20260002', 2, '糯米', '/pets/bichon_nuomi.jpg', 'DOG', '比熊犬', 'FEMALE', '2024-02-10', 'SMALL', 5.20, 'CURLY', 'SLIGHT', '["粘人乖巧", "美容台站立配合", "喜欢鸡肉冻干安抚"]', '韩系圆润棉花糖头型，眼周毛剪清爽防泪痕，四肢留蓬松小喇叭腿', 1, 'CHIP3101150002', 'ACTIVE'),
(3, 'PET20260003', 3, '雪球', '/pets/ragdoll_xueqiu.jpg', 'CAT', '海双布偶猫', 'FEMALE', '2024-06-20', 'MEDIUM', 4.80, 'LONG', 'MODERATE', '["胆小慢热", "极怕大风吹水机", "剪前爪需戴伊丽莎白圈"]', '严禁使用高速吹水机直吹头部，必须进猫咪静音烘干舱，围脖毛保留蓬松感，屁股毛适度修短防沾便', 1, 'CHIP3101150003', 'ACTIVE'),
(4, 'PET20260004', 1, '豆包', '/pets/corgi_doubao.jpg', 'DOG', '威尔士柯基', 'MALE', '2024-08-05', 'MEDIUM', 12.60, 'DOUBLE', 'NONE', '["活泼贪吃", "磨前指甲轻微护爪", "换毛季掉毛大户"]', '重点梳除死毛底绒，柯基蜜桃臀修圆润，挤肛门腺动作需轻柔', 1, 'CHIP3101150004', 'ACTIVE'),
(5, 'PET20260005', 4, '棉花糖', '/pets/samoyed_mianhuatang.jpg', 'DOG', '萨摩耶', 'FEMALE', '2023-11-12', 'LARGE', 24.00, 'DOUBLE', 'SEVERE', '["微笑天使", "精力旺盛", "耳后腋下易打死结"]', '白毛增白亮泽香波+深层护毛素，如有打结优先人工开结保留毛量，尽量不剃秃', 0, 'CHIP3101150005', 'ACTIVE'),
(6, 'PET20260006', 5, '赤丸', '/pets/shiba_chiwan.jpg', 'DOG', '日本柴犬', 'MALE', '2023-09-18', 'MEDIUM', 11.20, 'DOUBLE', 'NONE', '["傲娇独立", "洗澡不叫但会绷紧身体", "零食诱惑有效"]', '基础精致洗护+去底绒，不需要修剪造型，指甲磨圆防止抓伤家里地板', 1, 'CHIP3101150006', 'ACTIVE'),
(7, 'PET20260007', 5, '煤球', '/pets/cat_meiqiu.jpg', 'CAT', '英国短毛蓝猫', 'MALE', '2025-05-01', 'SMALL', 4.20, 'SHORT', 'NONE', '["好奇心强", "温顺好抱"]', '猫咪基础除浮毛洗护', 1, NULL, 'SUSPENDED');

-- 7. 宠物与宠主多对多关联及授权表 (涵盖一人多宠、夫妻共管、授权代管)
INSERT INTO `pet_owner_relation` (`id`, `pet_id`, `customer_id`, `role_type`, `relation_label`, `can_book`, `can_pickup`, `use_shared_card`, `transfer_note`) VALUES
(1, 1, 1, 'PRIMARY', '男主人(主账号)', 1, 1, 1, '原始建档宠主'),
(2, 4, 1, 'PRIMARY', '男主人(主账号)', 1, 1, 1, '沈逸飞名下第二只爱犬(一人多宠)'),
(3, 2, 2, 'PRIMARY', '女主人', 1, 1, 1, '原始建档宠主'),
(4, 3, 3, 'PRIMARY', '男主人', 1, 1, 1, '原始建档宠主'),
(5, 5, 4, 'PRIMARY', '男主人', 1, 1, 1, '原始建档宠主'),
(6, 6, 5, 'PRIMARY', '男主人', 1, 1, 1, '王梓轩名下犬只'),
(7, 7, 5, 'PRIMARY', '男主人', 1, 1, 1, '王梓轩名下猫咪(一人多宠)');

-- 8. 宠物健康、疫苗驱虫与洗护禁忌档案数据
INSERT INTO `pet_health_note` (`id`, `pet_id`, `allergens`, `skin_condition`, `health_risks`, `last_vaccine_date`, `next_vaccine_date`, `vaccine_Institution`, `last_deworm_date`, `next_deworm_date`, `suggest_suspend`, `suspend_reason`, `recorded_by`) VALUES
(1, 1, '对含高浓度香精的普通香波敏感', '右后腿外侧曾有季节性湿疹，已痊愈', '体重较大，上下美容台需双人托抱防腰椎扭伤', '2026-03-15', '2027-03-15', '瑞鹏宠物医院(翠湖分院)', DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 0, NULL, '张伟(资深洗护师)'),
(2, 2, '无已知过敏源', '眼角泪腺分泌旺盛，皮肤健康无红疹', '无特殊健康风险', '2026-02-20', '2027-02-20', '芭比堂动物医院', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 0, NULL, '周凯(首席美容师)'),
(3, 3, '鸡肉源零食轻微软便', '耳道偶有少量褐色分泌物，皮肤薄嫩', '典型高敏感布偶猫，强噪音易引发张口呼吸应激，必须全程低噪音操作', '2026-05-10', '2027-05-10', '瑞派宠物医院', DATE_SUB(CURDATE(), INTERVAL 25 DAY), DATE_ADD(CURDATE(), INTERVAL 5 DAY), 0, NULL, '苏婉(猫咪洗护专家)'),
(4, 4, '无', '皮肤健康，换毛期底绒极厚', '后肢髌骨正常，剪前爪时若强行拉扯会缩脚', '2026-04-02', '2027-04-02', '瑞鹏宠物医院(翠湖分院)', DATE_SUB(CURDATE(), INTERVAL 12 DAY), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 0, NULL, '李铭(造型护理师)'),
(5, 5, '无', '毛发极厚，腋下及耳后根部有4处明显毡化毛结，局部皮肤闷热潮红', '无内脏疾病风险，但开结时间不宜超过35分钟以免烦躁', '2026-01-18', '2027-01-18', '安安宠医', DATE_SUB(CURDATE(), INTERVAL 28 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), 0, NULL, '张伟(资深洗护师)'),
(6, 6, '无', '皮肤健康，油脂分泌正常', '无', '2026-06-12', '2027-06-12', '瑞鹏宠物医院', DATE_SUB(CURDATE(), INTERVAL 8 DAY), DATE_ADD(CURDATE(), INTERVAL 22 DAY), 0, NULL, '李铭(造型护理师)'),
(7, 7, '无', '正常', '刚完成年度猫三联加强针接种未满7天，免疫力波动期严禁洗澡以防感冒诱发呼吸道感染', DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 362 DAY), '瑞派宠物医院', DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 25 DAY), 1, '距离最近一次猫三联疫苗接种仅3天，未满7天安全观察期，系统已开启预约风险拦截', '苏婉(猫咪洗护专家)');

-- 9. 宠物历史洗护摘要与前后对比档案数据
INSERT INTO `pet_service_history` (`id`, `pet_id`, `grooming_order_id`, `service_date`, `service_summary`, `groomer_id`, `groomer_name`, `weight_at_service`, `skin_ coat_feedback`, `behavior_feedback`, `before_photo_url`, `after_photo_url`, `next_suggest_date`) VALUES
(1, 1, NULL, DATE_SUB(CURDATE(), INTERVAL 14 DAY), '大型犬精致洗护SPA套餐 + 去底绒护理', 2, '张伟', 28.30, '底绒梳出两大团死毛，茶树精油浴后皮屑明显减少，毛发蓬松度极佳', '洗澡非常配合，吹肚子时依然爱扭动跟洗护师撒娇', '/history/wangcai_before_01.jpg', '/history/wangcai_after_01.jpg', CURDATE()),
(2, 2, 1, CURDATE(), '精致洗护SPA套餐 + 轻微耳后开结', 1, '周凯', 5.20, '泪痕区域做了专门清洁护理，全身毛发吹拉蓬松，圆头造型立体', '全程乖巧站立，配合度满分', '/history/nuomi_before_today.jpg', '/history/nuomi_after_today.jpg', DATE_ADD(CURDATE(), INTERVAL 10 DAY)),
(3, 3, NULL, DATE_SUB(CURDATE(), INTERVAL 21 DAY), '猫咪无应激柔顺洗护套餐', 3, '苏婉', 4.75, '长毛顺滑无静电，下巴黑下巴区域已温和清理干净', '在静音烘干舱内情绪平稳，未出现哈气或张口呼吸', '/history/xueqiu_before_01.jpg', '/history/xueqiu_after_01.jpg', CURDATE()),
(4, 4, 2, CURDATE(), '中型犬精致洗护套餐 + 蜜桃臀精修', 4, '李铭', 12.60, '臀部线条修剪圆润饱满，趾间杂毛清理干净', '磨前爪时用冻干引导后顺利完成', '/history/doubao_before_today.jpg', '/history/doubao_after_today.jpg', DATE_ADD(CURDATE(), INTERVAL 14 DAY));

-- 10. 洗护服务分类初始数据
INSERT INTO `service_category` (`id`, `category_code`, `category_name`, `icon`, `sort_order`, `status`, `description`) VALUES
(1, 'BASIC_BATH', '基础精致洗护', 'BathTub', 1, 1, '包含五官清洁、剪指甲磨甲、剃脚底毛腹底毛、挤肛门腺、两遍进口香波沐浴、护毛素与吹干拉毛全套10道标准工序'),
(2, 'FULL_GROOM', '全身美容造型', 'Scissors', 2, 1, '在精致洗护基础上，由资深美容师提供泰迪装、比熊圆头、柯基蜜桃臀、贵宾日系萌系等全身手剪/机剪造型'),
(3, 'COAT_CARE', '毛发SPA与深层护理', 'Sparkles', 3, 1, '针对掉毛季去底绒、毛发干枯打结、泪痕重、敏感肌等提供碳酸泉SPA、中草药药浴、深层精油发膜护理'),
(4, 'LOCAL_CARE', '局部单项快护', 'HeartHandshake', 4, 1, '免洗澡单项快护：含洁耳、剪磨指甲、剃脚底毛、眼周修剪、局部卫生修剪等，随到随做');

-- 11. 洗护标准服务项目初始数据
INSERT INTO `service_item` (`id`, `category_id`, `item_code`, `item_name`, `applicable_species`, `base_price`, `base_duration_min`, `required_skill_level`, `process_steps`, `cover_image`, `status`, `sort_order`) VALUES
(1, 1, 'SRV_BATH_BASIC', '精致深层净爽洗护', 'ALL', 58.00, 45, 'JUNIOR', '["基础五官清洁","剪指甲磨甲","剃脚底毛腹毛","挤肛门腺","氨基酸温和香波浴","深层蓬松护毛素","吸水毛巾擦拭","大功率低噪吹干拉毛"]', '/services/bath_basic.jpg', 1, 1),
(2, 1, 'SRV_BATH_SPA', '天然碳酸泉深层滋养SPA洗护', 'ALL', 88.00, 60, 'SENIOR', '["全套精致洗护工序","日本微纳米碳酸泉泡澡8分钟","天然植物皮屑净化精油按摩","深层滋养受损毛发","负离子恒温柔顺拉毛"]', '/services/bath_spa.jpg', 1, 2),
(3, 2, 'SRV_GROOM_ROUND', '比熊/博美棉花糖萌系圆头精修', 'DOG', 128.00, 75, 'MASTER', '["全套精致深层洗护","脸部纯手剪圆头定型","耳位精细轮廓修剪","身体线条梳理调和","胡须与眼周精修"]', '/services/groom_round.jpg', 1, 3),
(4, 2, 'SRV_GROOM_CORGI', '柯基蜜桃臀与线条立体精剪', 'DOG', 98.00, 60, 'SENIOR', '["全套精致洗护","柯基专属浑圆蜜桃臀手剪","后腿线条流线修饰","腹底毛卫生修剪","颈部围脖立体拉毛"]', '/services/groom_corgi.jpg', 1, 4),
(5, 2, 'SRV_GROOM_CAT_LOWSTRESS', '长毛猫低应激纯手剪去油护理', 'CAT', 138.00, 70, 'MASTER', '["二楼专属静音猫室","费洛蒙扩香安抚","无应激长毛去油膏预洗","天然低敏猫用香波","静音烘干舱柔风恒温烘干","爪周与卫生毛精修"]', '/services/groom_cat.jpg', 1, 5),
(6, 3, 'SRV_FUR_UNDERCOAT', '换毛季双层毛深层去底绒死毛护理', 'DOG', 78.00, 45, 'SENIOR', '["专业开结排梳深层开荒梳理","高风速吹水机脱底绒死毛","防静电柔顺喷雾导入","茶树精油舒缓按摩"]', '/services/fur_undercoat.jpg', 1, 6),
(7, 4, 'SRV_LOCAL_QUICK', '萌宠日常卫生单项四合一快护', 'ALL', 35.00, 20, 'JUNIOR', '["温和清耳拔耳毛","剪指甲圆润磨甲","剃脚底毛防滑","剃腹部及肛门周边卫生毛"]', '/services/local_quick.jpg', 1, 7);

-- 12. 服务按体型/毛量阶梯定价规则初始数据
INSERT INTO `service_price_rule` (`id`, `service_item_id`, `species`, `size_type`, `min_weight_kg`, `max_weight_kg`, `coat_type`, `final_price`, `extra_duration_min`, `remark`) VALUES
(1, 1, 'DOG', 'SMALL', 0.00, 8.00, 'ALL', 58.00, 0, '小型犬基础洗护(泰迪、比熊、博美等<=8kg)'),
(2, 1, 'DOG', 'MEDIUM', 8.01, 20.00, 'ALL', 78.00, 20, '中型犬基础洗护(柯基、柴犬、法斗等8-20kg)'),
(3, 1, 'DOG', 'LARGE', 20.01, 35.00, 'ALL', 98.00, 45, '大型犬基础洗护(金毛、拉布拉多、萨摩耶等20-35kg)'),
(4, 1, 'DOG', 'GIANT', 35.01, 60.00, 'ALL', 138.00, 75, '巨型犬基础洗护(阿拉斯加、伯恩山、古代牧羊等>35kg)'),
(5, 1, 'CAT', 'SMALL', 0.00, 5.00, 'ALL', 68.00, 10, '小型短毛猫基础洗护(英短、美短等<=5kg)'),
(6, 1, 'CAT', 'MEDIUM', 5.01, 12.00, 'LONG', 88.00, 25, '中大型长毛猫基础洗护(布偶、缅因、金渐层等>5kg)'),
(7, 2, 'DOG', 'LARGE', 20.01, 35.00, 'LONG', 158.00, 50, '大型长毛犬碳酸泉滋养SPA(金毛、萨摩耶特惠)'),
(8, 3, 'DOG', 'SMALL', 0.00, 8.00, 'ALL', 128.00, 30, '小型犬萌系纯手剪圆头标准价');

-- 13. 洗护套餐定义初始数据
INSERT INTO `service_package` (`id`, `package_code`, `package_name`, `applicable_species`, `original_total_price`, `package_price`, `large_pet_surcharge`, `estimated_duration_min`, `description`, `status`) VALUES
(1, 'PKG_DELUXE_SPA', '精致洗护SPA旗舰套餐(含开结+药浴+四合一)', 'ALL', 194.00, 138.00, 40.00, 90, '包含精致洗护、天然碳酸泉药浴滋养、轻度毛结开结与日常卫生四合一，性价比极高', 1),
(2, 'PKG_BEAUTY_TRANS', '萌宠夏日焕新萌系统修造型套餐', 'DOG', 226.00, 168.00, 50.00, 100, '资深A级美容师主理，包含全套精致深层洗护+纯手剪个性化萌系全身造型修剪', 1),
(3, 'PKG_CAT_STRESSLESS', '猫咪静音无应激长毛滋养洗护套餐', 'CAT', 206.00, 158.00, 20.00, 80, '二楼静音猫室独立洗护，费洛蒙安抚+全身去油去打结+负离子静音烘干', 1);

-- 14. 套餐明细关联数据
INSERT INTO `service_package_item` (`id`, `package_id`, `service_item_id`, `quantity`, `item_sort`) VALUES
(1, 1, 1, 1, 1),
(2, 1, 2, 1, 2),
(3, 1, 7, 1, 3),
(4, 2, 1, 1, 1),
(5, 2, 3, 1, 2),
(6, 3, 5, 1, 1),
(7, 3, 7, 1, 2);

-- 15. 标准附加收费项字典数据
INSERT INTO `service_addon` (`id`, `addon_code`, `addon_name`, `charge_type`, `default_price`, `extra_duration_min`, `need_customer_confirm`, `status`, `description`) VALUES
(1, 'ADD_DEMAT_SLIGHT', '轻中度毛发打结人工精细开结费', 'FIXED', 20.00, 15, 1, 1, '适用于耳后、四肢等局部2~3处轻中度毛结，使用开结喷雾人工顺毛保留毛长'),
(2, 'ADD_DEMAT_SEVERE', '严重毡化打结人工开结加收', 'FIXED', 30.00, 25, 1, 1, '适用于全身多处或大面积紧贴皮肤的死结/毛毡，需洗护师耗时25分钟以上精细挑开，必须上传实拍图经宠主线上确认后执行'),
(3, 'ADD_MEDICATED_BATH', '天然中草药/氯己定抗菌药浴升级', 'FIXED', 48.00, 15, 1, 1, '接宠检查发现轻微皮屑、红疹或趾间红肿时建议升级，需浸泡按摩10分钟'),
(4, 'ADD_FLEA_DEWORM', '大宠爱/福来恩体外驱虫滴剂代操作', 'FIXED', 58.00, 5, 1, 1, '含门店正品驱虫滴剂1支及洗护后48小时皮脂恢复指引'),
(5, 'ADD_TEAR_STAIN', '比熊/博美重度泪痕温和净白护理', 'FIXED', 25.00, 10, 1, 1, '使用进口植物泪痕清洁膏软化眼周氧化褐毛并修剪粘连毛发');

-- 16. 员工档案数据
INSERT INTO `employee` (`id`, `user_id`, `emp_no`, `name`, `phone`, `position`, `skill_level`, `specialty_tags`, `accepted_sizes`, `accept_cat`, `commission_type`, `commission_rate`, `work_status`, `hire_date`, `avatar`, `intro`) VALUES
(1, 3, 'EMP001', '周凯', '13800138002', 'SENIOR_GROOMER', 'MASTER', '["CKU认证A级美容师","比熊棉花糖圆头","贵宾日系萌系手剪","赛级修剪"]', 'SMALL,MEDIUM,LARGE', 1, 'PERCENTAGE', 20.00, 'ON_DUTY', '2022-03-01', '/avatars/groomer_zhou.png', '8年宠物美容经验，CKU A级美容师，擅长比熊、贵宾手剪萌系造型。'),
(2, 4, 'EMP002', '张伟', '13800138003', 'GROOMER', 'SENIOR', '["大型犬深度精洗","萨摩耶蓬松拉毛","严重死结无痛开结","金毛去底绒"]', 'SMALL,MEDIUM,LARGE,GIANT', 0, 'PERCENTAGE', 18.00, 'BUSY', '2023-05-15', '/avatars/groomer_zhang.png', '6年大型犬洗护专精，体能充沛、耐心极佳，深受金毛、萨摩耶家长信赖。'),
(3, 5, 'EMP003', '苏婉', '13800138004', 'GROOMER', 'SENIOR', '["CFA猫咪低应激洗护认证","布偶猫长毛去油柔顺","胆小猫安抚","幼宠初洗"]', 'SMALL,MEDIUM', 1, 'PERCENTAGE', 18.00, 'BUSY', '2023-08-10', '/avatars/groomer_su.png', '专注猫咪无应激洗护5年，懂得解读猫咪微表情与肢体语言，全程温柔零强迫。'),
(4, 6, 'EMP004', '李铭', '13800138005', 'GROOMER', 'SENIOR', '["柯基蜜桃臀精修","柴犬深度去底绒","药浴SPA护理","护爪犬脱敏洗护"]', 'SMALL,MEDIUM,LARGE', 1, 'PERCENTAGE', 15.00, 'ON_DUTY', '2024-02-20', '/avatars/groomer_li.png', '4年资深洗护造型师，擅长柯基臀线修剪与双层毛犬换毛季深度去浮毛护理。'),
(5, 2, 'EMP005', '陈雨桐', '13800138001', 'CASHIER', 'JUNIOR', '["前台客户接待","预约排程协调","收银与次卡管理"]', 'SMALL,MEDIUM', 1, 'FIXED', 5.00, 'ON_DUTY', '2024-06-01', '/avatars/staff_cashier.png', '门店金牌前台管家，负责预约时段协调、到店接待与会员专享服务。'),
(6, 1, 'EMP006', '林晓晴', '13800138000', 'STORE_MANAGER', 'MASTER', '["门店运营管理","洗护品控复检","疑难客诉处理"]', 'SMALL,MEDIUM,LARGE,GIANT', 1, 'PERCENTAGE', 10.00, 'ON_DUTY', '2021-10-01', '/avatars/staff_admin.png', '门店主理人兼品控店长，严把每一只出浴萌宠的卫生与造型质量关。');

-- 17. 员工排班初始数据
INSERT INTO `employee_schedule` (`id`, `employee_id`, `schedule_date`, `shift_name`, `start_time`, `end_time`, `break_start`, `break_end`, `max_appointments`, `booked_count`, `status`, `remark`) VALUES
(1, 1, CURDATE(), '全天班', '09:00:00', '19:00:00', '12:30:00', '13:30:00', 6, 3, 'NORMAL', '今日主接比熊、贵宾精修造型及VIP预约'),
(2, 2, CURDATE(), '全天班', '09:00:00', '19:30:00', '13:00:00', '14:00:00', 6, 3, 'NORMAL', '今日主接大型犬金毛、萨摩耶洗护工单'),
(3, 3, CURDATE(), '全天班', '09:30:00', '19:30:00', '12:30:00', '13:30:00', 5, 2, 'NORMAL', '今日驻守2号猫咪独立静音洗护间'),
(4, 4, CURDATE(), '晚班', '10:00:00', '20:00:00', '13:30:00', '14:30:00', 7, 3, 'NORMAL', '今日负责中小型犬洗护及下午到店预约'),
(5, 5, CURDATE(), '全天班', '09:00:00', '19:30:00', '12:00:00', '13:00:00', 20, 0, 'NORMAL', '前台签到与收银当班'),
(6, 1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '全天班', '09:00:00', '19:00:00', '12:30:00', '13:30:00', 6, 2, 'NORMAL', '明日正常排班'),
(7, 2, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '全天班', '09:00:00', '19:30:00', '13:00:00', '14:00:00', 6, 1, 'NORMAL', '明日正常排班'),
(8, 3, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '公休调休', '09:30:00', '19:30:00', '12:30:00', '13:30:00', 0, 0, 'OFF', '周常调休一天'),
(9, 4, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '全天班', '09:00:00', '19:30:00', '12:30:00', '13:30:00', 7, 2, 'NORMAL', '明日正常排班');

-- 18. 门店洗护工位资源数据
INSERT INTO `service_resource` (`id`, `resource_code`, `resource_name`, `resource_type`, `supported_sizes`, `cat_exclusive`, `status`, `current_order_id`, `location_zone`, `remark`) VALUES
(1, 'STATION_BATH_01', '1号大型犬升降冲浪洗护池', 'BATH_TUB', 'MEDIUM,LARGE,GIANT', 0, 'IN_USE', 3, '一楼犬类洗护区', '配备电动升降踏板与恒温增压花洒，当前正为金毛“旺财”沐浴'),
(2, 'STATION_BATH_02', '2号中小型犬恒温泡泡洗护池', 'BATH_TUB', 'SMALL,MEDIUM', 0, 'IN_USE', 4, '一楼犬类洗护区', '配备微纳米碳酸泉，当前萨摩耶“棉花糖”正在等待加项确认'),
(3, 'STATION_CAT_01', '1号猫咪独立静音无应激洗护室', 'CAT_ROOM', 'SMALL,MEDIUM', 1, 'IN_USE', 5, '二楼猫咪专属静音区', '全封闭隔音玻璃+费洛蒙香薰，犬猫物理隔离防应激'),
(4, 'STATION_GROOM_A1', 'A1首席液压升降美容台', 'GROOM_TABLE', 'SMALL,MEDIUM,LARGE', 0, 'AVAILABLE', NULL, '一楼透明精修区', '周凯专用精修美容台，已完成紫外线消毒'),
(5, 'STATION_GROOM_A2', 'A2多功能吹水拉毛美容台', 'GROOM_TABLE', 'SMALL,MEDIUM,LARGE,GIANT', 0, 'CLEANING', NULL, '一楼透明精修区', '刚完成柯基“豆包”修剪，正在进行15分钟消毒缓冲'),
(6, 'STATION_DRY_01', '1号智能负离子静音烘干舱', 'DRYING_BOX', 'SMALL,MEDIUM', 1, 'IN_USE', 5, '二楼猫咪专属静音区', '38.5℃恒温底部送风，布偶猫“雪球”烘干中'),
(7, 'STATION_CAGE_01', 'VIP-01阳光新风待接宠独立格', 'CAGE', 'SMALL,MEDIUM,LARGE', 0, 'IN_USE', 2, '一楼前台等候区', '柯基“豆包”洗护完毕在此等候女主人叶知秋接宠'),
(8, 'STATION_CAGE_02', 'VIP-02阳光新风待接宠独立格', 'CAGE', 'SMALL,MEDIUM', 0, 'AVAILABLE', NULL, '一楼前台等候区', '空闲已消毒');

-- 19. 在线预约记录数据 (覆盖今日与昨日真实业务状态)
INSERT INTO `appointment` (`id`, `appointment_no`, `customer_id`, `booker_phone`, `appointment_date`, `start_time`, `end_time`, `buffer_clean_min`, `source_type`, `pet_count`, `estimated_total_amount`, `estimated_total_duration`, `status`, `cancel_type`, `cancel_reason`, `customer_note`, `store_note`) VALUES
(1, 'APT20261029001', 1, '13812345678', CURDATE(), '09:30:00', '11:00:00', 15, 'ONLINE', 1, 148.00, 90, 'COMPLETED', NULL, NULL, '请帮糯米修剪圆圆的脑袋，耳朵后有一点打结', '周凯主理，已核销畅洗次卡1次并顺利交宠'),
(2, 'APT20261029002', 2, '13987654321', CURDATE(), '10:00:00', '11:30:00', 15, 'ONLINE', 1, 98.00, 90, 'COMPLETED', NULL, NULL, '蜜桃臀修剪圆润一点，剪指甲时小心右前爪', '洗护完成，已通知叶女士接宠'),
(3, 'APT20261029003', 1, '13812345678', CURDATE(), '14:00:00', '15:45:00', 15, 'ONLINE', 1, 158.00, 105, 'IN_SERVICE', NULL, NULL, '旺财最近掉毛厉害，做个碳酸泉SPA去底绒', '张伟主理，洗澡中，状态优良'),
(4, 'APT20261029004', 3, '13700001111', CURDATE(), '14:30:00', '16:00:00', 15, 'ONLINE', 1, 128.00, 90, 'IN_SERVICE', NULL, NULL, '毛量厚，腋下有结，需要仔细打理', '检查发现严重打结，已发起加项¥30申请等待宠主确认'),
(5, 'APT20261029005', 1, '13812345678', CURDATE(), '15:00:00', '16:15:00', 15, 'ONLINE', 1, 138.00, 75, 'IN_SERVICE', NULL, NULL, '雪球胆子小怕大声，请在猫咪静音室洗护', '苏婉主理，烘干中，表现平稳'),
(6, 'APT20261029006', 4, '13611112222', CURDATE(), '16:30:00', '17:30:00', 15, 'WALK_IN', 1, 88.00, 60, 'CONFIRMED', NULL, NULL, '到店直接预约下午档期，洗澡梳毛', '李铭主理，待到店签到接宠');

-- 20. 预约明细项数据
INSERT INTO `appointment_item` (`id`, `appointment_id`, `pet_id`, `service_item_id`, `package_id`, `assigned_employee_id`, `assigned_resource_id`, `pet_weight_at_booking`, `estimated_price`, `estimated_duration_min`, `item_status`) VALUES
(1, 1, 2, 3, 2, 1, 4, 5.20, 148.00, 90, 'COMPLETED'),
(2, 2, 4, 4, NULL, 4, 5, 12.60, 98.00, 90, 'COMPLETED'),
(3, 3, 1, 2, 1, 2, 1, 28.50, 158.00, 105, 'IN_PROGRESS'),
(4, 4, 5, 1, NULL, 2, 2, 24.20, 128.00, 90, 'IN_PROGRESS'),
(5, 5, 3, 5, 3, 3, 3, 4.80, 138.00, 75, 'IN_PROGRESS'),
(6, 6, 6, 1, NULL, 4, 2, 11.00, 88.00, 60, 'PENDING');

-- 21. 洗护服务工单数据 (核心业务状态机，状态独立于订单支付)
INSERT INTO `grooming_order` (`id`, `order_no`, `appointment_id`, `customer_id`, `pet_id`, `primary_groomer_id`, `station_id`, `service_status`, `current_stage`, `progress_percent`, `checkin_time`, `start_time`, `estimated_finish_time`, `actual_finish_time`, `handover_time`, `pickup_code`, `actual_pickup_name`, `actual_pickup_relation`, `checkin_inspector_id`, `ear_eye_status`, `skin_check_result`, `fur_matting_status`, `parasite_check_result`, `emotional_state`, `abnormal_marks`, `risk_acknowledged`, `defer_recommended`, `defer_reason`, `care_summary`) VALUES
(1, 'GROOM20261029001', 1, 1, 2, 1, 4, 'COMPLETED', 'DONE', 100, '09:25:00', '09:35:00', '11:00:00', '10:55:00', '11:10:00', '882103', '林知夏', '本人', 1, '耳道清洁微黄无异味，泪痕中度已专项净白', '健康粉嫩无皮屑湿疹', '轻度耳后毛结已顺理挑开', '无跳蚤螨虫', '乖巧黏人配合度极高', '无伤痕', 1, 0, NULL, '糯米本次圆头修剪圆润饱满，泪痕已做针对性深层清洁护理，两耳耳道清爽干净。'),
(2, 'GROOM20261029002', 2, 2, 4, 4, 7, 'WAITING_PICKUP', 'DONE', 90, '09:55:00', '10:05:00', '11:30:00', '11:20:00', NULL, '551920', NULL, NULL, 4, '耳道正常干净，眼角无分泌物', '健康无红疹，油脂分泌适中', '正常无明显死结，底绒较厚已深层梳理', '无寄生虫', '活泼好动，后爪轻微缩脚已脱敏安抚', '无', 1, 0, NULL, '豆包蜜桃臀线条已精剪圆润饱满，腹底及脚爪防滑毛清理干净，正在1号阳光新风隔等待叶女士接宠。'),
(3, 'GROOM20261029003', 3, 1, 1, 2, 1, 'GROOMING', 'BATHING', 45, '13:50:00', '14:02:00', '15:45:00', NULL, NULL, '339182', NULL, NULL, 2, '耳道微红轻微油脂分泌，已洗耳液深层清洗', '右后腿湿疹已完全结痂恢复，背部皮肤良好', '换毛期底绒较厚已做开荒梳理，无大面积毛结', '无', '温顺热情，喜欢洗头按摩', '无新创伤', 1, 0, NULL, '旺财当前在1号升降大池进行碳酸泉SPA浸泡按摩，去底绒死毛效果显著。'),
(4, 'GROOM20261029004', 4, 3, 5, 2, 2, 'GROOMING', 'WAITING', 25, '14:20:00', '14:30:00', '16:00:00', NULL, NULL, '776201', NULL, NULL, 2, '耳道轻微油垢，眼睛清澈', '腋下及耳后根部发现4处严重毡化打结，局部皮肤微红', '大面积板结毛毡紧贴皮肤', '无', '略微焦躁，洗护师已给冻干安抚', '毛毡紧贴皮肤', 1, 0, NULL, '棉花糖毛发严重毡化，已上传实拍照片发起¥30精细开结加项申请，等待宠主线上确认。'),
(5, 'GROOM20261029005', 5, 1, 3, 3, 6, 'GROOMING', 'DRYING', 70, '14:50:00', '15:00:00', '16:15:00', NULL, NULL, '920415', NULL, NULL, 3, '耳道洁净无螨虫，下巴轻微粉刺已清洁', '薄嫩健康无红肿', '长毛顺滑，腹部轻微小毛结已解开', '无', '安静胆小，全程轻声细语安抚', '无', 1, 0, NULL, '雪球已完成去油长毛沐浴，当前在静音恒温烘干舱内（38.5℃）享受柔风吹干，状态恬静。');

-- 22. 洗护工单阶段打卡流水数据
INSERT INTO `grooming_record` (`id`, `grooming_order_id`, `stage_code`, `stage_name`, `operator_id`, `operator_name`, `start_time`, `end_time`, `duration_min`, `remarks`, `photo_url`) VALUES
(1, 1, 'CHECK_IN', '到店接宠检查与风险告知签署', 1, '周凯', '09:25:00', '09:35:00', 10, '五官四肢皮肤体检正常，林女士当面签字确认', '/records/nuomi_checkin.jpg'),
(2, 1, 'BATHING', '两遍低敏香波沐浴与护毛润泽', 1, '周凯', '09:35:00', '10:05:00', 30, '使用白毛专用珍珠微闪香波洗净，冲水彻底', '/records/nuomi_bath.jpg'),
(3, 1, 'DRYING', '大功率吹水机拉毛与梳理', 1, '周凯', '10:05:00', '10:25:00', 20, '毛发蓬松拉直无卷曲', '/records/nuomi_dry.jpg'),
(4, 1, 'TRIMMING', '比熊经典圆头萌系纯手剪', 1, '周凯', '10:25:00', '10:55:00', 30, '面部圆润萌动，脚底修整圆融', '/records/nuomi_trim.jpg'),
(5, 1, 'DONE', '洗护完成品控质检与交宠通知', 1, '周凯', '10:55:00', '11:10:00', 15, '已发送到店接宠通知，宠主凭取件码882103顺利交接', '/records/nuomi_done.jpg'),
(6, 3, 'CHECK_IN', '到店接宠检查与底绒梳理', 2, '张伟', '13:50:00', '14:02:00', 12, '右后腿湿疹已完全结痂恢复，体重28.5kg体况良好', '/records/wangcai_checkin.jpg'),
(7, 3, 'BATHING', '碳酸泉深层去油与SPA药浴浸泡', 2, '张伟', '14:02:00', NULL, NULL, '当前正在进行10分钟碳酸泉精油浸泡按摩', '/records/wangcai_bath.jpg');

-- 23. 服务临时追加项目申请与宠主确认数据 (体现毕设核心业务亮点：加项必须宠主同意)
INSERT INTO `grooming_addon_confirm` (`id`, `grooming_order_id`, `addon_id`, `addon_name`, `addon_price`, `extra_duration_min`, `request_reason`, `photo_proof_url`, `requested_by_id`, `requested_by_name`, `confirm_status`, `reject_reason`, `requested_at`, `confirmed_at`) VALUES
(1, 1, 1, '轻中度毛发打结人工精细开结费', 20.00, 15, '耳后及腋下有2处轻度毛结，洗护师建议开结顺毛避免剃秃', '/addons/nuomi_knot.jpg', 1, '周凯', 'APPROVED', NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(2, 4, 2, '严重毡化打结人工开结加收', 30.00, 25, '接宠检查发现双侧腋下及大腿内侧毛发严重毡化，强行梳理易拉伤毛囊，需配合专用解结喷雾精细挑开25分钟', '/addons/marshmallow_knot.jpg', 2, '张伟', 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 15 MINUTE), NULL);

-- 24. 洗护服务异常事件记录数据 (体现洗护应激、设备异常应急管理亮点)
INSERT INTO `grooming_incident` (`id`, `grooming_order_id`, `pet_id`, `incident_type`, `severity_level`, `incident_desc`, `immediate_action`, `owner_notified`, `notified_time`, `resolution_status`, `manager_review_note`, `reported_by_id`, `created_at`) VALUES
(1, 5, 3, 'MILD_STRESS', 'LOW', '雪球刚进入吹水间听到吹风机启动瞬间出现瞳孔放大、蹲伏倒退轻度应激迹象', '洗护师苏婉立即关闭高风速吹水机，喷洒猫用费洛蒙喷雾安抚抱抱，并转入二楼静音负离子恒温烘干舱（<40dB运行）进行平缓烘干', 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE), 'RESOLVED', '店长复核处理得当，猫咪情绪已平复，静音舱烘干顺利，无需暂停洗护。', 3, DATE_SUB(NOW(), INTERVAL 35 MINUTE));

-- 25. 结算订单初始数据 (订单状态独立于洗护状态)
INSERT INTO `orders` (`id`, `order_no`, `grooming_order_id`, `appointment_id`, `customer_id`, `pet_id`, `total_base_amount`, `total_addon_amount`, `member_discount_amount`, `coupon_deduct_amount`, `card_deduct_amount`, `points_deduct_amount`, `actual_payable_amount`, `paid_amount`, `pay_status`, `payment_method`, `cashier_id`, `cashier_name`, `settle_time`, `created_at`) VALUES
(1, 'ORD20261029001', 1, 1, 1, 2, 148.00, 20.00, 0.00, 0.00, 168.00, 0.00, 0.00, 0.00, 'PAID', 'SERVICE_CARD', 5, '陈雨桐', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(2, 'ORD20261029002', 2, 2, 2, 4, 98.00, 0.00, 11.76, 10.00, 0.00, 0.00, 76.24, 76.24, 'PAID', 'MEMBER_BALANCE', 5, '陈雨桐', DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(3, 'ORD20261029003', 3, 3, 1, 1, 158.00, 0.00, 18.96, 0.00, 0.00, 0.00, 139.04, 0.00, 'UNPAID', 'COMBINED', 5, '陈雨桐', NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(4, 'ORD20261029004', 4, 4, 3, 5, 128.00, 30.00, 0.00, 0.00, 0.00, 0.00, 158.00, 0.00, 'UNPAID', 'PENDING_CONFIRM', 5, '陈雨桐', NULL, DATE_SUB(NOW(), INTERVAL 20 MINUTE));

-- 26. 订单明细项数据 (包含提成计提金额)
INSERT INTO `order_item` (`id`, `order_id`, `item_type`, `item_id`, `item_name`, `unit_price`, `quantity`, `subtotal_amount`, `commission_rate`, `commission_amount`, `groomer_id`) VALUES
(1, 1, 'PACKAGE', 2, '萌宠夏日焕新萌系统修造型套餐(小型)', 148.00, 1, 148.00, 20.00, 29.60, 1),
(2, 1, 'ADDON', 1, '轻中度毛发打结人工精细开结费', 20.00, 1, 20.00, 20.00, 4.00, 1),
(3, 2, 'SERVICE', 4, '柯基蜜桃臀与线条立体精剪', 98.00, 1, 98.00, 15.00, 14.70, 4),
(4, 3, 'PACKAGE', 1, '精致洗护SPA旗舰套餐(大型犬金毛)', 158.00, 1, 158.00, 18.00, 28.44, 2);

-- 27. 支付与流水记录数据 (体现无微信接口下的到店现金/扫码、次卡核销、余额抵扣组合模式)
INSERT INTO `payment_record` (`id`, `payment_no`, `order_id`, `order_no`, `pay_method`, `pay_amount`, `pay_status`, `channel_trade_no`, `cashier_id`, `voucher_img_url`, `remark`, `paid_at`) VALUES
(1, 'PAY20261029001', 1, 'ORD20261029001', 'SERVICE_CARD', 168.00, 'SUCCESS', 'CARD_TX_20261029_001', 5, NULL, '宠主林知夏使用“萌宠10次畅洗VIP卡”抵扣1次全套精洗美容(核销前剩7次,核销后剩6次)', DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(2, 'PAY20261029002', 2, 'ORD20261029002', 'MEMBER_BALANCE', 76.24, 'SUCCESS', 'BAL_TX_20261029_002', 5, NULL, '金卡会员叶知秋储值余额自动扣款，享受8.8折叠加10元新人券', DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- 28. 会员账户与次卡数据 (支持乐观锁与防重复扣减)
INSERT INTO `member_account` (`id`, `customer_id`, `stored_balance`, `accumulated_recharge`, `frozen_balance`, `version`) VALUES
(1, 1, 560.00, 2000.00, 0.00, 3),
(2, 2, 843.76, 1500.00, 0.00, 2),
(3, 3, 0.00, 0.00, 0.00, 1),
(4, 4, 320.00, 500.00, 0.00, 1);

INSERT INTO `member_balance_flow` (`id`, `customer_id`, `flow_no`, `biz_type`, `amount`, `balance_before`, `balance_after`, `related_order_no`, `operator_name`, `remark`, `created_at`) VALUES
(1, 1, 'FLOW20261001001', 'RECHARGE', 1000.00, 0.00, 1000.00, NULL, '前台陈雨桐', '前台现金扫码充值入账，赠送200积分', '2026-10-01 10:00:00'),
(2, 2, 'FLOW20261029001', 'CONSUMPTION', -76.24, 920.00, 843.76, 'ORD20261029002', '前台陈雨桐', '柯基豆包蜜桃臀精修洗护扣款', DATE_SUB(NOW(), INTERVAL 2 HOUR));

INSERT INTO `service_card` (`id`, `card_no`, `customer_id`, `card_name`, `applicable_services`, `total_times`, `used_times`, `remaining_times`, `card_price`, `status`, `expire_date`, `version`) VALUES
(1, 'CARD20261001001', 1, '萌宠10次全能畅洗VIP次卡', 'ALL_BATH_AND_GROOM', 10, 4, 6, 880.00, 'ACTIVE', '2027-10-01', 4),
(2, 'CARD20261015002', 2, '柯基专属5次造型护理卡', 'CORGI_SPECIAL', 5, 2, 3, 399.00, 'ACTIVE', '2027-04-15', 2);

INSERT INTO `service_card_usage` (`id`, `card_id`, `order_id`, `grooming_order_id`, `customer_id`, `change_times`, `remain_times_after`, `action_type`, `operator_id`, `operator_name`, `remark`, `created_at`) VALUES
(1, 1, 1, 1, 1, -1, 6, 'DEDUCT', 5, '陈雨桐', '比熊糯米圆头洗护造型核销第4次，核销后剩余6次', DATE_SUB(NOW(), INTERVAL 3 HOUR));

-- 29. 优惠券字典与领券记录
INSERT INTO `coupon` (`id`, `coupon_code`, `coupon_name`, `coupon_type`, `threshold_amount`, `discount_amount`, `valid_days`, `total_qty`, `issued_qty`, `status`) VALUES
(1, 'CPN_NEW_10', '新客专享无门槛立减券', 'DIRECT_REDUCE', 0.00, 10.00, 30, 200, 68, 1),
(2, 'CPN_FULL_150_20', '洗护满150立减20元券', 'FULL_REDUCE', 150.00, 20.00, 60, 500, 120, 1),
(3, 'CPN_SPA_30', '天然碳酸泉SPA立减30元专享券', 'SERVICE_SPECIFIC', 100.00, 30.00, 30, 100, 25, 1);

INSERT INTO `customer_coupon` (`id`, `coupon_id`, `customer_id`, `status`, `start_time`, `expire_time`, `used_time`, `order_id`) VALUES
(1, 1, 1, 'UNUSED', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), NULL, NULL),
(2, 2, 1, 'UNUSED', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY), NULL, NULL),
(3, 1, 2, 'USED', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 2 HOUR), 2);

-- 30. 商品与耗材库存数据 (支持洗护工单耗材自动关联扣减与低库存预警)
INSERT INTO `product` (`id`, `sku_code`, `product_name`, `category`, `product_type`, `spec`, `unit`, `retail_price`, `cost_price`, `stock_quantity`, `safety_stock_threshold`, `auto_consume_rule`, `expiry_date`, `supplier_name`, `status`) VALUES
(1, 'SKU_SHAMPOO_OAT', '澳大利亚伊诺有机燕麦温和低敏香波', 'SHAMPOO', 'CONSUMABLE', '5000ml/桶', 'ml', 0.15, 0.06, 850, 1000, '{"SMALL":30,"MEDIUM":45,"LARGE":70,"GIANT":100}', '2027-08-31', '伊诺宠物用品(上海)有限公司', 1),
(2, 'SKU_SHAMPOO_WHITE', '日本顶尖珍珠柔白护色专用香波', 'SHAMPOO', 'CONSUMABLE', '4000ml/桶', 'ml', 0.20, 0.08, 2200, 800, '{"SMALL":25,"MEDIUM":40,"LARGE":60}', '2027-10-15', '名犬名猫供应链', 1),
(3, 'SKU_COND_SILK', '深层水润蚕丝蛋白护毛素', 'CONDITIONER', 'CONSUMABLE', '4000ml/桶', 'ml', 0.18, 0.07, 1800, 600, '{"SMALL":20,"MEDIUM":30,"LARGE":50}', '2027-09-20', '伊诺宠物用品(上海)有限公司', 1),
(4, 'SKU_SPA_TABLET', '日式微纳米弱酸性天然碳酸泉SPA泡腾锭', 'SPA_CARE', 'CONSUMABLE', '100锭/罐', '锭', 8.00, 3.20, 18, 30, '{"PER_SERVICE":1}', '2028-01-01', '日本株式会社PET-SPA', 1),
(5, 'SKU_EAR_CLEANER', '法国维克耳漂温和洗耳液', 'CLEANER', 'CONSUMABLE', '125ml/瓶', 'ml', 0.80, 0.35, 1200, 300, '{"PER_SERVICE":10}', '2027-12-31', '维克动物保健', 1),
(6, 'SKU_SNACK_DUCK', '低温烘焙纯鸭肉梨条去泪痕冻干', 'SNACK', 'RETAIL', '150g/袋', '袋', 38.00, 16.00, 42, 10, NULL, '2027-06-30', '网易严选宠物供应链', 1);

-- 31. 库存出入库流水数据
INSERT INTO `inventory_transaction` (`id`, `tx_no`, `product_id`, `product_name`, `tx_type`, `change_quantity`, `stock_balance_after`, `related_order_no`, `operator_name`, `remark`, `created_at`) VALUES
(1, 'TX20261001001', 1, '澳大利亚伊诺有机燕麦温和低敏香波', 'PURCHASE_IN', 5000, 5000, NULL, '店长林晓晴', '采购入库大桶装耗材', '2026-10-01 10:00:00'),
(2, 'TX20261029001', 2, '日本顶尖珍珠柔白护色专用香波', 'GROOM_CONSUME', -25, 2200, 'GROOM20261029001', '洗护师周凯', '比熊糯米洗护自动扣减消耗', DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(3, 'TX20261029002', 4, '日式微纳米弱酸性天然碳酸泉SPA泡腾锭', 'GROOM_CONSUME', -1, 18, 'GROOM20261029003', '洗护师张伟', '金毛旺财SPA领用1锭(触发库存不足30预警)', DATE_SUB(NOW(), INTERVAL 1 HOUR));

-- 32. 洗护服务评价数据
INSERT INTO `service_review` (`id`, `order_id`, `order_no`, `customer_id`, `pet_id`, `groomer_id`, `rating`, `tech_score`, `environment_score`, `attitude_score`, `content`, `review_photos`, `store_reply`, `appeal_status`, `created_at`) VALUES
(1, 1, 'ORD20261029001', 1, 2, 1, 5, 5, 5, 5, '周凯老师手艺太绝了！糯米的头剪得超级圆像一颗大棉花糖，而且身上香香的一点都不刺鼻，开结也很温柔没有弄疼狗狗，以后认准周老师！', '["/reviews/nuomi_review_01.jpg"]', '非常感谢知夏对周凯老师手艺的认可与赞美！糯米性格超级乖巧，我们洗护组的小伙伴都非常喜欢它，期待下次再来玩哦！', 'NONE', DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- 33. 系统站内消息通知数据 (实时联动宠主端与门店端)
INSERT INTO `notification` (`id`, `target_role`, `customer_id`, `title`, `content`, `notification_type`, `related_biz_no`, `is_read`, `is_urgent`, `created_at`) VALUES
(1, 'CUSTOMER', 1, '洗护完成待接宠提醒', '您的爱宠【糯米】已完成今日精致萌系造型洗护，状态极佳！接宠凭证码为【882103】，请在1小时内凭码到店接宠哦。', 'PICKUP_READY', 'GROOM20261029001', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(2, 'CUSTOMER', 3, '洗护中发现严重打结加项申请', '洗护师张伟在为【棉花糖】洗护过程中发现双侧腋下毛发严重毡化，已上传实拍照片发起【严重打结精细开结加收 ¥30.00 (+25分钟)】加项申请，请您核对确认。', 'ADDON_CONFIRM', 'GROOM20261029004', 0, 1, DATE_SUB(NOW(), INTERVAL 15 MINUTE)),
(3, 'STORE', NULL, '商品耗材低库存预警', '耗材【日式微纳米弱酸性天然碳酸泉SPA泡腾锭】当前剩余库存仅18锭，已低于最低安全库存阈值(30锭)，请店长及时补货采购！', 'INVENTORY_ALERT', 'SKU_SPA_TABLET', 0, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR));

-- 34. 系统关键业务操作日志
INSERT INTO `operation_log` (`id`, `module_name`, `action_type`, `operator_name`, `role_code`, `biz_no`, `details`, `client_ip`, `created_at`) VALUES
(1, '预约管理', 'CREATE', '林知夏', 'ROLE_CUSTOMER', 'APT20261029003', '宠主在线预约金毛旺财下午14:00碳酸泉SPA套餐', '127.0.0.1', DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(2, '工单状态机', 'CHECK_IN', '周凯', 'ROLE_GROOMER', 'GROOM20261029001', '比熊糯米到店接宠检查，确认皮肤正常，开启洗护工单', '192.168.1.101', DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(3, '加项确认', 'REQUEST', '张伟', 'ROLE_GROOMER', 'GROOM20261029004', '萨摩耶棉花糖腋下严重毛毡，发起¥30精细开结申请', '192.168.1.102', DATE_SUB(NOW(), INTERVAL 15 MINUTE)),
(4, '财务收银', 'SETTLE', '陈雨桐', 'ROLE_CASHIER', 'ORD20261029001', '前台核销林知夏萌宠畅洗VIP次卡1次，抵扣¥168.00', '192.168.1.100', DATE_SUB(NOW(), INTERVAL 3 HOUR));

SET FOREIGN_KEY_CHECKS = 1;
-- ============================================================================
-- SQL初始化脚本结束 (共计 39 张业务表结构 + 34 组初始仿真演示数据)
-- ============================================================================
