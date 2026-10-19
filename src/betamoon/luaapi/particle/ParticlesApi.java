package betamoon.luaapi.particle;

import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.TextureAsset;
import betamoon.client.particles.ClientParticles;
import betamoon.client.particles.ParticleRuntime;
import betamoon.content.ContentKey;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.particles.ParticleDefinition;
import betamoon.particles.ParticleDiagnostics;
import betamoon.particles.ParticleEffectDefinition;
import betamoon.particles.ParticleRegistry;
import betamoon.runtime.RuntimeEnvironment;
import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Entity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.particle.LuaParticleAccess.argument;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;

/**
 * Public registry and world presentation facade. Particle declarations remain
 * distinct from live handles.
 */
public final class ParticlesApi {
    private ParticlesApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable particles = registry(false);
        particles.set("effects", registry(true));
        particles.set("getStats", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaTable result = new LuaTable();
                if (RuntimeEnvironment.isClient()) {
                    for (Map.Entry<String, ParticleRuntime.Counters> entry : ClientParticles.runtime().snapshot()
                            .entrySet()) {
                        ParticleRuntime.Counters value = entry.getValue();
                        LuaTable counters = new LuaTable();
                        counters.set("active", value.active);
                        counters.set("emitted", valueOf(value.emitted));
                        counters.set("distanceCulled", valueOf(value.distanceCulled));
                        counters.set("budgetDropped", valueOf(value.budgetDropped));
                        counters.set("failedDefinitions", valueOf(value.failedDefinitions));
                        result.set(entry.getKey(), counters);
                    }
                }
                return result;
            }
        });
        module.set("particles", particles);
    }

    private static LuaTable registry(boolean effect) {
        LuaTable api = new LuaTable();
        api.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaCallbackScope.requireNoBindingMutation();
                requireHooks();
                try {
                    LuaValue value = argument(args, api, 1);
                    if (effect) {
                        ParticleEffectDefinition definition = ParticleDeclarations.effect(value);
                        ParticleRegistry.stage(definition.key, definition, null);
                        return new ParticleReference(definition);
                    }
                    ParticleDefinition definition = ParticleDeclarations.particle(value);
                    if (definition.texture != null && RuntimeEnvironment.isClient()) {
                        ClientAssets.resolveTexture(definition.texture);
                    }
                    TextureAsset validation = definition.texture == null || !RuntimeEnvironment.isClient()
                            ? null
                            : ClientAssets.acquireTexture(definition.texture);
                    ParticleRegistry.stage(definition.key, definition, validation);
                    return new ParticleReference(definition);
                } catch (IOException | IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("Particle declaration: " + error.getMessage());
                }
            }
        });
        for (String method : new String[]{"get", "getRequired"}) {
            api.set(method, new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    ContentKey key = ParticleDeclarations.referenceKey(argument(args, api, 1), effect);
                    Object found = ParticleRegistry.find(key);
                    if (found == null) {
                        if ("getRequired".equals(method)) {
                            throw new LuaError("Particle " + (effect ? "effect" : "type") + " not registered: " + key);
                        }
                        return NIL;
                    }
                    return new ParticleReference(found);
                }
            });
        }
        return api;
    }

    public static void attachWorld(LuaTable api, LuaCallbackScope scope, World world, int x, int y, int z,
            boolean allowPresentation) {
        LuaValue vanilla = api.get("spawnParticle");
        api.set("spawnParticle", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue requested = argument(args, api, 1);
                if (requested.type() == LuaValue.TSTRING && requested.tojstring().indexOf(':') < 0) {
                    return vanilla.invoke(args);
                }
                requirePresentation(scope, allowPresentation);
                ParticleDefinition definition = ParticleRegistry
                        .particle(ParticleDeclarations.referenceKey(requested, false));
                if (definition == null) {
                    throw new LuaError("spawnParticle: particle type is not registered");
                }
                LuaValue options = argument(args, api, 2);
                if (!options.isnil()) {
                    fields(options, "spawnParticle.options", "x", "y", "z", "velocityX", "velocityY", "velocityZ",
                            "scale", "color", "seed");
                }
                double px = position(options, "x", x + 0.5);
                double py = position(options, "y", y + 0.5);
                double pz = position(options, "z", z + 0.5);
                double vx = option(options, "velocityX", 0, -16, 16);
                double vy = option(options, "velocityY", 0, -16, 16);
                double vz = option(options, "velocityZ", 0, -16, 16);
                double scale = options.isnil() || options.get("scale").isnil()
                        ? 1
                        : ParticleDeclarations.positive(options.get("scale"), "spawnParticle.scale", 4);
                double[] tint = ParticleDeclarations.color(field(options, "color"), "spawnParticle.color");
                long seed = seed(options);
                String unavailable = route(world);
                if (unavailable != null) {
                    ParticleDiagnostics.unavailable(requestOwner(definition.owner), unavailable);
                    return NONE;
                }
                ClientParticles.runtime().spawn(definition, requestOwner(definition.owner), px, py, pz, vx, vy, vz,
                        scale, tint, seed, 16);
                return NONE;
            }
        });
        api.set("playParticleEffect", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requirePresentation(scope, allowPresentation);
                ContentKey key = ParticleDeclarations.referenceKey(argument(args, api, 1), true);
                ParticleEffectDefinition definition = (ParticleEffectDefinition) ParticleRegistry.find(key);
                if (definition == null) {
                    throw new LuaError("playParticleEffect: effect is not registered: " + key);
                }
                definition = resolveEffectParticles(definition);
                LuaValue options = argument(args, api, 2);
                if (!options.isnil()) {
                    fields(options, "playParticleEffect.options", "x", "y", "z", "attachTo", "offset", "seed");
                }
                double px = position(options, "x", x + 0.5);
                double py = position(options, "y", y + 0.5);
                double pz = position(options, "z", z + 0.5);
                LuaValue offset = field(options, "offset");
                if (!offset.isnil()) {
                    fields(offset, "playParticleEffect.offset", "x", "y", "z");
                }
                LuaValue attachment = field(options, "attachTo");
                if (!offset.isnil() && attachment.isnil()) {
                    throw new LuaError("playParticleEffect.offset requires attachTo");
                }
                Entity actor = attachment.isnil()
                        ? null
                        : LuaEntityActionAccess.target(attachment, world, "playParticleEffect");
                if (!attachment.isnil() && actor == null) {
                    return varargsOf(NIL, valueOf("attachment_unavailable"));
                }
                double ox = option(offset, "x", 0, -64, 64);
                double oy = option(offset, "y", 0, -64, 64);
                double oz = option(offset, "z", 0, -64, 64);
                long seed = seed(options);
                String unavailable = route(world);
                if (unavailable != null) {
                    return varargsOf(NIL, valueOf(unavailable));
                }
                ParticleRuntime.Effect effect = ClientParticles.runtime().play(definition,
                        requestOwner(definition.owner), px, py, pz, actor, ox, oy, oz, seed);
                return effect == null ? varargsOf(NIL, valueOf("budget_exceeded")) : new EffectHandle(effect);
            }
        });
    }

    private static void requirePresentation(LuaCallbackScope scope, boolean allowed) {
        LuaCallbackScope.requireNoBindingMutation();
        if (allowed) {
            scope.requireActive();
        } else {
            scope.requireMutable();
        }
    }

    private static ParticleEffectDefinition resolveEffectParticles(ParticleEffectDefinition definition) {
        List<ParticleEffectDefinition.Emitter> emitters = new ArrayList<ParticleEffectDefinition.Emitter>();
        for (ParticleEffectDefinition.Emitter emitter : definition.emitters) {
            ParticleDefinition particle = ParticleRegistry.particle(emitter.particle.key);
            if (particle == null) {
                throw new LuaError("Particle effect dependency is unavailable: " + emitter.particle.key);
            }
            emitters.add(emitter.withParticle(particle));
        }
        return new ParticleEffectDefinition(definition.key, definition.owner, emitters, definition.viewDistance,
                definition.decorative);
    }

    private static String route(World world) {
        return RuntimeEnvironment.isClient() ? ClientParticles.route(world) : "client_unavailable";
    }

    private static String requestOwner(String definitionOwner) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        // Older native callbacks do not install a script execution scope. Their visual
        // definition still
        // supplies a stable cleanup/budget owner without changing unrelated callback
        // routing.
        return owner == null ? definitionOwner : owner;
    }

    private static LuaValue field(LuaValue value, String key) {
        return value.isnil() ? LuaValue.NIL : value.get(key);
    }

    private static double position(LuaValue options, String axis, double fallback) {
        return option(options, axis, fallback, -30000000, 30000000);
    }

    private static double option(LuaValue options, String field, double fallback, double min, double max) {
        return ParticleDeclarations.bounded(field(options, field), fallback, "particle.options." + field, min, max);
    }

    private static long seed(LuaValue options) {
        LuaValue value = field(options, "seed");
        return value.isnil()
                ? System.nanoTime()
                : integer(value, "particle.seed", Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private static void requireHooks() {
        if (!RuntimeEnvironment.isClient()) {
            return;
        }
        if (!AgentRuntime.isRegistered()) {
            throw new LuaError("Custom particles require BetaMoon native instrumentation");
        }
        for (String id : new String[]{"tick", "render", "clear", "spawn"}) {
            boolean available = false;
            for (HookDiagnostic diagnostic : AgentRuntime.getReport().snapshot()) {
                if (("betamoon:particles:" + id).equals(diagnostic.getHookId())) {
                    HookStatus status = diagnostic.getStatus();
                    available = status == HookStatus.APPLIED || status == HookStatus.ALREADY_APPLIED
                            || status == HookStatus.WAITING_FOR_TARGET;
                    break;
                }
            }
            if (!available) {
                throw new LuaError("Custom particles require unavailable hook betamoon:particles:" + id);
            }
        }
    }

    private static final class EffectHandle extends LuaTable {
        private final ParticleRuntime.Effect effect;

        private EffectHandle(ParticleRuntime.Effect effect) {
            this.effect = effect;
            set("isActive", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    requireOwner();
                    return valueOf(effect.isActive());
                }
            });
            set("stop", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    requireOwner();
                    LuaCallbackScope.requireNoBindingMutation();
                    effect.stop();
                    return NONE;
                }
            });
            set("setPosition", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    requireOwner();
                    for (int i = 1; i <= 3; i++) {
                        if (argument(args, EffectHandle.this, i).isnil()) {
                            throw new LuaError("particleEffect.setPosition: x, y, and z are required");
                        }
                    }
                    double x = ParticleDeclarations.bounded(argument(args, EffectHandle.this, 1), 0,
                            "particleEffect.setPosition.x", -30000000, 30000000);
                    double y = ParticleDeclarations.bounded(argument(args, EffectHandle.this, 2), 0,
                            "particleEffect.setPosition.y", -30000000, 30000000);
                    double z = ParticleDeclarations.bounded(argument(args, EffectHandle.this, 3), 0,
                            "particleEffect.setPosition.z", -30000000, 30000000);
                    LuaCallbackScope.requireNoBindingMutation();
                    try {
                        effect.position(x, y, z);
                    } catch (IllegalStateException error) {
                        throw new LuaError(error.getMessage());
                    }
                    return NONE;
                }
            });
        }

        private void requireOwner() {
            String owner = LuaScriptRegistry.getCurrentScriptFile();
            if (owner != null && !effect.owner.equals(owner)) {
                throw new LuaError("Particle effect handle belongs to another script");
            }
        }
    }
}
