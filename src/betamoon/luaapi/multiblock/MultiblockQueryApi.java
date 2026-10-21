package betamoon.luaapi.multiblock;

import betamoon.content.ContentKey;
import betamoon.luaapi.block.BlockFaces;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaPositionArguments;
import betamoon.multiblock.ControllerlessMultiblockRuntime;
import betamoon.multiblock.MultiblockActivationResult;
import betamoon.multiblock.MultiblockDefinition;
import betamoon.multiblock.MultiblockDefinition.Variant;
import betamoon.multiblock.MultiblockInstance;
import betamoon.multiblock.MultiblockItemUse;
import betamoon.multiblock.MultiblockPlacement;
import betamoon.multiblock.MultiblockPosition;
import betamoon.multiblock.MultiblockRuntime;
import betamoon.multiblock.MultiblockSearch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Scoped pure discovery, membership and actor-aware exact activation. */
public final class MultiblockQueryApi {
    private MultiblockQueryApi() {
    }

    private interface Call {
        Varargs invoke(Varargs arguments);
    }

    private static void method(LuaTable api, String name, Call call) {
        api.set(name, new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                try {
                    return call.invoke(arguments.arg1() == api ? arguments.subargs(2) : arguments);
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    throw new LuaError(invalid.getMessage());
                }
            }
        });
    }

    static void attach(LuaTable api, LuaCallbackScope scope, World world) {
        method(api, "findMultiblocks", args -> {
            scope.requireActive();
            MultiblockDefinition definition = MultiblocksApi.definition(args.arg1());
            LuaValue options = args.arg(2);
            fields(options, "findMultiblocks.options", "near", "bounds", "containedIn", "facings", "mirror", "repeats",
                    "maxMatches");
            MultiblockSearch.Bounds bounds = bounds(options.get("bounds"), "bounds");
            MultiblockSearch.Bounds contained = options.get("containedIn").isnil()
                    ? null
                    : bounds(options.get("containedIn"), "containedIn");
            List<Integer> facings = facings(definition, options.get("facings"));
            boolean mirror = bool(options.get("mirror"), "mirror", definition.mirror);
            if (mirror && !definition.mirror) {
                throw new LuaError("Mirroring is not enabled for this definition");
            }
            Integer repeats = repeats(definition, options.get("repeats"));
            Iterator<MultiblockPlacement> candidates;
            if (options.get("near").isnil()) {
                candidates = MultiblockSearch.within(definition, bounds, contained, facings, mirror, repeats);
            } else {
                LuaValue near = options.get("near");
                fields(near, "near", "x", "y", "z", "cells");
                candidates = MultiblockSearch.near(MultiblockSearch.seeds(definition,
                        selection(definition, near.get("cells")), facings, mirror, repeats), point(near, "near"),
                        bounds, contained);
            }
            int max = options.get("maxMatches").isnil() ? 64 : integer(options.get("maxMatches"), "maxMatches", 1, 256);
            MultiblockSearch search = new MultiblockSearch(candidates, ControllerlessMultiblockRuntime.readers(world),
                    max);
            scope.useMultiblockWork(search.advance(scope.multiblockWorkRemaining()));
            LuaTable result = new LuaTable();
            result.set("complete", LuaValue.valueOf(search.complete()));
            result.set("reason", search.reason());
            result.set("truncated", LuaValue.valueOf(!search.complete()));
            LuaTable matches = new LuaTable();
            int index = 0;
            for (MultiblockSearch.Match match : search.matches()) {
                LuaTable entry = MultiblocksApi.report(definition, match.placement.anchor, match.placement.facing,
                        match.placement.mirror, match.report);
                LuaTable captures = new LuaTable();
                for (String capture : definition.captures.keySet()) {
                    LuaTable positions = new LuaTable();
                    int count = 0;
                    for (int cell = 0; cell < match.placement.variant.cells.size(); cell++) {
                        if (definition.captures.get(capture).contains(match.placement.variant.cells.get(cell).symbol)) {
                            positions.set(++count, position(match.placement.positions.get(cell)));
                        }
                    }
                    captures.set(capture, positions);
                }
                entry.set("captures", captures);
                LuaTable box = new LuaTable();
                box.set("min", position(
                        new MultiblockPosition(match.placement.minX, match.placement.minY, match.placement.minZ)));
                box.set("max", position(
                        new MultiblockPosition(match.placement.maxX, match.placement.maxY, match.placement.maxZ)));
                entry.set("bounds", box);
                matches.set(++index, entry);
            }
            result.set("matches", matches);
            return result;
        });
        method(api, "getMultiblocksAt", args -> {
            scope.requireActive();
            LuaPositionArguments.Position position = LuaPositionArguments.integers(args, null, 1,
                    "getMultiblocksAt", -32000000, 31999999, 0, 127);
            int x = position.xInt();
            int y = position.yInt();
            int z = position.zInt();
            LuaValue options = args.arg(position.nextArgument);
            ContentKey filter = null;
            if (!options.isnil()) {
                fields(options, "getMultiblocksAt.options", "definition");
                if (!options.get("definition").isnil()) {
                    filter = MultiblocksApi.definition(options.get("definition")).key;
                }
            }
            List<MultiblockInstance> found = new ArrayList<>(
                    ControllerlessMultiblockRuntime.at(world, x, y, z, filter));
            for (MultiblockInstance instance : MultiblockRuntime.at(world, x, y, z)) {
                if (filter == null || instance.definition.key.equals(filter)) {
                    found.add(instance);
                }
            }
            found.sort((left, right) -> left.identity.compareTo(right.identity));
            LuaTable result = new LuaTable();
            for (int index = 0; index < found.size(); index++) {
                result.set(index + 1, MultiblocksApi.instance(scope, found.get(index), false));
            }
            return result;
        });
    }

    public static void attachItem(LuaTable api, LuaCallbackScope scope, World world, EntityPlayer player,
            ItemStack stack, int clickedX, int clickedY, int clickedZ, int clickedFace) {
        method(api, "activateMultiblock", args -> {
            scope.requireMutable();
            MultiblockDefinition definition = MultiblocksApi.definition(args.arg1());
            if (definition.management == null) {
                throw new LuaError("activateMultiblock requires managed controllerless discovery");
            }
            LuaPositionArguments.Position position = LuaPositionArguments.integers(args, null, 2,
                    "activateMultiblock", -32000000, 31999999, 0, 127);
            MultiblockPosition anchor = new MultiblockPosition(position.xInt(), position.yInt(), position.zInt());
            LuaValue options = args.arg(position.nextArgument).isnil()
                    ? new LuaTable() : args.arg(position.nextArgument);
            fields(options, "activateMultiblock.options", "facing", "mirror", "repeats", "clicked", "face");
            int facing = options.get("facing").isnil()
                    ? definition.management.facings.get(0)
                    : MultiblockDeclarations.direction(options.get("facing"), "facing");
            if (!definition.management.facings.contains(facing)) {
                throw new LuaError("Facing is outside discovery.facings");
            }
            boolean mirror = bool(options.get("mirror"), "mirror", false);
            if (mirror && !definition.mirror) {
                throw new LuaError("Mirroring is not enabled");
            }
            Integer repeats = repeats(definition, options.get("repeats"));
            Variant variant = definition.variants.get(0);
            for (Variant candidate : definition.variants) {
                if (repeats != null && candidate.repeats == repeats) {
                    variant = candidate;
                }
            }
            int work = variant.cells.size() * (8 + definition.constraints.size() * 4);
            if (work > scope.multiblockWorkRemaining()) {
                return result(scope, new MultiblockActivationResult(false, true, "search_budget_exceeded", null));
            }
            scope.useMultiblockWork(work);
            MultiblockPosition clicked = options.get("clicked").isnil()
                    ? new MultiblockPosition(clickedX, clickedY, clickedZ)
                    : point(options.get("clicked"), "clicked");
            int face = options.get("face").isnil()
                    ? clickedFace
                    : options.get("face").isstring()
                            ? BlockFaces.side(options.get("face").tojstring(), -1)
                            : integer(options.get("face"), "face", 0, 5);
            if (clickedFace < 0 && options.get("clicked").isnil()) {
                return result(scope, new MultiblockActivationResult(false, false, "block_target_required", null));
            }
            MultiblockActivationResult outcome = ControllerlessMultiblockRuntime.activate(world,
                    new MultiblockPlacement(definition, anchor, facing, mirror, variant), player, stack, clicked, face);
            MultiblockItemUse.handled(outcome.handled);
            return result(scope, outcome);
        });
    }

    static LuaTable result(LuaCallbackScope scope, MultiblockActivationResult outcome) {
        LuaTable result = new LuaTable();
        result.set("activated", LuaValue.valueOf(outcome.activated));
        result.set("handled", LuaValue.valueOf(outcome.handled));
        result.set("reason", outcome.reason);
        if (outcome.instance != null && !outcome.instance.expired) {
            result.set("instance", MultiblocksApi.instance(scope, outcome.instance, false));
        }
        return result;
    }

    private static String selection(MultiblockDefinition definition, LuaValue value) {
        String name = string(value, "near.cells");
        String result = definition.captures.getOrDefault(name, name);
        if (result.isEmpty()) {
            throw new LuaError("near.cells requires a symbol selection or capture");
        }
        for (int index = 0; index < result.length(); index++) {
            if (!definition.symbols.contains(result.substring(index, index + 1))) {
                throw new LuaError("Unknown near.cells symbol/capture");
            }
        }
        return result;
    }

    private static List<Integer> facings(MultiblockDefinition definition, LuaValue value) {
        List<Integer> allowed = definition.management != null
                ? definition.management.facings
                : definition.fixedFacing == null
                        ? Arrays.asList(0, 1, 2, 3)
                        : Collections.singletonList(definition.fixedFacing);
        if (value.isnil()) {
            return allowed;
        }
        List<Integer> result = new ArrayList<>();
        int count = length(value, "facings");
        if (count < 1 || count > 4) {
            throw new LuaError("facings requires 1..4 directions");
        }
        for (int index = 1; index <= count; index++) {
            int facing = MultiblockDeclarations.direction(value.get(index), "facings");
            if (!allowed.contains(facing) || result.contains(facing)) {
                throw new LuaError("Duplicate or disallowed facing");
            }
            result.add(facing);
        }
        result.sort(Integer::compare);
        return result;
    }

    private static Integer repeats(MultiblockDefinition definition, LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        int selected = integer(value, "repeats", 0, 32);
        for (Variant variant : definition.variants) {
            if (variant.repeats == selected) {
                return selected;
            }
        }
        throw new LuaError("Repeat count is outside the declared range");
    }

    private static MultiblockSearch.Bounds bounds(LuaValue value, String path) {
        fields(value, path, "min", "max");
        MultiblockPosition min = point(value.get("min"), path + ".min");
        MultiblockPosition max = point(value.get("max"), path + ".max");
        return new MultiblockSearch.Bounds(min.x, min.y, min.z, max.x, max.y, max.z, !path.equals("containedIn"));
    }

    private static MultiblockPosition point(LuaValue value, String path) {
        if (path.equals("near")) {
            fields(value, path, "x", "y", "z", "cells");
        } else {
            fields(value, path, "x", "y", "z");
        }
        return new MultiblockPosition(coordinate(value.get("x"), path + ".x"),
                integer(value.get("y"), path + ".y", 0, 127), coordinate(value.get("z"), path + ".z"));
    }

    private static LuaTable position(MultiblockPosition point) {
        LuaTable result = new LuaTable();
        result.set("x", point.x);
        result.set("y", point.y);
        result.set("z", point.z);
        return result;
    }

    private static int coordinate(LuaValue value, String path) {
        return integer(value, path, -32000000, 31999999);
    }
}
