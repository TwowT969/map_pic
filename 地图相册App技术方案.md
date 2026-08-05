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
| App 形态 | 独立 App（安卓 + iOS）→ **App+H5 (Capacitor)** |
| 地图 SDK | 高德地图 |
| 部署环境 | 云服务器（阿里云） |

> **路线调整**（2026-07）：放弃原方案的 Uni-app 重写路线。
> 改用 **Capacitor** 作为原生壳，现有 Vue3 + Vite H5 嵌入 WebView。
> 核心理由：现有代码复用 85%+，仅需 UI 移动端适配；H5 天然支持热更新。

---

## 二、技术选型

整体思路：**用 Web 技术栈（HTML/JS）做 App**，复用 Java 后端能力，不引入全新语言。

| 层 | 方案 | 选型理由 |
|---|---|---|
| **手机端** | **Capacitor + Vue3 H5** | 现有 Web 代码直接复用，Capacitor 壳提供原生相机/GPS/文件系统桥接；天然热更新 |
| **后台管理端** | Vue3 + 高德 JS API | 与手机端同一套 H5 代码，PC 大屏管理 |
| **后端** | Spring Boot 2.7 + MyBatis-Plus | KSHG 规范（SB2.7/JDK8），复用 Java，生态成熟 |
| **数据库** | MySQL 8（空间索引） + Redis | 业务数据 + 缓存 |
| **消息队列** | Kafka | 图片上传削峰 + 异步处理流水线 |
| **对象存储** | 阿里云 OSS + CDN | 图片不入库，CDN 加速访问 |
| **地图** | 高德 JS API v2.0 | 同一套 Key 体系，WebView 中可用 |
| **部署** | 阿里云 ECS + Docker | 后端容器化 |

---

## 三、系统架构

```
┌─────────────────────────────────────────────────────┐
│  原生壳 (Capacitor)                                   │
│  ┌─────────────────────────────────────────────────┐│
│  │  WebView (H5)                                    ││
│  │  地图主页 · 拍照上传 · 点位管理 · 照片光箱 · 搜索  ││
│  └───────────────────┬─────────────────────────────┘│
│        ↕ Capacitor Bridge (Camera/GPS/Filesystem)    │
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS
┌──────────────────────▼──────────────────────────────┐
│  后端 Spring Boot                                     │
│  ┌─────────┬─────────┬──────────┬────────┬────────┐ │
│  │ 用户认证 │ 地图接口 │ 照片接口 │ 景点接口│后台管理│ │
│  └─────────┴─────────┴──────────┴────────┴────────┘ │
└───┬───────────┬──────────────┬───────────────┬──────┘
    │           │              │               │
    ▼           ▼              ▼               ▼
┌───────┐  ┌────────┐    ┌──────────┐    ┌─────────┐
│ MySQL │  │ Redis  │    │  Kafka   │    │  OSS    │
│业务数据│  │ 缓存   │    │ 异步流水线│    │ 图片存储│
└───────┘  └────────┘    └──────────┘    └─────────┘
```

---

## 四、项目结构

```
map-album/
├── map-album-web/               # Capacitor + Vue3 H5
│   ├── capacitor.config.json    # App 配置(appId/webDir/插件)
│   ├── android/                 # npx cap add android → Android Studio
│   ├── ios/                     # npx cap add ios → Xcode
│   ├── dist/                    # Vite build 输出(Capacitor webDir)
│   ├── src/
│   │   ├── components/          # Vue 组件（响应式适配）
│   │   ├── composables/         # useAmap / useSpots / usePhotos
│   │   ├── api/                 # REST 封装
│   │   ├── utils/
│   │   │   ├── capacitor.js     # 原生桥接层
│   │   │   ├── coord.js         # WGS84→GCJ02
│   │   │   └── marker.js        # 自定义 Marker 样式
│   │   ├── App.vue              # 主入口
│   │   └── main.js
│   ├── vite.config.js
│   └── package.json
│
├── map-album-server/            # Spring Boot 后端
│   └── src/main/java/.../mapalbum/
│       ├── controller/
│       ├── service/
│       ├── mapper/
│       ├── entity/
│       └── ...
│
├── sql/init.sql
└── README.md
```

---

## 五、数据库设计（同原方案 §5）

略，参见 `sql/init.sql`（已实现 user + spot + photo 三表，含 SPATIAL INDEX）。

---

## 六、核心接口设计（REST API）

同原方案 §6，当前已实现：

| 方法 | 路径 | 状态 |
|---|---|---|
| GET | `/api/photos/mine` | ✅ |
| GET | `/api/photos/spot/{spotId}` | ✅ |
| POST | `/api/photos` (multipart) | ✅ |
| DELETE | `/api/photos/{id}` | ✅ |
| GET | `/api/spots/mine` | ✅ |
| POST | `/api/spots` | ✅ |
| PUT | `/api/spots` | ✅ |
| DELETE | `/api/spots/{id}` | ✅ |
| GET | `/api/location/ip` | ✅ |
| GET | `/api/location/suggest` | ✅ |
| GET | `/api/location/amap-config` | ✅ |
| POST | `/api/user/login` | ✅ (SSO) |

---

## 七、关键技术点与实现思路

### 7.1 Capacitor 桥接层 (`src/utils/capacitor.js`)

```
├── takePhoto()              → @capacitor/camera 原生拍照
├── pickFromGallery()        → 相册多选
├── getCurrentPosition()     → @capacitor/geolocation 原生GPS
├── cacheImage/removeCache   → @capacitor/filesystem 离线缓存
├── onNetworkChange()        → @capacitor/network 弱网检测
└── onKeyboardChange()       → @capacitor/keyboard 键盘避让
```

浏览器开发时自动降级为 `<input>` / `navigator.geolocation` 等 Web API。

### 7.2 拍照 + EXIF（前端）

- Capacitor Camera 拍照 / `<input capture>` 选相册 → 返回 File 对象
- exifr 前端解析 GPS → WGS84→GCJ02 转换
- 自动按坐标分组（±0.0003° ≈ 30m）→ 已有/新建点位 → 上传

### 7.3 移动端 UI 适配

| 组件 | 改造 |
|---|---|
| `SpotPanel` | PC 右侧滑出 → 手机**底部抽屉**（可拖拽关闭） |
| `PhotoLightbox` | 上下翻页按钮 + **左右滑动** + **下滑关闭** + 缩略图条 |
| `FAB` | 右下角（拇指热区），新增🤳拍照 |
| `PhotoGrid` | 3列 → 手机 2列 |
| 地图 | 右键 → 手机**长按**进入创建模式 |

### 7.4 地图聚合展示

- AMap.MarkerCluster 聚合，WebView 中性能可接受
- 原生 GPS (`locateNative()`) 替代 IP 定位提高首次定位精度

### 7.5 图片上传方式

- 方式 A（推荐）：OSS 直传（前端拿 STS Token） → ⏳ 待接入
- 方式 B：后端转存 → ✅ 当前实现（**后端收 multipart 转存到 OSS**，2026-08-05）
  - 私有 bucket，DB 存 object key，读取时由 `OssClient` 生成签名 URL 下发
  - 坑：aliyun-sdk-oss 默认 HTTP 被链路注入 HTML，`OssClient` 强制 HTTPS 解决
- → 下一步（可选）：改前端 STS 直传，减轻后端带宽

### 7.6 内容审核 / 防盗刷 / Kafka

参见原方案 §7.5-7.6、§8，评估中未实施。

---

## 八、开发路线图（已更新）

### MVP ✅ 已完成 (2-3 周)

- [x] 建库建表（user + spot + photo + SPATIAL INDEX）
- [x] 后端 CRUD 全部接口
- [x] 前端地图展示（高德 JS API v2.0 + MarkerCluster）
- [x] 搜索框（输入提示 + 搜索定位）
- [x] 点位管理面板（创建/编辑/删除/地址回填）
- [x] 照片上传/删除/网格预览
- [x] EXIF GPS 自动解析 → 自动创点 + 上传
- [x] 光箱（图片大图预览）

### Capacitor 改造 ✅ 已完成 (3 天)

- [x] Capacitor 壳工程搭建（`@capacitor/core|cli|android|ios`）
- [x] 原生桥接层 `src/utils/capacitor.js`
- [x] 移动端 UI 响应式适配（底部抽屉/光箱翻页/长按地图）
- [x] 原生定位集成 (`locateNative`)
- [x] 前端构建验证通过

### 对象存储接入 ✅ 已完成 (2026-08-05)

- [x] 阿里云 OSS（北京 / 私有 bucket / 签名 URL）替代本地文件系统
- [x] 后端 `OssClient`（上传/签名/删除，强制 HTTPS）；DB 存 object key，读取时签名下发
- [x] 修复 aliyun-sdk-oss 默认 HTTP 被链路注入 HTML 的坑（强制 HTTPS）

### APK 最小实例验证 ⏳ 进行中（当前下一步）

> 目标：手机装 debug APK，跑通「地图加载 -> 定位 -> 拍照上传 OSS -> 签名 URL 回显 -> 点位 CRUD」最小闭环。

- [ ] 前端 `src/api/index.js`：`API_BASE` 改为 `import.meta.env.VITE_API_BASE || '/api'`，APK 构建时注入后端 LAN IP
- [ ] `npm run build`（带 `VITE_API_BASE=http://<局域网IP>:48081/api`）+ `npx cap sync android` + `npx cap open android` 出 debug APK
- [ ] 后端对手机可达：手机与 PC 同局域网 + Windows 防火墙放行入站 48081
- [ ] （验证期）`capacitor.config.json` 置 `webContentsDebuggingEnabled: true`，便于 chrome://inspect 看日志
- [ ] 验证高德 JS Key 在 WebView origin `https://mapalbum.app` 下可用（securityCode 模式一般可，失败则在控制台绑定包名+SHA1）
- [ ] 真机验证最小闭环；DB 需有占位用户（当前硬编码 userId=2）

### V1（1-2 周）-- 打包上架

- [ ] `npx cap add android`（已完成）+ Android Studio 出 release APK（签名 keystore）
- [ ] `npx cap add ios` + Xcode 构建 IPA
- [ ] 用户认证（SSO/JWT，替换硬编码 userId=2）
- [ ] 缩略图生成
- [ ] App Store 上架准备（隐私政策/权限说明/截图）

### V2（2-3 周）-- 体验完善

- [ ] 光箱全局时间线（按日期分组浏览）
- [ ] 后台审核管理
- [ ] Redis 缓存
- [ ] Kafka 异步流水线（缩略图/EXIF/审核）
- [ ] 内容审核接入
- [ ] 离线弱网优化（Filesystem 缓存 + 上传队列）

### V3（按需）

- [ ] 旅程 Trip 自动聚合 + 路线可视化
- [ ] Canal + ES 全文搜索
- [ ] Docker Compose 一键部署

---

## 九、注意事项与风险

1. **坐标系统一**：高德用 GCJ-02，EXIF 是 WGS-84，前端 exifr 解析后用 `coord.js` 转换。
2. **App 上架**：iOS 需审核（定位用途说明、隐私政策），需准备。
3. **WebView 性能**：地图在低端安卓设备上可能卡顿，MarkerCluster 已做聚合优化。
4. **弱网体验**：景区信号差，Capacitor Filesystem 可做本地缓存 + 上传队列。
5. **隐私合规**：收集用户位置/照片需隐私政策，符合个保法。
6. **热更新合规**：H5 代码部署到服务端，WebView 加载最新版本 **合规**（非 JSBundle 热更）。

---

## 附：技术栈速查

| 层 | 计划 | 当前实现 |
|---|---|---|
| 手机端 | ~~Uni-app~~ → **App+H5 (Capacitor)** | ✅ Vue3 + Vite H5 嵌入 Capacitor WebView，`@capacitor/camera|geolocation|filesystem` 桥接原生能力 |
| 后台管理端 | Vue3 + 高德 JS API | **同一 Web 前端**，地图主页即管理端 |
| 后端 | Spring Boot 3 + MyBatis-Plus + Spring Security | **Spring Boot 2.7.18** + MyBatis-Plus 3.5.5，暂未接入 Security |
| 数据库 | MySQL 8（空间索引 SPATIAL） | ✅ MySQL 8，InnoDB，POINT 列已建但 TypeHandler 待补 |
| 缓存 | Redis 7 | ⏳ 已配置未启用 |
| 消息队列 | Kafka（图片处理流水线） | ⏳ docker-compose 已配置未启用 |
| 对象存储 | 阿里云 OSS + CDN | ✅ 阿里云 OSS（北京/私有/签名URL，后端转存）；CDN ⏳ 待接入 |
| 地图 | 高德：JS API v2.0 + Web 服务 API | ✅ `AMapLoader` + `AMap.MarkerCluster` + inputtips + IP 定位 + 逆地理编码 |
| 前端构建 | — | Vite 5 → Capacitor `webDir: dist` |
| 部署 | Docker + Nginx + 阿里云 ECS | ⏳ **当前仅本地开发**（`mvn spring-boot:run` / `npm run dev`） |
| 认证 | Spring Security + JWT / SSO | ⏳ **暂硬编码 admin(uid=2)**，SSO 待接入 |
