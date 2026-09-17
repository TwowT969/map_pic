# 地图相册（map_pic）

「地图相册」是一套**照片按拍摄位置落在地图上**的记录型应用：在景区或任意位置拍照上传，照片按地理位置聚合展示在地图上，点开点位即可浏览图片列表与一一对应的备注。

本仓库包含三部分：

| 模块 | 说明 | 技术栈 |
| --- | --- | --- |
| `map-album-server/` | 后端服务（本仓库主体） | JDK 8 · Spring Boot 2.7.18 · MyBatis-Plus 3.5.5 · MySQL 8 |
| `map-album-web/` | Web/H5 前端，同时承载 Android APK 的 WebView 资源 | Vue 3 · Vite · Capacitor 8 · 高德 JS API |
| `map-album-web/android/` | Android 壳工程（APK） | Capacitor 8 · targetSdk 36 |

整体技术设计见 [地图相册App技术方案.md](地图相册App技术方案.md)；接口明细见 [docs/API.md](docs/API.md)；版本变更见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

## 功能总览（v1.1.0）

**地图与点位**
- 地图主页：定位链依次回退 —— 系统定位（先做权限用途说明）→ 历史定位缓存 → IP 网络定位 → 默认苏州
- 底部中央蓝色 `＋` 上传入口：拍照 / 相册选择（原生端多选，最多 20 张）
- 上传选点：底部滑出面板 + 地图蓝色可拖动标记，逆地理编码显示省市区/地址
- 点位创建：地图长按 / 右键 / 点击空白放置标记
- 点位移动：长按标记拖拽，松手保存新位置并回写地址
- 点位详情弹窗：图片列表 + 一一对应备注 + 图片/点位标签，头部下拉关闭

**照片与浏览**
- EXIF 元数据：解析拍摄时间、设备、GPS（压缩前解析，随上传参数显式携带）
- 客户端压缩：最长边 2048 / JPEG q0.85，GIF 跳过
- 上传可靠性：失败或断网自动进入持久化待传队列（IndexedDB），联网后自动补传，横幅一键重试
- 大图光箱：捏合/双击缩放、拖动平移、滑动翻页、下滑关闭；备注在线编辑与删除
- 相册视图：按拍摄月份分组的时间轴 + 三列宫格，点击进入光箱
- 搜索：高德地点联想 + 「我的内容」（点位名/标签/地址、照片备注）直达

**账号与我的**
- 密码登录：`app-` 渠道账号必须密码（SHA-256 摘要，首次登录设置密码），`dev-` 渠道免密（Web 调试用）
- 我的页：昵称修改、点位/照片/城市统计、待上传数、检查更新、隐私政策、退出登录

**可靠性与合规**
- 隐私合规：原生端首次启动弹出隐私政策与权限说明，同意后才初始化地图
- 崩溃与埋点：全局错误捕获 + 关键行为埋点，批量上报 `/api/applog`（未登录先本地缓冲）
- 应用内更新：`GET /api/app/version` 比对 versionCode，弹窗直链下载 APK
- HTTPS：自签证书（SAN 含服务器 IP，有效期 10 年）；APK 端运行时探测 HTTPS 可达性，失败自动回退 HTTP

## 工程结构

```
map_pic/
├── README.md                       本文件
├── 地图相册App技术方案.md            技术设计文档
├── docs/
│   ├── API.md                      接口明细
│   └── CHANGELOG.md                版本变更记录
├── pom.xml                         父 POM（dependencyManagement 锁版本）
├── sql/init.sql                    建库脚本（含 user.password、app_log）
├── map-album-server/               后端服务
│   └── src/main/java/org/lxp/mapalbum/
│       ├── controller/app/         user / spot / photo / location / appinfo / applog
│       ├── service/                业务层（user / spot / photo）
│       ├── dal/                    dataobject / mysql / redis
│       ├── framework/              common / mybatis / web
│       └── enums/                  错误码等枚举
└── map-album-web/                  前端 + Android
    ├── src/
    │   ├── api/index.js            请求层：API 基址探测（HTTPS→HTTP 回退）、登录态
    │   ├── components/             18 个组件（地图、面板、光箱、相册、我的、隐私等）
    │   ├── composables/            useAmap / useSpots / usePhotos / useToast
    │   └── utils/                  capacitor / pendingQueue / applog / coord / marker
    └── android/                    Capacitor 壳工程（versionCode 2 / versionName 1.1.0）
```

## 快速开始

### 后端

前置：JDK 8、Maven 3.6+、Docker。

```bash
# 1. 启动本地中间件
docker-compose up -d

# 2. 编译
mvn -q -pl map-album-server -am compile

# 3. 测试
mvn -q -pl map-album-server test

# 4. 启动（context-path=/api）
mvn -pl map-album-server spring-boot:run
# 接口前缀：http://localhost:48081/api/
```

### Web / H5

```bash
cd map-album-web
npm install
npm run dev          # dev- 渠道免密登录
```

生产构建使用 `.env.production.local`：

```
VITE_API_BASE=http://59.110.53.169/api   # HTTP 回退基址
VITE_APP_VERSION_CODE=2
VITE_APP_VERSION_NAME=1.1.0
```

### Android APK

```bash
cd map-album-web
npm run build && npx cap sync android
cd android && ./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 国内构建若 gradle 发行包下载失败，可将 zip 预置到
> `~/.gradle/wrapper/dists/gradle-8.14.3-all/<hash>/`（华为云镜像 `mirrors.huaweicloud.com/gradle` 可直连）。

## 生产部署（阿里云 ECS 59.110.53.169）

| 组件 | 说明 |
| --- | --- |
| 后端 | systemd 单元 `map-album`，jar `target/map-album-server.jar`，监听 `127.0.0.1:48081` |
| 数据库 | MySQL 库 `map_album`（`sql/init.sql`） |
| nginx | 80/443：`/` → `/var/www/map-album`（H5 静态）；`/api/`、`/uploads/` → `127.0.0.1:48081`；`/apk/` → `/var/www/apk/` |
| HTTPS | 自签证书 `/etc/nginx/ssl/mapalbum.{crt,key}`，SAN：`IP:59.110.53.169, DNS:mapalbum.app`，有效期 10 年 |
| H5 发布 | `npm run build` 后将 `dist/` 拷贝到 `/var/www/map-album/` |
| APK 发布 | 拷贝到 `/var/www/apk/map-album-v1.1.0-debug.apk`，并同步 `application.yml` 的 `app.version.download-url` |

> **注意**：云服务器安全组目前仅放行 22/80。需在阿里云控制台放行 443 后 HTTPS 才对外生效；
> 放行前 APK 端探测 HTTPS 失败会自动回退 HTTP，功能不受影响。

## 安全说明

- 密码不以明文落库：服务端存储 `SHA-256(password + ":" + ssoUserId)` 摘要
- `app-` 前缀账号为 App 渠道（必须密码）；`dev-` 前缀为 Web 调试渠道（免密）
- 写操作（POST/PUT/DELETE）需登录态（token），GET 公开
- 自签证书仅用于当前开发/试用阶段，正式发布建议更换 CA 签发证书

## 版本历史

- **v1.1.0**（versionCode 2）：P0+P1 补全 —— 上传可靠性、相册时间轴、我的页、密码登录、隐私合规、光箱手势、应用内更新、HTTPS。详见 [docs/CHANGELOG.md](docs/CHANGELOG.md)
- **v1.0**（versionCode 1）：地图主页、拍照/相册上传、选点面板、缩略图聚合标记、详情弹窗、照片备注、长按拖动改位

## 分层约定（KSHG 规范）

- `controller` 只做参数接收、校验、调用 Service、返回 `CommonResult`；不写业务、不直连 Mapper、不返回 DO
- `service` 承担业务编排、规则校验、事务控制；多表写入用 `@Transactional(rollbackFor = Exception.class)`
- `dal/mysql` 只做持久化；常规条件用 `LambdaQueryWrapperX`，复杂查询走 `resources/mapper/*.xml`
- 业务 DO 继承 `BaseDO`（审计字段自动填充）；单租户，不含 `tenant_id`
- 业务异常用 `throw exception(ErrorCode)`，由 `GlobalExceptionHandler` 兜底
- URL：方法名与路径对齐；`context-path=/api`
