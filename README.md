# Undying

**Minecraft 1.20.1 · Forge 47.x · Java 17 · MIT**

---

## 功能一览 / Feature list

一眼看清这个模组能做什么。每一项的细节见下文对应章节。

Everything this mod does, at a glance. Each item is detailed in its own section below.

**不死与伤害 / Not dying**

| 功能 / Feature | 作用 / What it does |
|---|---|
| 死亡锁 / Death lock | 沿原版死亡流程逐环拦截，从伤害入口到实体移除 / Intercepts every stage of vanilla's death pipeline |
| 健康闸门 / Health gate | 血量不会被写成 0，包括绕过 `hurt()` 的直接写入 / Health can never be written as 0, including direct writes that skip `hurt()` |
| 隐藏槽位 / Hidden slots | 隐形图腾、360° 守护神盾、隐藏盔甲，玩家不可见不可丢 / Invisible totem, ward shield, hidden armour — unseen and undroppable |
| 反篡改 / Anti-tamper | 拦截有害状态效果与大额经验扣除 / Blocks harmful effects and large XP drains |
| 传送校验 / Teleport guard | 每次传送先拦下，弹窗显示目的地与来源并暂停游戏询问 / Every teleport is held and shown as a question |
| 飞行 / Flight | 不朽模式下双击空格起飞，再双击落地 / Double-tap jump to fly while immortal |
| 物品栏预览 / Inventory preview | 长按 `I` 冻结游戏，列出背包自上次查看以来的变化 / Hold `I` to list what changed |
| 快捷控制 / Quick controls | O 面板里直接设置时间、天气、游戏模式 / Time, weather and game mode from the panel |
| 指令审计 / Command audit | 左上角小窗显示每条执行的指令，常见指令翻成一句话 / Every command, translated |
| 透视 / Outline | 隔墙显示生物轮廓，可选目标、时长、半径与碰撞箱 / See creatures through walls, with target, duration and hitbox options |
| 出生保护 / Spawn protection | 进入世界后短暂无敌 / Brief invulnerability after entering the world |

**物品与进度 / Items and progress**

| 功能 / Feature | 作用 / What it does |
|---|---|
| 状态回滚 / State rollback | 定期快照玩家状态，死亡或异常时还原；不经过原版方法链 / Periodic snapshots restored on death or corruption, bypassing the vanilla chain |
| 物品栏快照 / Inventory guard | 一次变动 5 格以上且无合理原因时，左下角询问是否回滚（Y×3 / N×3，15 秒不作答即忽略）/ Asks in the corner when 5+ slots change with no cause; Y×3 to roll back, silent timeout means do nothing |

**环境与生物 / Environment and mobs**

| 功能 / Feature | 作用 / What it does |
|---|---|
| 虚空剑 / Void sword | 右键清场，左键四层处决链 / Area clear on right click, four-layer execution on attack |
| 死亡光环 / Death aura | 清除周围敌对生物 / Clears hostile mobs nearby |
| 领域 / Domain | 推开不在放行名单内的生物——**不杀死、不掉落** / Pushes mobs away without killing them |
| 虚空救援 / Void rescue | 掉入虚空时传送回来 / Teleports the player back out of the void |
| 常驻增益 / Permanent buffs | 吸收、再生、抗性、抗火 / Absorption, regeneration, resistance, fire resistance |

**诊断与报告 / Diagnostics**

| 功能 / Feature | 作用 / What it does |
|---|---|
| 自检 / Self-check | 10 项探针，回答「钩子还在不在」/ 10 probes answering "is the hook still attached" |
| 完整性守卫 / Integrity guard | 定时核对配置与隐藏槽位，被改动则修复 / Periodically verifies and repairs config and hidden slots |
| 模组扫描 / Mod scan | 5 个维度，含原版资源覆盖检测 / Five dimensions including vanilla resource overrides |
| 滚动报告 / Rolling report | 7 份 × 12000 字，永不无限增长 / Seven files of 12000 characters, never unbounded |
| 呈现控制 / Presentation prompt | 进世界询问是否启用扫描 / Asks once, on world entry, whether to enable the scan |

**界面 / Interface**

| 功能 / Feature | 作用 / What it does |
|---|---|
| 配置界面 / Config screen | `O` 键打开，25 个开关，另有领域白名单、透视、快捷控制三页；键位可在「控制 → 按键绑定」里改 / Opened with `O`, twenty-five switches plus three sub-pages |
| 领域白名单 / Domain allow-list | 按类别勾选 + 按实体搜索勾选 / Category toggles plus a searchable entity list |
| 语言 / Language | 界面随游戏语言切换；聊天中英并排；配置注释双语 / GUI follows the game language; chat shows both; bilingual config comments |
| 物品栏提示 / Inventory prompt | 左下角非阻塞面板，`Y×3` 回滚 / `N×3` 忽略 / Non-blocking corner panel, `Y×3` to roll back, `N×3` to ignore |

**联机防护 / Multiplayer**

| 功能 / Feature | 作用 / What it does |
|---|---|
| 两层封堵 / Two independent layers | 客户端按钮置灰（每 tick 复核）+ 双端环境门禁 / Buttons disabled and re-checked every tick, plus a dual-side environment gate |

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

### 规模一览 / At a glance

防护不是靠一个开关生效，而是分布在多个独立环节上。当前实现的规模：

Protection does not rest on a single switch; it is spread across many independent stages. The
current implementation:

| 项目 / Item | 数量 / Count |
|---|---|
| Mixin 类 / Mixin classes | 14 |
| 注入点 / Injection points | 22 |
| 其中可取消原版流程 / Of which cancellable | 15 |
| 死亡锁拦截点 / Death-lock interception points | 11 |
| 环境门禁调用点 / Environment gate call sites | 27（分布 18 个文件 / across 18 files） |
| 自检探针 / Self-check probes | 10 |
| 主要类 / Main classes | 84（不含 14 个 mixin / excluding the 14 mixins） |
| 单元测试 / Unit tests | 118（13 个测试类 / 13 test classes） |

**一个死亡要真正发生，需要上述拦截点同时失效。** 每个点都在代码里独立实现，不共享同一个开关。

**For a death to actually happen, all of those interception points must fail together.** Each is
implemented independently in code and does not share a single switch.

---

## 无敌模式 / Immortal mode

### 开关 / Toggling

| 方式 / Method | 操作 / Action |
|---|---|
| 图形界面 / GUI | `O` 键打开（可在「控制 → 按键绑定」改键）/ `O`, rebindable in Controls → Key Binds |
| 聊天框 / Chat | `toggle` 或 `无敌` / `toggle` or `无敌` |
| 指令 / Command | `/antigrief master` |
| 配置文件 / Config | `config/undying-common.toml` 中的 `masterToggle` / `masterToggle` in `config/undying-common.toml` |

开启后聊天栏会输出 `无敌模式：开启`，关闭时输出 `无敌模式：关闭`。

On enable the chat reports `无敌模式：开启`; on disable, `无敌模式：关闭`.

**总开关的语义 / What the master switch means**：关掉总开关会连同它的全部子项一起关闭；关掉任意一个**子项**只影响那一项，总开关和其他子项保持原状。打开任何一项都不会隐式打开总开关——「我开了光环」不应该等于「我交出了无敌」。

Turning the master switch off turns every switch off with it. Turning a **single child** off affects only that child: the master and the others keep their state. Turning something on never enables the master implicitly — "I turned the aura on" must not quietly mean "I handed out invulnerability".

### 死亡锁 / Death lock

死亡锁不是一个开关，而是**沿原版死亡流程分布的拦截点**。从「伤害进入」到「实体被移除」，原版有五个必经环节，本模组在每一环都留有独立的拦截。

The death lock is not a single switch but **a set of interception points distributed along vanilla's death pipeline**. From "damage enters" to "entity is removed" vanilla has five mandatory stages; this mod intercepts at every one of them.

先看拦截点的位置 —— 这是它为什么难被绕过的原因：

Here is where the interception points sit — this is why it is hard to route around:

| # | 拦截点 / Interception point | 目标方法 / Target | 类型 / Kind |
|---|---|---|---|
| 1 | 血量下限（拒绝致死伤害）/ Lethal damage refusal | `LivingEntity.hurt` | `@Inject` HEAD，可取消 |
| 2 | 守护神盾 / Ward shield | `LivingEntity.hurt` | `@Inject` HEAD，全伤害免疫，可取消 |
| 3 | 血量写入下限 / Health floor on write | `LivingEntity.setHealth` | `@ModifyVariable` |
| 4 | 血量同步写入闸门 / Synched health write gate | `SynchedEntityData.set` | `@ModifyVariable` |
| 5 | 血量同步写入闸门（内部写入口）/ Internal write gate | `SynchedEntityData.set` | `@ModifyVariable` |
| 6 | 死亡流程 / Death pipeline | `LivingEntity.die` | `@Inject` HEAD，可取消 |
| 7 | `kill()` 拦截 / `kill()` interception | `Entity.kill` | `@Inject` HEAD，可取消 |
| 8 | 移除拦截 / Removal interception | `Entity.remove` | `@Inject` HEAD，可取消 |
| 9 | 隐藏盔甲 / Hidden armour | `LivingEntity.getArmorValue` | `@Inject` RETURN，可取消 |
| 10 | 图腾补挂 / Totem injection | `LivingEntity.checkTotemDeathProtection` | `@Inject` HEAD |
| 11 | 图腾归还 / Totem restore | `LivingEntity.checkTotemDeathProtection` | `@Inject` RETURN |

| # | 拦截点 / Interception point | 目标方法 / Target | 类型 / Kind |
|---|---|---|---|
| 1 | Lethal damage refusal | `LivingEntity.hurt` | `@Inject` HEAD, cancellable |
| 2 | Ward shield | `LivingEntity.hurt` | `@Inject` HEAD, full immunity, cancellable |
| 3 | Health floor on write | `LivingEntity.setHealth` | `@ModifyVariable` |
| 4 | Synched health write gate | `SynchedEntityData.set` | `@ModifyVariable` |
| 5 | Internal synched write gate | `SynchedEntityData.set` | `@ModifyVariable` |
| 6 | Death pipeline | `LivingEntity.die` | `@Inject` HEAD, cancellable |
| 7 | `kill()` interception | `Entity.kill` | `@Inject` HEAD, cancellable |
| 8 | Removal interception | `Entity.remove` | `@Inject` HEAD, cancellable |
| 9 | Hidden armour | `LivingEntity.getArmorValue` | `@Inject` RETURN, cancellable |
| 10 | Totem injection | `LivingEntity.checkTotemDeathProtection` | `@Inject` HEAD |
| 11 | Totem restore | `LivingEntity.checkTotemDeathProtection` | `@Inject` RETURN |

**11 个注入点，其中 6 个可取消原版流程（第 1、2、6、7、8、9 行）。** 另有 3 处 `@ModifyVariable`（第 3、4、5 行）改写传入的血量值，属于改值而非取消，不计入。要真正杀死玩家，这些点必须**同时**失效 —— 这就是「四层机制」背后的实际规模。

**11 injection points, 6 of which can cancel vanilla's flow (rows 1, 2, 6, 7, 8, 9).** The three
`@ModifyVariable` points (rows 3, 4, 5) rewrite the incoming health value rather than cancelling
the call, so they are not counted as cancellable. For a death to succeed, all of them must fail
*simultaneously* — that is the actual scale behind "four mechanisms".

下面是这些拦截点**各自负责什么**：

Below is **what each of them is responsible for**:

| 层 / Layer | 机制 / Mechanism | 说明 / Notes |
|---|---|---|
| 1 | 拒绝伤害 / Damage refusal | 血量到达保底线时，该次伤害不被接受：不扣血、不触发事件、不产生击退<br>At the health floor the hit is not accepted: no damage, no event, no knockback |
| 2 | 血量闸门 / Health clamp | 血量同步数据的两个写入口。任何把血量写到保底线以下的尝试都会被抬回<br>Both write paths to synched health data; any attempt to write below the floor is pushed back up |
| 3 | `die()` 取消 / Cancellation | 取消死亡流程并回滚：清除死亡计时器、恢复血量、退还图腾效果<br>Cancels and rewinds the death pipeline: timer cleared, health restored, totem effect refunded |
| 4 | 移除拦截 / Removal interception | 拦截 `remove(KILLED)` 与 `kill()`；`DISCARDED` 一律放行<br>Intercepts `remove(KILLED)` and `kill()`; `DISCARDED` is always let through |

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
| **`/kill` 拦截 / `kill()` command interception** | 拦截 `/kill` 指令对玩家生效的路径<br>Intercepts the path by which `/kill` acts on the player |
| **有害药水拦截 / Harmful effect interception** | 拦截外部施加的有害状态效果（如瞬间伤害、凋零）<br>Intercepts externally applied harmful effects (e.g. instant damage, wither) |
| **经验扣除拦截 / XP drain interception** | 拦截单次大额经验扣除<br>Intercepts a single large experience drain |
| **掉落伤害减免 / Fall damage reduction** | 由隐藏装备的摔落保护结算，每级 12%，上限 80%<br>Resolved through the hidden gear's Feather Falling, 12% per level, capped at 80% |
| **荆棘反伤 / Thorns reflection** | 由隐藏装备的荆棘结算，同原版公式<br>Resolved through the hidden gear's Thorns, same formula as vanilla |
| **游戏模式锁定 / Game-mode lock** | 阻止游戏模式被外部改为创造/旁观<br>Prevents the game mode being changed externally to creative/spectator |
| **OP 保持 / OP retention** | 单人世界中保持 OP 权限不被剥夺<br>Keeps OP status from being revoked in single-player |
| **创造权限授予 / Creative permissions** | 单人世界自动获得 OP<br>Grants OP automatically in single-player |

**这些拦截之所以需要，是因为「不死」不等于「不损失」。** 一个玩家可以不掉血但被清空经验、被塞满有害药水、被改成旁观模式 —— 本模组把这些路径一并封住。

**These interceptions exist because "not dying" is not the same as "not losing".** A player can keep their health while having their XP wiped, being loaded with harmful effects, or being switched to spectator mode — this mod closes those paths as well.

常驻增益不含夜视与饱和：这两项会改变画面亮度并遮挡饥饿条。

Permanent effects exclude Night Vision and Saturation: both alter screen brightness and obscure
the hunger bar.

### 反篡改 / Anti-tamper

| 拦截 / Interception | 说明 / Description |
|---|---|
| 有害状态效果 / Harmful effects | `MobEffectInstance` 施加有害效果时拦截<br>Intercepts when a harmful `MobEffectInstance` is applied |
| 大额经验扣除 / Large XP drain | 单次扣除超过阈值的经验时拦截<br>Intercepts when a single drain exceeds the threshold |

**只对 `ServerPlayer` 生效，且只在无敌模式开启时。** 其他实体、以及无敌模式关闭时，行为与原版一致。

**Applies only to `ServerPlayer`, and only while immortal mode is on.** Other entities, and the periods when immortal mode is off, behave exactly as vanilla.

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

写入采用「先写 `.tmp` 再原子改名」，不会原地覆盖；上一份快照保留为 `<uuid>.dat.bak`，主文件读不出来或内容无效时自动回退到它。回滚成功后下一次采集会强制重新落盘，让损坏的主文件自愈。`.dat` 与 `.bak` 均为标准压缩 NBT，可用现成 NBT 工具直接打开、手动回滚。

Player state is periodically written to `_undying_backup/<uuid>.dat` inside the world directory.
When death or state corruption is detected, state is rolled back from that file.

Writes go to a `.tmp` file and are then renamed into place, never overwritten in situ; the previous
snapshot is kept as `<uuid>.dat.bak`, and is used automatically when the primary cannot be parsed
or is not a valid snapshot. After a restore the next capture always rewrites the primary, so a
damaged file heals itself. Both `.dat` and `.bak` are standard compressed NBT and can be opened
with off-the-shelf NBT tools for a manual rollback.

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

## 传送校验 / Teleport guard

**每一次传送都先问一句。** 拦下来，告诉你它想去哪、是谁发起的，然后由你决定。

**Every teleport is asked about.** It is stopped, shown to you — where, and who asked — and left to you to decide.

| 情形 / Case | 行为 / Behaviour |
|---|---|
| 你在聊天框敲 `/tp` | **也会问**，来源显示「你敲的命令」 / also asked; the origin reads "your command" |
| 命令方块、数据包函数、服务器控制台发起 | **拦下**，来源显示命令原文 / held; the origin shows the command |
| 其他模组直接调用 `teleportTo` | **拦下**，来源显示「其他模组」 / held; the origin reads "another mod" |
| 你点了「允许」 | 执行原样的传送 / the teleport is performed as it was meant to be |

弹窗会**暂停游戏**（单人世界），显示维度、精确坐标和来源。按 ESC 等同于「留在这里」。

The prompt **pauses the game** in single-player and shows the dimension, the exact coordinates and the origin. ESC counts as "stay here".

### 为什么连自己敲的 `/tp` 也要问 / Why even your own `/tp` is asked about

早先的版本想放行玩家自己敲的 `/tp`：在命令入口记一个「本 tick 内、是否玩家发起」的标记，传送时比对。**实测里它判错了** —— 一次聊天框里敲的 `/tp` 被当成模组行为拦下，而这个标记为什么失效，看代码看不出来。

所以判据被整个删掉了。留着一条"平时对、偶尔错"的规则，比一条枯燥但不会错的规则更危险。代价是一下按钮：**弹窗冻结游戏、显示目的地、并把命令原文回显给你**，你自己敲的东西一眼就能认出来，不存在赶时间的问题。

The earlier version let a player-typed `/tp` through, using a marker recorded at the command entry (valid within the same tick, carrying whether a player issued it). **It was measured to be wrong**: a `/tp` typed into chat was intercepted as if a mod had sent it, and the reason the marker failed was not visible in the code.

So the judgement is gone. A rule that is usually right and occasionally wrong is more dangerous than a boring rule that is never wrong. The cost is one button press: the prompt freezes the game, shows the destination, and echoes the command back, so your own command is recognisable at a glance.

### 做到哪一步 / Where it stops

只挂两个入口：`ServerPlayer.teleportTo(ServerLevel, ..., Set<RelativeMovement>, ...)`（`/tp` 与多数模组走这里）和 `ServerPlayer.teleportTo(ServerLevel, x, y, z, yRot, xRot)`。

Only two entry points are hooked: `ServerPlayer.teleportTo(ServerLevel, ..., Set<RelativeMovement>, ...)` — what `/tp` and most mods use — and `ServerPlayer.teleportTo(ServerLevel, x, y, z, yRot, xRot)`.

**不拦**（写在这里，而不是等玩家掉进虚空才发现）：末影珍珠、紫颂果、下界传送门、`setPos` / `moveTo` / `changeDimension`、以及三参数 `teleportTo(x, y, z)`。前几个是原版机制，拦了等于把游戏弄坏；后几个是「模组绕过校验」的已知缺口。

顺带一个实测过的结论：**重生和过传送门都不会弹窗**——`PlayerList.respawn` 是新建一个玩家实体，`changeDimension` 是移除再添加，两者都不调用被挂的那两个方法。唯一多出来的一处是旁观者「传送到实体」（`handleTeleportToEntityPacket`），那是玩家自己点的，问一次也合理。

**Not covered** (written down here rather than discovered by a player who fell into the void): ender pearls, chorus fruit, nether portals, `setPos` / `moveTo` / `changeDimension`, and the three-argument `teleportTo(x, y, z)`. The first group are vanilla mechanics where interception would be felt as breakage; the second is the known gap through which a mod could bypass the check.

Measured, not assumed: **respawn and portal travel never prompt** — `PlayerList.respawn` constructs a new player entity and `changeDimension` removes and re-adds, and neither calls the two hooked methods. The one extra path is the spectator "teleport to entity" packet, which the player triggers by hand.

配置项：`[undying.teleportGuard] enabled`，默认开启。和本模组其它功能一样，只在单人世界生效。

Config: `[undying.teleportGuard] enabled`, on by default. Like everything else here, single-player only.

---

## 透视 / Outline

**隔墙看见生物。** 用的是原版发光效果，轮廓由游戏自己绘制——遮挡、距离和光影都按正常规则走。

**See creatures through walls.** Vanilla's glowing effect does the drawing, so occlusion, distance and shaders behave normally.

| 设置 / Setting | 可选项 / Options |
|---|---|
| 目标 / Target | 全部 · 敌对 · 友好 · 自定义 / all, hostile, friendly, custom |
| 持续 / Duration | 30 秒 · 60 秒 · 5 分钟 · 持续（点击即开启）/ 30 s, 60 s, 5 min, until turned off |
| 半径 / Radius | 16 · 32 · 48 · 64 格 / blocks |
| 碰撞箱 / Hitboxes | 画出每个发光生物的碰撞框 / draw the box around each glowing creature |
| 数量上限 / Cap | 同时标记最近的 32 个（`maxTargets`）/ nearest 32 at a time (`maxTargets`) |

⚠️ **没有「中立」这一档**，因为 Minecraft 没有这个分类。可靠的标记只有 `Enemy` 接口（会主动敌对），而末影人、僵尸猪灵都归在 `MONSTER` 里。需要中间地带就把目标设为「自定义」，在配置文件里按类别或具体实体列出来。

⚠️ **There is no "neutral" option**, because Minecraft has no such category. The only dependable marker is the `Enemy` interface; endermen and zombified piglins sit in `MONSTER`. Pick Custom and list what you want by category or entity id.

只清理本模组加上的发光：光灵箭给同一只生物上的发光不会被取消。

Only the effects this mod applied are cleared — a spectral arrow's glow on the same creature is left alone.

⚠️ **轮廓被光影吞掉时看碰撞箱。** 原版轮廓走的是 `OutlineBufferSource`，而光影/优化模组（Oculus、Iris、部分渲染优化）会改写这条管线，轮廓可能因此不可见。碰撞箱是本模组自己画的线框，不经那条管线，所以看不见轮廓时它就是唯一还看得见的通道。开启透视时聊天栏会报「标记 N 个」：0 个说明目标或半径设错了，大于 0 却看不见轮廓说明是渲染通道的问题。

⚠️ **If the outline is invisible under a shader pack, use hitboxes.** Vanilla outlines go through `OutlineBufferSource`, which shader and render-optimisation mods rewrite. The hitboxes are drawn by this mod and do not use that pipeline. Starting the outline reports how many creatures were marked: zero means the target/radius is wrong, non-zero with nothing visible means the render path is the problem.

配置 `[undying.glow]`；入口在 `O` →「透视…」。

Config `[undying.glow]`; opened from `O` → "Outline...".

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

`Environment.isAllowed(...)` 在 20 处被调用，分布在 14 个文件中：聊天触发词、进入世界、每 tick 任务、指令、物品交互，以及所有可作用于 `ServerPlayer` 的 mixin 注入点。

`Environment.isAllowed(...)` is consulted at 20 call sites across 14 files: chat triggers, world
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

## 界面与语言 / Interface and language

### 配置界面 / Config screen

`O` 键打开（默认键位，可在「控制 → 按键绑定 → Undying」里改）。25 个开关两列排开，状态用颜色区分；底部四个入口分别进入「领域白名单」「透视」「快捷控制」和关闭界面。所有改动即时写入 TOML，没有「保存」按钮——需要二次确认的勾选框是最容易被存错的东西。

Opened with `O` (a real key binding, so it appears under Controls → Key Binds → Undying and can be rebound). Twenty switches in two columns; the buttons at the bottom open the domain allow-list editor or close the screen. Changes are written to the TOML immediately — there is no Save button, because a checkbox that needs a second confirmation is the thing that gets saved wrongly.

### 语言 / Language

四类文本，处理方式不同，理由也不同：

Four kinds of text, handled differently on purpose:

| 文本 / Text | 方式 / Approach | 理由 / Why |
|---|---|---|
| 图形界面 / GUI | `assets/undying/lang/en_us.json` + `zh_cn.json`，随游戏语言切换 / follows the game language | 空间有限，两种语言同时显示会撑破按钮 / no room for both |
| 聊天与提示 / Chat | 中英同时显示，`中文 / English` / both, always | 一眼扫过，显示两种不会比一种更累；也不必猜玩家把游戏设成哪种语言 / read in motion; never shows the wrong one |
| 配置注释 / Config comments | 英文在前，中文在后 / English first, then Chinese | Forge 的 TOML 注释没有本地化机制，只能写死在注释里 / Forge cannot localise TOML comments |
| 开发者日志 / Logs | 仅英文 / English only | 给排查问题的人看，不是给玩家看的 / written for whoever debugs it, not for the player |

新增文本的规矩：GUI 一律走语言键，不要在 `Screen` 里写死字符串；聊天用 `Msg.bi(中文, English)`；日志用英文。缺一个语言键时 Minecraft 会直接把键名显示出来，那是可见的错，不会静默。

Rule for new text: GUI strings are language keys (never literals inside a `Screen`), chat uses `Msg.bi(chinese, english)`, logs stay English. A missing key shows up as the raw key in the UI — visible, not silent.

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

单元测试覆盖的是**能被测试的那一层**：环境门禁的两条判据、联机入口拦截、审计比对、领域的放行规则、透视目标筛选、物品栏变动的判定阈值与计数、Y/N 三次确认、开关级联、配置界面的网格排布、报告文件滚动、jar 内资源覆盖的解析与分类。测试不将 Forge 工具链放入 classpath，因此无需预先搭建游戏环境。

Unit tests cover the layer that *can* be tested: both environment-gate predicates, the multiplayer
entrance ban, audit comparison, the domain allow-list rules, the outline target filter, the
inventory-change threshold and counting, the three-press confirmation, the switch rules, the config
screen's grid layout, report-file rotation, and mod-jar resource parsing and classification. The Forge toolchain is deliberately excluded from the test
classpath, so tests run without provisioning the game.

**这层覆盖不到的东西要说清楚**：19 个注入点、图形界面、以及任何需要真实 Forge 运行时对象的
代码（`IModFile`、`SecureJar`、mixin 类），单测通过都不构成它们能工作的证据。
上一版有 10 个用例全绿、生产环境却 100% 失效，原因正是测试用的是真实文件系统上的真实文件。

**What that does not cover**: the 19 injection points, the GUI, and anything that touches real
Forge runtime objects (`IModFile`, `SecureJar`, mixin classes). A green test run is not evidence
that those work. The previous release shipped ten passing tests on top of a feature that failed
on every machine in production: the tests used real files on a real filesystem, which was the one
case that worked.

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

ShadowGraff, DeepSeek, Kimi

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

**Kimi 是月之暗面（Moonshot AI，公司注册名：月之暗面科技股份有限公司）开发的 AI 助手名称。**
本项目在作者署名与开发说明中提及该名称，**仅用于说明本项目所获得的 AI 辅助的来源**。此提及**不代表月之暗面对本项目的任何赞助、认可、担保或官方关联**；本项目为个人独立作品，与月之暗面无任何隶属或合作关系。相关服务条款以月之暗面官方公布为准。

**Kimi is the name of the AI assistant developed by Moonshot AI (registered company name:
月之暗面科技股份有限公司).**
The name is referenced in this project's authorship line and development note **solely to identify
the source of the AI assistance used**. This reference **does not imply any sponsorship,
endorsement, warranty, or official affiliation with Moonshot AI**; this project is an independent
work by an individual and has no affiliation or partnership with the company. For the service's
own terms, refer to Moonshot AI's official publications.

---

## 开发说明 / Development note

本 README 由 AI 生成。模组本身同样如此：**全部实现代码由 AI 编写**，而**功能逻辑与规则、目标与边界、以及全部 BUG 测试与实机验证均来自人类开发者**。实现过程中模型多次偏离设计意图时，由人类开发者重新定方向。

This README was generated by an AI. So was the mod itself: **every line of implementation code
was written by an AI**, while **the feature logic and rules, the goals and boundaries, and all bug
testing and in-game verification came from the human developer**. When the implementation drifted
away from the intended design, the human developer set the direction again.

具体分工：

The division of labour:

| 环节 / Stage | 负责方 / Owner |
|---|---|
| 功能逻辑与规则 / Feature logic and rules | 人类 / Human —— 完整制定 / Defined in full |
| 目标、边界与验收标准 / Goals, boundaries, acceptance criteria | 人类 / Human |
| 方向纠偏 / Course correction | 人类 / Human —— 实现偏离时重新定方向 / Re-aimed when the implementation drifted |
| 实现代码 / Implementation code | AI（DeepSeek、Kimi，见[商标声明](#商标声明--trademark-notice)）/ AI (DeepSeek and Kimi — see the [trademark notice](#商标声明--trademark-notice)) |
| BUG 测试与实机验证 / Bug testing and in-game verification | 人类 / Human |
| 日志分析与代码修改 / Log analysis and code changes | AI |

文中关于行为、数值与实现细节的描述均以源代码为依据。如有不符，以源代码为准。

Statements here about behaviour, values and implementation details are based on the source code.
Where they disagree, the source code is authoritative.
