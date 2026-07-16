# 地图相册 App 技术方案

> 游客在景区/指定位置拍照上传，照片按地理位置落在地图上，后续可在地图上浏览查看。

---

## 一、项目背景与目标

做一个**独立手机 App**，核心能力：

- 在指定位置/风景区定位
- 游客现场拍照并上传
- 照片按经纬度落在地图上（聚合展示）
- 后续任何人可在地图上点选位置查看该地的照片

### 确定的技术方向

| 决策项 | 选择 |
|---|---|
| App 形态 | 独立 App（安卓 + iOS） |
| 地图 SDK | 高德地图 |
| 部署环境 | 云服务器（阿里云） |

---

## 二、技术选型

整体思路：**用 Web 技术栈（HTML/JS）做 App**，复用 Java 后端能力，不引入全新语言。

| 层 | 方案 | 选型理由 |
|---|---|---|
| **手机端** | Uni-app（Vue3 语法） | Vue 语法上手快；DCloud HBuilderX 一键打包安卓/iOS；uni-app 的 `map` 组件默认就是高德，无需额外接 SDK |
| **后台管理端** | Vue3 + 高德 JS API | 景点/照片审核管理 |
| **后端** | Spring Boot 3 + MyBatis-Plus + Spring Security | 复用 Java，生态成熟 |
| **数据库** | MySQL 8（空间索引） + Redis | 业务数据 + 缓存 |
| **消息队列** | Kafka | 图片上传削峰 + 异步处理流水线 |
| **对象存储** | 阿里云 OSS + CDN | 图片不入库，CDN 加速访问 |
| **地图** | 高德：App 端 uni-app map 组件，后台 JS API | 同一套 Key 体系 |
| **部署** | 阿里云 ECS + Docker | 后端容器化，复用 docker-cluster 经验 |
| **进阶（可选）** | Canal + Elasticsearch | 照片全文搜索 / 景区热度分析 |

---

## 三、系统架构

```
┌─────────────────────────────────────────────────────┐
│  手机端 (Uni-app -> App)                             │
│  地图主页 · 拍照上传 · 我的相册 · 景点详情 · 登录     │
└───────────────────┬─────────────────────────────────┘
                    │ HTTPS
┌───────────────────▼─────────────────────────────────┐
│  后端 Spring Boot                                    │
│  ┌─────────┬─────────┬──────────┬────────┬────────┐ │
│  │ 用户认证 │ 地图接口 │ 照片接口 │ 景点接口│后台管理│ │
│  └─────────┴─────────┴──────────┴────────┴────────┘ │
└───┬───────────┬──────────────┬───────────────┬──────┘
    │           │              │               │
    ▼           ▼              ▼               ▼
┌───────┐  ┌────────┐    ┌──────────┐    ┌─────────┐
│ MySQL │  │ Redis  │    │  Kafka   │    │  OSS    │
│业务数据│  │ 缓存   │    │ 异步流水线│    │ 图片存储│
└───────┘  └────────┘    └────┬─────┘    └─────────┘
                              ▼
                ┌──────────────────────────┐
                │ 图片处理消费者             │
                │ ① 缩略图 ② EXIF提取 ③ 审核│
                └────────────┬─────────────┘
                             ▼
                    ┌──────────────────┐
                    │ 回写 MySQL / OSS  │
                    └──────────────────┘
        （可选）Canal -> Elasticsearch 全文搜索
```

---

## 四、项目结构

```
map-album/
├── map-album-app/              # Uni-app 前端(手机端)
│   ├── pages/
│   │   ├── map/                # 地图主页(聚合点看照片)
│   │   ├── upload/             # 拍照上传
│   │   ├── photo-detail/       # 照片详情
│   │   ├── mine/               # 我的相册
│   │   └── login/              # 登录
│   ├── static/
│   ├── utils/                  # 请求封装、定位等
│   ├── manifest.json           # App 打包配置(权限/高德Key)
│   └── pages.json              # 路由配置
│
├── map-album-admin/            # 后台管理(Vue3 + 高德JS API)
│   └── views/  spot/  photo/  audit/
│
├── map-album-server/           # Spring Boot 后端
│   ├── src/main/java/.../mapalbum/
│   │   ├── controller/         # REST 接口
│   │   ├── service/            # 业务逻辑
│   │   ├── mapper/             # MyBatis 数据访问
│   │   ├── entity/             # 实体
│   │   ├── dto/                # 请求/响应对象
│   │   ├── config/             # Kafka/Redis/OSS/Security 配置
│   │   └── consumer/           # Kafka 图片处理消费者
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── mapper/*.xml
│   └── pom.xml
│
├── sql/
│   └── init.sql                # 建库建表脚本
│
├── docker-compose.yml          # 本地一键起 MySQL/Redis/Kafka
└── README.md
```

---

## 五、数据库设计

### 5.1 景点表 `scenic_spot`

```sql
CREATE TABLE scenic_spot (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  name         VARCHAR(100) NOT NULL COMMENT '景点名称',
  description  VARCHAR(500) COMMENT '描述',
  lat          DECIMAL(10,7) NOT NULL COMMENT '纬度',
  lng          DECIMAL(10,7) NOT NULL COMMENT '经度',
  geo          POINT SRID 4326 COMMENT '空间点',
  cover_url    VARCHAR(500) COMMENT '封面图',
  status       TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  SPATIAL INDEX idx_geo (geo),
  INDEX idx_status (status)
) COMMENT='景点/兴趣点';
```

### 5.2 照片表 `photo`

```sql
CREATE TABLE photo (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT NOT NULL COMMENT '上传用户',
  spot_id      BIGINT COMMENT '关联景点(可空,自由落点)',
  url          VARCHAR(500) NOT NULL COMMENT 'OSS原图地址',
  thumb_url    VARCHAR(500) COMMENT '缩略图地址',
  lat          DECIMAL(10,7) NOT NULL COMMENT '纬度',
  lng          DECIMAL(10,7) NOT NULL COMMENT '经度',
  shot_time    DATETIME COMMENT '拍摄时间(从EXIF读)',
  description  VARCHAR(500) COMMENT '描述',
  audit_status TINYINT DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  view_count   INT DEFAULT 0 COMMENT '查看数',
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_spot (spot_id),
  INDEX idx_user (user_id),
  INDEX idx_audit (audit_status),
  INDEX idx_geo (lat, lng)
) COMMENT='用户上传照片';
```

### 5.3 用户表 `user`

```sql
CREATE TABLE `user` (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  nickname     VARCHAR(50),
  avatar_url   VARCHAR(500),
  phone        VARCHAR(20) UNIQUE,
  password     VARCHAR(100) COMMENT '加密后',
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT='用户';
```

### 5.4 旅程表 `trip`（V2 新增）

> 一次出游通常包含多个点位、多张照片。通过 `shot_time` 自动聚类，生成"旅程"，
> 用户浏览照片时可按时间线连续翻页，而非逐点位展开。

```sql
CREATE TABLE trip (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT NOT NULL COMMENT '用户',
  name         VARCHAR(100) COMMENT '旅程名称（可自动生成，如"7月16日苏州行"）',
  start_time   DATETIME NOT NULL COMMENT '旅程起始时间（首张照片）',
  end_time     DATETIME NOT NULL COMMENT '旅程结束时间（末张照片）',
  photo_count  INT DEFAULT 0 COMMENT '照片总数',
  spot_count   INT DEFAULT 0 COMMENT '途经点位数',
  -- 路线摘要（points JSON 数组，存储途经点位坐标序列，用于地图连线展示）
  route_json   JSON COMMENT '点位顺序 [{lat,lng,spotId,time}, ...]',
  -- 封面
  cover_url    VARCHAR(500) COMMENT '最具代表性的照片',
  -- 自动 / 手动
  source       TINYINT DEFAULT 0 COMMENT '0自动聚合 1手动创建',
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user (user_id),
  INDEX idx_time (start_time)
) COMMENT='旅程（照片时间线聚合）';
```

**photo 表新增字段**：
```sql
ALTER TABLE photo ADD COLUMN trip_id BIGINT COMMENT '关联旅程（可空）';
ALTER TABLE photo ADD INDEX idx_trip (trip_id);
ALTER TABLE photo ADD INDEX idx_user_shot (user_id, shot_time);
```

**旅程自动聚合算法**：
- 按 `user_id` + `shot_time` 排序
- 相邻两张照片间隔 > N 小时（默认 6h，可配）→ 切分为两个旅程
- 同一旅程内照片按 `shot_time` 升序排列，形成"轨迹"
- 每张新照片上传时，检查是否可归入最近的进行中旅程（间隔 < 6h），是则追加，否则开启新旅程
- 已聚合的旅程支持手动合并/拆分

---

## 六、核心接口设计（REST API）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/register` | 注册 |
| GET | `/api/spots` | 景点列表（支持矩形范围查询） |
| GET | `/api/spots/{id}` | 景点详情 |
| GET | `/api/photos/map` | **地图查询**：按经纬度范围返回照片聚合点 |
| GET | `/api/photos/spot/{spotId}` | 某景点的照片列表（分页） |
| POST | `/api/photos/upload-token` | 获取 OSS 直传/后端上传凭证 |
| POST | `/api/photos` | 保存照片元数据（url/经纬度/描述） |
| GET | `/api/photos/mine` | 我的相册（分页） |
| GET | `/api/photos/{id}` | 照片详情（view_count+1） |

### 地图查询接口示例

```
GET /api/photos/map?minLat=30.1&maxLat=30.3&minLng=120.1&maxLng=120.3&zoom=12

响应(聚合点):
{
  "code": 0,
  "data": [
    { "lat": 30.20, "lng": 120.20, "count": 15, "thumb": "https://..." },
    { "lat": 30.21, "lng": 120.22, "count": 3,  "thumb": "https://..." }
  ]
}
```

> 缩放级别低（zoom 小）时返回聚合点；级别高时返回单张照片 Marker。后端按 zoom 决定聚合粒度。

### 旅程相关接口（V2）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/trips` | 用户的旅程列表（按时间倒序） |
| GET | `/api/trips/{id}` | 旅程详情（含照片列表、路线坐标序列） |
| GET | `/api/trips/{id}/photos` | 旅程内所有照片（按 shot_time 升序，用于连续翻页预览） |
| GET | `/api/photos/timeline` | 用户全部照片时间线（`?userId=&date=` 按天过滤，`?tripId=` 按旅程过滤），统一按 shot_time 排序 |
| POST | `/api/trips/{id}/merge` | 合并两个旅程 |
| POST | `/api/trips/{id}/split` | 在指定位置拆分旅程 |

**时间线查询示例**（光箱连续翻页的数据源）：
```
GET /api/photos/timeline?userId=2&tripId=1
→ 按 shot_time 升序返回照片列表 [{id, url, spotId, spotName, shotTime, lat, lng}, ...]
→ 前端光箱按此列表 prev/next 翻页
```

**旅程路线示例**（地图连线展示）：
```
GET /api/trips/{id}
→ { name, startTime, endTime,
    route: [{lat, lng, spotId, spotName, time, photoCount}, ...],
    photos: [...]
  }
→ 前端在 map 上用 AMap.Polyline 依次连接 route 中坐标，
   标记出"上午 → 下午"的游览轨迹
```

---

## 七、关键技术点与实现思路

### 7.1 拍照 + 定位（前端）
- `uni.chooseImage` 调相机，`uni.getLocation(type='gcj02')` 取定位（高德用 GCJ-02 坐标系，务必统一，否则点位偏移）。
- 权限申请：相机、定位、存储，在 `manifest.json` 配置。
- 景区弱网：先存本地（`uni.setStorage` 或 SQLite），有网后断点续传。

### 7.2 EXIF 读取（后端）
- 手机定位可能不准（室内/延迟），照片本身带拍摄时间和 GPS。
- 后端用 `metadata-extractor` 库解析 EXIF，提取 `DateTimeOriginal` 和 GPS，比手机实时定位更可靠。
- 注意 EXIF 坐标是 WGS-84，需转 GCJ-02 再入库。

### 7.3 地图聚合展示
- 照片多时不能全画 Marker，用高德聚合点（Cluster）。
- uni-app `map` 组件支持 `markers`，配合后端按范围 + zoom 返回聚合数据。
- 缩放/拖动时重新请求当前视口数据。

### 7.4 图片上传方式
- **方式 A（推荐）：OSS 直传**--前端拿后端颁发的 STS Token 直传 OSS，后端只存元数据，省带宽。
- **方式 B：后端转存**--前端传后端，后端存 OSS，简单但吃服务器带宽。
- 上传成功后发 Kafka 消息触发异步处理。

### 7.5 内容审核
- 游客上传必须审核，防止违规图片。
- 走 Kafka 异步：照片先存 `audit_status=0`（待审），调用阿里云内容安全 API，结果回写。
- 未通过的照片地图上不展示。

### 7.6 防盗刷
- 单用户上传频率限制：Redis + Lua 令牌桶（如每分钟最多 10 张）。
- 接口鉴权：Spring Security + JWT。

### 7.7 照片旅程（Trip）与连续预览（V2 新增）

#### 7.7.1 旅程自动聚合（后端）

**触发时机**：
- 每次照片上传成功后，异步检查是否归入旅程（不阻塞上传响应）
- 定时任务（每小时）兜底：扫描无 `trip_id` 的照片，尝试聚合

**聚合逻辑**：
```
1. 按 user_id + shot_time ASC 排序
2. 遍历照片：
   a. 若当前无进行中旅程 → 新建旅程，首张照片为起点
   b. 若与前一张间隔 < 6h → 归入当前旅程
   c. 若间隔 >= 6h → 闭合当前旅程，开启新旅程
3. 更新旅程 route_json（追加点位坐标序列）
4. 更新旅程 photo_count / spot_count
```

**降级方案（MVP 可先不做 trip 表，纯前端实现）**：
- 后端只提供 `/api/photos/timeline?userId=2`，按 shot_time 排序返回所有照片
- 前端在光箱中按此顺序翻页
- 前端按日期分组展示（`2026-07-16` / `2026-07-15` …）
- 不涉及 trip 表、不画路线连线

#### 7.7.2 连续图片预览（前端光箱）

**现状**：光箱只显示单张图片 + 关闭按钮。浏览同一地点的多张照片需要反复打开/关闭。

**目标**：光箱支持 **上一张 / 下一张** 按钮，在「全部照片时间线」或「当前旅程照片」列表中连续翻页。

**实现方案**（组件化）：

```
PhotoLightbox.vue（改造）
  ├── props: photos: Photo[]     ← 照片列表（当前上下文）
  ├── props: index: number       ← 当前显示的照片索引
  ├── 左侧箭头 ← @click → index-- → emit('prev')
  ├── 右侧箭头 → @click → index++ → emit('next')
  ├── 底部计数 "3 / 15"
  ├── 键盘事件 ← → 左右箭头翻页
  └── 手势滑动（移动端）
```

**数据来源**：
- **点位面板内**：`photos` = 当前点位照片（现有行为），翻页范围仅此点位
- **全局时间线**：`photos` = `/api/photos/timeline` 返回的全部有序照片，翻页跨点位
- **旅程内**：`photos` = `/api/trips/{id}/photos`，按游览顺序翻页

**入口**：
1. 点位面板 PhotoGrid 点击任意照片 → 光箱打开，`photos` = 当前点位照片列表（现有行为）
2. 新增"时间线"按钮（地图角落）→ 按日期分组时间线面板 → 点击照片 → 光箱打开，`photos` = 当日全部照片
3. 旅程详情页 → 点击照片 → 光箱打开，`photos` = 旅程全部照片

#### 7.7.3 路线可视化（进阶）

用户某一天/一次出游的"游览轨迹"在地图上连线展示：

```
1. 获取旅程 route_json [{lat, lng, spotId, time}, ...]
2. 后端已排序（按 shot_time ASC）
3. 前端用 AMap.Polyline 依次连接坐标：
   const polyline = new AMap.Polyline({
     path: route.map(p => [p.lng, p.lat]),
     strokeColor: '#4a90d9',
     strokeWeight: 4,
     strokeStyle: 'dashed',    // 虚线，区分于道路
     showDir: true,            // 显示方向箭头，看出移动方向
   })
   map.add(polyline)
4. 每个节点叠加 Marker（照片缩略图），可点击查看该点位的所有照片
5. 点击路线 → 弹出该段的时间和移动距离
```

#### 7.7.4 时间线面板 UI

```
┌─────────────────────────────┐
│  📅 2026-07-16  苏州         │  ← 日期分组 Header
│  ┌──┐ ┌──┐ ┌──┐ ┌──┐       │
│  │  │ │  │ │  │ │  │       │  ← 照片缩略图网格
│  └──┘ └──┘ └──┘ └──┘       │
│  09:30  10:15  14:00  16:30  │  ← 拍摄时间
│  🗺 查看路线                 │  ← 跳转到地图路线
├─────────────────────────────┤
│  📅 2026-07-15  杭州         │
│  ┌──┐ ┌──┐                  │
│  │  │ │  │                  │
│  └──┘ └──┘                  │
│  ...                        │
└─────────────────────────────┘
```

---

## 八、进阶架构（Kafka / Canal / Redis）

### 8.1 Kafka 图片处理流水线
照片元数据落库后，发消息到 `photo.upload` topic，三个消费者并行消费（互不阻塞）：

```
photo.upload topic
   ├── 消费者①：生成缩略图（Thumbnailator）-> 回写 thumb_url
   ├── 消费者②：提取 EXIF -> 回写 shot_time / 校准经纬度
   └── 消费者③：内容审核 -> 回写 audit_status
```

节假日高并发上传时，Kafka 削峰，避免同步处理拖垮接口。

### 8.2 Canal + Elasticsearch（可选，V2）
- Canal 监听 MySQL binlog，同步 `photo` 表到 ES。
- ES 支持"按描述/景点名全文搜索照片"、"按时间/热度排序"，比 MySQL LIKE 强很多。
- 也可同步到数据仓库做景区热度分析。

### 8.3 Redis 缓存策略
- 热门景点照片列表缓存（`photo:spot:{id}`），TTL 5 分钟。
- 地图聚合点缓存（按视口网格 key），减轻 DB 空间查询压力。
- 用户会话 / JWT 黑名单。

---

## 九、开发路线图

### MVP（2-3 周）-- 打通主链路 ✅ 已完成

> **当前实现**：Vue3 + Vite Web 前端（非 Uni-app App），Spring Boot 2.7.18 后端，本地开发模式。
> 暂未接入 SSO，使用硬编码 `userId=2` (admin) 作为默认用户。照片存储为本地文件系统。

- [x] 建库建表（`user` + `spot` + `photo`）-- `sql/init.sql`
- [x] 后端：点位 CRUD（`/api/spots`）+ 照片 CRUD（`/api/photos`）+ IP 定位（`/api/location/ip`）+ 搜索提示（高德 inputtips）+ AMap 配置下发
- [x] 前端：地图主页（高德 JS API v2.0 + AMapLoader）+ MarkerCluster 聚合标记 + 自定义 Marker（照片缩略图）
- [x] 前端：搜索框（毛玻璃透明）+ 点位创建/编辑/删除面板 + 照片上传/删除/网格预览 + 灯箱
- [x] 前端：EXIF GPS 自动解析 → 自动创建点位 + 上传照片
- [x] 前端：地图控件（ToolBar 缩放 + Scale 比例尺 + Geolocation 定位）
- [x] 跑通：拍照上传 → 本地存储 → 落库 → 地图 Marker 展示 → 点击查看/编辑

**MVP 超出部分（原计划 V1 项提前实现）**：
- [x] 地图聚合优化（AMap.MarkerCluster，weight 权重区分有/无照片）
- [x] 点位详情 / 照片管理
- [x] 上传图片 EXIF 位置信息自动解析（`exifr` 前端解析 GPS）
- [ ] SSO 登录 -- **暂缓**，本地测试用 admin(uid=2) 硬编码，待部署前接入 JWT

### V1（2 周）-- 补全基础能力

- [ ] 用户注册登录（SSO/JWT）
- [ ] OSS 对象存储（替代本地文件系统）
- [ ] 缩略图生成
- [ ] 光箱连续翻页预览（上/下一张） ← 从 V2 提前
- [ ] 时间线面板（按日期分组浏览照片） ← 从 V2 提前
- [ ] 后台管理端（点位/照片审核）

### V2（2-3 周）-- 进阶与健壮性

- [ ] Kafka 异步流水线（缩略图/EXIF/审核）
- [ ] Redis 缓存
- [ ] 内容审核接入
- [ ] Canal + ES 全文搜索
- [ ] 防盗刷、弱网断点续传
- [ ] 照片旅程（Trip）自动聚合（`trip` 表 + 后端聚合算法）
- [ ] 路线可视化（Polyline 串联游览轨迹）
- [ ] Uni-app 手机端打包（安卓 + iOS）

---

## 十、部署方案（云服务器）

| 组件 | 部署方式 |
|---|---|
| MySQL / Redis / Kafka | 阿里云 ECS 上 Docker Compose 起（复用 docker-cluster 经验） |
| Spring Boot 后端 | Docker 镜像部署到 ECS，Nginx 反向代理 + HTTPS |
| OSS | 阿里云 OSS Bucket + CDN 加速 |
| App | HBuilderX 云打包，上架安卓各市场 / Apple App Store |
| 高德 | 申请 Web 端 + Android/iOS 端 Key |

### docker-compose 核心服务
```yaml
services:
  mysql:    # MySQL 8
  redis:    # Redis 7
  kafka:    # KRaft 模式(免 ZooKeeper) 或 带 ZK
  server:   # Spring Boot 后端
```

---

## 十一、注意事项与风险

1. **坐标系统一**：高德用 GCJ-02，EXIF 是 WGS-84，必须转换，否则点位偏差几十米。
2. **App 上架**：iOS 需要审核（定位用途说明、隐私政策），安卓各市场也要；提前准备。
3. **OSS 成本**：图片流量走 CDN，注意费用；缩略图降低带宽。
4. **弱网体验**：景区信号差，上传必须支持断点续传 + 本地暂存，否则丢数据。
5. **隐私合规**：收集用户位置/照片需隐私政策，符合个保法。
6. **审核时效**：内容审核异步进行，待审照片如何展示需产品决策（先展示后撤回 vs 审核后才展示）。

---

## 附：技术栈速查

| 层 | 计划 | 当前实现 |
|---|---|---|
| 手机端 | Uni-app（Vue3 语法） | **暂未实现**，目前为 Vue3 + Vite Web 前端（`map-album-web/`） |
| 后台管理端 | Vue3 + 高德 JS API | **同一 Web 前端**，地图主页即管理端 |
| 后端 | Spring Boot 3 + MyBatis-Plus + Spring Security | **Spring Boot 2.7.18** + MyBatis-Plus 3.5.5，暂未接入 Security |
| 数据库 | MySQL 8（空间索引 SPATIAL） | ✅ MySQL 8，InnoDB，POINT 列已建但 TypeHandler 待补 |
| 缓存 | Redis 7 | ⏳ 已配置未启用 |
| 消息队列 | Kafka（图片处理流水线） | ⏳ docker-compose 已配置未启用 |
| 对象存储 | 阿里云 OSS + CDN | ⏳ **当前为本地文件系统**（`C:\Users\DELL\Desktop\STOAGE`） |
| 地图 | 高德：JS API v2.0 + Web 服务 API | ✅ `AMapLoader` + `AMap.MarkerCluster` + inputtips + IP 定位 + 逆地理编码 |
| 前端构建 | — | Vite 5 + `http-server`（生产） |
| 部署 | Docker + Nginx + 阿里云 ECS | ⏳ **当前仅本地开发**（`mvn spring-boot:run` / `npm run dev`） |
| 认证 | Spring Security + JWT / SSO | ⏳ **暂硬编码 admin(uid=2)**，SSO 待接入 |
