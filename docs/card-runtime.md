# 卡片运行与并发约定

## UI 声明

`CardManifest.hasUi` 根据 `requires.bridges`、WASM `capabilities`、
`ui` 参数声明及 `widget` 判断卡片是否可能使用界面。Hub 索引也下发 `hasUi`。
这是声明信息；本次是否实际弹窗取决于参数、脚本分支及运行级 `UiMode`。
`HEADLESS` 的 UI bridge 立即返回，不等待用户。

## 并发与交互

`CardRunManager` 默认允许两张卡片同时计算。每次运行有独立的 job、交互 id、
计算名额与超时上下文。

- 卡片等待表单、确认、操作屏或媒体选择时释放计算名额，回答后再获取名额继续执行。
- 交互请求排队显示，不覆盖另一张卡片的请求；排队时间不消耗用户回答的等待预算。
- 每个请求有独立的就绪信号，界面立即回答也不会丢失。
- 取消运行会移除该运行正在显示和排队的交互，并让下一张卡片显示。
- 超时交互在没有后续请求时保留，支持迟到回答续跑；有后续请求时取消迟到交互并交接界面。
- `ui.show` 的结果保留到用户关闭或新的界面请求接替；卡片结束不会让结果立即消失。

同步 JNI 调用等待用户时仍占用执行线程，但不会占用卡片的计算名额。

## 执行限制

Lua 只开放基础计算库和宿主注册的模块，不开放 `io`、`os`、文件加载器和动态库加载。
Lua/JS 各限制 64 MiB 引擎内存，指令钩子检查取消和超时；
用户交互等待时间不计入默认 120 秒计算预算。脚本不能延长或清除宿主指定的截止时间。

WASM 使用 ABI v1 的内存、输出和 fuel 限额。JNI 在宿主调用时检查取消，
并同时验证能力名称、具体方法及网络目标。纯 WASM 计算通过 fuel 耗尽终止，
不保证取消请求发生时立即停止。

## 包安装与发布

标准 WASM 包包含 `manifest.json` 和 `card.wasm`，可附带 assets/locales。
Android 安装时生成本地 `card.json` 兼容元数据；二进制及 ABI manifest 在加载时形成快照，
避免更新删除旧目录后影响已经加载的运行。

下载和安装校验哈希、卡片名称、版本、入口文件及 WASM 头。
同一卡片的安装/卸载互斥，替换前先校验暂存目录，失败保留旧版本。
压缩包上限 8 MiB、解压上限 32 MiB、条目上限 256，拒绝越界路径和重复条目。

服务端先通过 `POST /admin/khatkit/cards/artifacts` 上传完整包（原始请求体），
使用响应中的 `wasmHash` / `wasmUrl` 发布或更新卡片。版本必须匹配包内 manifest，
下载返回原始包字节，索引哈希与下载内容一致。只提供外部 URL 或缺少二进制不能发布。

## 验证

Android：

```bash
KHATKIT_NATIVE_LIB="$PWD/khatkit-core/target/debug/libkhatkit_core.so" \
  ./gradlew :khatkit:testDebugUnitTest :card-validator:test :app:compileDebugKotlin
```

原生库可通过 `khatkit-core/build-android.sh` 构建 arm64-v8a 和 x86_64，
需要配置 `ANDROID_HOME` 或 `ANDROID_NDK_HOME`。JNI 测试使用本机编译的库；
实际 MoonBit 集成测试读取相邻的 KhatKitCards 仓库，也可设置 `KHATKIT_CARDS_REPO`。

服务端运行 `./kotlin test --include-classes='*KhatKit*' --include-classes='*BuiltinCardsSeedTest'`。
卡片仓库运行 `bash moonbit/scripts/test-cards.sh` 和 `bash moonbit/scripts/test-ui.sh`；
业务用例必须声明预期结果，合法 JSON 本身不算成功。
