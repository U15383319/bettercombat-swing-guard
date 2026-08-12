package dev.cosmos.bettercombat.swingguard.api;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public interface SwingGuardState {
    boolean swingGuard$begin(int durationTicks, Hand hand, ItemStack weaponStack, long serverTick);

    void swingGuard$end();

    boolean swingGuard$isGuarding();

    ItemStack swingGuard$getGuardingStack();

    Hand swingGuard$getGuardingHand();
}
