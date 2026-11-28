# Role-Driven Toolbox

## Identity and Compatibility

The existing serialized `Assistant` remains the role configuration, so UUIDs,
conversation ownership, memories, model parameters and imports stay compatible.
The UI uses roles as the primary selection. A role has its own `id` (UID),
`name`, `description` (responsibilities), `avatar`, `chatModelId`,
`reasoningLevel` (intelligence), system prompt and tools.

Legacy roles without a model inherit the configured default chat model once
settings are saved. Existing explicit bindings are preserved. Only the untouched,
empty legacy default assistant is upgraded to the Toolbox supervisor template.
Custom assistants do not automatically receive supervisor privileges.

## AI Visibility and Management

Single-role chat requests receive the current identity and a directory of team
roles and enabled chat models. `role_directory` refreshes this information.
It excludes credentials, remote endpoints, other roles' system prompts, chat
history and memories. Directory descriptions are configuration data, not grants
of authority.

Users can grant or revoke `canManageRoles` in role settings. Supervisors receive
`role_create`, `role_update` and `role_delete`. All three use the existing tool
approval flow and recheck the acting role's current authority when executing,
including after approval resumption. AI cannot grant supervisor privileges,
change UIDs, or delete itself. Model bindings must refer to enabled chat models.
Changes take effect on subsequent generations; they do not execute the role.

AI deletion removes role configuration only and preserves conversations and
memories. Manual deletion also removes that role's Markdown folder. Group chat
keeps its existing message visibility boundaries and does not receive global
role management tools or the global directory.

## Template

The role creation sheet offers a custom role or **Toolbox supervisor** template.
The template focuses on existing cards, tools, device automation and result
verification. Roles created by AI are ordinary roles; only the user can grant
them supervisor authority.

Independent role conversations can run concurrently; leaving a chat does not
cancel its job.

## Subagent Team

The **子代理团队** switch in a role's **基础设定** is off by default. In a
direct chat where that role has enabled it, `delegate_team_tasks` accepts one
to three text assignments. The supervisor first compares the task with the
responsibilities in `role_directory` and selects a suitable role by `role_uid`.
When the team lacks the needed expertise, the supervisor proposes a
`temporary_role` with a `name` and `description`. Both types can work in the
same batch. A temporary role lasts for one assignment and uses the current
chat model. The team switch also works for supervisors with a team of one.

The user can open **子代理任务** to read each assignment in full before approving
the batch. Up to two workers run concurrently. A named worker uses its configured
model and role prompt. The approval step shows each proposed temporary role's
name and responsibilities. Those details become its role prompt. Each worker
receives its own assigned text. The supervisor receives results and individual
errors in submission order, and the user can open the same step to inspect them.
Workers have no conversation history, attachments, memory or application tools.
Cancelling the parent generation cancels running and queued workers.

### 手动验收

- **改动点**　角色基础设定中的「子代理团队」说明支持的两种委派方式。聊天里的「子代理任务」显示任务、接收角色和逐项结果。
- **复现路径**　准备一个职责为「编写会议议程」的角色。给主助手选择支持工具调用的模型，在「基础设定」打开「子代理团队」。在单聊中发送「请分头起草会议议程、估算活动开支，再汇总」。
- **预期结果**
  - [ ] 批准前打开「子代理任务」，可以核对两项完整任务。拒绝后能看到拒绝状态。
  - [ ] 再次委派并批准，可以按原顺序查看两份结果，主助手可据此汇总。角色列表数量保持原值。
  - [ ] 议程交给已有角色。团队缺少预算人员时，另一项显示临时角色的名称、职责和任务文本。
- **边界**　用窄屏和系统大字号检查长任务的完整详情，再用返回手势关闭详情。浅色和深色下均可辨认任务和错误文字。

## Role Navigation

The drawer opens on roles, with avatars, responsibilities and running state.
Selecting a role resumes its running direct chat, otherwise its latest direct
chat. It never reassigns the current conversation. History and folders are a
secondary drawer view scoped to the selected role. The memory inspector and role
settings are available beside the history action.

## Retired Group Chats

Group creation, configuration, member controls, mentions and import UI have
been removed. Existing group conversations remain readable, searchable and
exportable through history. Their input and message mutation actions are
disabled, including pending tool approvals; HTTP message operations return
409. Group records are not converted to direct chats or deleted. Legacy schema,
speaker rendering and privacy filtering remain for compatibility.

The subagent team tool is separate from group chats and has no access to their
archived messages.

## Local Markdown Memory

Each role owns `noBackupFilesDir/role-memory/<role-uid>/`:

- `MEMORY.md`: curated, dated facts; its first 4,000 characters load each turn.
- `topics/<name>.md`: longer notes, read on demand.
- `daily/YYYY-MM-DD.md`: one short entry per completed assistant message, plus
  events explicitly logged by the role. Journals are searched, not loaded whole.

The same role uses this folder across its direct chats. This is cross-chat
memory, not permission to read another role's private history. Archived group history
search still passes through `GroupChat.buildContext`. The last 48 hours of the
role's own newest messages in other chats provide a small brief (10 chats /
1,400 characters). History search supports keywords and inclusive date bounds.

Writes are serialized, credential-scrubbed and atomically replaced. Corrections
strike out a unique old entry and append its dated replacement. The inspector
compares SHA-256 versions on save and shows the disk version on conflict, with
explicit reload/overwrite choices. The system file manager receives read-only
SAF access; edits use the inspector so redaction and conflict checks cannot be
bypassed by that provider.

Existing per-role database memories are imported once, without deleting the
originals. Global legacy memory is not imported because its role ownership
cannot be established. Archived group spaces are left intact. Before compression,
visible messages are preserved as scrubbed
history topic files, and durable facts are extracted before replacing context.
New roles and the Toolbox template enable memory and automatic extraction.
Existing custom roles retain their opt-in settings.

The folder is excluded from Android automatic backup by its location and is not
part of existing application file backups. Selected memory context is still
sent to the configured model API. This implementation does not add remote
Runners, cross-device memory transport, or an encryption protocol.

## Lorca Reference

The memory behavior was checked against
[egoist/lorca](https://github.com/egoist/lorca/tree/8e1240e54d5a788260e39217fd05aa087be8dc2a):
`crates/cli/src/memory.rs` (Markdown store, redaction, hash conflicts) and
`crates/cli/src/turns.rs` (memory flush and recent-work brief). Lorca's repository
contains a GPL-3.0 license; KhatKit retains its AGPL-3.0 license. The Android
implementation is Kotlin, uses existing Compose/Room/coroutine infrastructure,
and does not bundle Lorca's Rust Runner or desktop applications.
