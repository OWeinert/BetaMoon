package betamoon.luaapi.multiblock;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.multiblock.MultiblockDefinition.Cell;
import betamoon.multiblock.MultiblockDefinition.Variant;
import betamoon.multiblock.MultiblockManagement;
import betamoon.multiblock.MultiblockLimits;
import betamoon.multiblock.MultiblockRule;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Item;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Declaration parsing stays outside recognition and world interaction. */
final class MultiblockManagementDeclarations {
    private MultiblockManagementDeclarations() {
    }

    static MultiblockManagement parse(LuaValue declaration, int controller, Integer fixedFacing, List<Variant> variants,
            Map<String, MultiblockRule> palette, Set<String> symbols, Map<String, String> captures) {
        LuaValue discovery = declaration.get("discovery");
        LuaValue activation = declaration.get("activation");
        if (discovery.isnil()) {
            if (!activation.isnil() || !declaration.get("canActivate").isnil()
                    || !declaration.get("onActivated").isnil() || !declaration.get("onDeactivated").isnil()) {
                throw error("discovery", "managed activation requires a controllerless discovery declaration");
            }
            return null;
        }
        if (controller >= 0) {
            throw error("discovery", "cannot be combined with controller");
        }
        fields(discovery, "discovery", "mode", "cells", "facings");
        String mode = discovery.get("mode").isnil() ? "automatic" : string(discovery.get("mode"), "discovery.mode");
        if (!mode.equals("manual") && !mode.equals("automatic")) {
            throw error("discovery.mode", "expected automatic or manual");
        }
        boolean manual = mode.equals("manual");
        String seeds = discovery.get("cells").isnil() && manual
                ? ""
                : selection(discovery.get("cells"), symbols, captures, "discovery.cells");
        List<Integer> facings = new ArrayList<>();
        if (discovery.get("facings").isnil()) {
            if (fixedFacing != null) {
                facings.add(fixedFacing);
            } else {
                for (int facing = 0; facing < 4; facing++) {
                    facings.add(facing);
                }
            }
        } else {
            int count = length(discovery.get("facings"), "discovery.facings");
            if (count < 1 || count > 4) {
                throw error("discovery.facings", "expected 1..4 distinct horizontal facings");
            }
            for (int index = 1; index <= count; index++) {
                int facing = MultiblockDeclarations.direction(discovery.get("facings").get(index), "discovery.facings");
                if (facings.contains(facing) || fixedFacing != null && facing != fixedFacing) {
                    throw error("discovery.facings", "duplicate facing or conflicts with fixed facing");
                }
                facings.add(facing);
            }
        }
        facings.sort(Integer::compare);
        Set<Integer> seedBlocks = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (!manual && seeds.contains(symbol)) {
                Set<Integer> ids = palette.get(symbol).candidateBlocks();
                if (ids == null || ids.isEmpty() || ids.contains(0)) {
                    throw error("discovery.cells", "seed rules must have a finite non-air block set");
                }
                seedBlocks.addAll(ids);
            }
        }
        long transformed = 0;
        for (Variant variant : variants) {
            transformed += (long) variant.cells.size() * facings.size()
                    * (declaration.get("mirror").optboolean(false) ? 2 : 1);
        }
        if (transformed > 65536) {
            throw error("discovery", "at most 65536 transformed cells across variants");
        }
        if (!activation.isnil()) {
            fields(activation, "activation", "mode", "item", "damage", "target", "cost", "fill");
        }
        String activationMode = activation.isnil()
                ? "automatic"
                : activation.get("mode").isnil() ? "automatic" : string(activation.get("mode"), "activation.mode");
        boolean automatic = activationMode.equals("automatic");
        if (!automatic && !activationMode.equals("item")) {
            throw error("activation.mode", "expected automatic or item");
        }
        if (manual && automatic) {
            throw error("activation.mode", "manual discovery requires item activation");
        }
        int item = -1;
        int itemDamage = -1;
        int damage = 0;
        int consume = 0;
        String targets = "";
        boolean adjacent = false;
        if (!automatic) {
            Item selected = ResourceApi.requireItemReference(activation.get("item"));
            item = selected.shiftedIndex;
            if (Item.itemsList[item] != selected) {
                throw error("activation.item", "item is no longer registered");
            }
            itemDamage = activation.get("damage").isnil()
                    ? -1
                    : integer(activation.get("damage"), "activation.damage", 0, 32767);
            LuaValue target = activation.get("target");
            fields(target, "activation.target", "cells", "position");
            targets = selection(target.get("cells"), symbols, captures, "activation.target.cells");
            String position = target.get("position").isnil()
                    ? "clicked"
                    : string(target.get("position"), "activation.target.position");
            if (!position.equals("clicked") && !position.equals("adjacent")) {
                throw error("activation.target.position", "expected clicked or adjacent");
            }
            adjacent = position.equals("adjacent");
            LuaValue cost = activation.get("cost");
            if (!cost.isnil()) {
                fields(cost, "activation.cost", "damage", "consume");
                damage = cost.get("damage").isnil()
                        ? 0
                        : integer(cost.get("damage"), "activation.cost.damage", 1, 32767);
                consume = cost.get("consume").isnil()
                        ? 0
                        : integer(cost.get("consume"), "activation.cost.consume", 1, 64);
                if (damage > 0 && (consume > 0 || selected.getMaxDamage() <= 0)) {
                    throw error("activation.cost", "damage requires a damageable item and cannot combine with consume");
                }
            }
        } else if (!activation.isnil() && (!activation.get("item").isnil() || !activation.get("damage").isnil()
                || !activation.get("target").isnil() || !activation.get("cost").isnil())) {
            throw error("activation", "item, damage, target and cost require item mode");
        }
        String fillCells = "";
        int fillBlock = -1;
        String facingState = null;
        LuaValue fill = activation.isnil() ? LuaValue.NIL : activation.get("fill");
        if (!fill.isnil()) {
            fields(fill, "activation.fill", "cells", "block", "facingState");
            fillCells = selection(fill.get("cells"), symbols, captures, "activation.fill.cells");
            fillBlock = ResourceApi.requireBlockReference(fill.get("block")).blockID;
            if (fillBlock == 0 || Block.isBlockContainer[fillBlock]) {
                throw error("activation.fill.block", "requires a non-air block without a tile entity");
            }
            if (!fill.get("facingState").isnil()) {
                facingState = string(fill.get("facingState"), "activation.fill.facingState");
                BlockDefinition block = BlockCallbackRegistry.get(fillBlock);
                if (block == null) {
                    throw error("activation.fill.facingState", "block has no declared state");
                }
                block.state.requireEnumValues(facingState, "north", "east", "south", "west");
            }
            for (Variant variant : variants) {
                for (Cell cell : variant.cells) {
                    if (fillCells.contains(cell.symbol) && cell.rule.matches(fillBlock, 0, 0, false)) {
                        throw error("activation.fill", "output must differ from the original selected cells");
                    }
                }
            }
        }
        int constraints = declaration.get("constraints").isnil() ? 0 : declaration.get("constraints").length();
        for (Variant variant : variants) {
            int work = variant.cells.size() * (8 + constraints * 4);
            if (work > Math.min(65536, MultiblockLimits.visitsPerTick() / 2)) {
                throw error("activation", "transformed pattern exceeds the bounded activation allowance; "
                        + "reduce required cells/constraints or increase multiblock visitsPerTick");
            }
        }
        return new MultiblockManagement(manual, seeds, facings, seedBlocks, automatic, item, itemDamage, targets,
                adjacent, damage, consume, fillCells, fillBlock, facingState);
    }

    private static String selection(LuaValue value, Set<String> symbols, Map<String, String> captures, String path) {
        String name = string(value, path);
        String selected = captures.getOrDefault(name, name);
        if (selected.isEmpty()) {
            throw error(path, "expected a symbol selection or capture");
        }
        for (int index = 0; index < selected.length(); index++) {
            if (!symbols.contains(selected.substring(index, index + 1))) {
                throw error(path, "unknown symbol or capture");
            }
        }
        return selected;
    }
}
