package betamoon.loot;

import betamoon.assets.AssetKey;
import betamoon.assets.model.ModelJson;
import betamoon.loot.LootStackDefinition.LootStack;
import betamoon.luaapi.utils.LuaDataSnapshot;
import betamoon.worldgen.SeedMixer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Inline or registered loot-table source sampled independently for runtime gameplay events. */
public final class GameplayLootDefinition {
    private final LootTableDefinition inline;
    private final AssetKey table;
    private final String identity;

    private GameplayLootDefinition(LootTableDefinition inline, AssetKey table) {
        this.inline = inline;
        this.table = table;
        this.identity = inline == null ? "table:" + table : "inline:" + inline.semanticHash();
    }

    public static GameplayLootDefinition read(LuaValue input, String path) {
        try {
            Map<String, Object> value = LuaDataSnapshot.object(input, path);
            ModelJson.fields(value, path, "pools", "table");
            Object pools = value.get("pools");
            Object table = value.get("table");
            if ((pools == null) == (table == null)) {
                throw new IOException(path + ": expected exactly one of pools or table");
            }
            if (pools != null) {
                return new GameplayLootDefinition(LootTableDefinition.readInline(pools, path + ".pools"), null);
            }
            AssetKey key;
            try {
                key = AssetKey.parse(ModelJson.name(table, path + ".table"));
            } catch (IllegalArgumentException error) {
                throw new IOException(path + ".table: " + error.getMessage());
            }
            return new GameplayLootDefinition(null, key);
        } catch (IOException error) {
            throw new LuaError(error.getMessage());
        }
    }

    /** Samples once using a fresh value from the world's runtime random stream. */
    public List<ItemStack> sample(Random random) {
        LootTableDefinition definition = definition();
        if (definition == null) {
            return Collections.emptyList();
        }
        long eventRandom = random.nextLong();
        long evaluationRandom = SeedMixer.derive(eventRandom, SeedMixer.hash(identity));
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (LootStack stack : definition.generate(evaluationRandom)) {
            result.add(stack.create());
        }
        return result;
    }

    public void validateReferences() throws IOException {
        LootTableDefinition definition = definition();
        if (definition == null) {
            throw new IOException("unknown loot table " + table);
        }
        definition.validate();
    }

    private LootTableDefinition definition() {
        return inline == null ? LootTableRegistry.find(table) : inline;
    }
}
