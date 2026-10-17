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

An independent AI coding workspace and autonomous inter-role task delegation are
not part of this change. Independent role conversations can run concurrently;
leaving a chat does not cancel its job.

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

Supervisor tools manage role definitions, not task delegation. Dispatching,
waiting for or cancelling another role's work is not implemented by removing
group chats. Independent direct role chats can still run concurrently.

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
