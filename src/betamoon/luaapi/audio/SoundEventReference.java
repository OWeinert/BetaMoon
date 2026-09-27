package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import betamoon.client.audio.SoundAsset;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable live Lua reference to a sound-event key. */
public final class SoundEventReference extends LuaTable {
    private final AssetKey key;

    SoundEventReference(AssetKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(SoundEventReference.this.key.toString());
            }
        });
        set("override", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return applyOverride(args.arg(args.arg1() == SoundEventReference.this ? 2 : 1));
            }
        });
    }

    @Override
    public LuaValue get(LuaValue field) {
        if (!field.isstring()) {
            return super.get(field);
        }
        String name = field.tojstring();
        SoundEvents.Entry entry = SoundEvents.findEntry(key);
        if (name.equals("key")) {
            return valueOf(key.toString());
        }
        if (name.equals("exists")) {
            return valueOf(entry != null);
        }
        if (entry == null) {
            return super.get(field);
        }
        SoundEventDefinition definition = entry.effective;
        if (name.equals("owner")) {
            return valueOf(entry.owner);
        }
        if (name.equals("enabled")) {
            return valueOf(definition.enabled);
        }
        if (name.equals("volume")) {
            return valueOf(definition.volume);
        }
        if (name.equals("range")) {
            return valueOf(definition.range);
        }
        if (name.equals("pitch")) {
            LuaTable pitch = new LuaTable();
            pitch.set("min", definition.pitchMin);
            pitch.set("max", definition.pitchMax);
            return pitch;
        }
        if (name.equals("clips")) {
            LuaTable clips = new LuaTable();
            for (int index = 0; index < definition.clips.size(); index++) {
                SoundEventDefinition.Clip clip = definition.clips.get(index);
                LuaTable value = new LuaTable();
                value.set("sound", clip.location.toString());
                value.set("weight", clip.weight);
                clips.set(index + 1, value);
            }
            return clips;
        }
        return super.get(field);
    }

    AssetKey key() {
        return key;
    }

    boolean matches(LuaValue query) {
        SoundEvents.Entry entry = SoundEvents.findEntry(key);
        if (entry == null) {
            return false;
        }
        if (!query.get("key").isnil() && !key.toString().equals(query.get("key").checkjstring())) {
            return false;
        }
        if (!query.get("owner").isnil() && !entry.owner.equals(query.get("owner").checkjstring())) {
            return false;
        }
        return query.get("enabled").isnil()
                || entry.effective.enabled == query.get("enabled").checkboolean();
    }

    LuaValue applyOverride(LuaValue definition) {
        final SoundEvents.Entry entry = SoundEvents.findEntry(key);
        if (entry == null) {
            throw new LuaError("Sound event is no longer registered: " + key);
        }
        if (!definition.istable()) {
            throw new LuaError("Sound event override expects a table.");
        }
        LuaValue when = definition.get("when");
        if (!when.isnil() && !when.istable()) {
            throw new LuaError("Sound event override when must be a table.");
        }
        final LuaTable handle = new LuaTable();
        handle.set("target", this);
        if (!when.isnil() && !when.get("owner").isnil()
                && !entry.owner.equals(when.get("owner").checkjstring())) {
            handle.set("active", FALSE);
            handle.set("reason", "target owner did not match");
            return handle;
        }
        LuaValue changes = definition.get("changes");
        if (changes.isnil()) {
            changes = definition;
        }
        if (!changes.istable()) {
            throw new LuaError("Sound event override changes must be a table.");
        }
        LuaTable patchTable = new LuaTable();
        LuaValue field = NIL;
        while (!(field = changes.next(field).arg1()).isnil()) {
            String name = field.checkjstring();
            if (!name.equals("when") && !name.equals("changes") && !name.equals("priority")
                    && !name.equals("target")) {
                patchTable.set(field, changes.get(field));
            }
        }
        final SoundEventPatch patch = SoundEventParser.readPatch(patchTable);
        final RetainedSounds retained = new RetainedSounds(SoundEvents.acquire(patch));
        ScriptResourceTracker.track(retained);
        final OverrideManager.Layer<SoundEvents.Entry, SoundEventPatch> layer;
        try {
            layer = OverrideManager.apply("soundEvent:" + key, entry, SoundEvents.OVERRIDE_PROPERTY, patch,
                    definition.get("priority").optint(0));
        } catch (RuntimeException error) {
            retained.run();
            throw error;
        }
        handle.set("active", TRUE);
        handle.set("remove", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                if (handle.get("active").toboolean()) {
                    layer.remove();
                    retained.run();
                    handle.set("active", FALSE);
                }
                return NIL;
            }
        });
        return handle;
    }

    private static final class RetainedSounds implements ScriptResourceTracker.Cleanup {
        private List<SoundAsset> sounds;

        private RetainedSounds(List<SoundAsset> sounds) {
            this.sounds = sounds;
        }

        public synchronized void run() {
            if (sounds != null) {
                SoundEvents.close(sounds);
                sounds = null;
            }
        }
    }
}
