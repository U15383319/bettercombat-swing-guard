package dev.cosmos.bettercombat.swingguard.mixin;

import dev.cosmos.bettercombat.swingguard.api.SwingGuardState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Redirect(
            method = "blockedByShield",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;isBlocking()Z"
            )
    )
    private boolean swingGuard$allowGuardDuringWeaponSwing(LivingEntity entity) {
        if (entity.isBlocking()) {
            return true;
        }
        return entity instanceof PlayerEntity
                && ((SwingGuardState) entity).swingGuard$isGuarding();
    }
}

