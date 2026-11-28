# automation / workflow / record 内聚与职责分层（t36）

> 依据：`/tmp/khatkit-reviewer-ownership.md`（t28，57 组映射）M19–M32、t26/t27 归属审计、
> `docs/architecture-map.md` 与 `AGENTS.md` §包放置/§命名。范围只用三个 feature 包与其 tests。
> **本批未运行 Gradle / 未碰设备 / 未提交**（编译与集成验证由 verifier 独占）。

## 1. 结论

- M19–M30 已落到源码：自动化授权/触发/队列/轨迹/磁贴/回放、workflow 模型/仓库/算法/事件、录制链。
- M31（`settings/data/WorkflowPreferencesRepository` → `workflow/data`）与 M32（`CreateWorkflowPage` /
  `TriggerCardEditor` / `SettingTriggersPage` → 各 feature）**按合同留最后 core 批次**：它们的源文件在
  `feature/settings`，本批 inScope 不含；本批只完成对它们所依赖符号的重命名与迁出，未改其文件。
- 三个包内旧符号残留 0（`AutomationBus` / `TriggerController` / `TriggerEventSources` / `AutomationTracer` /
  `TriggerTileRegistry` / `WorkflowEngine` / `WorkflowLegacy` 等），外部调用者清单见 §5。

## 2. 不变量（逐条有证据，未改）

| 契约 | 证据 |
|---|---|
| SharedPreferences 文件名 / key | `TriggerSettingsRepository` 仍 `khatkit_trigger_settings` + `master_enabled`/`disabled_cards`/`override_`/`max_parallel`；`ApprovalPolicyRepository` 仍 `khatkit_automation` + `approval_policy_`；`TriggerTileRepository` 仍 `khatkit_trigger_tiles` + `slot_` |
| 序列化 wire 名 | `CardTriggerOverride` 的 `max_retries`/`retry_delay_seconds`/`disabled_events` 未动；`TraceStep`/`UiActionDto`/`TraceMeta`/`TraceRecord` 字段与默认值逐字保留；`WorkflowRunEvent` 字段不变 |
| 轨迹落盘格式 | `<baseDir>/<traceId>.jsonl` 每行一个 TraceStep + `index.json`（ListSerializer）路径与编码不变，只把 IO 移入 `AutomationTraceRepository` |
| Room | `WorkflowRepository` 仍用 `WorkflowEntity`/`WorkflowRunEntity`/`WorkflowRunStepEntity` 与原 `graphJson`；旧偏好仅 `importLegacyIfEmpty` 触发一次 |
| WorkManager FQCN / tag | `CardScheduleWorker` 类名与包 `feature.automation.data` 未动；tag `khatkit-schedule`、uniqueName `CardScheduleRepository.workName` 不变 |
| 平台注册 | manifest 的 `TriggerService`/`TriggerTileService{,2,3}`/`TriggerShortcutActivity`/`TriggerBootReceiver`/`ApprovalActionReceiver`/`KhatKitAccessibilityService`/`KhatKitNotificationListenerService`/`RecorderStopReceiver` 全部仍在原包原类名 |
| 全部 R.string | 迁出的类只改类名/包，资源 id 与 `getString/stringResource` 调用原样保留（`ApprovalCategory`/`ApprovalPolicy` 的 `@StringRes` 标签一并搬入 data） |
| 既有 WIP | `ApprovalButtons`/`AutomationOverlayService`/`TraceAuditPage`/`TriggerController`/`TriggerShortcutPublisher`/`TriggerTiles`/`OperationRecorderNotifier`/`RecordCardSaver` 的未提交改动内容保留，本批只做符号/包同步 |

## 3. M19–M32 判定

| 项 | 处理 | 落点 |
|---|---|---|
| M19 | 已做：`TriggerController` → `TriggerManager`（有状态运行流程） | `automation/helper/TriggerManager.kt` |
| M20 | 已做：审批策略持久化下沉 Repository，模型独立 data，流程留 Manager | `data/ApprovalPolicyRepository.kt`、`data/AutomationModels.kt`、`helper/AutomationManager.kt` |
| M21 | 已做：偏好唯一入口 Repository，覆盖模型独立文件 | `data/TriggerSettingsRepository.kt`、`data/CardTriggerOverride.kt` |
| M22 | 已做：事件源注册/注销 + FIFO 队列是流程 → Manager；Item/Batch → data | `helper/TriggerEventManager.kt`、`helper/TriggerRunQueueManager.kt`、`data/TriggerRunModels.kt` |
| M23 | 已做：槽位持久化 → Repository；requestAdd 系统适配 → Helper | `data/TriggerTileRepository.kt`、`helper/TriggerTileHelper.kt` |
| M24 | 已做：begin/record/finish 生命周期留 Manager，JSONL/index 读写 → Repository，DTO → data | `helper/AutomationTraceManager.kt`、`data/AutomationTraceRepository.kt`、`data/AutomationTraceModels.kt` |
| M25 | 已做：capture + Map 读取是桥接适配 → Helper；三个 Serializable 模型留 data | `helper/UiAutomationSnapshotHelper.kt`、`data/UiAutomationSnapshot.kt` |
| M26 | 已做：`toStepViews` 与两个视图模型是纯投影 → Extensions | `helper/TraceAuditExtensions.kt` |
| M27 | 已做：5 个无状态副作用适配统一 Helper 命名 | `helper/TriggerAlarmHelper.kt`、`TriggerShortcutHelper.kt`、`TraceReplayHelper.kt`、`BusApprovalHelper.kt`、`ScheduleBridgeHelper.kt` |
| M28 | 已做（保留功能分支）：录制链 Manager/Repository/Notification Helper 分层，`RecorderStopReceiver` 平台类原位 | `record/helper/OperationRecordManager.kt`、`record/data/RecordedCardRepository.kt`、`record/helper/OperationRecordNotificationHelper.kt` |
| M29 | 已做：模型/仓库 → data，样例构造 → Helper（纯函数） | `workflow/data/VisualWorkflow.kt`、`workflow/data/WorkflowRepository.kt`、`workflow/helper/WorkflowSampleExtensions.kt` |
| M30 | 已做：算法/迁移是纯函数 → Extensions；事件 Bus 持 SharedFlow → Manager，事件模型 → data | `workflow/helper/WorkflowExtensions.kt`、`WorkflowLegacyExtensions.kt`、`WorkflowEventManager.kt`、`workflow/data/WorkflowRunEvent.kt` |
| M31/M32 | **留最后 core 批次**：源在 `feature/settings`，不在本批 inScope；本批已把消费点需要的符号迁出并登记 | 见 §6 |

## 4. 逐文件映射

### automation
| 旧 | 新 |
|---|---|
| `TriggerController.kt`（`TriggerController`） | `helper/TriggerManager.kt`（`TriggerManager`） |
| `data/AutomationBus.kt`（`object AutomationBus` + 内嵌 4 个类型 + policy 读写） | `helper/AutomationManager.kt` + `data/AutomationModels.kt` + `data/ApprovalPolicyRepository.kt` |
| `data/TriggerSettings.kt` | `data/TriggerSettingsRepository.kt` + `data/CardTriggerOverride.kt` |
| `data/TriggerRunQueue.kt` | `helper/TriggerRunQueueManager.kt` + `data/TriggerRunModels.kt` |
| `data/TriggerEventSources.kt` | `helper/TriggerEventManager.kt` |
| `helper/AutomationTracer.kt` | `helper/AutomationTraceManager.kt` + `data/AutomationTraceRepository.kt` + `data/AutomationTraceModels.kt` |
| `data/TraceAudit.kt` | `helper/TraceAuditExtensions.kt`（DTO 见 `data/AutomationTraceModels.kt`） |
| `data/UiAutomationSnapshot.kt`（模型 + Provider） | `data/UiAutomationSnapshot.kt`（仅模型） + `helper/UiAutomationSnapshotHelper.kt` |
| `helper/TriggerTiles.kt` | `data/TriggerTileRepository.kt` + `helper/TriggerTileHelper.kt` |
| `helper/TriggerExactAlarmScheduler.kt` | `helper/TriggerAlarmHelper.kt` |
| `helper/TriggerShortcutPublisher.kt` | `helper/TriggerShortcutHelper.kt` |
| `helper/TracePlayer.kt` | `helper/TraceReplayHelper.kt` + `data/TraceReplayResult.kt` |
| `helper/BusApprovalGate.kt` | `helper/BusApprovalHelper.kt` |
| `helper/ScheduleBridgeImpl.kt` | `helper/ScheduleBridgeHelper.kt` |

### workflow
| 旧 | 新 |
|---|---|
| `VisualWorkflow.kt`（模型/常量 + `WorkflowSamples`） | `data/VisualWorkflow.kt`（模型/常量/`WorkflowJson`/`encode`/`decodeWorkflow`） + `helper/WorkflowSampleExtensions.kt` |
| `WorkflowRepository.kt` | `data/WorkflowRepository.kt`（类名不变） |
| `WorkflowEngine.kt`（`object`） | `helper/WorkflowExtensions.kt`（顶层纯函数 `executeWorkflow` / `exportWorkflowLua` / `interpretWorkflowScript` / `workflowFlowSpecJson` / `workflowCardName`；`ScriptRunner` 仍为文件内 private） |
| `WorkflowLegacy.kt`（`object`） | `helper/WorkflowLegacyExtensions.kt`（`WorkflowDefinition.toVisualWorkflow()` / `VisualWorkflow.toStoredNodes()`） |
| `WorkflowEventBus.kt` | `helper/WorkflowEventManager.kt` + `data/WorkflowRunEvent.kt` |

### record
| 旧 | 新 |
|---|---|
| `KhatKitOperationRecorder.kt` | `helper/OperationRecordManager.kt` |
| `RecordCardSaver.kt` | `data/RecordedCardRepository.kt` + `data/RecordCardSaveResult` |
| `OperationRecorderNotifier.kt` | `helper/OperationRecordNotificationHelper.kt` |
| `RecorderStopReceiver.kt` | 原位（平台 Receiver），内部引用改 `OperationRecordManager` |

### tests
| 旧 | 新 |
|---|---|
| `AutomationTracerTest.kt` | `AutomationTraceManagerTest.kt` |
| `TriggerRunQueueTest.kt` | `TriggerRunQueueManagerTest.kt` |
| `WorkflowEngineTest.kt` | `WorkflowExtensionsTest.kt` |
| `TraceAuditTest.kt` / `UiAutomationSnapshotTest.kt` / `CardScheduleRepositoryTest.kt` | 原位，import 与类型同步 |

## 5. 外部调用者更新清单（本批未越界修改，交最终引用集成任务）

| 文件 | 旧符号（行） | 新 |
|---|---|---|
| `androidTest/feature/automation/AutomationToastTest.kt` | `AutomationBus` (行 39, 60, 61, 75, 106, 183, 184, 195, 197, 198, 199, 233, 234, 235, 279, 283, 284, 291, 292, 293, 312) | AutomationManager(helper) |
| `KhatKitApp.kt` | `AutomationBus` (行 30, 163, 164) | AutomationManager(helper) |
| `RouteActivity.kt` | `AutomationBus` (行 153, 243, 247) | AutomationManager(helper) |
| `core/data/ai/tools/KhatKitTools.kt` | `AutomationBus` (行 27, 243, 244, 271, 503, 650, 651, 825, 838, 857, 887, 893, 931, 1515, 1530, 1534, 1671, 1720, 1724, 1830, 1836, 1838, 1843, 1856, 1857, 1864, 1870) | AutomationManager(helper) |
| `core/ui/components/permission/PermissionChecklist.kt` | `AutomationBus` (行 54, 553, 572) | AutomationManager(helper) |
| `core/di/AppEntryPoint.kt` | `TriggerController` (行 24, 83) | TriggerManager(helper) |
| `core/di/AppHiltModule.kt` | `TriggerController` (行 39, 199) | TriggerManager(helper) |
| `feature/settings/SettingTriggersPage.kt` | `TriggerController` (行 49, 99) | TriggerManager(helper) |
| `feature/settings/TriggerCardEditor.kt` | `TriggerController` (行 48, 62) | TriggerManager(helper) |
| `feature/settings/SettingTriggersPage.kt` | `TriggerSettings` (行 52, 255) | TriggerSettingsRepository(data) |
| `feature/settings/SettingTriggersPage.kt` | `TriggerExactAlarmScheduler` (行 50, 129) | TriggerAlarmHelper(helper) |
| `feature/settings/TriggerCardEditor.kt` | `TriggerTilePublisher` (行 49, 543) | TriggerTileHelper(helper) |
| `core/data/ai/tools/KhatKitTools.kt` | `AutomationTracer` (行 28, 303, 2023, 2025) | AutomationTraceManager(helper) + TraceStep/TraceMeta/TraceRecord/UiActionDto/BBoxDto(data) |
| `core/data/ai/tools/KhatKitTools.kt` | `UiAutomationSnapshotProvider` (行 30, 1849) | UiAutomationSnapshotHelper(helper) |
| `core/data/ai/tools/KhatKitTools.kt` | `BusApprovalGate` (行 29, 504) | BusApprovalHelper(helper) |
| `core/data/ai/tools/KhatKitTools.kt` | `ScheduleBridgeImpl` (行 48, 288) | ScheduleBridgeHelper(helper) |
| `core/network/routes/EventsRoutes.kt` | `WorkflowEventBus` (行 16, 108) | WorkflowEventManager(workflow/helper) |
| `feature/settings/CreateWorkflowPage.kt` | `WorkflowSamples` (行 44, 162) | notificationClassifySample/linearClassifySample/repeatNotifySample(workflow/helper) |
| `feature/settings/CreateWorkflowPage.kt` | `WorkflowEngine` (行 41, 226) | executeWorkflow/exportWorkflowLua/interpretWorkflowScript/workflowFlowSpecJson/workflowCardName(workflow/helper) |
| `feature/settings/CreateWorkflowPage.kt` | `WorkflowLegacy` (行 43, 103, 165, 225, 285) | WorkflowDefinition.toVisualWorkflow()/VisualWorkflow.toStoredNodes()(workflow/helper) |
| `feature/settings/data/WorkflowPreferencesRepository.kt` | `WorkflowLegacy` (行 19) | WorkflowDefinition.toVisualWorkflow()/VisualWorkflow.toStoredNodes()(workflow/helper) |
| `feature/settings/helper/WorkflowTextHelper.kt` | `WorkflowLegacy` (行 7) | WorkflowDefinition.toVisualWorkflow()/VisualWorkflow.toStoredNodes()(workflow/helper) |

## 6. 真实未闭合项

1. **M31**：`feature/settings/data/WorkflowPreferencesRepository.kt`（`WorkflowNode`/`WorkflowDefinition`）
   仍在 settings；`workflow/data/WorkflowRepository.kt` 与 `workflow/helper/WorkflowLegacyExtensions.kt` 保留对它的
   import（迁移条件 `khatkit_workflows` / `graphJson` / 旧中文判定值不在本批改）。归最后 core 批次。
2. **M32**：`settings/CreateWorkflowPage.kt`、`settings/TriggerCardEditor.kt`、`settings/SettingTriggersPage.kt`
   仍是 settings 页面（Page 留根、导航 key 不变），因此不在本批移动；其依赖符号已就绪，接线见 §5。
3. **DI / 应用入口**：`core/di/AppHiltModule.kt`、`AppEntryPoint.kt`、`KhatKitApp.kt`、`RouteActivity.kt`、
   `core/ui/components/permission/PermissionChecklist.kt`、`core/data/ai/tools/KhatKitTools.kt`、
   `core/network/routes/EventsRoutes.kt` 仍引用旧符号 → 由最终引用集成任务统一改，`khatkit` 不受影响。
4. **androidTest**：`app/src/androidTest/.../AutomationToastTest.kt` 不在本批 inScope，待集成任务同步。
5. **录制链可达性**：app/src 仍无 `start/save/discard` 消费者（审计 O1），本批按「保留功能」分支只做分层，
   未删任何类、未动 manifest receiver；可达性结论需要产品裁定，不由本批删除。
6. **日志/异常中文**：`TriggerAlarmHelper` 的 log tag 仍叫 `TriggerExactAlarm`、`CardScheduleWorker` 的
   `Log.w` 文案不变（非用户可见、属 t7 既有候选），本批不改以免与资源批次交叉。

## 7. 验证

- `git diff --check` → 0（见任务 commandsRun）。
- 静态自检：三个包内旧符号引用 0；新文件花括号/字符串配平；所有 `import heizige...feature.{automation,workflow,record}.*`
  指向实际存在的包与顶层符号（扩展函数按 receiver 名单独核对）。
- **未运行** `./gradlew`、未 assemble、未碰设备；编译级验证归 verifier / 最终集成。
