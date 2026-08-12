package dev.cosmos.bettercombat.swingguard.logic;

import dev.cosmos.bettercombat.swingguard.api.SwingGuardState;
import net.bettercombat.BetterCombat;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.network.Packets;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;
import java.util.Objects;

public final class GuardController {
    private static final int MAX_GUARD_TICKS = 200;
    private static final float LENGTH_TOLERANCE_TICKS = 2.0F;
    private static final float COOLDOWN_TOLERANCE = 0.20F;

    private GuardController() {
    }

    public static void acceptAttackAnimation(ServerPlayerEntity player, Packets.AttackAnimation packet) {
        SwingGuardState state = (SwingGuardState) player;

        if (Packets.AttackAnimation.StopSymbol.equals(packet.animationName())) {
            state.swingGuard$end();
            return;
        }

        if (!player.isAlive()
                || player.isSpectator()
                || player.getItemCooldownManager().isCoolingDown(Items.SHIELD)) {
            state.swingGuard$end();
            return;
        }

        Hand hand = packet.animatedHand().isOffHand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack stack = player.getStackInHand(hand);
        WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);

        if (!isValidHand(packet.animatedHand(), player, stack, attributes)) {
            state.swingGuard$end();
            return;
        }

        WeaponAttributes.Attack attack = Arrays.stream(attributes.attacks())
                .filter(candidate -> Objects.equals(candidate.animation(), packet.animationName()))
                .findFirst()
                .orElse(null);
        if (attack == null) {
            state.swingGuard$end();
            return;
        }

        float serverLength = PlayerAttackHelper.getAttackCooldownTicksCapped(player);
        if (!Float.isFinite(serverLength) || serverLength <= 0.0F) {
            state.swingGuard$end();
            return;
        }

        float allowedLengthDifference = Math.max(LENGTH_TOLERANCE_TICKS, serverLength * 0.35F);
        if (!Float.isFinite(packet.length())
                || Math.abs(packet.length() - serverLength) > allowedLengthDifference) {
            state.swingGuard$end();
            return;
        }

        double upswingRate = MathHelper.clamp(attack.upswing(), 0.0D, 1.0D)
                * BetterCombat.config.getUpswingMultiplier();
        float minimumCooldown = (float) Math.max(0.0D, 1.0D - upswingRate - COOLDOWN_TOLERANCE);
        if (player.getAttackCooldownProgress(0.5F) < minimumCooldown) {
            state.swingGuard$end();
            return;
        }

        int durationTicks = MathHelper.clamp(Math.round(serverLength), 1, MAX_GUARD_TICKS);
        state.swingGuard$begin(
                durationTicks,
                hand,
                stack,
                player.world.getTime()
        );
    }

    private static boolean isValidHand(
            AnimatedHand animatedHand,
            ServerPlayerEntity player,
            ItemStack stack,
            WeaponAttributes attributes
    ) {
        if (stack.isEmpty() || attributes == null || attributes.attacks() == null || attributes.attacks().length == 0) {
            return false;
        }

        return switch (animatedHand) {
            case TWO_HANDED -> attributes.isTwoHanded();
            case OFF_HAND -> !attributes.isTwoHanded() && PlayerAttackHelper.isDualWielding(player);
            case MAIN_HAND -> !attributes.isTwoHanded();
        };
    }
}
