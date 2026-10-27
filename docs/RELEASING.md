# ReTieba 构建与发布

## 当前公开版本

首个公开版本为 `1.0.0-rc.3`，标签 `v1.0.0-rc.3`，在本项目 [GitHub Releases](https://github.com/npc0451/ReTieba/releases) 以正式 Release 形式提供。

本次可安装 APK 使用 optimized 构建，关闭调试并开启 R8，沿用开发签名与 `dev.infinityf4p.tiebapure.reader` 安装标识。公开候选包不等于长期正式签名版本。

## 构建与验证

使用 JDK 17、Android SDK 36、Build Tools 36.0.0：

```bash
./gradlew --no-daemon test :app:lintRelease :app:assembleDebug :app:assembleOptimized
```

Windows 使用 `gradlew.bat`。安装包位于 `app/build/outputs/apk/optimized/app-optimized.apk`，需要使用 Android Build Tools 的 `apksigner verify --print-certs` 检查签名，并核对应用 ID、版本与调试标志。检查源码标签与 APK 对应，生成 SHA-256 校验文件。

未连接设备时，只能报告设备测试代码编译通过，不得宣称已执行设备回归。

## 长期正式签名

Release 签名沿用四个环境变量，须全部配置或全部不配置：

```text
TIEBAPURE_ANDROID_KEYSTORE
TIEBAPURE_ANDROID_STORE_PASSWORD
TIEBAPURE_ANDROID_KEY_ALIAS
TIEBAPURE_ANDROID_KEY_PASSWORD
```

配置后执行 `:app:assembleRelease :app:bundleRelease`。未配置时 release APK 为 unsigned；AAB 不可直接安装。

密钥和密码由维护者保管，不提交 Git、不写入日志或源码归档。签名变更会影响覆盖升级，需要先设计本地数据迁移；离线备份不包含全部本地数据。

## 发布内容

- 源码仓库保留代码、构建脚本、测试、README、安装／开发文档、版本说明与必要许可。
- APK 与校验文件放在 Release 附件，源码使用对应 Git 标签；R8 映射文件由维护者在本地保存。
- 不提交设计过程稿、DOCX、构建缓存、崩溃日志、本机配置或签名材料。
- 保留 `LICENSE`、`LICENSES/`、`THIRD_PARTY_NOTICES.md` 和上游来源。README 应明确只读功能通过关闭入口、切断调用及 API 拦截实现，工程尚保留部分上游账号与写操作代码。

CI 只执行构建与检查，不自动公开发布安装包。
