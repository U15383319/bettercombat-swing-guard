package dev.cosmos.bettercombat.swingguard.forge;

import dev.cosmos.bettercombat.swingguard.SwingGuard;
import net.minecraftforge.fml.common.Mod;

@Mod(SwingGuard.MOD_ID)
public final class SwingGuardForge {
    public SwingGuardForge() {
        SwingGuard.init();
    }
}

