package betamoon.debug;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetRegistration;
import betamoon.assets.BuiltinAssets;
import betamoon.entity.EntityTypeRegistry;
import betamoon.fuel.FuelSetDefinition;
import betamoon.fuel.FuelRegistry;
import betamoon.luaapi.audio.SoundEvents;
import betamoon.luaapi.module.ModuleRegistry;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.luamodloader.ScriptAssetScope;
import betamoon.recipes.custom.CustomRecipes;
import betamoon.recipes.custom.RecipeTypes;
import betamoon.tileentity.TileEntityRegistry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.Item;

/** Exports lookup-oriented content identities with explicit identifier kinds. */
final class DebugContentExporter implements DebugExporter {
    @Override
    public void export(DebugExportSession session) throws Exception {
        DebugBlockExporter.export(session);
        DebugItemExporter.export(session);
        session.writeTextFile("content_keys.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                List<String> rows = rows();
                for (String row : rows) {
                    writer.write(row);
                    writer.newLine();
                }
                return rows.size();
            }
        });
    }

    private static List<String> rows() {
        List<String> rows = new ArrayList<String>();
        Map<String, String> owners = contentOwners();
        for (int id = 0; id < Block.blocksList.length; id++) {
            if (Block.blocksList[id] != null) {
                rows.add("type: block | canonical key: unavailable | block ID: " + id + " | owner: "
                        + owner(owners, "block", id));
            }
        }
        for (Item item : Item.itemsList) {
            if (item != null && item.shiftedIndex >= 256) {
                rows.add("type: item | canonical key: unavailable | item registry ID: " + item.shiftedIndex
                        + " | legacy constructor ID: " + (item.shiftedIndex - 256) + " | owner: "
                        + owner(owners, "item", item.shiftedIndex));
            }
        }
        for (AssetRegistration registration : ScriptAssetScope.snapshot()) {
            rows.add("type: asset/" + registration.getDefinition().getId().getKind().name().toLowerCase()
                    + " | canonical key: " + registration.getDefinition().getId().getKey() + " | owner: "
                    + safe(registration.getOwner()));
        }
        for (Map.Entry<AssetId, AssetDefinition> asset : BuiltinAssets.models().entrySet()) {
            rows.add("type: asset/model | canonical key: " + asset.getKey().getKey() + " | owner: minecraft");
        }
        for (SoundEvents.Description event : SoundEvents.snapshot()) {
            rows.add("type: sound event | canonical key: " + event.key + " | owner: " + safe(event.owner));
        }
        for (EntityTypeRegistry.Description entity : EntityTypeRegistry.snapshot()) {
            rows.add("type: entity | canonical key: " + entity.key + " | owner: " + safe(entity.owner));
        }
        for (TileEntityRegistry.TileDescription tile : TileEntityRegistry.tileEntityDescriptions()) {
            rows.add("type: tile entity | registration name: " + safe(tile.name) + " | owner: " + safe(tile.owner));
        }
        for (TileEntityRegistry.ContainerDescription container : TileEntityRegistry.containerDescriptions()) {
            rows.add("type: container | registration name: " + safe(container.name) + " | owner: "
                    + safe(container.owner));
        }
        for (TileEntityRegistry.GuiDescription gui : TileEntityRegistry.guiDescriptions()) {
            rows.add("type: container GUI | registration name: " + safe(gui.name) + " | owner: "
                    + safe(gui.owner));
        }
        for (FuelSetDefinition set : FuelRegistry.sets()) {
            rows.add("type: fuel set | canonical key: " + set.key + " | owner: " + safe(set.owner));
        }
        for (RecipeTypes.Type type : RecipeTypes.all()) {
            rows.add("type: recipe type | key: " + safe(type.name) + " | owner: " + safe(type.owner));
        }
        for (CustomRecipes.Entry recipe : CustomRecipes.all()) {
            rows.add("type: recipe | key: " + safe(recipe.key) + " | owner: " + safe(recipe.owner));
        }
        for (ModuleRegistry.Description module : ModuleRegistry.snapshot()) {
            rows.add("type: exported module | name: " + safe(module.name) + " | owner: " + safe(module.owner));
        }
        Collections.sort(rows);
        return rows;
    }

    private static Map<String, String> contentOwners() {
        Map<String, String> result = new HashMap<String, String>();
        for (LuaContentRegistry.ContentDescription entry : LuaContentRegistry.snapshot()) {
            result.put(entry.namespace + ":" + entry.id, safe(entry.owner));
        }
        return result;
    }

    private static String owner(Map<String, String> owners, String namespace, int id) {
        String owner = owners.get(namespace + ":" + id);
        return owner == null ? "minecraft/foreign" : owner;
    }

    private static String safe(String value) {
        return DebugExportNames.safeString(value == null ? "unavailable" : value);
    }
}
