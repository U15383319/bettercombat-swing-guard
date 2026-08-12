package dev.cosmos.bettercombat.swingguard.mixin;

import dev.cosmos.bettercombat.swingguard.api.SwingGuardState;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements SwingGuardState {
    @Unique
    private int swingGuard$remainingTicks;
    @Unique
    private Hand swingGuard$hand;
    @Unique
    private ItemStack swingGuard$weaponStack = ItemStack.EMPTY;
    @Unique
    private int swingGuard$selectedSlot = -1;
    @Unique
    private long swingGuard$lastStartTick = Long.MIN_VALUE;

    @Inject(method = "tick", at = @At("HEAD"))
    private void swingGuard$tickGuardWindow(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.world.isClient || swingGuard$remainingTicks <= 0) {
            return;
        }

        if (!swingGuard$isGuarding()) {
            swingGuard$end();
            return;
        }

        --swingGuard$remainingTicks;
        if (swingGuard$remainingTicks <= 0) {
            swingGuard$end();
        }
    }

    @Inject(method = "damageShield", at = @At("HEAD"))
    private void swingGuard$damageGuardingWeapon(float amount, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.world.isClient
                || player.isBlocking()
                || amount < 3.0F
                || !swingGuard$isGuarding()) {
            return;
        }

        ItemStack stack = swingGuard$getGuardingStack();
        Hand hand = swingGuard$getGuardingHand();
        if (stack.isEmpty() || hand == null) {
            return;
        }

        int durabilityDamage = 1 + MathHelper.floor(amount);
        stack.damage(durabilityDamage, player, brokenPlayer -> brokenPlayer.sendToolBreakStatus(hand));
        if (stack.isEmpty()) {
            swingGuard$end();
        }
    }

    @Inject(method = "disableShield", at = @At("TAIL"))
    private void swingGuard$respectAxeShieldDisable(boolean sprinting, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getItemCooldownManager().isCoolingDown(Items.SHIELD)) {
            swingGuard$end();
        }
    }

    @Override
    public boolean swingGuard$begin(int durationTicks, Hand hand, ItemStack weaponStack, long serverTick) {
        if (swingGuard$remainingTicks > 0 || serverTick == swingGuard$lastStartTick) {
            return false;
        }

        swingGuard$lastStartTick = serverTick;
        swingGuard$remainingTicks = Math.max(durationTicks, 1);
        swingGuard$hand = hand;
        swingGuard$weaponStack = weaponStack;
        swingGuard$selectedSlot = ((PlayerEntity) (Object) this).getInventory().selectedSlot;
        return true;
    }

    @Override
    public void swingGuard$end() {
        swingGuard$remainingTicks = 0;
        swingGuard$hand = null;
        swingGuard$weaponStack = ItemStack.EMPTY;
        swingGuard$selectedSlot = -1;
    }

    @Override
    public boolean swingGuard$isGuarding() {
        if (swingGuard$remainingTicks <= 0
                || swingGuard$hand == null
                || swingGuard$weaponStack.isEmpty()) {
            return false;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.isAlive()
                || player.isSpectator()
                || player.isUsingItem()
                || player.getInventory().selectedSlot != swingGuard$selectedSlot
                || player.getItemCooldownManager().isCoolingDown(Items.SHIELD)) {
            return false;
        }

        ItemStack currentStack = player.getStackInHand(swingGuard$hand);
        return !currentStack.isEmpty()
                && currentStack == swingGuard$weaponStack
                && WeaponRegistry.getAttributes(currentStack) != null;
    }

    @Override
    public ItemStack swingGuard$getGuardingStack() {
        if (!swingGuard$isGuarding()) {
            return ItemStack.EMPTY;
        }
        return ((PlayerEntity) (Object) this).getStackInHand(swingGuard$hand);
    }

    @Override
    public Hand swingGuard$getGuardingHand() {
        return swingGuard$hand;
    }
}
