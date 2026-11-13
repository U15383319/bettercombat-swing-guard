package dev.cosmos.bettercombat.swingguard.mixin;

import dev.cosmos.bettercombat.swingguard.logic.GuardController;
import net.bettercombat.network.Packets;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerNetwork.class, remap = false)
public abstract class ServerNetworkMixin {
    @Inject(method = "handleAttackAnimation", at = @At("HEAD"), remap = false)
    private static void swingGuard$observeAttackAnimation(
            Packets.AttackAnimation packet,
            MinecraftServer server,
            ServerPlayerEntity player,
            CallbackInfo ci
    ) {
        if (server.isOnThread()) {
            GuardController.acceptAttackAnimation(player, packet);
        } else {
            server.execute(() -> GuardController.acceptAttackAnimation(player, packet));
        }
    }
}
