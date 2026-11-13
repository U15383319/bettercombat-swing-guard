package dev.cosmos.bettercombat.swingguard.neoforge;

import dev.cosmos.bettercombat.swingguard.SwingGuard;
import net.neoforged.fml.common.Mod;

@Mod(SwingGuard.MOD_ID)
public final class SwingGuardNeoForge {
    public SwingGuardNeoForge() {
        SwingGuard.init();
    }
}
