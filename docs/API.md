# API 接口明细

统一前缀：`/api`（context-path）。除注明外，写操作（POST/PUT/DELETE）需登录态 `token`（请求头 `Authorization`，登录接口返回），GET 公开。

统一响应：`CommonResult { code, data, msg }`，`code=0` 成功。

## 用户 user

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/user/login` | 登录/注册。入参 `{ ssoUserId, nickname?, password? }`。**全账号必须密码**：首次登录设置密码（≥6 位），之后校验；历史无密码老账号首次登录所填密码即被设置。返回 `{ token, user }` |
| PUT | `/api/user/profile` | 更新昵称。入参 `{ nickname }`，返回更新后用户 |

**登录错误码**：`1005` 请输入密码；`1006` 账号或密码不正确；`1007` 密码至少 6 位。

密码存储：`SHA-256(password + ":" + ssoUserId)` 十六进制摘要。

## 点位 spot

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/spots/mine` | 当前用户可见点位列表（含聚合信息） |
| POST | `/api/spots` | 创建点位：`{ name, lat, lng, address?, province?, city?, district?, category?, tags? }` |
| PUT | `/api/spots` | 更新点位：`{ id, ...可更新字段 }`（长按拖动改位即调用此接口回写 lat/lng/address） |
| DELETE | `/api/spots/{id}` | 删除点位 |

## 照片 photo

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/photos/spot/{spotId}` | 某点位的照片列表（`thumbUrl`/`url`（历史远程照片）、`localPath`/`localThumbPath`（本地化照片）、`description`…） |
| GET | `/api/photos/mine` | 当前用户照片（平铺，按拍摄时间倒序） |
| POST | `/api/photos` | 创建照片元数据（v1.2.0 本地化：图片只存设备本地，不再上传文件）。JSON：`{ spotId, description?, shotTime?, device?, localPath?, localThumbPath? }`，仅照片所有者可见 |
| PUT | `/api/photos` | 更新照片（备注/排序/封面）：`{ id, description?, sortOrder?, isCover? }`，仅照片所有者可改 |
| DELETE | `/api/photos/{id}` | 删除照片，仅所有者可删 |

**拍摄时间规则**：展示与排序取 `shotTime || createTime`（EXIF 缺失时回退上传时间）。

## 定位 location

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/location/ip` | IP 定位兜底 |
| GET | `/api/location/suggest?keywords=` | 高德地点联想 |
| GET | `/api/location/amap-config` | 下发高德 JS key / 安全密钥（需登录） |

## 应用信息 appinfo

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/app/version` | 版本检查。返回 `{ versionCode, versionName, downloadUrl, notes }`（当前 5 / 1.2.0）。客户端比对本地 versionCode 决定是否弹更新 |

## 埋点上报 applog

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/applog` | 批量上报（单次 ≤ 50 条）：`[{ level, event, extra, userId, createTime }]`。字段超限自动截断。客户端未登录时本地缓冲，登录后补报 |

## 静态资源

| 路径 | 说明 |
| --- | --- |
| `/uploads/**` | 照片文件（nginx 反代到后端） |
| `/apk/**` | APK 直链下载（nginx alias `/var/www/apk/`） |
| `/privacy.html` | 隐私政策页面（H5 静态资源） |
