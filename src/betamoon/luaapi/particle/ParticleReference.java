package betamoon.luaapi.particle;

import betamoon.content.ContentKey;
import betamoon.particles.ParticleDefinition;
import betamoon.particles.ParticleEffectDefinition;
import betamoon.particles.ParticleRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** A typed reference tied to one successful owner generation. */
public final class ParticleReference extends LuaTable {
    private final ContentKey key;
    private final Object definition;
    private final boolean effect;

    public ParticleReference(Object definition) {
        this.definition = definition;
        effect = definition instanceof ParticleEffectDefinition;
        key = effect ? ((ParticleEffectDefinition) definition).key : ((ParticleDefinition) definition).key;
        set("getKey", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(key.toString());
            }
        });
    }

    public ContentKey key() {
        return key;
    }

    public void requireCurrent(boolean expectedEffect) {
        if (effect != expectedEffect) {
            throw new LuaError("Expected a particle " + (expectedEffect ? "effect" : "type") + " reference");
        }
        if (ParticleRegistry.find(key) != definition) {
            throw new LuaError("Particle reference expired after owner reload or unload: " + key);
        }
    }
}
