# Technical guide

[Back to README](../README.md)

This guide describes the `main` branch, targeting Minecraft 1.21.1 and Better Combat 2.4.0+1.21.1. The previous Minecraft 1.20.1 version is preserved on the `1.20.1` branch.

## Compatibility and installation

Use the Fabric or NeoForge build for your Minecraft version and loader. Install Better Combat and its required dependencies, including Fabric API on Fabric, Cloth Config, and playerAnimator. Better Combat's Minecraft 1.21.1 releases use NeoForge in place of Forge.

| Component | Build dependency |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 |
| Better Combat | 2.4.0+1.21.1 |
| Fabric Loader | 0.16.5 |
| Fabric API | 0.103.0+1.21.1 |
| NeoForge | 21.1.1 |
| Cloth Config | 15.0.130 |
| playerAnimator | 2.0.0-alpha1+1.21 |
| Tiny Config (development runtime) | 3.1.0 |

These are the versions pinned in [`gradle.properties`](../gradle.properties). The Fabric descriptor requires Fabric Loader 0.16.5 or newer; the NeoForge descriptor requires NeoForge 21.1.1 or newer. Both require Minecraft 1.21.1 and the exact Better Combat version above because the integration targets its networking API.

Only items recognized by Better Combat's weapon registry, with at least one configured attack, can start a guard window. The integration depends on the attack and networking APIs of the targeted Better Combat release.

Better Combat bundles Tiny Config in its release JAR. The development builds add it explicitly because Loom removes embedded dependencies when remapping mods.

## Guard mechanics

- A valid attack animation starts a guard window for the full server-calculated swing duration. Cancelling the swing ends it immediately.
- Only attacks from the front are blocked, using vanilla shield direction checks. Damage that bypasses shields and piercing arrows still pass through.
- Successful blocks use vanilla shield damage negation, sounds, status feedback, and attacker reactions.
- If the incoming damage is at least 3 points, the swinging weapon takes `1 + floor(incoming damage)` durability damage through vanilla item durability handling.
- Dual-wielding charges durability to the hand that performs the attack. Two-handed attacks use the main-hand weapon.
- When vanilla axe shield disabling applies a shield cooldown, the current guard ends. Shield cooldowns also prevent a new guard from starting.
- Guarding ends when its timer expires or the player dies, becomes a spectator, uses an item, changes the selected hotbar slot, or no longer holds the same eligible weapon stack. Breaking that weapon also ends the guard.
- An active guard cannot be extended by another start packet, and a second guard cannot start in the same server tick.

The feature activates automatically without an additional keybind. It leaves Better Combat's hit detection, combos, damage, and animations unchanged.

## Server-side implementation

Better Combat sends an `AttackAnimation` payload when a client starts an attack animation. Fabric and NeoForge route that payload through the shared `ServerNetwork.handleAttackAnimation` method. This add-on injects at the start of that method through [`ServerNetworkMixin`](../common/src/main/java/dev/cosmos/bettercombat/swingguard/mixin/ServerNetworkMixin.java), leaving Better Combat's original handling intact. Guard validation runs on the server thread and uses the authenticated sending player rather than the payload's entity ID.

[`GuardController`](../common/src/main/java/dev/cosmos/bettercombat/swingguard/logic/GuardController.java) handles the following checks:

1. A stop-animation packet ends the guard. Other packets require a living, non-spectator player without a shield cooldown.
2. The selected hand must hold a registered weapon with configured attacks. Off-hand attacks require dual-wielding, and two-handed animations require a two-handed weapon.
3. The animation name must match an attack configured on that weapon.
4. The server attack duration must be finite and positive. The packet duration must be finite and differ by no more than `max(2 ticks, 35% of the server duration)`.
5. Attack cooldown progress must be at least `max(0, 1 - clamp(upswing, 0, 1) * upswingMultiplier - 0.20)`.
6. A valid guard uses the rounded server duration, clamped to 1-200 ticks, and records the attacking hand, weapon stack, selected slot, and start tick.

[`PlayerEntityMixin`](../common/src/main/java/dev/cosmos/bettercombat/swingguard/mixin/PlayerEntityMixin.java) maintains that state and handles durability and shield disable behavior. [`LivingEntityMixin`](../common/src/main/java/dev/cosmos/bettercombat/swingguard/mixin/LivingEntityMixin.java) lets an active swing guard satisfy the blocking check inside `LivingEntity.blockedByShield`; the remaining vanilla shield rules still apply.

Using the existing animation packets avoids adding a separate client guard timer or a custom networking channel.

## Project layout

- `common/`: guard state, validation, and vanilla entity mixins shared by both loaders.
- `fabric/`: Fabric entrypoint and mod metadata.
- `neoforge/`: NeoForge entrypoint and mod metadata.

The build uses Architectury, Loom, and Gradle 8.8, with Java compilation targeting version 21. Layered Yarn mappings include Architectury's NeoForge mapping patch so both loaders share the same sources.

## Building

Use JDK 21. From the repository root, run:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```sh
./gradlew build
```

The first build needs network access to download Gradle, Minecraft, plugins, and dependencies. The distributable JARs for this branch are:

```text
fabric/build/libs/bettercombat-swing-guard-fabric-1.0.0+1.21.1.jar
neoforge/build/libs/bettercombat-swing-guard-neoforge-1.0.0+1.21.1.jar
```

Choose the JAR for your loader. The `-dev`, `-dev-shadow`, and `-sources` JARs are development artifacts.

## Automated gameplay checks

Run the Fabric headless GameTest server with JDK 21:

```powershell
.\gradlew.bat :fabric:runGameTest
```

The tests exercise Better Combat's real animation handler and vanilla damage processing: guard activation and cancellation, invalid payload rejection, dual-wielding, front/back direction checks, weapon durability, axe disabling, and weapon replacement. The test mod is kept in a separate source set and is excluded from release JARs. Results are written to `fabric/build/gametest/results.xml`.
