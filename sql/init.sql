-- ============================================================
-- 地图相册 App 数据库初始化脚本
-- MySQL 8 + InnoDB + utf8mb4
-- 审计字段对齐 KSHG BaseDO：creator / create_time / updater / update_time / deleted
-- ============================================================

CREATE DATABASE IF NOT EXISTS `map_album`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `map_album`;

-- ============================================================
-- 1. 用户表
--    主键自增 BIGINT 供内部 FK 关联(短索引)；SSO 标识(sso_user_id + sso_provider)为外部唯一凭证。
--    phone/email 均可选──未绑定则传 NULL，uk_phone/uk_email 允许 NULL 重复(MySQL UNIQUE 对 NULL 不判重)。
--    扩展性：预留 gender 等基础画像字段；后续可加 source(注册渠道)、last_login_time、province/city 等。
-- ============================================================
CREATE TABLE `user` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `sso_user_id`    VARCHAR(64)  NOT NULL COMMENT 'SSO 用户唯一标识',
    `sso_provider`   VARCHAR(30)  NOT NULL DEFAULT '' COMMENT 'SSO 来源(cas/oauth2/wechat/miniapp)',
    `password`       VARCHAR(128) DEFAULT NULL COMMENT '登录密码(SHA-256摘要,app通道必填,dev通道为空)',
    `nickname`       VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar_url`     VARCHAR(500) DEFAULT NULL COMMENT '头像 URL',
    `phone`          VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`          VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `gender`         TINYINT      NOT NULL DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1正常',
    -- 审计字段（BaseDO）
    `creator`        BIGINT       DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        BIGINT       DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sso` (`sso_user_id`, `sso_provider`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 2. 点位表
--    用户在前台地图上选择/管理点位；经审核上线后全量可见。
--    坐标统一 GCJ-02（高德坐标系），lat/lng 为权威来源，geo(POINT) 为冗余列配合 SPATIAL INDEX 加速空间查询。
--    扩展性：category/tags 支持分类与标签体系；photo_count/like_count 冗余减少 JOIN；
--           后续可加 province/city/district 供区域聚合（从高德逆地理编码回填）。
-- ============================================================
CREATE TABLE `spot` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(100) NOT NULL COMMENT '点位名称',
    `description`    VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `category`       VARCHAR(30)  DEFAULT NULL COMMENT '分类(scenic/restaurant/viewpoint/activity/other)',
    `tags`           VARCHAR(500) DEFAULT NULL COMMENT '标签 JSON 数组，如 ["花海","日出","网红"]',
    `lat`            DECIMAL(10,7) NOT NULL COMMENT '纬度(GCJ-02)',
    `lng`            DECIMAL(10,7) NOT NULL COMMENT '经度(GCJ-02)',
    `geo`            POINT       DEFAULT NULL COMMENT '空间点(与lat/lng冗余,模板期可空,后续手动 ST_GeomFromText 填充)',
    `cover_photo_id` BIGINT       DEFAULT NULL COMMENT '封面照片ID(关联 photo 表)',
    `address`        VARCHAR(300) DEFAULT NULL COMMENT '地址描述',
    `province`       VARCHAR(30)  DEFAULT NULL COMMENT '省(逆地理编码回填)',
    `city`           VARCHAR(30)  DEFAULT NULL COMMENT '市(逆地理编码回填)',
    `district`       VARCHAR(30)  DEFAULT NULL COMMENT '区/县(逆地理编码回填)',
    `photo_count`    INT          NOT NULL DEFAULT 0 COMMENT '关联照片数(冗余字段)',
    `like_count`     INT          NOT NULL DEFAULT 0 COMMENT '点赞数(冗余字段)',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审 1上线 2驳回 3下架',
    -- 审计字段（BaseDO）
    `creator`        BIGINT       DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        BIGINT       DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    PRIMARY KEY (`id`),
    SPATIAL INDEX `idx_geo` (`geo`),
    INDEX `idx_status` (`status`),
    INDEX `idx_category` (`category`),
    INDEX `idx_city` (`city`),
    INDEX `idx_creator` (`creator`),
    INDEX `idx_lat_lng` (`lat`, `lng`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点位表(前台选点/管理)';

-- ============================================================
-- 3. 照片表
--    每张照片归属于一个点位(spot_id NOT NULL, 1:N──一个点位可有多张照片；
--    如需严格 1:1 则给 spot_id 加 UNIQUE KEY)。关联 SSO 服务(sso_user_id 冗余字段)。
--    URL/thumb_url 存 OSS 地址，文件不入库。审核状态支持待审/通过/驳回三级。
--    扩展性：EXIF 元数据(width/height/size/format/device/shot_time)；
--            is_cover 标记点位封面；sort_order 控制同点位展示顺序；
--            audit_reason/auditor 支持审核留痕；后续可加 album_id 分组。
-- ============================================================
CREATE TABLE `photo` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `spot_id`        BIGINT       NOT NULL COMMENT '关联点位(1:N)',
    `user_id`        BIGINT       NOT NULL COMMENT '上传用户(关联 user.id)',
    `sso_user_id`    VARCHAR(64)  DEFAULT NULL COMMENT 'SSO用户标识(冗余字段,跨服务查询用)',
    -- 存储
    `url`            VARCHAR(500) NOT NULL COMMENT 'OSS 原图 URL',
    `thumb_url`      VARCHAR(500) DEFAULT NULL COMMENT '缩略图 URL',
    -- 图片元数据(EXIF + OSS 回写)
    `width`          INT          DEFAULT NULL COMMENT '图片宽度(px)',
    `height`         INT          DEFAULT NULL COMMENT '图片高度(px)',
    `size_bytes`     BIGINT       DEFAULT NULL COMMENT '文件大小(byte)',
    `format`         VARCHAR(10)  DEFAULT NULL COMMENT '图片格式(jpg/png/webp/heic)',
    -- 定位
    `lat`            DECIMAL(10,7) NOT NULL COMMENT '拍摄纬度(GCJ-02)',
    `lng`            DECIMAL(10,7) NOT NULL COMMENT '拍摄经度(GCJ-02)',
    `geo`            POINT       DEFAULT NULL COMMENT '空间点(后续建 SPATIAL INDEX 加速地图查询)',
    -- 拍摄信息
    `shot_time`      DATETIME     DEFAULT NULL COMMENT '拍摄时间(从 EXIF 读取)',
    `device`         VARCHAR(100) DEFAULT NULL COMMENT '拍摄设备(EXIF Make + Model)',
    `description`    VARCHAR(500) DEFAULT NULL COMMENT '用户描述/文案',
    -- 审核
    `audit_status`   TINYINT      NOT NULL DEFAULT 0 COMMENT '审核状态 0待审 1通过 2驳回',
    `audit_reason`   VARCHAR(300) DEFAULT NULL COMMENT '驳回原因',
    `audit_time`     DATETIME     DEFAULT NULL COMMENT '审核时间',
    `auditor`        BIGINT       DEFAULT NULL COMMENT '审核人(关联 user.id)',
    -- 展示
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '同点位排序(升序,小的在前)',
    `view_count`     INT          NOT NULL DEFAULT 0 COMMENT '查看次数',
    `like_count`     INT          NOT NULL DEFAULT 0 COMMENT '点赞数(冗余)',
    `is_cover`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否点位封面 0否 1是',
    -- 审计字段（BaseDO）
    `creator`        BIGINT       DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        BIGINT       DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    PRIMARY KEY (`id`),
    INDEX `idx_spot` (`spot_id`),
    INDEX `idx_user` (`user_id`),
    INDEX `idx_audit` (`audit_status`),
    INDEX `idx_sso` (`sso_user_id`),
    INDEX `idx_lat_lng` (`lat`, `lng`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_is_cover` (`spot_id`, `is_cover`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='照片表(1:1关联点位,关联SSO)';

-- ============================================================
-- 维护与扩展备忘
-- ============================================================
-- 1) 照片表如需严格 1:1(一个点位仅一张照片)：ALTER TABLE photo ADD UNIQUE KEY uk_spot (spot_id);
-- 2) 图片审核上线后更新点位 cover_photo_id 与 photo.is_cover 需在 Service 层事务内操作。
-- 3) 点赞/计数等高并发字段后续建议走 Redis 计数 + 定时刷回 MySQL，避免热点行锁。
-- 4) SSO 用户首次登录时由 Service 层负责「查 sso_user_id + sso_provider → 无则 INSERT、有则 UPDATE 昵称/头像」。
-- 5) SPATIAL INDEX 需要 MySQL 8.0+，且表必须为 InnoDB；SRID 4326 对应 WGS84/GCJ-02。
-- 6) docker-compose.yml 中 MySQL 容器名供 spring.datasource.url 引用（jdbc:mysql://mysql:3306/map_album...）。


-- ============================================================
-- App 端日志/埋点（崩溃上报 + 轻量行为埋点）
-- ============================================================
CREATE TABLE `app_log` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT        DEFAULT NULL COMMENT '上报用户(未登录可空)',
    `level`       VARCHAR(16)   NOT NULL DEFAULT 'info' COMMENT '级别 info/error',
    `tag`         VARCHAR(64)   DEFAULT NULL COMMENT '标签 crash/track:xxx',
    `message`     VARCHAR(1000) DEFAULT NULL COMMENT '摘要信息',
    `stack`       TEXT          COMMENT '堆栈',
    `app_version` VARCHAR(32)   DEFAULT NULL COMMENT 'App版本号',
    `device`      VARCHAR(200)  DEFAULT NULL COMMENT '设备描述',
    `creator`     BIGINT        DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     BIGINT        DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_level` (`level`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='App端日志/埋点';
