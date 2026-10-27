# 安装 ReTieba

支持 Android 6.0（API 23）及以上。

在 [GitHub Releases](https://github.com/npc0451/TiebaPure-Android/releases/tag/v1.0.0-rc.3) 下载 `ReTieba-1.0.0-rc.3-candidate.apk`。这是首次公开的预发布版本，关闭调试并启用 R8 压缩，版本码为 25。

下载后允许浏览器或文件管理器安装应用，再打开 APK。可用同页提供的 `SHA256SUMS.txt` 核对文件完整性。

## 更新与签名

本次沿用开发签名，安装标识为 `dev.infinityf4p.tiebapure.reader`，可以覆盖更新此前同签名的 ReTieba 开发版。它不覆盖上游 TiebaPure。

开发签名不作为长期正式发行的身份保证。以后切换签名时可能需要单独迁移，不能直接覆盖安装；不要为了换签名直接卸载仍有本地数据的旧版。离线备份不包含全部订阅、关注与书签数据。

源码可从 Release 对应标签获取。`release-unsigned.apk`、AAB 及 GitHub 自动生成的 Source code 压缩包均不是可直接安装的 APK。构建与签名说明见 [RELEASING.md](RELEASING.md)。
