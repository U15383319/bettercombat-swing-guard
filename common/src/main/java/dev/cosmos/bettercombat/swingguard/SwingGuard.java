package dev.cosmos.bettercombat.swingguard;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class SwingGuard {
    public static final String MOD_ID = "bettercombat_swing_guard";
    public static final String MOD_NAME = "Better Combat Swing Guard";
    public static final Logger LOGGER = LogUtils.getLogger();

    private SwingGuard() {
    }

    public static void init() {
        LOGGER.info("{} initialized", MOD_NAME);
    }
}

