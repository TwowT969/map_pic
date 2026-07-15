# 地图相册 App 后端（map-album）

游客在景区/指定位置拍照上传，照片按地理位置落在地图上聚合展示，后续可在地图上点选位置查看照片。
本仓库为后端服务，技术方案见 `地图相册App技术方案.md`。

## 技术基线（KSHG 规范）

| 类目 | 选型 |
| --- | --- |
| JDK | 8 |
| 构建 | Maven 多模块（父 POM + map-album-server） |
| Spring Boot | 2.7.18 |
| Spring Cloud | 2021.0.8（基线对齐，暂未启用 Cloud 组件） |
| ORM | MyBatis + MyBatis-Plus 3.5.5 |
| 校验 | Hibernate Validator（JSR-303） |
| 日志 | SLF4J + Logback |
| 测试 | JUnit 5 + Mockito |
| 中间件 | MySQL 8 + Redis 7（Kafka 待引入） |

> 技术方案文档原文写 Spring Boot 3，本工程按 KSHG 后端规范统一为 Spring Boot 2.7 + JDK 8。

## 工程结构

```
map_pic/
├── pom.xml                       父 POM（dependencyManagement 锁版本）
├── docker-compose.yml            本地 MySQL / Redis / Kafka
├── sql/init.sql                  建库脚本（业务表 DDL 待按域补全）
└── map-album-server/             服务实现模块
    └── src/main/java/org/lxp/mapalbum/
        ├── MapAlbumApplication   启动类（@MapperScan 扫描 dal.mysql）
        ├── framework/            框架层（common / mybatis / web）
        ├── controller/admin|app  HTTP 入口（按访问端分包，业务待补）
        ├── service               业务层（业务待补）
        ├── dal/dataobject|mysql|redis  数据访问层
        ├── convert               对象转换
        ├── enums                 枚举与错误码
        ├── api                   对外契约实现
        ├── mq                    Kafka 消费者（待引入）
        └── job                   定时任务
```

## 分层约定（KSHG 规范 §3）

- `controller` 只做参数接收、校验、调用 Service、返回 `CommonResult`；不写业务、不直连 Mapper、不返回 DO。
- `service` 承担业务编排、规则校验、事务控制；多表写入用 `@Transactional(rollbackFor = Exception.class)`。
- `dal/mysql` 只做持久化；常规条件用 `LambdaQueryWrapperX`，复杂查询走 `resources/mapper/*.xml`。
- `convert` 集中 VO/DO/DTO 互转，可用 MapStruct 或框架 `BeanUtils`。
- 业务 DO 继承 `BaseDO`（含审计字段，框架自动填充；逻辑删除 `@TableLogic`）。单租户，不含 `tenant_id`。
- 业务异常用 `throw exception(ErrorCode)`，由 `GlobalExceptionHandler` 兜底。
- URL：方法名与路径对齐，转 kebab-case（如 `treeByArea -> /tree-by-area`）；`context-path=/api`。

## 快速开始

前置：JDK 8、Maven 3.6+、Docker。

```bash
# 1. 启动本地中间件
docker-compose up -d

# 2. 编译
mvn -q -pl map-album-server -am compile

# 3. 运行测试
mvn -q -pl map-album-server test

# 4. 启动（默认 local profile，无中间件亦可启动冒烟）
mvn -pl map-album-server spring-boot:run
# 接口前缀：http://localhost:48080/api/
```

## 当前状态

脚手架阶段：基础框架类与分层包就绪，**业务域代码（user / scenic_spot / photo）尚未实现**。
后续按技术方案开发路线图（MVP → V1 → V2）补全。
