package dev.cosmos.bettercombat.swingguard.gametest;

import com.mojang.authlib.GameProfile;
import dev.cosmos.bettercombat.swingguard.api.SwingGuardState;
import io.netty.channel.embedded.EmbeddedChannel;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.mixin.player.LivingEntityAccessor;
import net.bettercombat.network.Packets;
import net.bettercombat.network.ServerNetwork;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

import java.util.UUID;

public class SwingGuardGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void animationPacketStartsGuardForSendingPlayer(TestContext context) {
        ServerPlayerEntity player = player(context);
        start(player, AnimatedHand.MAIN_HAND);
        context.assertTrue(state(player).swingGuard$isGuarding(), "Animation payload must start a guard");
        context.assertEquals(state(player).swingGuard$getGuardingStack(), player.getMainHandStack(), "Guard must use the sender's weapon");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cancellationEndsGuard(TestContext context) {
        ServerPlayerEntity player = player(context);
        start(player, AnimatedHand.MAIN_HAND);
        receive(player, Packets.AttackAnimation.stop(-1, 0));
        context.assertFalse(state(player).swingGuard$isGuarding(), "Stop payload must end the guard");
        start(player, AnimatedHand.MAIN_HAND);
        context.assertFalse(state(player).swingGuard$isGuarding(), "A guard must not restart in the same tick");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void invalidAnimationAndDurationsAreRejected(TestContext context) {
        ServerPlayerEntity player = player(context);
        Packets.AttackAnimation valid = packet(player, AnimatedHand.MAIN_HAND);
        for (float length : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -10.0F, valid.length() + 100.0F}) {
            receive(player, new Packets.AttackAnimation(-1, valid.animatedHand(), valid.animationName(), length, 0, 0, 0, Packets.SwingParticles.EMPTY));
            context.assertFalse(state(player).swingGuard$isGuarding(), "Invalid duration must not grant a guard");
        }
        receive(player, new Packets.AttackAnimation(-1, valid.animatedHand(), "swingguard:unknown", valid.length(), 0, 0, 0, Packets.SwingParticles.EMPTY));
        context.assertFalse(state(player).swingGuard$isGuarding(), "Unknown animation must not grant a guard");
        receive(player, valid);
        context.assertTrue(state(player).swingGuard$isGuarding(), "A valid payload must still be accepted");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void offHandRequiresDualWielding(TestContext context) {
        ServerPlayerEntity player = player(context);
        Packets.AttackAnimation valid = packet(player, AnimatedHand.MAIN_HAND);
        receive(player, new Packets.AttackAnimation(-1, AnimatedHand.OFF_HAND, valid.animationName(), valid.length(), 0, 0, 0, Packets.SwingParticles.EMPTY));
        context.assertFalse(state(player).swingGuard$isGuarding(), "An empty off-hand must not grant a guard");
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        start(player, AnimatedHand.OFF_HAND);
        context.assertTrue(state(player).swingGuard$isGuarding(), "Dual-wielded off-hand attack must guard");
        context.assertEquals(state(player).swingGuard$getGuardingHand(), Hand.OFF_HAND, "Guard must track the attacking hand");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onlyFrontalAttacksAreBlocked(TestContext context) {
        ServerPlayerEntity player = player(context);
        start(player, AnimatedHand.MAIN_HAND);
        PlayerEntity attacker = attacker(context, player);
        context.assertTrue(player.blockedByShield(player.getDamageSources().playerAttack(attacker)), "Frontal attack must be blocked");
        attacker.setPosition(player.getX(), player.getY(), player.getZ() - 2.0D);
        context.assertFalse(player.blockedByShield(player.getDamageSources().playerAttack(attacker)), "Attack from behind must pass through");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blockingDamagesOnlyTheAttackingWeapon(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        // Vanilla grants new server players 60 ticks of spawn protection.
        context.waitAndRun(65, () -> {
            start(player, AnimatedHand.OFF_HAND);
            float health = player.getHealth();
            player.damage(player.getDamageSources().playerAttack(attacker(context, player)), 5.0F);
            context.assertEquals(player.getHealth(), health, "Guard must negate frontal damage");
            context.assertEquals(player.getOffHandStack().getDamage(), 6, "Off-hand weapon must lose shield-formula durability");
            context.assertEquals(player.getMainHandStack().getDamage(), 0, "Main-hand weapon must retain its durability");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void axeHitDisablesGuardAndStartsShieldCooldown(TestContext context) {
        ServerPlayerEntity player = player(context);
        context.waitAndRun(65, () -> {
            start(player, AnimatedHand.MAIN_HAND);
            PlayerEntity attacker = attacker(context, player);
            attacker.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            player.damage(player.getDamageSources().playerAttack(attacker), 5.0F);
            context.assertFalse(state(player).swingGuard$isGuarding(), "Axe shield disable must end the guard");
            context.assertTrue(player.getItemCooldownManager().isCoolingDown(Items.SHIELD), "Axe shield disable must apply the vanilla cooldown");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changingTheWeaponEndsGuard(TestContext context) {
        ServerPlayerEntity player = player(context);
        start(player, AnimatedHand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        context.assertFalse(state(player).swingGuard$isGuarding(), "Replacing the weapon stack must end guarding");
        context.complete();
    }

    private static ServerPlayerEntity player(TestContext context) {
        var world = context.getWorld();
        var server = world.getServer();
        server.setPvpEnabled(true);
        var client = ConnectedClientData.createDefault(
                new GameProfile(UUID.randomUUID(), "swingguard-test"), false
        );
        // The vanilla creative mock overrides isCreative(), even after changing mode.
        ServerPlayerEntity player = new ServerPlayerEntity(server, world, client.gameProfile(), client.syncedOptions());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, client);
        player.changeGameMode(GameMode.SURVIVAL);
        player.onTeleportationDone();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.setYaw(0.0F);
        player.setHeadYaw(0.0F);
        ((LivingEntityAccessor) player).setLastAttackedTicks(100);
        return player;
    }

    private static PlayerEntity attacker(TestContext context, ServerPlayerEntity defender) {
        PlayerEntity attacker = context.createMockPlayer(GameMode.SURVIVAL);
        attacker.setPosition(defender.getX(), defender.getY(), defender.getZ() + 2.0D);
        return attacker;
    }

    private static SwingGuardState state(ServerPlayerEntity player) {
        return (SwingGuardState) player;
    }

    private static Packets.AttackAnimation packet(ServerPlayerEntity player, AnimatedHand hand) {
        ItemStack weapon = player.getStackInHand(hand.isOffHand() ? Hand.OFF_HAND : Hand.MAIN_HAND);
        var attributes = WeaponRegistry.getAttributes(weapon);
        if (attributes == null || attributes.attacks() == null || attributes.attacks().length == 0) {
            throw new IllegalStateException("Better Combat must register the test weapon's attacks");
        }
        return new Packets.AttackAnimation(-1, hand, attributes.attacks()[0].animation(), PlayerAttackHelper.getAttackCooldownTicksCapped(player), 0, 0, 0, Packets.SwingParticles.EMPTY);
    }

    private static void start(ServerPlayerEntity player, AnimatedHand hand) {
        receive(player, packet(player, hand));
    }

    private static void receive(ServerPlayerEntity player, Packets.AttackAnimation packet) {
        ServerNetwork.handleAttackAnimation(packet, player.server, player);
    }
}
