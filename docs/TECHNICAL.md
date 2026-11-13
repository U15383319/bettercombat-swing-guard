# Technical guide

[Back to README](../README.md)

This guide describes the `1.19.2` branch, targeting Minecraft 1.19.2 and Better Combat 1.7.1+1.19.

## Compatibility and installation

Use the Fabric or Forge build for your Minecraft version and loader. Install Better Combat and its required dependencies, including Fabric API on Fabric, Cloth Config, and playerAnimator.

| Component | Build dependency |
| --- | --- |
| Minecraft | 1.19.2 |
| Java | 17 |
| Better Combat | 1.7.1+1.19 |
| Fabric Loader | 0.14.9 |
| Fabric API | 0.60.0+1.19.2 |
| Forge | 43.1.25 |
| Cloth Config | 8.0.75 |
| playerAnimator | 1.0.2 |

These are the versions pinned in [`gradle.properties`](../gradle.properties). The Fabric descriptor requires Fabric Loader 0.14.9 or newer and the exact Better Combat version above. The Forge descriptor requires Forge 43.1 or newer and declares the Better Combat range `[1.7.1,1.8.0)` (inclusive lower bound, exclusive upper bound). That range is a metadata constraint, not a claim that every version in it has been tested.

Only items recognized by Better Combat's weapon registry, with at least one configured attack, can start a guard window. The integration depends on the attack and networking APIs of the targeted Better Combat release.

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

Better Combat sends an `AttackAnimation` packet when a client starts an attack animation. This add-on wraps Better Combat's existing receiver on each loader, inspects a copy of the packet buffer, and still calls the original receiver. Guard validation runs on the server thread and uses the authenticated sending player rather than the packet's entity ID.

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
- `fabric/`: Fabric entrypoint, networking receiver wrapper, and mod metadata.
- `forge/`: Forge entrypoint, networking receiver wrapper, and mod metadata.

The build uses Architectury and Loom, with Java compilation targeting version 17.

## Building

Use JDK 17. From the repository root, run:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```sh
./gradlew build
```

The first build needs network access to download Gradle, Minecraft, plugins, and dependencies. The distributable JARs for this branch are:

```text
fabric/build/libs/bettercombat-swing-guard-fabric-1.0.0+1.19.2.jar
forge/build/libs/bettercombat-swing-guard-forge-1.0.0+1.19.2.jar
```

Choose the JAR for your loader. The `-dev`, `-dev-shadow`, and `-sources` JARs are development artifacts.
