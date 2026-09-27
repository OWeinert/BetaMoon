package betamoon.luaapi.fuel;

import betamoon.assets.AssetKey;
import betamoon.fuel.FuelRegistry;
import betamoon.fuel.FuelRegistration;
import betamoon.fuel.FuelResolution;
import betamoon.fuel.FuelSetDefinition;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.resource.LuaResultList;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs named fuel sets, registrations, lookup, and machine-facing queries. */
public final class FuelsApi {
    public static final int MAX_BURN_TIME = FuelRegistry.MAX_BURN_TIME;
    private static final Map<FuelRegistration, FuelRegistrationReference> REGISTRATION_REFERENCES = new IdentityHashMap<FuelRegistration, FuelRegistrationReference>();

    private FuelsApi() {
    }

    public static void attach(LuaTable module) {
        FuelService service = new FuelService();
        service.set("add", new AddFuel(service));
        service.set("getBurnTime", new GetBurnTime(service));
        service.set("isFuel", new IsFuel(service));
        service.set("find", new FindFuels(service, 0));
        service.set("first", new FindFuels(service, 1));
        service.set("one", new FindFuels(service, 2));
        service.set("sets", new FuelSets());
        module.set("fuels", service);
    }

    public static AssetKey optionalSetKey(LuaValue value, String path) {
        return value == null || value.isnil() ? FuelRegistry.FURNACE : requireSetKey(value, path);
    }

    public static AssetKey requireSetKey(LuaValue value, String path) {
        AssetKey key;
        if (value instanceof FuelSetReference) {
            key = ((FuelSetReference) value).definition.key;
        } else if (value.isstring()) {
            key = parseKey(value.checkjstring(), path);
        } else {
            throw new LuaError(path + " must be a fuel-set reference or key.");
        }
        if (FuelRegistry.findSet(key) == null) {
            throw new LuaError(path + " refers to an unknown fuel set: " + key);
        }
        return key;
    }

    private static final class FuelService extends LuaTable {
    }

    private static final class FuelSets extends LuaTable {
        private FuelSets() {
            set("add", new AddSet(this));
            set("get", new GetSet(this, false));
            set("getRequired", new GetSet(this, true));
            set("list", new ListSets(this));
        }
    }

    private static final class AddFuel extends VarArgFunction {
        private final FuelService service;

        private AddFuel(FuelService service) {
            this.service = service;
        }

        @Override
        public Varargs invoke(Varargs args) {
            LuaValue definition = argument(args, service, 1);
            if (!definition.istable()) {
                throw new LuaError("fuels:add expects a definition table.");
            }
            String owner = requireOwner();
            AssetKey setKey = optionalSetKey(definition.get("set"), "fuel.set");
            LuaValue itemValue = required(definition, "item", "fuel");
            ItemStack item = readItem(itemValue, "fuel.item");
            Integer damage = readDamage(definition, itemValue);
            int burnTime = positiveInteger(required(definition, "burnTime", "fuel"), "fuel.burnTime",
                    MAX_BURN_TIME);
            final FuelRegistration registration;
            try {
                registration = FuelRegistry.add(owner, setKey, item.itemID, damage, burnTime);
            } catch (IllegalArgumentException error) {
                throw new LuaError("fuel: " + error.getMessage());
            }
            ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
                public void run() {
                    FuelRegistry.remove(registration);
                }
            });
            return registrationReference(registration);
        }
    }

    private static final class FindFuels extends VarArgFunction {
        private final FuelService service;
        private final int mode;

        private FindFuels(FuelService service, int mode) {
            this.service = service;
            this.mode = mode;
        }

        @Override
        public Varargs invoke(Varargs args) {
            LuaValue query = argument(args, service, 1);
            if (query.isnil()) {
                query = new LuaTable();
            }
            if (!query.istable()) {
                throw new LuaError("fuel query must be a table.");
            }
            List<LuaValue> matches = new ArrayList<LuaValue>();
            for (FuelRegistration registration : FuelRegistry.registrations()) {
                FuelRegistrationReference reference = registrationReference(registration);
                if (reference.matches(query)) {
                    matches.add(reference);
                }
            }
            if (mode == 1) {
                return matches.isEmpty() ? NIL : matches.get(0);
            }
            if (mode == 2) {
                if (matches.isEmpty()) {
                    return NIL;
                }
                if (matches.size() != 1) {
                    throw new LuaError("Expected exactly one fuel registration, found " + matches.size() + ".");
                }
                return matches.get(0);
            }
            return new LuaResultList(matches, (reference, definition, index) ->
                    ((FuelRegistrationReference) reference).applyOverride(definition));
        }
    }

    private static final class GetBurnTime extends VarArgFunction {
        private final FuelService service;

        private GetBurnTime(FuelService service) {
            this.service = service;
        }

        @Override
        public Varargs invoke(Varargs args) {
            ItemStack stack = readQueryItem(argument(args, service, 1), "fuels:getBurnTime stack");
            AssetKey setKey = optionalSetKey(argument(args, service, 2), "fuels:getBurnTime set");
            return LuaValue.valueOf(FuelRegistry.resolve(stack, setKey).burnTime);
        }
    }

    private static final class IsFuel extends VarArgFunction {
        private final FuelService service;

        private IsFuel(FuelService service) {
            this.service = service;
        }

        @Override
        public Varargs invoke(Varargs args) {
            ItemStack stack = readQueryItem(argument(args, service, 1), "fuels:isFuel stack");
            AssetKey setKey = optionalSetKey(argument(args, service, 2), "fuels:isFuel set");
            return LuaValue.valueOf(FuelRegistry.resolve(stack, setKey).isFuel());
        }
    }

    private static final class AddSet extends VarArgFunction {
        private final FuelSets sets;

        private AddSet(FuelSets sets) {
            this.sets = sets;
        }

        @Override
        public Varargs invoke(Varargs args) {
            LuaValue definition = argument(args, sets, 1);
            if (!definition.istable()) {
                throw new LuaError("fuels.sets:add expects a definition table.");
            }
            String owner = requireOwner();
            AssetKey key = parseKey(required(definition, "key", "fuel set").checkjstring(), "fuel set key");
            List<AssetKey> includes = new ArrayList<AssetKey>();
            LuaValue includeValues = definition.get("include");
            if (!includeValues.isnil()) {
                if (!includeValues.istable()) {
                    throw new LuaError("fuel set include must be a list.");
                }
                for (int index = 1; index <= includeValues.length(); index++) {
                    includes.add(requireSetKey(includeValues.get(index), "fuel set include[" + index + "]"));
                }
            }
            final FuelSetDefinition set;
            try {
                set = FuelRegistry.addSet(owner, key, includes);
            } catch (IllegalArgumentException error) {
                throw new LuaError("fuel set: " + error.getMessage());
            }
            ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
                public void run() {
                    FuelRegistry.removeSet(set);
                }
            });
            return new FuelSetReference(set);
        }
    }

    private static final class GetSet extends VarArgFunction {
        private final FuelSets sets;
        private final boolean required;

        private GetSet(FuelSets sets, boolean required) {
            this.sets = sets;
            this.required = required;
        }

        @Override
        public Varargs invoke(Varargs args) {
            AssetKey key = parseKey(argument(args, sets, 1).checkjstring(), "fuel set key");
            FuelSetDefinition definition = FuelRegistry.findSet(key);
            if (definition == null && required) {
                throw new LuaError("Fuel set is not registered: " + key);
            }
            return definition == null ? LuaValue.NIL : new FuelSetReference(definition);
        }
    }

    private static final class ListSets extends VarArgFunction {
        private final FuelSets sets;

        private ListSets(FuelSets sets) {
            this.sets = sets;
        }

        @Override
        public Varargs invoke(Varargs args) {
            LuaTable result = new LuaTable();
            List<FuelSetDefinition> definitions = FuelRegistry.sets();
            for (int index = 0; index < definitions.size(); index++) {
                FuelSetDefinition definition = definitions.get(index);
                result.set(index + 1, new FuelSetReference(definition));
            }
            return result;
        }
    }

    private static final class FuelSetReference extends LuaTable {
        private static final LuaValue EXISTS = LuaValue.valueOf("exists");
        private final FuelSetDefinition definition;

        private FuelSetReference(FuelSetDefinition definition) {
            this.definition = definition;
            set("key", definition.key.toString());
            set("owner", definition.owner);
            set("exists", LuaValue.TRUE);
            set("getBurnTime", new SetBurnTime(this));
            set("contains", new SetContains(this));
            set("remove", new RemoveSet(this));
            set("override", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return applyOverride(argument(args, FuelSetReference.this, 1));
                }
            });
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (key.raweq(EXISTS)) {
                return LuaValue.valueOf(FuelRegistry.contains(definition));
            }
            if (key.isstring() && key.tojstring().equals("include")) {
                LuaTable result = new LuaTable();
                for (int index = 0; index < definition.includes.size(); index++) {
                    result.set(index + 1, definition.includes.get(index).toString());
                }
                return result;
            }
            return super.get(key);
        }

        private LuaValue applyOverride(LuaValue value) {
            requireLive(this);
            if (!value.istable()) {
                throw new LuaError("fuel-set override expects a table.");
            }
            LuaValue when = value.get("when");
            final LuaTable handle = new LuaTable();
            handle.set("target", this);
            if (!when.isnil() && !when.get("owner").isnil()
                    && !definition.owner.equals(when.get("owner").checkjstring())) {
                handle.set("active", FALSE);
                handle.set("reason", "target owner did not match");
                return handle;
            }
            LuaValue changes = value.get("changes");
            if (changes.isnil()) {
                changes = value;
            }
            LuaValue includes = changes.get("include");
            if (includes.isnil() || !includes.istable()) {
                throw new LuaError("fuel-set override requires an include list.");
            }
            List<AssetKey> parsed = new ArrayList<AssetKey>();
            for (int index = 1; index <= includes.length(); index++) {
                parsed.add(requireSetKey(includes.get(index), "fuel-set override.include[" + index + "]"));
            }
            final OverrideManager.Layer<FuelSetDefinition, List<AssetKey>> layer = OverrideManager.apply(
                    "fuelSet:" + definition.key, definition, SET_INCLUDES, parsed,
                    value.get("priority").optint(0));
            handle.set("active", TRUE);
            handle.set("remove", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    if (handle.get("active").toboolean()) {
                        layer.remove();
                        handle.set("active", FALSE);
                    }
                    return NIL;
                }
            });
            return handle;
        }
    }

    private static final OverrideManager.Property<FuelSetDefinition, List<AssetKey>> SET_INCLUDES =
            new OverrideManager.Property<FuelSetDefinition, List<AssetKey>>("include",
                    new OverrideManager.PropertyAdapter<FuelSetDefinition, List<AssetKey>>() {
                        public List<AssetKey> read(FuelSetDefinition target) {
                            return target.includes;
                        }

                        public void write(FuelSetDefinition target, List<AssetKey> value) {
                            try {
                                FuelRegistry.updateIncludes(target, value);
                            } catch (IllegalArgumentException error) {
                                throw new LuaError("fuel-set override: " + error.getMessage());
                            }
                        }
                    });

    private static final class SetBurnTime extends VarArgFunction {
        private final FuelSetReference set;

        private SetBurnTime(FuelSetReference set) {
            this.set = set;
        }

        @Override
        public Varargs invoke(Varargs args) {
            requireLive(set);
            ItemStack stack = readQueryItem(argument(args, set, 1), "fuelSet:getBurnTime stack");
            return LuaValue.valueOf(FuelRegistry.resolve(stack, set.definition.key).burnTime);
        }
    }

    private static final class SetContains extends VarArgFunction {
        private final FuelSetReference set;

        private SetContains(FuelSetReference set) {
            this.set = set;
        }

        @Override
        public Varargs invoke(Varargs args) {
            requireLive(set);
            ItemStack stack = readQueryItem(argument(args, set, 1), "fuelSet:contains stack");
            return LuaValue.valueOf(FuelRegistry.resolve(stack, set.definition.key).isFuel());
        }
    }

    private static final class RemoveSet extends VarArgFunction {
        private final FuelSetReference set;

        private RemoveSet(FuelSetReference set) {
            this.set = set;
        }

        @Override
        public Varargs invoke(Varargs args) {
            boolean removed = FuelRegistry.removeSet(set.definition);
            if (removed) {
                set.set("exists", LuaValue.FALSE);
            }
            return LuaValue.valueOf(removed);
        }
    }

    private static final class FuelRegistrationReference extends LuaTable {
        private static final LuaValue EXISTS = LuaValue.valueOf("exists");
        private final FuelRegistration registration;

        private FuelRegistrationReference(FuelRegistration registration) {
            this.registration = registration;
            set("item", registration.itemId);
            set("damage", registration.damage == null ? LuaValue.NIL : LuaValue.valueOf(registration.damage));
            set("burnTime", registration.burnTime);
            set("set", registration.setKey.toString());
            set("owner", registration.owner);
            set("exists", LuaValue.TRUE);
            set("remove", new RemoveRegistration(this));
            set("override", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    return applyOverride(argument(args, FuelRegistrationReference.this, 1));
                }
            });
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (key.raweq(EXISTS)) {
                return LuaValue.valueOf(FuelRegistry.contains(registration));
            }
            if (key.isstring() && key.tojstring().equals("burnTime")) {
                return LuaValue.valueOf(registration.burnTime);
            }
            if (key.isstring() && key.tojstring().equals("enabled")) {
                return LuaValue.valueOf(registration.enabled);
            }
            return super.get(key);
        }

        private boolean matches(LuaValue query) {
            if (!query.get("owner").isnil() && !registration.owner.equals(query.get("owner").checkjstring())) {
                return false;
            }
            if (!query.get("set").isnil()
                    && !registration.setKey.equals(requireSetKey(query.get("set"), "fuel query.set"))) {
                return false;
            }
            if (!query.get("item").isnil()
                    && registration.itemId != readItem(query.get("item"), "fuel query.item").itemID) {
                return false;
            }
            if (!query.get("damage").isnil()
                    && (registration.damage == null
                    || registration.damage.intValue() != nonnegativeInteger(query.get("damage"),
                            "fuel query.damage", Short.MAX_VALUE))) {
                return false;
            }
            return query.get("enabled").isnil()
                    || registration.enabled == query.get("enabled").checkboolean();
        }

        private LuaValue applyOverride(LuaValue definition) {
            if (!FuelRegistry.contains(registration)) {
                throw new LuaError("Fuel registration no longer exists.");
            }
            if (!definition.istable()) {
                throw new LuaError("fuel override expects a table.");
            }
            final LuaTable handle = new LuaTable();
            handle.set("target", this);
            LuaValue when = definition.get("when");
            if (!when.isnil() && !when.get("owner").isnil()
                    && !registration.owner.equals(when.get("owner").checkjstring())) {
                handle.set("active", FALSE);
                handle.set("reason", "target owner did not match");
                return handle;
            }
            LuaValue changes = definition.get("changes");
            if (changes.isnil()) {
                changes = definition;
            }
            int priority = definition.get("priority").optint(0);
            List<OverrideManager.Request<?, ?>> requests = new ArrayList<OverrideManager.Request<?, ?>>();
            LuaValue key = NIL;
            while (!(key = changes.next(key).arg1()).isnil()) {
                String property = key.checkjstring();
                if (property.equals("when") || property.equals("priority") || property.equals("target")
                        || property.equals("changes")) {
                    continue;
                }
                if (property.equals("burnTime")) {
                    requests.add(OverrideManager.request("fuel:" + registration.id, registration,
                            BURN_TIME_PROPERTY, Integer.valueOf(positiveInteger(changes.get(key),
                                    "fuel override.burnTime", MAX_BURN_TIME)), priority));
                } else if (property.equals("enabled")) {
                    requests.add(OverrideManager.request("fuel:" + registration.id, registration,
                            ENABLED_PROPERTY, Boolean.valueOf(changes.get(key).checkboolean()), priority));
                } else {
                    throw new LuaError("Property '" + property + "' cannot be overridden on a fuel registration.");
                }
            }
            final List<OverrideManager.Layer<?, ?>> layers = OverrideManager.applyAll(requests);
            handle.set("active", TRUE);
            handle.set("remove", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    if (handle.get("active").toboolean()) {
                        for (int i = layers.size() - 1; i >= 0; i--) {
                            layers.get(i).remove();
                        }
                        handle.set("active", FALSE);
                    }
                    return NIL;
                }
            });
            return handle;
        }
    }

    private static final OverrideManager.Property<FuelRegistration, Integer> BURN_TIME_PROPERTY = new OverrideManager.Property<FuelRegistration, Integer>(
            "burnTime", new OverrideManager.PropertyAdapter<FuelRegistration, Integer>() {
                public Integer read(FuelRegistration target) {
                    return Integer.valueOf(target.burnTime);
                }

                public void write(FuelRegistration target, Integer value) {
                    target.burnTime = value.intValue();
                    FuelRegistry.refresh();
                }
            });

    private static final OverrideManager.Property<FuelRegistration, Boolean> ENABLED_PROPERTY = new OverrideManager.Property<FuelRegistration, Boolean>(
            "enabled", new OverrideManager.PropertyAdapter<FuelRegistration, Boolean>() {
                public Boolean read(FuelRegistration target) {
                    return Boolean.valueOf(target.enabled);
                }

                public void write(FuelRegistration target, Boolean value) {
                    target.enabled = value.booleanValue();
                    FuelRegistry.refresh();
                }
            });

    private static final class RemoveRegistration extends VarArgFunction {
        private final FuelRegistrationReference reference;

        private RemoveRegistration(FuelRegistrationReference reference) {
            this.reference = reference;
        }

        @Override
        public Varargs invoke(Varargs args) {
            boolean removed = FuelRegistry.remove(reference.registration);
            if (removed) {
                reference.set("exists", LuaValue.FALSE);
            }
            return LuaValue.valueOf(removed);
        }
    }

    private static void requireLive(FuelSetReference set) {
        if (!FuelRegistry.contains(set.definition)) {
            set.set("exists", LuaValue.FALSE);
            throw new LuaError("Fuel set is no longer registered: " + set.definition.key);
        }
    }

    private static synchronized FuelRegistrationReference registrationReference(FuelRegistration registration) {
        FuelRegistrationReference reference = REGISTRATION_REFERENCES.get(registration);
        if (reference == null) {
            reference = new FuelRegistrationReference(registration);
            REGISTRATION_REFERENCES.put(registration, reference);
        }
        return reference;
    }

    private static LuaValue required(LuaValue table, String field, String context) {
        LuaValue value = table.get(field);
        if (value.isnil()) {
            throw new LuaError(context + " requires '" + field + "'.");
        }
        return value;
    }

    private static Integer readDamage(LuaValue definition, LuaValue item) {
        LuaValue explicit = definition.get("damage");
        if (!explicit.isnil()) {
            return Integer.valueOf(nonnegativeInteger(explicit, "fuel.damage", Short.MAX_VALUE));
        }
        boolean resourceReference = item.istable() && !item.get("exists").isnil() && !item.get("owner").isnil();
        if (item.istable() && !resourceReference && !item.get("damage").isnil()) {
            return Integer.valueOf(nonnegativeInteger(item.get("damage"), "fuel.item.damage", Short.MAX_VALUE));
        }
        return null;
    }

    private static ItemStack readItem(LuaValue value, String path) {
        ItemStack stack;
        // LuaJ numbers are string-coercible, so numeric dispatch must come first.
        if (value.isnumber()) {
            stack = LuaApiUtils.readItemStack(value, true, path);
        } else if (value.isstring()) {
            int itemId = resolveItemKey(value.checkjstring());
            if (itemId < 0) {
                throw new LuaError(path + " refers to an unknown item: " + value.checkjstring());
            }
            stack = new ItemStack(itemId, 1, 0);
        } else {
            stack = LuaApiUtils.readItemStack(value, true, path);
        }
        if (stack.itemID < 0 || stack.itemID >= Item.itemsList.length || Item.itemsList[stack.itemID] == null) {
            throw new LuaError(path + " refers to an unregistered item ID: " + stack.itemID);
        }
        return stack;
    }

    private static ItemStack readQueryItem(LuaValue value, String path) {
        return value == null || value.isnil() ? null : readItem(value, path);
    }

    private static int resolveItemKey(String value) {
        String path = value;
        int separator = value.indexOf(':');
        if (separator >= 0) {
            try {
                path = AssetKey.parse(value).getPath();
            } catch (IllegalArgumentException error) {
                throw new LuaError("Invalid item key: " + error.getMessage());
            }
        }
        String shortPath = path.startsWith("item/") ? path.substring("item/".length()) : path;
        for (int itemId = 0; itemId < Item.itemsList.length; itemId++) {
            Item item = Item.itemsList[itemId];
            if (item == null) {
                continue;
            }
            String name = item.getItemName();
            String bare = name != null && name.startsWith("item.") ? name.substring("item.".length()) : name;
            if (path.equals(name) || path.equals(bare) || shortPath.equals(name) || shortPath.equals(bare)) {
                return itemId;
            }
        }
        return -1;
    }

    private static int positiveInteger(LuaValue value, String path, int maximum) {
        return integer(value, path, 1, maximum);
    }

    private static int nonnegativeInteger(LuaValue value, String path, int maximum) {
        return integer(value, path, 0, maximum);
    }

    private static int integer(LuaValue value, String path, int minimum, int maximum) {
        if (!value.isnumber()) {
            throw new LuaError(path + " must be an integer.");
        }
        double number = value.checkdouble();
        if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.floor(number) || number < minimum
                || number > maximum) {
            throw new LuaError(path + " must be an integer from " + minimum + " to " + maximum + ".");
        }
        return (int) number;
    }

    private static AssetKey parseKey(String value, String path) {
        try {
            return AssetKey.parse(value);
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static String requireOwner() {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Fuel registrations are only available while a mod is loading.");
        }
        return owner;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        int offset = args.arg1() == receiver ? 1 : 0;
        return args.arg(index + offset);
    }
}
