# Release 签名密钥

> 状态：**2026-11-11 起生效**。release 包统一用本密钥签名，旧的 `CN=Hei zige` 密钥停止使用。

## 密钥

| 项 | 值 |
| --- | --- |
| keystore 文件 | `khatkit-release.jks`（由发布者离线备份，不随仓库分发） |
| 类型 | JKS，RSA 4096，有效期 36500 天 |
| alias | `khatkit` |
| storePassword / keyPassword | `khatkit` / `khatkit` |
| subject | `CN=khatkit, OU=khatkit, O=khatkit, L=khatkit, ST=khatkit, C=CN` |
| 证书 SHA-256 指纹 | `32:3C:87:93:FE:29:7F:CB:E9:DC:EA:FB:B3:30:BF:40:DC:03:C7:44:81:A5:E6:67:A2:AD:9D:B2:8F:1B:C4:53` |

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
