# Release 签名密钥

> 状态：**2026-11-14 重新生成**（2026-11-11 那把密钥已丢失且无备份）。release 包统一用本密钥签名，旧的 `CN=Hei zige` 与 2026-11-11 密钥均停止使用。
>
> ⚠️ 换钥代价：此前用旧密钥签名的安装包无法覆盖升级，用户需卸载重装一次（自更新通道需同步换包）。

## 密钥

| 项 | 值 |
| --- | --- |
| keystore 文件 | `khatkit-release.jks`（发布者需离线双备份；密钥只应存在于本机安全位置与仓库 secrets） |
| 类型 | JKS，RSA 4096，有效期 36500 天 |
| alias | `khatkit` |
| storePassword / keyPassword | `khatkit` / `khatkit` |
| subject | `CN=khatkit, OU=khatkit, O=khatkit, L=khatkit, ST=khatkit, C=CN` |
| 证书 SHA-256 指纹 | `F7:61:18:C9:C1:F4:14:57:7B:D6:FF:FF:5D:57:D5:78:F0:C5:BD:33:00:1E:8D:3B:BC:55:5D:2D:B5:62:A1:BF` |

## CI 里的用法

仓库 Actions secrets：

- `KEY_BASE64`：`base64 -w0 khatkit-release.jks` 的结果
- `SIGNING_CONFIG`：

  ```
  storeFile=app.key
  storePassword=khatkit
  keyAlias=khatkit
  keyPassword=khatkit
  ```

`.github/workflows/build-apk.yml`（手动构建）和 `.github/workflows/daily-build.yml`（nightly）
都会把 keystore 解码到 `app/app.key`，并把 `SIGNING_CONFIG` 写成仓库根目录的 `local.properties`
（`app/build.gradle.kts:50-73` 读取这四个键）。

`build-apk.yml` 在 secrets 缺失时（例如 fork）会退化为一次性临时密钥，只为产出可安装的包。

## 本地构建 release

`local.properties`：

```properties
storeFile=/绝对路径/khatkit-release.jks
storePassword=khatkit
keyAlias=khatkit
keyPassword=khatkit
```

## 更新通道与轮换注意

- 同包名不同签名的 APK **无法覆盖安装**（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。
  更新通道 `GET /api/khatkit/app/update` 下发的存量 APK 若仍是旧密钥签名，客户端升级到
  新密钥版本会失败，需要用户卸载重装一次；更稳妥的做法是把更新通道里已发布的包一并换成
  同一把密钥签名的版本再放量。
- 之后所有版本都必须继续用这把密钥签名，否则自更新链会断。

## CI 签名预检（2026-11-14 起）

`build-apk.yml` / `daily-build.yml` 不再直接信 `SIGNING_CONFIG`：统一调用
`.github/scripts/prepare-signing.sh`，顺序为

1. 用 `SIGNING_CONFIG` 的 `storePassword` 试 keystore；
2. 失败则用本文档记录的 `khatkit` 再试（secrets 与文档漂移时自愈）；
3. 仍失败 → 打印 `::warning::` 并退化到一次性临时密钥（保证 CI 能产出可安装包）。

脚本会打印 keystore 的 SHA-256 指纹并与本文档的期望指纹比对：
不匹配时输出 warning——说明 `KEY_BASE64` 里不是当前正式密钥，**更新通道会断**，
需要在仓库 Settings → Secrets 里更新 `KEY_BASE64`（以及 `SIGNING_CONFIG` 的密码）。
