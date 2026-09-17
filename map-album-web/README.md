# map-album-web

「地图相册」前端：Vue 3 + Vite 单页应用，通过 Capacitor 8 打包为 Android APK。

## 开发

```bash
npm install
npm run dev        # dev- 渠道免密登录，VITE_API_BASE 指向后端
```

## 环境变量（.env.production.local）

| 变量 | 说明 |
| --- | --- |
| `VITE_API_BASE` | HTTP 回退基址（HTTPS 探测失败时使用） |
| `VITE_APP_VERSION_CODE` | 与 android `versionCode` 保持一致，用于更新检查比对 |
| `VITE_APP_VERSION_NAME` | 「我的」页展示用 |

## 构建与发布

```bash
# H5
npm run build            # 产物 dist/，部署到 nginx /var/www/map-album/

# Android
npx cap sync android
cd android && ./gradlew assembleDebug
# 发版前：android/app/build.gradle 的 versionCode/versionName
#        与 .env.production.local 的 VITE_APP_VERSION_* 同步递增
```

## 目录要点

- `src/api/index.js`：API 基址探测（HTTPS→HTTP 回退，缓存 24h）、登录态、全部接口封装
- `src/components/`：地图容器、上传选点面板、详情弹窗、光箱（手势+编辑）、相册、我的、隐私弹窗等
- `src/utils/pendingQueue.js`：IndexedDB 持久化待上传队列
- `src/utils/applog.js`：崩溃/埋点采集与批量上报
- `src/utils/capacitor.js`：拍照/多选/定位/权限/压缩封装
- `public/privacy.html`：隐私政策页面
