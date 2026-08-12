package dev.cosmos.bettercombat.swingguard.fabric;

import dev.cosmos.bettercombat.swingguard.SwingGuard;
import net.fabricmc.api.ModInitializer;

public final class SwingGuardFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SwingGuard.init();
    }
}

