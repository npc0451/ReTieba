# 安装 ReTieba

支持 Android 6.0（API 23）及以上。

当前版本为 `1.0.0-rc.2`。目前只准备本地候选包，尚无本项目的 GitHub Release 下载地址；不要把上游 TiebaPure 的安装包当作 ReTieba。

- `ReTieba-1.0.0-rc.2-candidate.apk`：可安装候选包，关闭调试并启用压缩，沿用既有开发签名，可更新此前由本项目同一签名生成的开发版。
- `ReTieba-1.0.0-rc.2-debug.apk`：用于开发调试。
- `release-unsigned.apk` 和 `.aab`：构建工件，不是可直接安装的正式签名 APK。

本地交付目录同时提供源码归档、验证记录和 SHA-256 清单。

正式公开发行前还需确定长期签名。不同签名不能直接覆盖安装；不要为了换签名直接卸载仍有本地数据的开发版。签名与迁移说明见 [RELEASING.md](RELEASING.md)。
