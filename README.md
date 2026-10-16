# Undying

**No-death protection for Minecraft — a single-player cheat, built to stay that way.**

Minecraft 1.20.1 · Forge 47.x · Java 17 · MIT

---

## What this is

A Forge mod that makes you genuinely hard to kill in **single-player**, and refuses to
participate in multiplayer at all.

This is a **cheat**, and it says so up front. It is not a combat overhaul, not a difficulty
option, and not balanced. It is for players who want to watch a boss fight play out without
dying to it, walk into the Deep Dark to see what is there, or survive a modpack that keeps
one-shotting them.

Because "you cannot die" is exactly the kind of thing that ruins a server for everyone else,
the mod is **single-player only by construction** — see [Multiplayer](#multiplayer-is-disabled)
below.

---

## Features

Everything is off until you turn on the master switch. Nothing here activates by itself.

### Death lock — the reason this mod exists

Four independent layers. Any one of them holding is enough to stop a death.

| Layer | What it does |
|---|---|
| 1. Damage refusal | At the health floor, the hit is not accepted at all — no damage, no event, no knockback |
| 2. Health clamp | The only write path to health data; any code that tries to set health below the floor is pushed back up |
| 3. `die()` cancellation | The death pipeline is cancelled and rewound — timer cleared, health restored, totem effect refunded |
| 4. Removal interception | Blocks `remove(KILLED)` and `kill()` |

> **Layer 1 refuses rather than heals.** Once vanilla's death animation starts, editing health
> does not stop it — you end up in a half-dead state where the server thinks you are alive and
> tries to remove you every 50 ms. Refusing the damage avoids that entirely.

> **Layer 4 blocks `KILLED`, never `DISCARDED`.** `DISCARDED` is the normal cleanup used when
> changing dimension or respawning. Blocking it puts two of you in the world and then crashes
> the chunk manager.

**The one way to actually die:** `/antigrief die`, or the suicide chat trigger. That channel is
deliberately never blocked — being unable to die when you want to is its own kind of trap.

### Hidden slots and gear

Three slots the player cannot see, touch, drop, or have stripped by commands:

- **Hidden totem** — a real totem of undying, triggered through vanilla's own flow, so events,
  sound and particles all behave correctly
- **Ward shield** — 360° immunity to all damage, unlimited durability
- **Hidden armour** — never enters the real armour bar; its value is injected so a full
  netherite set (20) applies without occupying a slot

Enchantments on hidden gear are resolved manually using vanilla formulas, because vanilla
cannot read enchantments out of a slot it does not know exists:

| Enchantment | Effect |
|---|---|
| Protection | 4% per level, capped at 80% |
| Feather Falling | 12% per level, capped at 80% |
| Thorns | Vanilla `ThornsEnchantment` formula |
| Respiration | Oxygen never consumed, continuously refilled |
| Aqua Affinity | Removes the 5× underwater mining penalty |

`hiddenGear` additionally grants a full netherite set with tools, once per save, on first join.

### Void Sword

Obtained with the `sword` / `取剑` chat trigger.

- **Right click** — clears every entity within 5 blocks
- **Left click** — starts a four-layer execution chain that escalates until the target is
  genuinely dead: full damage → forced removal → data wipe → peaceful mode. Each layer is
  followed by a check that the target really entered the death pipeline.

### Aura

Clears hostile mobs in a radius. Deliberately **excludes the Ender Dragon, Wither and Warden** —
removing those mid-fight breaks world progression.

- Timed activation, or permanent mode
- Automatic mode via the `deathAura` switch

### Survival assistance

| Feature | Effect |
|---|---|
| Void rescue | Below y=-100, teleports you back to a safe point — bed or respawn anchor first, else world spawn, following your dimension |
| Spawn protection | Brief full immunity after joining or respawning |
| Permanent effects | Absorption, Regeneration, Resistance, Fire Resistance |
| Save snapshot | Periodic player-state backup to `_undying_backup/<uuid>.dat`, restored automatically when death or corruption is detected |

> **Why the snapshot layer exists.** Every other protection hangs off vanilla's call chain. If a
> coremod rewrites that whole chain, all the hooks go dead at once. So this layer takes a
> different road: collect, write to disk, restore — never touching `hurt`, `setHealth`, `die` or
> `kill`.
>
> **Its honest limit.** Against a JVMTI runtime agent that specifically rewrites this class,
> nothing in-process wins, including this. What it does beat is coremod rewriting of vanilla
> methods, and it raises the cost of going around it. It is not a promise of absolute safety.

### Integrity guard and self-check

- Re-verifies every 2 seconds that config switches have not been flipped externally, and repairs
  them if they have
- Runs 12 read-only probes over the vanilla structures it depends on, confirms its mixins are
  actually attached, and confirms the protection switch is really on — then reports the result

> The self-check is **read-only and always tells you the truth**, including when the answer is
> "protection is currently doing nothing". A diagnostic that can lie is worse than no
> diagnostic.

---

## Multiplayer is disabled

**This mod does not work in multiplayer, and actively prevents itself from being used there.**

The reason is not policy, it is the feature: installing this on a server hands invulnerability
to every player on it.

Two independent layers enforce it.

### 1. The entrances are closed (client)

The **Multiplayer** button on the title screen and the **Open to LAN** button in the pause menu
are greyed out and unclickable, with a tooltip explaining why.

They are matched by **translation key** (`menu.multiplayer`, `menu.shareToLan`), not by
displayed text — so this works identically on every language. Matching rendered text would
silently fail on any non-English client.

### 2. Every feature entry point is gated (both sides)

`Environment.isAllowed(...)` is consulted at 13 entry points: chat triggers, world join,
per-tick work, commands, item interaction, and every mixin injector that can touch a
`ServerPlayer`.

| Environment | Behaviour |
|---|---|
| Single player | works normally |
| Opened to LAN | disabled |
| Joined a remote server | disabled |
| Dedicated server | disabled |

**Fail-closed.** If the environment cannot be determined, the gate reports "blocked". Being
wrongly disabled in single-player is an annoyance; being wrongly enabled on a server is the
thing this exists to prevent.

**It never crashes a server.** Crashing would be hostile to whoever runs it and would make this
mod look like malware. The gate disables everything and states it plainly, once per session, in
both the log and chat.

The two layers are complementary and neither replaces the other: the GUI ban stops a player from
*reaching* multiplayer, and the gate covers the case where someone starts a modded server
anyway. Note also the deliberate asymmetry — the GUI ban **fails open** (a broken menu is worse
than an un-banned button, and the gate still holds), while the gate **fails closed**.

---

## Installation

1. Install Minecraft Forge 47.x for Minecraft 1.20.1
2. Drop the jar into your `mods` folder
3. That is all — there are no required dependencies

The mod is client-side complete: single-player works with it installed on the client alone.

---

## Configuration

Config file: `config/undying-common.toml`. Most switches are also reachable in-game through chat
triggers or `/antigrief`.

The master switch ships **off**. With it off, the mod does nothing at all — which is what the
self-check will tell you if you forget you turned it off.

---

## Chat triggers

| Trigger | Effect |
|---|---|
| `toggle` / `无敌` | Master switch |
| `aura` / `光环` | Timed aura |
| `aura on` / `光环常驻` | Permanent aura |
| `aura off` / `光环关闭` | Aura off |
| `sword` / `取剑` | Obtain the Void Sword |
| `creative` / `创造` | Creative item menu |

Triggers are bilingual by request; English and Chinese both work.

---

## Building from source

Requires JDK 17.

```bash
./gradlew build
```

The jar lands in `build/libs/`. Run the unit tests with:

```bash
./gradlew test
```

Tests cover pure logic only — the environment gate, the multiplayer GUI ban, and audit
comparison — deliberately without the Forge toolchain on the classpath, so `gradle test` stays
fast and runs without provisioning the game.

---

## A note on the source

### How this was written

Most of the code here was written by an AI, and the design was directed by one person who does
not read Java. That is stated plainly because it is relevant to anyone deciding whether to trust
this mod or to build on it.

What that division of labour produced, in practice:

- **Every design decision was a human call.** What the mod should do, what it must refuse to do,
  which trade-offs were acceptable — none of that was delegated.
- **The AI wrote the implementation, and got things wrong.** Several of those mistakes are
  documented in the comments next to the code that fixed them. The multiplayer ban alone took
  three attempts, and the reason each earlier attempt failed is written where it matters.
- **Nothing was accepted on the strength of "it compiles".** The build passing and the feature
  working turned out to be different things more than once.

If you find that off-putting, that is a fair reaction. The alternative — leaving it unsaid — would
be worse, because you would be reading code without knowing how it came to be.

### Reading the code

This codebase carries unusually heavy comments for its size. That is intentional: the comments
record *why*, not *what*, and several of them document bugs that were shipped and then found.
Where a decision looks strange, the reason is usually written next to it — including the
mistakes.

Two conventions worth knowing before reading the mixins:

- **Targets are addressed by SRG name** (`m_142416_`, `f_120018_`) with `remap = false`, because
  the mod ships no refmap. A development name in an injection target would compile and then
  silently not apply at runtime.
- **Inject at the outermost frame.** If a caller keeps modifying an object after the call
  returns, no injection point inside the callee can win. This is documented in full in the
  source, because it cost three attempts to get right.

---

## Licence

MIT. See [LICENSE](LICENSE).

You may use, modify, and redistribute this freely, including commercially and in closed-source
projects. The only conditions are that the copyright notice stays, and that the authors are not
held liable.

---

## Authors

ShadowGraff, DeepSeek
