package betamoon.tileentity;

import org.luaj.vm2.LuaValue;

/** Structural redstone behavior attached to a tile-entity block. */
public final class RedstoneDefinition {
    public final String weakPowerField;
    public final String strongPowerField;
    public final LuaValue neighborAction;
    boolean neighborActionEnabled = true;

    public RedstoneDefinition(String weakPowerField, String strongPowerField, LuaValue neighborAction) {
        this.weakPowerField = weakPowerField;
        this.strongPowerField = strongPowerField;
        this.neighborAction = neighborAction;
    }

    public boolean providesPower() {
        return weakPowerField != null || strongPowerField != null;
    }
}
