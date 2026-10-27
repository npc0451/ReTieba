# ReTieba 发布准备

当前准备的是本地 `1.0.0-rc.2` 候选版，不自动创建仓库、推送分支、打标签或上传 GitHub Release。

## 签名与升级

- 本地可安装候选包使用 optimized 构建，关闭调试并启用 R8，但沿用开发签名，安装标识仍为 `dev.infinityf4p.tiebapure.reader`，以便保留已有开发版数据进行测试。
- 这份包不作为长期公开发行签名包。没有配置正式签名时，release APK 为 unsigned，不能直接安装；AAB 也不是手机安装包。
- 正式公开发布前，必须确定长期应用 ID、签名密钥和开发版迁移方式。不同签名不能覆盖安装；不要为了换签名直接卸载已有数据的开发版。当前离线备份不等于订阅、关注、书签等全部本地数据的统一备份。
- 密钥和密码由维护者保管，放在仓库外。不要提交到 Git、发到聊天或打入源码归档。

构建沿用原工程四项环境变量名称：

```text
TIEBAPURE_ANDROID_KEYSTORE
TIEBAPURE_ANDROID_STORE_PASSWORD
TIEBAPURE_ANDROID_KEY_ALIAS
TIEBAPURE_ANDROID_KEY_PASSWORD
```

四项须全部配置或全部不配置。配置后执行 `:app:assembleRelease :app:bundleRelease`，并用本机 Android Build Tools 的 `apksigner verify --print-certs` 核对 APK 与长期签名证书。

## 构建与检查

JDK 17、SDK 36、Build Tools 36.0.0：

```bash
./gradlew --no-daemon test lintRelease \
  :app:assembleDebug :app:assembleOptimized :app:assembleRelease :app:bundleRelease \
  :app:compileDebugAndroidTestKotlin :feature:home:compileDebugAndroidTestKotlin \
  :feature:thread:compileDebugAndroidTestKotlin
```

Windows 使用 `gradlew.bat`。不通过降低 SDK 或替换依赖版本绕过本地环境问题。没有连接设备时，只能声称原生测试代码编译通过，不能声称设备测试或截图验收通过。

## 本地交付文件

构建结果、签名摘要、测试和 Lint 数量写入本地 `artifacts/`。该目录不提交到源码仓库。

- `ReTieba-1.0.0-rc.2-candidate.apk`：沿用开发签名的可安装候选包。
- `ReTieba-1.0.0-rc.2-debug.apk`：开发调试包。
- `ReTieba-1.0.0-rc.2-release-unsigned.apk`：未配置正式签名时的 Release 工件。
- `ReTieba-1.0.0-rc.2-release.aab`：Release 构建工件，不直接安装。
- 对应源码归档、R8 mapping、验证记录及 SHA-256 清单。

发布资料应与同一次验证的源码对应，保留 `LICENSE`、`LICENSES/`、`THIRD_PARTY_NOTICES.md` 和上游来源。不要将本地崩溃日志、构建缓存、密钥、local.properties 或私人参考截图作为发布附件。

## GitHub 准备

README 不使用上游下载徽章，也不把上游 APK 当作 ReTieba。当前 CI 构建和测试，使用只读权限，不上传工件或自动发布。

远程仓库确定后再补充本项目下载地址、Issue 地址和经验证的原生截图。当前设计稿只用于设计讨论，不充当真机截图。首次正式 Release 还需完成原生明暗、平板分屏、字号放大、导入字体、媒体与本地数据回归。
