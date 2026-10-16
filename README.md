# Undying

**Minecraft 1.20.1 · Forge 47.x · Java 17 · MIT**

---

## 适用场景 / Intended use

本模组适用于以下情况：

This mod is intended for the following situations:

- **还想继续长期游玩、或对你有特殊意义的存档，被其他模组压制成死档。** 某些模组的战斗数值强到「进入即死」，此时即使有存档备份也无法解决：读档后死亡仍然必然发生，世界无法继续。本模组通过移除死亡结果来解除这一状态，让这类存档重新可以游玩。
- **不敢玩恐怖模组，但又希望亲自体验。** 部分恐怖模组的演出效果很好，但玩家因害怕而无法推进流程。开启无敌模式后可以在不被打断的情况下完整看完演出。
- **反向碾压。** 单纯体验以绝对优势对抗原本难以应对的内容。

- **A save you still want to keep playing, or that matters to you, rendered unplayable by another
  mod.** Some mods reach combat values where entering the fight means certain death. A save backup
  does not solve this: after loading, the death still happens on schedule and the world cannot
  continue. This mod removes the death outcome, which makes such a save playable again.
- **Not daring to play a horror mod, but wanting to experience it first-hand.** Some horror mods
  are built around excellent set pieces, but the player cannot get through them out of fear. With
  immortal mode on, the sequence can be watched in full without interruption.
- **Reversed dominance.** Playing purely for the experience of overwhelming content that would
  otherwise be difficult.

**不适用于：**对战类玩法、希望保留正常难度的生存流程，以及任何联机场景。

**Not intended for:** competitive play, survival runs where the normal difficulty is the point, or
any multiplayer scenario.

这不是「存档修复工具」。它不修复损坏的区块、不恢复已丢失的实体数据，也不修复存档文件本身。它做的是在玩家死亡时回滚玩家状态，使其不产生死亡的后果。

This is not a save-repair tool. It does not repair corrupted chunks, recover lost entity data, or
repair the save files themselves. What it does is roll back player state when the player dies, so
that the death produces no consequence.

---

## 这是什么 / What this is

一个面向单人世界的「神权」模组：开启无敌模式后，玩家在该世界内不受死亡与伤害规则约束。

A single-player "god-mode" mod: with immortal mode enabled, the player is not subject to death
or damage rules in that world.

关闭无敌模式后，本模组不介入任何游戏逻辑，等同于正常单人游戏。

With immortal mode disabled, the mod does not intervene in any game logic and the world behaves
as an ordinary single-player game.

**本模组不支持联机。** 详见[联机](#联机--multiplayer)。

**This mod does not support multiplayer.** See [Multiplayer](#联机--multiplayer).

---

## 概述 / Overview

本模组由两组互相独立的功能组成：

This mod consists of two independent groups of features:

| 分组 / Group | 说明 / Description |
|---|---|
| 无敌模式 / Immortal mode | 在单人世界内解除死亡、伤害、物品栏与游戏模式约束。由总开关 `masterToggle` 统一控制<br>Lifts death, damage, inventory and game-mode constraints inside a single-player world. Controlled by the `masterToggle` master switch |
| 状态回滚 / State rollback | 定期备份玩家状态，并在检测到死亡或状态异常时回滚。不经过原版的 `hurt` / `setHealth` / `die` / `kill`，因此当其他模组改写了原版方法链时仍然可用<br>Periodically backs up player state and rolls it back when death or state corruption is detected. It does not go through vanilla's `hurt` / `setHealth` / `die` / `kill`, so it keeps working when another mod has rewritten that pipeline |

**总开关默认开启**（`masterToggle = true`）。设为 `false` 后模组不介入任何游戏逻辑。

**The master switch defaults to on** (`masterToggle = true`). Set it to `false` and the mod does
not intervene in any game logic.

---

## 无敌模式 / Immortal mode

### 开关 / Toggling

| 方式 / Method | 操作 / Action |
|---|---|
| 聊天框 / Chat | `toggle` 或 `无敌` / `toggle` or `无敌` |
| 指令 / Command | `/antigrief master` |
| 配置文件 / Config | `config/undying-common.toml` 中的 `masterToggle` / `masterToggle` in `config/undying-common.toml` |

开启后聊天栏会输出 `无敌模式：开启`，关闭时输出 `无敌模式：关闭`。

On enable the chat reports `无敌模式：开启`; on disable, `无敌模式：关闭`.

### 死亡锁 / Death lock

四层独立机制。任意一层生效即可阻止死亡。

Four independent mechanisms. Any one of them holding prevents a death.

| 层 / Layer | 机制 / Mechanism | 说明 / Notes |
|---|---|---|
| 1 | 拒绝伤害 / Damage refusal | 血量到达保底线时，该次伤害不被接受：不扣血、不触发事件、不产生击退<br>At the health floor the hit is not accepted: no damage, no event, no knockback |
| 2 | 血量闸门 / Health clamp | 血量同步数据的唯一写入口。任何把血量写到保底线以下的尝试都会被抬回<br>The only write path to synched health data; any attempt to write below the floor is pushed back up |
| 3 | `die()` 取消 / Cancellation | 取消死亡流程并回滚：清除死亡计时器、恢复血量、退还图腾效果<br>Cancels and rewinds the death pipeline: timer cleared, health restored, totem effect refunded |
| 4 | 移除拦截 / Removal interception | 拦截 `remove(KILLED)` 与 `kill()`<br>Intercepts `remove(KILLED)` and `kill()` |

**第 1 层采用「拒绝」而非「事后回血」。** 原版死亡动画（`tickDeath`）一旦启动，修改血量无法中止该流程，玩家会停留在服务端认为存活、但每 50 毫秒被尝试移除一次的状态。在伤害入口直接拒绝可以避免该状态。

**Layer 1 refuses rather than heals.** Once vanilla's death animation (`tickDeath`) has started,
editing health does not stop it; the player ends up in a state where the server considers them
alive while attempting removal every 50 ms. Refusing the damage at the entry point avoids this.

**第 4 层只拦截 `KILLED`，不拦截 `DISCARDED`。** `DISCARDED` 是原版换维度与重生流程的正常清理路径；拦截它会导致同一玩家在世界上出现两份实体，并在随后触发区块管理器空指针异常。

**Layer 4 intercepts `KILLED` only, never `DISCARDED`.** `DISCARDED` is vanilla's normal cleanup
path for dimension changes and respawns; intercepting it produces two entities for the same
player and subsequently a null-pointer exception in the chunk manager.

**解除死亡锁的方式：**`/antigrief die`，或聊天框自杀触发词。该路径不经过死亡锁，使其始终可用。

**Releasing the death lock:** `/antigrief die`, or the suicide chat trigger. This path bypasses
the death lock so that it remains available at all times.

### 隐藏槽位 / Hidden slots

三个玩家不可见、不可操作、不可通过指令移除的槽位。

Three slots that are not visible to the player, cannot be manipulated, and cannot be removed by
commands.

| 槽位 / Slot | 内容 / Contents | 行为 / Behaviour |
|---|---|---|
| 隐藏图腾 / Hidden totem | 不死图腾 / Totem of undying | 通过原版机制触发，事件、音效、粒子表现与原版一致<br>Triggered through vanilla's own mechanism; events, sound and particles behave as vanilla |
| 守护神盾 / Ward shield | 全伤害免疫 / Full damage immunity | 360° 伤害免疫，无限耐久<br>360° immunity to all damage, unlimited durability |
| 隐藏盔甲 / Hidden armour | 下界合金护甲值 / Netherite armour value | 不占用真实盔甲栏；护甲值以注入方式计入，等效整套下界合金(20)<br>Does not occupy the real armour bar; the value is injected, equivalent to a full netherite set (20) |

隐藏槽位内的附魔无法被原版读取，因此按原版公式单独结算：

Enchantments inside hidden slots are not readable by vanilla, so they are resolved separately
using vanilla formulas:

| 附魔 / Enchantment | 结算公式 / Formula |
|---|---|
| 保护 / Protection | 每级 4%，上限 80% / 4% per level, capped at 80% |
| 摔落保护 / Feather Falling | 每级 12%，上限 80% / 12% per level, capped at 80% |
| 荆棘 / Thorns | 同原版 `ThornsEnchantment` / Same as vanilla `ThornsEnchantment` |
| 水下呼吸 / Respiration | 氧气不消耗，持续补充 / Oxygen never consumed, continuously refilled |
| 水下速掘 / Aqua Affinity | 抵消水下挖掘的 5 倍减速 / Cancels the 5× underwater mining penalty |

未实现的附魔：迅捷潜行、深海探索者、灵魂疾行。这三项会改变移动手感与画面表现。

Not implemented: Swift Sneak, Depth Strider, Soul Speed. These change movement feel and visual
presentation.

### 隐藏装备发放 / Hidden gear grant

`hiddenGear` 开启时，每个存档首次进入世界发放一整套下界合金装备与工具。装备可正常脱下与丢弃。

With `hiddenGear` enabled, a full netherite set and tools are granted on first join per save. The
items can be removed and dropped normally.

### 虚空剑 / Void Sword

聊天框输入 `sword` 或 `取剑` 获取。

Obtained with the `sword` / `取剑` chat trigger.

| 操作 / Action | 效果 / Effect |
|---|---|
| 右键 / Right click | 清除周围 5 格内所有实体<br>Clears every entity within 5 blocks |
| 左键 / Left click | 对目标启动四层执行链：满伤害 → 强制移除 → 数据清空 → 和平模式<br>Starts the four-layer execution chain on the target: full damage → forced removal → data wipe → peaceful mode |

执行链每一层结束后都会校验目标是否已进入死亡流程；未进入则进入下一层。分层的原因是不同环境下被拦截的环节不同。

Each layer of the execution chain is followed by a check that the target entered the death
pipeline; if not, the next layer runs. The chain is layered because different environments
intercept different mechanisms.

执行链不对 `Player` 类型目标做特殊处理。早期版本会在此处互相忽略以规避 PvP，该行为可被「把实体注册为玩家类型」的模组利用为免死手段，因此已移除。

The chain applies no special handling to `Player`-type targets. An earlier version made two
players ignore each other here to avoid PvP; that behaviour could be exploited as a death
immunity by registering an entity as a player type, and was removed.

### 光环 / Aura

清除半径内的敌对生物。

Clears hostile mobs in a radius.

| 触发词 / Trigger | 效果 / Effect |
|---|---|
| `aura` / `光环` | 激活，限时 / Activate, timed |
| `aura on` / `光环常驻` | 切换为常驻 / Switch to permanent |
| `aura off` / `光环关闭` | 关闭 / Disable |

半径上限为 `Const.AURA_RANGE`。末影龙、凋灵与监守者不在清除范围内：在战斗中移除这些实体会中断世界进程。

The radius is capped at `Const.AURA_RANGE`. The Ender Dragon, Wither and Warden are excluded:
removing them mid-fight interrupts world progression.

`deathAura` 开启时自动模式常驻，无需手动触发。

With `deathAura` enabled the aura runs automatically without a manual trigger.

### 保命辅助 / Survival assistance

| 功能 / Feature | 说明 / Description |
|---|---|
| 虚空救援 / Void rescue | 玩家低于 y=-100 时传送回安全点。优先床或重生锚，未设置则回世界出生点，维度跟随玩家<br>Below y=-100, teleports to a safe point: bed or respawn anchor first, else world spawn, following the player's dimension |
| 出生保护 / Spawn protection | 进入世界或重生后短时间内完全免伤，时长由 `spawnProtectTicks` 控制，默认 120 tick<br>Brief full damage immunity after joining or respawning; duration set by `spawnProtectTicks`, default 120 ticks |
| 常驻增益 / Permanent effects | 吸收、再生、抗性提升、抗火<br>Absorption, Regeneration, Resistance, Fire Resistance |

常驻增益不含夜视与饱和：这两项会改变画面亮度并遮挡饥饿条。

Permanent effects exclude Night Vision and Saturation: both alter screen brightness and obscure
the hunger bar.

### 完整性守卫 / Integrity guard

每 2 秒核对配置开关是否被外部修改，若被修改则自动恢复并报告。

Every 2 seconds, verifies that config switches have not been modified externally; if they have,
restores them and reports.

比对范围不含 `damageBoost` 与 `deathAura`：这两项由玩家自行决定，修改它们不属于篡改。

The comparison excludes `damageBoost` and `deathAura`: these are player choices, and changing
them is not tampering.

---

## 状态回滚 / State rollback

### 存档急救包 / Save snapshot

| 配置项 / Option | 说明 / Description |
|---|---|
| `enabled` | 总开关 / Master switch |
| `autoRestore` | 检测到死亡或状态异常时自动回滚 / Roll back automatically when death or state corruption is detected |
| `intervalTicks` | 采集间隔，默认 100 tick（5 秒）/ Capture interval, default 100 ticks (5 seconds) |

玩家状态定期写入世界目录下的 `_undying_backup/<uuid>.dat`。检测到死亡或状态异常时，从该文件回滚。

Player state is periodically written to `_undying_backup/<uuid>.dat` inside the world directory.
When death or state corruption is detected, state is rolled back from that file.

**回滚的是玩家状态，不是存档。** 具体包括位置、血量、物品栏、经验等玩家自身数据。世界数据（区块、实体、方块）不在回滚范围内。

**What is rolled back is player state, not the save.** Specifically the player's own data: position,
health, inventory, experience. World data — chunks, entities, blocks — is not covered.

**该功能不经过原版的 `hurt` / `setHealth` / `die` / `kill`。** 其余防护均挂在原版方法调用链上；若该调用链被 coremod 整体改写，这些钩子会同时失效。存档急救包通过直接读写字段与文件实现，因此不受该情况影响。

**This feature does not go through vanilla's `hurt` / `setHealth` / `die` / `kill`.** Every other
protection hangs off vanilla's method call chain; if a coremod rewrites that chain wholesale,
those hooks fail together. The save snapshot reads and writes fields and files directly, so it is
unaffected.

**能力边界：**针对本类专门编写的 JVMTI 运行时 Agent 可以绕过该功能。本功能覆盖的范围是「coremod 改写原版方法」这一类情况，不构成绝对安全保证。

**Limitation:** a JVMTI runtime agent written specifically against this class can bypass it. This
feature covers the case of a coremod rewriting vanilla methods; it is not a guarantee of absolute
safety.

---

## 自检 / Self-check

自检以只读方式运行，包含 10 条探针，核对以下内容：

The self-check runs read-only and includes 10 probes, verifying:

1. 依赖的原版结构是否存在 / that the vanilla structures it depends on exist
2. mixin 是否已实际挂载 / that its mixins are actually attached
3. 防护总开关是否处于开启状态 / whether the protection master switch is on

结果会明确输出，包括结果为「防护当前未生效」的情况。

The result is reported explicitly, including when the result is "protection is not currently
active".

---

## 联机 / Multiplayer

**本模组不工作于联机环境，并主动阻止自身被用于联机。**

**This mod does not work in multiplayer and actively prevents itself from being used there.**

原因不是策略选择，而是功能本身：该模组解除死亡与伤害规则，安装到服务器上会使该服务器上所有玩家同时获得该状态。

The reason is not a policy choice but the feature itself: the mod lifts death and damage rules,
so installing it on a server grants that state to every player on that server.

两层互相独立的机制：

Two independent mechanisms:

### 1. 客户端入口拦截 / Client-side entrance ban

主菜单的**多人游戏**按钮与暂停菜单的**对局域网开放**按钮被设为不可用状态，并附带说明原因的悬浮提示。

The **Multiplayer** button on the title screen and the **Open to LAN** button in the pause menu
are set to a disabled state, with a tooltip stating the reason.

匹配依据是**翻译键**（`menu.multiplayer`、`menu.shareToLan`），不是显示文本。按显示文本匹配会在非英文客户端上失效。

Matching is by **translation key** (`menu.multiplayer`, `menu.shareToLan`), not by displayed text.
Matching displayed text fails on any non-English client.

### 2. 功能入口门禁 / Feature entry-point gate

`Environment.isAllowed(...)` 在 19 处被调用，分布在 13 个文件中：聊天触发词、进入世界、每 tick 任务、指令、物品交互，以及所有可作用于 `ServerPlayer` 的 mixin 注入点。

`Environment.isAllowed(...)` is consulted at 19 call sites across 13 files: chat triggers, world
join, per-tick work, commands, item interaction, and every mixin injector that can affect a
`ServerPlayer`.

| 环境 / Environment | 行为 / Behaviour |
|---|---|
| 单人游戏 / Single player | 正常工作 / Works normally |
| 开放到局域网 / Opened to LAN | 全部禁用 / All features disabled |
| 加入远程服务器 / Joined a remote server | 全部禁用 / All features disabled |
| 专用服务器 / Dedicated server | 全部禁用 / All features disabled |

**判定失败时按「已拦截」处理。** 环境无法判定时门禁返回禁用。在单人环境下被误判禁用的后果是功能不可用；在服务器上被误判开启的后果是该服务器所有玩家获得不死状态。两者的代价不对称。

**Undetermined environments are treated as blocked.** If the environment cannot be determined,
the gate returns "disabled". A false positive in single-player means the mod does not work; a
false negative on a server means every player gains invulnerability. The costs are not symmetric.

**门禁不会使服务器崩溃。** 判定为联机时，模组禁用全部功能，并在日志与聊天栏各输出一次说明。

**The gate does not crash a server.** When multiplayer is detected, the mod disables all features
and writes one explanation to the log and one to chat.

两层机制覆盖范围不同，不可互相替代：入口拦截阻止玩家进入联机界面；门禁处理「模组已被安装到服务器上」的情况。两者的失败方向也不同：入口拦截失败时保持按钮可用（不完整的界面比未拦截的按钮影响更大），门禁失败时按禁用处理。

The two mechanisms cover different cases and neither replaces the other: the entrance ban stops a
player from reaching the multiplayer UI; the gate handles the case where the mod has already been
installed on a server. Their failure directions also differ: the entrance ban fails open (a
broken UI is more disruptive than an un-banned button), while the gate fails closed.

---

## 安装 / Installation

1. 为 Minecraft 1.20.1 安装 Forge 47.x
2. 将 jar 放入 `mods` 目录

无需其他前置。模组为客户端完整，仅安装在客户端即可使用全部单人功能。

1. Install Forge 47.x for Minecraft 1.20.1
2. Place the jar in your `mods` directory

No other dependencies. The mod is client-side complete: installing it on the client alone
provides the full range of single-player functionality.

---

## 构建 / Building

需要 JDK 17。 / Requires JDK 17.

```bash
./gradlew build     # 产物在 build/libs/ / output in build/libs/
./gradlew test      # 单元测试 / unit tests
```

单元测试仅覆盖纯逻辑：环境门禁、联机入口拦截、审计比对。测试不将 Forge 工具链放入 classpath，因此无需预先搭建游戏环境。

Unit tests cover pure logic only: the environment gate, the multiplayer entrance ban, and audit
comparison. The Forge toolchain is deliberately excluded from the test classpath, so tests run
without provisioning the game.

---

## 实现约定 / Implementation notes

### Mixin 目标名称 / Mixin target names

所有 mixin 的注入目标使用 SRG 名（如 `m_142416_`、`f_120018_`）并指定 `remap = false`。本模组不包含 refmap。在注入目标中使用开发环境名称会编译通过，但在运行时静默不生效。

All mixin injection targets use SRG names (for example `m_142416_`, `f_120018_`) with
`remap = false`. The mod ships no refmap. Using a development-environment name in an injection
target compiles successfully but silently fails to apply at runtime.

### 注入位置 / Injection point

注入点应选在最外层方法。若调用者在调用返回后仍会修改该对象，则被调用方法内部的任何注入点都不会生效。

Injection points are chosen at the outermost method. If a caller continues to modify the object
after the call returns, no injection point inside the callee takes effect.

### 注释 / Comments

源码注释记录设计原因与已修复缺陷的成因，不描述代码功能。多个已发布缺陷的成因写在修复它们的代码旁。

Source comments record design rationale and the causes of fixed defects; they do not describe what
the code does. The causes of several shipped defects are written next to the code that fixed them.

---

## 作者 / Authors

ShadowGraff, DeepSeek

---

## 商标声明 / Trademark notice

**DeepSeek 是杭州深度求索人工智能基础技术研究有限公司的商标。**

本项目在作者署名与开发说明中提及该名称，**仅用于说明本项目所调用的 AI 服务的来源**。此提及**不代表深度求索公司对本项目的任何赞助、认可、担保或官方关联**；本项目为个人独立作品，与深度求索公司无任何隶属或合作关系。

如需了解该服务的相关条款，请参阅 [DeepSeek 用户协议](https://cdn.deepseek.com/policies/zh-CN/deepseek-terms-of-use.html)。

**DeepSeek is a trademark of Hangzhou DeepSeek Artificial Intelligence Basic Technology Research
Co., Ltd.**

The name is referenced in this project's authorship line and development note **solely to identify
the source of the AI service used**. This reference **does not imply any sponsorship, endorsement,
warranty, or official affiliation with DeepSeek**; this project is an independent work by an
individual and has no affiliation or partnership with the company.

For the service's own terms, see the [DeepSeek Terms of Use](https://cdn.deepseek.com/policies/en-US/deepseek-terms-of-use.html).

---

## 开发说明 / Development note

本 README 由 AI 生成。模组本身同样如此：**全部实现代码由 AI 编写**，而**灵感、大部分设计，以及全部 BUG 测试均来自人类开发者**。

This README was generated by an AI. So was the mod itself: **every line of implementation code
was written by an AI**, while **the idea, most of the design, and all bug testing came from human
developers**.

具体分工：

The division of labour:

| 环节 / Stage | 负责方 / Owner |
|---|---|
| 灵感与目标 / Idea and goals | 人类 / Human |
| 大部分设计 / Most of the design | 人类 / Human |
| 实现代码 / Implementation code | AI（DeepSeek，见[商标声明](#商标声明--trademark-notice)）/ AI (DeepSeek — see the [trademark notice](#商标声明--trademark-notice)) |
| BUG 测试 / Bug testing | 人类 / Human |
| 日志分析与修复 / Log analysis and fixes | AI |

文中关于行为、数值与实现细节的描述均以源代码为依据。如有不符，以源代码为准。

Statements here about behaviour, values and implementation details are based on the source code.
Where they disagree, the source code is authoritative.
