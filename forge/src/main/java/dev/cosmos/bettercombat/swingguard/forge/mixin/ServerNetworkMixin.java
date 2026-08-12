package dev.cosmos.bettercombat.swingguard.forge.mixin;

import dev.cosmos.bettercombat.swingguard.logic.GuardController;
import net.bettercombat.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.bettercombat.network.Packets;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ServerNetwork.class, remap = false)
public abstract class ServerNetworkMixin {
    @ModifyArg(
            method = "initializeHandlers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/bettercombat/fabricmc/fabric/api/networking/v1/ServerPlayNetworking;registerGlobalReceiver",
                    ordinal = 0,
                    remap = false
            ),
            index = 1,
            remap = false
    )
    private static ServerPlayNetworking.PlayChannelHandler swingGuard$observeAttackAnimations(
            ServerPlayNetworking.PlayChannelHandler original
    ) {
        return (server, player, handler, buffer, responseSender) -> {
            PacketByteBuf inspectionBuffer = new PacketByteBuf(buffer.copy());
            Packets.AttackAnimation packet;
            try {
                packet = Packets.AttackAnimation.read(inspectionBuffer);
            } finally {
                inspectionBuffer.release();
            }

            server.execute(() -> GuardController.acceptAttackAnimation(player, packet));
            original.receive(server, player, handler, buffer, responseSender);
        };
    }
}

