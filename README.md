# Undying

**不死防护模组 —— 一个单人游戏专用的作弊模组，并且被刻意锁死在单人。**
**No-death protection for Minecraft — a single-player cheat, built to stay that way.**

Minecraft 1.20.1 · Forge 47.x · Java 17 · MIT

---

## 这是什么 / What this is

这是一个 Forge 模组：在**单人游戏**里让你几乎死不掉，同时**彻底拒绝参与联机**。

This is a Forge mod that makes you genuinely hard to kill in **single-player**, and refuses to
participate in multiplayer at all.

**这就是一个作弊模组，我们一开始就说清楚。** 它不是战斗重做，不是难度选项，也不平衡。它写给这样一类玩家：想看完一场 Boss 战却不想死在里面、想走进深邃洞穴看看那儿到底有什么、或者想在一个动不动就秒杀你的整合包里活下来。

This is a **cheat**, and it says so up front. It is not a combat overhaul, not a difficulty
option, and not balanced. It is for players who want to watch a boss fight play out without
dying to it, walk into the Deep Dark to see what is there, or survive a modpack that keeps
one-shotting them.

因为「你死不掉」恰恰是最能毁掉一整台服务器的东西，所以这个模组**从设计上就是单人专用** —— 见下方[联机](#联机被禁用--multiplayer-is-disabled)。

Because "you cannot die" is exactly the kind of thing that ruins a server for everyone else,
the mod is **single-player only by construction** — see [Multiplayer](#联机被禁用--multiplayer-is-disabled)
below.

---

## 功能 / Features

**所有功能默认关闭**，要打开总开关才会生效。没有任何一项会自己启动。

Everything is off until you turn on the master switch. Nothing here activates by itself.

### 死亡锁 —— 这个模组存在的理由 / Death lock — the reason this mod exists

四层独立防护，任意一层拦住，你就死不了。

Four independent layers. Any one of them holding is enough to stop a death.

| 层 / Layer | 作用 / What it does |
|---|---|
| 1. 拒绝伤害 | 血量触底时，这一击**根本不被接受** —— 不扣血、不触发事件、不击退 |
| 2. 血量闸门 | 血量数据的唯一写入口；任何想把血量写到保底线以下的代码，都会被抬回去 |
| 3. `die()` 取消 | 死亡流程被取消并倒带 —— 计时器清零、血量恢复、图腾效果退还 |
| 4. 移除拦截 | 拦住 `remove(KILLED)` 与 `kill()` |

| 层 / Layer | 作用 / What it does |
|---|---|
| 1. Damage refusal | At the health floor, the hit is not accepted at all — no damage, no event, no knockback |
| 2. Health clamp | The only write path to health data; any code that tries to set health below the floor is pushed back up |
| 3. `die()` cancellation | The death pipeline is cancelled and rewound — timer cleared, health restored, totem effect refunded |
| 4. Removal interception | Blocks `remove(KILLED)` and `kill()` |

> **第 1 层是「拒绝」，不是「回血」。** 一旦原版死亡动画开始，改血量是停不下来的 —— 你会卡在「半死不活」状态：服务端认为你活着，却每 50 毫秒尝试移除你一次。直接拒绝这一击，能彻底绕开这个问题。
>
> **Layer 1 refuses rather than heals.** Once vanilla's death animation starts, editing health
> does not stop it — you end up in a half-dead state where the server thinks you are alive and
> tries to remove you every 50 ms. Refusing the damage avoids that entirely.

> **第 4 层只拦 `KILLED`，绝不拦 `DISCARDED`。** `DISCARDED` 是原版换维度、重生时的正常清理。拦它会让世界里出现两个你，随后区块管理器直接 NPE。
>
> **Layer 4 blocks `KILLED`, never `DISCARDED`.** `DISCARDED` is the normal cleanup used when
> changing dimension or respawning. Blocking it puts two of you in the world and then crashes
> the chunk manager.

**唯一真正的死法：** `/antigrief die`，或者聊天框自杀触发词。这条通道**刻意永不拦截** —— 想死的时候死不掉，本身就是另一种陷阱。

**The one way to actually die:** `/antigrief die`, or the suicide chat trigger. That channel is
deliberately never blocked — being unable to die when you want to is its own kind of trap.

### 隐藏槽与隐藏装备 / Hidden slots and gear

三个玩家看不见、摸不着、丢不掉、指令也剥夺不了的槽位：

Three slots the player cannot see, touch, drop, or have stripped by commands:

- **隐藏图腾** —— 一个真正的图腾，走原版自己的触发流程，所以事件、音效、粒子全都正常
- **守护神盾** —— 360° 全伤害免疫，无限耐久
- **隐藏盔甲** —— 不进入真实盔甲栏；它的数值被注入，所以整套下界合金(20)生效却不占槽位

- **Hidden totem** — a real totem of undying, triggered through vanilla's own flow, so events,
  sound and particles all behave correctly
- **Ward shield** — 360° immunity to all damage, unlimited durability
- **Hidden armour** — never enters the real armour bar; its value is injected so a full
  netherite set (20) applies without occupying a slot

隐藏装备上的附魔按原版公式手动结算 —— 因为原版读不到一个它不知道存在的槽位里的附魔：

Enchantments on hidden gear are resolved manually using vanilla formulas, because vanilla
cannot read enchantments out of a slot it does not know exists:

| 附魔 / Enchantment | 效果 / Effect |
|---|---|
| 保护 / Protection | 每级 4%，上限 80% / 4% per level, capped at 80% |
| 摔落保护 / Feather Falling | 每级 12%，上限 80% / 12% per level, capped at 80% |
| 荆棘 / Thorns | 原版 `ThornsEnchantment` 公式 / Vanilla `ThornsEnchantment` formula |
| 水下呼吸 / Respiration | 氧气不消耗，持续补充 / Oxygen never consumed, continuously refilled |
| 水下速掘 / Aqua Affinity | 取消水下挖掘的 5 倍减速 / Removes the 5× underwater mining penalty |

`hiddenGear` 额外在每个存档首次进入时，发放一整套下界合金装备与工具。

`hiddenGear` additionally grants a full netherite set with tools, once per save, on first join.

### 虚空剑 / Void Sword

聊天框输入 `sword` / `取剑` 获取。

Obtained with the `sword` / `取剑` chat trigger.

- **右键** —— 清除周围 5 格内所有实体
- **左键** —— 启动四层执行链，逐层递进直到目标真正死亡：满伤害 → 强制移除 → 数据清空 → 和平模式。每一层之后都会验尸，确认目标真的进入了死亡流程

- **Right click** — clears every entity within 5 blocks
- **Left click** — starts a four-layer execution chain that escalates until the target is
  genuinely dead: full damage → forced removal → data wipe → peaceful mode. Each layer is
  followed by a check that the target really entered the death pipeline.

### 光环 / Aura

清除半径内的敌对生物。**刻意排除末影龙、凋灵与监守者** —— 在战斗中移除它们会破坏世界进程。

Clears hostile mobs in a radius. Deliberately **excludes the Ender Dragon, Wither and Warden** —
removing those mid-fight breaks world progression.

- 限时激活，或常驻模式
- 通过 `deathAura` 开关启用自动模式

- Timed activation, or permanent mode
- Automatic mode via the `deathAura` switch

### 保命辅助 / Survival assistance

| 功能 / Feature | 效果 / Effect |
|---|---|
| 虚空救援 / Void rescue | 掉到 y=-100 以下时传回安全点 —— 优先床/重生锚，没设过则回世界出生点，维度跟着走 / Below y=-100, teleports you back to a safe point — bed or respawn anchor first, else world spawn, following your dimension |
| 出生保护 / Spawn protection | 进世界或重生后短暂完全免伤 / Brief full immunity after joining or respawning |
| 常驻增益 / Permanent effects | 吸收、再生、抗性提升、抗火 / Absorption, Regeneration, Resistance, Fire Resistance |
| 存档急救包 / Save snapshot | 定期备份玩家状态到 `_undying_backup/<uuid>.dat`，检测到死亡或被毁时自动恢复 / Periodic player-state backup to `_undying_backup/<uuid>.dat`, restored automatically when death or corruption is detected |

> **为什么需要这一层。** 上面所有防护都挂在原版的调用链上。如果那一整条链被 coremod 改写，所有钩子会**同时失效**。所以这一层换赛道：采集、落盘、恢复 —— 完全不碰 `hurt`、`setHealth`、`die`、`kill`。
>
> **Why the snapshot layer exists.** Every other protection hangs off vanilla's call chain. If a
> coremod rewrites that whole chain, all the hooks go dead at once. So this layer takes a
> different road: collect, write to disk, restore — never touching `hurt`, `setHealth`, `die` or
> `kill`.

> **它诚实的边界。** 面对专门重写本类的 JVMTI 运行时 Agent，进程内没有方案能赢，包括这一层。它能打赢的是「coremod 改写原版方法」，并把绕过成本抬上去。这不是「绝对安全」的承诺。
>
> **Its honest limit.** Against a JVMTI runtime agent that specifically rewrites this class,
> nothing in-process wins, including this. What it does beat is coremod rewriting of vanilla
> methods, and it raises the cost of going around it. It is not a promise of absolute safety.

### 完整性守卫与自检 / Integrity guard and self-check

- 每 2 秒核对配置开关有没有被外部改回，是则自动修复
- 用 12 条只读探针核对依赖的原版结构还在不在、mixin 有没有真的挂上、防护总开关有没有真的开着 —— 然后把结果明确报出来

- Re-verifies every 2 seconds that config switches have not been flipped externally, and repairs
  them if they have
- Runs 12 read-only probes over the vanilla structures it depends on, confirms its mixins are
  actually attached, and confirms the protection switch is really on — then reports the result

> 自检**只读，并且永远说真话** —— 包括答案是「防护当前什么都没做」的时候。一个会说谎的诊断，比没有诊断更糟。
>
> The self-check is **read-only and always tells you the truth**, including when the answer is
> "protection is currently doing nothing". A diagnostic that can lie is worse than no
> diagnostic.

---

## 联机被禁用 / Multiplayer is disabled

**这个模组在联机下不工作，并且会主动阻止自己被用在联机里。**

**This mod does not work in multiplayer, and actively prevents itself from being used there.**

原因不是政策，而是功能本身：把它装到服务器上，等于给那台服务器上每个玩家发无敌。

The reason is not policy, it is the feature: installing this on a server hands invulnerability
to every player on it.

有两层互相独立的机制在拦这件事。

Two independent layers enforce it.

### 1. 入口关掉（客户端）/ The entrances are closed (client)

主菜单的**多人游戏**按钮和暂停菜单的**对局域网开放**按钮会被**灰掉**、点不动，并附带说明原因的悬浮提示。

The **Multiplayer** button on the title screen and the **Open to LAN** button in the pause menu
are greyed out and unclickable, with a tooltip explaining why.

它们按**翻译键**（`menu.multiplayer`、`menu.shareToLan`）匹配，**不是按显示文字** —— 所以在任何语言下表现一致。按显示文字匹配会在任何非英文客户端上静默失效。

They are matched by **translation key** (`menu.multiplayer`, `menu.shareToLan`), not by
displayed text — so this works identically on every language. Matching rendered text would
silently fail on any non-English client.

### 2. 每个功能入口都有门禁（双端）/ Every feature entry point is gated (both sides)

`Environment.isAllowed(...)` 在 **19 处**被调用（分布在 13 个文件里）：聊天触发词、进入世界、每 tick 工作、指令、物品交互，以及**每一个可能碰到 `ServerPlayer` 的 mixin 注入点**。

`Environment.isAllowed(...)` is consulted at **19 call sites across 13 files**: chat triggers,
world join, per-tick work, commands, item interaction, and every mixin injector that can touch a
`ServerPlayer`.

| 环境 / Environment | 行为 / Behaviour |
|---|---|
| 单人游戏 / Single player | 正常工作 / works normally |
| 开放到局域网 / Opened to LAN | 禁用 / disabled |
| 加入远程服务器 / Joined a remote server | 禁用 / disabled |
| 专用服务器 / Dedicated server | 禁用 / disabled |

**失败时关闭（fail-closed）。** 如果环境无法判定，门禁报告「已拦截」。在单人里被误禁只是烦人；在服务器上被误开，才是这条规则存在要防的事。

**Fail-closed.** If the environment cannot be determined, the gate reports "blocked". Being
wrongly disabled in single-player is an annoyance; being wrongly enabled on a server is the
thing this exists to prevent.

**它绝不让服务器崩溃。** 崩溃对开服的人是敌意行为，也会让这个模组看起来像恶意软件。门禁的做法是禁用一切，并在日志与聊天里**每个会话明说一次**。

**It never crashes a server.** Crashing would be hostile to whoever runs it and would make this
mod look like malware. The gate disables everything and states it plainly, once per session, in
both the log and chat.

两层互补，谁也不能替代谁：GUI 拦截阻止玩家**抵达**联机，门禁覆盖「有人还是开了个装了它的服务器」这种情况。另外注意一个**刻意的不对称** —— GUI 拦截**失败时放行**（一个坏掉的菜单比一个没被拦的按钮更糟，而且门禁还在），而门禁**失败时关闭**。

The two layers are complementary and neither replaces the other: the GUI ban stops a player from
*reaching* multiplayer, and the gate covers the case where someone starts a modded server
anyway. Note also the deliberate asymmetry — the GUI ban **fails open** (a broken menu is worse
than an un-banned button, and the gate still holds), while the gate **fails closed**.

---

## 安装 / Installation

1. 装好 Minecraft 1.20.1 对应的 Forge 47.x
2. 把这个 jar 丢进 `mods` 文件夹
3. 就这样 —— 没有任何必需的前置

1. Install Minecraft Forge 47.x for Minecraft 1.20.1
2. Drop the jar into your `mods` folder
3. That is all — there are no required dependencies

模组在客户端是完整的：只装在客户端就能玩单人。

The mod is client-side complete: single-player works with it installed on the client alone.

---

## 配置 / Configuration

配置文件：`config/undying-common.toml`。大部分开关在游戏内也能通过聊天触发词或 `/antigrief` 操作。

Config file: `config/undying-common.toml`. Most switches are also reachable in-game through chat
triggers or `/antigrief`.

**总开关默认是关的。** 关着的时候这个模组什么都不做 —— 如果你忘了自己关过，自检会告诉你这件事。

The master switch ships **off**. With it off, the mod does nothing at all — which is what the
self-check will tell you if you forget you turned it off.

---

## 聊天触发词 / Chat triggers

| 触发词 / Trigger | 效果 / Effect |
|---|---|
| `toggle` / `无敌` | 总开关 / Master switch |
| `aura` / `光环` | 限时光环 / Timed aura |
| `aura on` / `光环常驻` | 常驻光环 / Permanent aura |
| `aura off` / `光环关闭` | 关闭光环 / Aura off |
| `sword` / `取剑` | 获取虚空剑 / Obtain the Void Sword |
| `creative` / `创造` | 创造模式物品菜单 / Creative item menu |

触发词是中英双语的；两种都可用。

Triggers are bilingual by request; English and Chinese both work.

---

## 从源码构建 / Building from source

需要 JDK 17。

Requires JDK 17.

```bash
./gradlew build
```

jar 会生成在 `build/libs/`。跑单元测试：

The jar lands in `build/libs/`. Run the unit tests with:

```bash
./gradlew test
```

测试**只覆盖纯逻辑** —— 环境门禁、联机 GUI 拦截、审计比对 —— 特意不把 Forge 工具链放进 classpath，所以 `gradle test` 很快，也不需要先把游戏环境搭起来。

Tests cover pure logic only — the environment gate, the multiplayer GUI ban, and audit
comparison — deliberately without the Forge toolchain on the classpath, so `gradle test` stays
fast and runs without provisioning the game.

---

## 关于这份源码 / A note on the source

### 它是怎么写出来的 / How this was written

**这里大部分代码是 AI 写的，而设计由一位不读 Java 的人主导。** 这件事我们直接讲明白，因为它关系到任何人判断「要不要信任这个模组、要不要在它之上继续开发」。

Most of the code here was written by an AI, and the design was directed by one person who does
not read Java. That is stated plainly because it is relevant to anyone deciding whether to trust
this mod or to build on it.

这种分工实际产出了什么：

What that division of labour produced, in practice:

- **每一个设计决定都是人拍的板。** 这个模组该做什么、必须拒绝做什么、哪些取舍可以接受 —— 这些一件都没有外包出去
- **AI 负责实现，并且犯过错。** 其中好几个错误就写在修好它的那段代码旁边。光是一个联机拦截就试了三次，每次失败的**原因**都写在要紧的地方
- **没有任何东西是凭「能编译」就被接受的。** 构建通过和功能真的能用，在这件事上不止一次是两回事

- **Every design decision was a human call.** What the mod should do, what it must refuse to do,
  which trade-offs were acceptable — none of that was delegated.
- **The AI wrote the implementation, and got things wrong.** Several of those mistakes are
  documented in the comments next to the code that fixed them. The multiplayer ban alone took
  three attempts, and the reason each earlier attempt failed is written where it matters.
- **Nothing was accepted on the strength of "it compiles".** The build passing and the feature
  working turned out to be different things more than once.

如果你因此对这个项目观感变差，那是完全合理的反应。但另一个选择 —— 不说 —— 只会更糟：你会读着一份代码，却不知道它是怎么来的。

If you find that off-putting, that is a fair reaction. The alternative — leaving it unsaid — would
be worse, because you would be reading code without knowing how it came to be.

### 怎么读这份代码 / Reading the code

这个代码库的注释量，相对它的体量来说大得不寻常。这是故意的：注释记录的是**为什么**，不是**是什么**，而且其中好几条记录的是**已经发布出去、后来才被发现的 bug**。哪里看起来古怪，原因通常就写在旁边 —— 包括那些错误。

This codebase carries unusually heavy comments for its size. That is intentional: the comments
record *why*, not *what*, and several of them document bugs that were shipped and then found.
Where a decision looks strange, the reason is usually written next to it — including the
mistakes.

读 mixin 之前，有两条约定值得先知道：

Two conventions worth knowing before reading the mixins:

- **注入目标一律用 SRG 名**（`m_142416_`、`f_120018_`）配 `remap = false`，因为本模组不带 refmap。在注入目标里写开发名，会**编译通过，然后在运行时静默不生效**
- **在「最外层」注入。** 如果调用者在调用返回之后还会继续改那个对象，那么被调用方法**内部的任何注入点都赢不了**。这条在源码里有完整记录，因为它是试了三次才弄对的

- **Targets are addressed by SRG name** (`m_142416_`, `f_120018_`) with `remap = false`, because
  the mod ships no refmap. A development name in an injection target would compile and then
  silently not apply at runtime.
- **Inject at the outermost frame.** If a caller keeps modifying an object after the call
  returns, no injection point inside the callee can win. This is documented in full in the
  source, because it cost three attempts to get right.

---

## 许可证 / Licence

MIT。见 [LICENSE](LICENSE)。

MIT. See [LICENSE](LICENSE).

你可以自由使用、修改、再分发，包括商业用途和闭源项目。仅有的条件是：保留版权声明，以及不要找作者追责。

You may use, modify, and redistribute this freely, including commercially and in closed-source
projects. The only conditions are that the copyright notice stays, and that the authors are not
held liable.

---

## 作者 / Authors

ShadowGraff, DeepSeek

---

## 关于这份 README / About this README

**这份 README 也是 AI 生成的** —— 由 DeepSeek 根据实际代码和实际发生过的测试结果写成，再由项目作者审阅。

**This README was also generated by an AI** — written by DeepSeek from the actual source code and
the actual test results, then reviewed by the project's author.

里面每一句关于行为、数值和坑的描述，都是照着代码和日志写的，不是回忆录。如果发现哪一句和代码不符，**以代码为准**。

Every claim here about behaviour, numbers and pitfalls was written against the code and the
logs, not from memory. If you find a sentence that does not match the source, **the source is
right**.
