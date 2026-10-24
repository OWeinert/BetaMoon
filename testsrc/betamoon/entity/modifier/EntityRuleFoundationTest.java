package betamoon.entity.modifier;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.luaapi.entity.EntityRuleDeclarations;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Modifier composition contracts, including numerical boundaries and headless
 * loading.
 */
public final class EntityRuleFoundationTest {
    private EntityRuleFoundationTest() {
    }

    public static void main(String[] arguments) throws Exception {
        validateValues();
        immutableContributions();
        parseDeclarations();
        multiplierComposition();
        absoluteComposition();
        booleanComposition();
        currentNativeBaselines();
        sourceIdentityAndLimits();
        numericalReference();
        headlessResolution();
        System.out.println("Entity rule foundations passed: strict declarations, immutable patches, "
                + "deterministic composition, numerical boundaries, current baselines and headless loading.");
    }

    private static void validateValues() {
        for (EntityMovementRule rule : EntityMovementRule.values()) {
            require(EntityMovementRule.require(rule.path()) == rule, "Rule paths must round-trip");
            if (rule.isNumeric()) {
                rule.validate(0);
                rule.validate(rule.maximum());
                reject(() -> rule.validate(-1), rule.path());
                reject(() -> rule.validate(rule.maximum() + 0.1), rule.path());
                reject(() -> rule.validate(Double.NaN), rule.path());
                reject(() -> rule.validate(Double.POSITIVE_INFINITY), rule.path());
                reject(() -> rule.validate(Double.NEGATIVE_INFINITY), rule.path());
                reject(() -> rule.validate("1"), rule.path());
                reject(() -> rule.validate(Boolean.TRUE), rule.path());
            } else {
                require(Boolean.FALSE.equals(rule.validate(false)), "Explicit false must remain a contribution");
                rule.validate(true);
                reject(() -> rule.validate(0), rule.path());
                reject(() -> rule.validate("false"), rule.path());
            }
            reject(() -> rule.validate(null), rule.path());
        }
        reject(() -> EntityMovementRule.require("physics.gravity"), "Unknown entity movement rule");
    }

    private static void immutableContributions() {
        EnumMap<EntityMovementRule, Object> input = new EnumMap<>(EntityMovementRule.class);
        input.put(EntityMovementRule.GRAVITY_MULTIPLIER, 0.5);
        EntityRuleSet original = EntityRuleSet.copyOf(input);
        input.clear();
        exact((Double) original.get(EntityMovementRule.GRAVITY_MULTIPLIER), 0.5);

        EntityRuleSet changed = original.with(EntityMovementRule.JUMP_ENABLED, false);
        require(original.get(EntityMovementRule.JUMP_ENABLED) == null, "A patch changed the original rule set");
        require(Boolean.FALSE.equals(changed.get(EntityMovementRule.JUMP_ENABLED)), "A patch lost explicit false");
        require(changed.without(EntityMovementRule.JUMP_ENABLED).get(EntityMovementRule.JUMP_ENABLED) == null,
                "Clearing a rule must remove its contribution");
        require(original.without(EntityMovementRule.GRAVITY_MULTIPLIER).isEmpty(),
                "Clearing the last rule must leave an empty contribution");
        reject(() -> original.with(EntityMovementRule.GRAVITY_MULTIPLIER, -1), "physics.gravityMultiplier");
        exact((Double) original.get(EntityMovementRule.GRAVITY_MULTIPLIER), 0.5);
        immutable(() -> original.asMap().put(EntityMovementRule.JUMP_ENABLED, true));
        require(EntityRuleSet.EMPTY.get(EntityMovementRule.GRAVITY_MULTIPLIER) == null,
                "Omitted rules must not invent a native default contribution");
    }

    private static void parseDeclarations() {
        EntityRuleSet parsed = parse("{physics={gravityMultiplier=0,airDrag={horizontal=0.91,vertical=1},"
                + "fluidDrag={horizontal=0.8,vertical=0.5},trackFallDistance=false},"
                + "movement={accelerationMultiplier=2,jumpMultiplier=0,jumpEnabled=false,swimLiftEnabled=true,"
                + "sneakSlowdown=1,sneakEdgeGuard=false,sneakBodyAdjustment=false},damage={fallMultiplier=0}}");
        exact((Double) parsed.get(EntityMovementRule.GRAVITY_MULTIPLIER), 0);
        exact((Double) parsed.get(EntityMovementRule.AIR_DRAG_HORIZONTAL), 0.91);
        require(Boolean.FALSE.equals(parsed.get(EntityMovementRule.TRACK_FALL_DISTANCE)),
                "The declaration reader discarded false");
        require(Boolean.TRUE.equals(parsed.get(EntityMovementRule.SWIM_LIFT_ENABLED)),
                "The declaration reader discarded true");
        EntityRuleSet patched = EntityRuleDeclarations.patch(parsed, lua("{physics={airDrag={vertical=0.75}}}"),
                "modifier.rules");
        exact((Double) patched.get(EntityMovementRule.AIR_DRAG_VERTICAL), 0.75);
        exact((Double) parsed.get(EntityMovementRule.AIR_DRAG_VERTICAL), 1);
        exact((Double) patched.get(EntityMovementRule.AIR_DRAG_HORIZONTAL), 0.91);
        require(EntityRuleDeclarations.patch(parsed, LuaValue.NIL, "modifier.rules") == parsed,
                "An omitted patch must be a no-op");
        require(parse("{physics={airDrag={}}}").isEmpty(), "Empty groups must contribute nothing");

        rejectLua(() -> parse("{physics={gravityMultiplier=0.5,airDrag={vertical=2}}}"),
                "modifier.rules.physics.airDrag.vertical");
        rejectLua(() -> parse("{physics={gravityMultiplier='1'}}"), "modifier.rules.physics.gravityMultiplier");
        rejectLua(() -> parse("{movement={jumpEnabled=0}}"), "modifier.rules.movement.jumpEnabled");
        rejectLua(() -> parse("{physics={airDrag=false}}"), "modifier.rules.physics.airDrag");
        rejectLua(() -> parse("{physics={gravity=1}}"), "modifier.rules.physics.gravity");
        rejectLua(() -> parse("{physics={airDrag={unknown=1}}}"), "modifier.rules.physics.airDrag.unknown");
        rejectLua(() -> parse("{onMotion=function() end}"), "modifier.rules.onMotion");
        rejectLua(() -> parse("{physics={gravityMultiplier=0/0}}"), "modifier.rules.physics.gravityMultiplier");
        rejectLua(() -> parse("{[1]={}}"), "modifier.rules key");
        rejectLua(
                () -> EntityRuleDeclarations.patch(parsed,
                        lua("{physics={gravityMultiplier=0.5},movement={jumpEnabled='no'}}"), "modifier.rules"),
                "modifier.rules.movement.jumpEnabled");
        exact((Double) parsed.get(EntityMovementRule.GRAVITY_MULTIPLIER), 0);
    }

    private static void multiplierComposition() {
        EntityMovementRule rule = EntityMovementRule.GRAVITY_MULTIPLIER;
        List<EntityRuleSource> sources = Arrays.asList(source("a.lua", "a", 0, rule, 8),
                source("b.lua", "b", 100, rule, 8), source("c.lua", "c", -100, rule, 0.01));
        EntityResolvedRules resolved = EntityRuleResolver.resolve(sources);
        near(resolved.numberValue(rule, 1), 0.64, 1e-15);
        require(!resolved.get(rule).isCapped(), "The resolver capped an intermediate product");

        resolved = EntityRuleResolver.resolve(sources.subList(0, 2));
        exact(resolved.numberValue(rule, 1), 8);
        require(resolved.get(rule).isCapped(), "Final capping must be reported");
        List<EntityRuleSource> zero = new ArrayList<>(sources.subList(0, 2));
        zero.add(source("z.lua", "zero", 0, rule, 0));
        resolved = EntityRuleResolver.resolve(zero);
        exact(resolved.numberValue(rule, 1), 0);
        require(!resolved.get(rule).isCapped(), "A zero final product must not report intermediate capping");

        resolved = EntityRuleResolver.resolve(Arrays.asList(source("a.lua", "tiny", 0, rule, Double.MIN_VALUE),
                source("b.lua", "halve", 0, rule, 0.5), source("c.lua", "recover", 0, rule, 8)));
        exact(resolved.numberValue(rule, 1), 4 * Double.MIN_VALUE);

        List<EntityRuleSource> shuffled = new ArrayList<>(sources);
        Random random = new Random(173);
        long expected = Double.doubleToLongBits(EntityRuleResolver.resolve(sources).numberValue(rule, 1));
        for (int i = 0; i < 40; i++) {
            Collections.shuffle(shuffled, random);
            require(Double.doubleToLongBits(EntityRuleResolver.resolve(shuffled).numberValue(rule, 1)) == expected,
                    "Registration order changed the resolved product");
        }
    }

    private static void absoluteComposition() {
        EntityMovementRule rule = EntityMovementRule.AIR_DRAG_HORIZONTAL;
        EntityRuleSource low = source("z.lua", "low", 0, rule, 0.1);
        EntityRuleSource ownerTie = source("b.lua", "a", 10, rule, 0.2);
        EntityRuleSource keyTie = source("a.lua", "z", 10, rule, 0.3);
        EntityRuleSource winner = source("a.lua", "a", 10, rule, 0.4);
        List<EntityRuleSource> sources = new ArrayList<>(Arrays.asList(low, ownerTie, keyTie, winner));
        for (int i = 0; i < 16; i++) {
            Collections.rotate(sources, 1);
            EntityResolvedRules result = EntityRuleResolver.resolve(sources);
            exact(result.numberValue(rule, 0.91), 0.4);
            require(result.get(rule).winner() == winner, "Absolute priority/owner/key resolution changed");
        }
        EntityResolvedRules result = EntityRuleResolver.resolve(Arrays.asList(low, ownerTie, keyTie));
        exact(result.numberValue(rule, 0.91), 0.3);
        result = EntityRuleResolver.resolve(Collections.singletonList(low));
        exact(result.numberValue(rule, 0.91), 0.1);
        exact(EntityRuleResolver.resolve(Collections.<EntityRuleSource>emptyList()).numberValue(rule, 0.91), 0.91);
        EntityResolvedRule resolvedRule = EntityRuleResolver.resolve(sources).get(rule);
        immutable(() -> resolvedRule.sources().clear());
    }

    private static void booleanComposition() {
        for (EntityMovementRule rule : new EntityMovementRule[]{EntityMovementRule.JUMP_ENABLED,
                EntityMovementRule.SWIM_LIFT_ENABLED}) {
            EntityRuleSource veto = source("z.lua", "deny", -10000, rule, false);
            EntityRuleSource allow = source("a.lua", "allow", 10000, rule, true);
            EntityResolvedRules result = EntityRuleResolver.resolve(Arrays.asList(allow, veto));
            require(!result.booleanValue(rule, true), "A higher priority true defeated an explicit veto");
            result = EntityRuleResolver.resolve(Collections.singletonList(allow));
            require(result.booleanValue(rule, true), "Removing the veto did not reveal native behavior");
            require(!result.booleanValue(rule, false), "Explicit true invented a native jump/lift");
        }
        EntityMovementRule rule = EntityMovementRule.TRACK_FALL_DISTANCE;
        EntityResolvedRules result = EntityRuleResolver.resolve(
                Arrays.asList(source("a.lua", "disabled", 0, rule, false), source("z.lua", "enabled", 1, rule, true)));
        require(result.booleanValue(rule, false), "Boolean overrides must use their own priority semantics");
    }

    private static void currentNativeBaselines() {
        EntityMovementRule multiplier = EntityMovementRule.GRAVITY_MULTIPLIER;
        EntityResolvedRules result = EntityRuleResolver
                .resolve(Collections.singletonList(source("a.lua", "gravity", 0, multiplier, 0.5)));
        exact(result.numberValue(multiplier, 0.08), 0.04);
        exact(result.numberValue(multiplier, 0.02), 0.01);
        require(result.get(EntityMovementRule.AIR_DRAG_HORIZONTAL) == null,
                "Omitted native retentions must remain unresolved");
        EntityResolvedRules empty = EntityRuleResolver.resolve(Collections.<EntityRuleSource>emptyList());
        exact(empty.numberValue(multiplier, 0.08), 0.08);
        exact(empty.numberValue(multiplier, -0.0), -0.0);
        require(!empty.booleanValue(EntityMovementRule.JUMP_ENABLED, false), "Omission replaced a native boolean");
        EntityResolvedRules neutral = EntityRuleResolver
                .resolve(Collections.singletonList(source("a.lua", "neutral", 0, multiplier, 1)));
        exact(neutral.numberValue(multiplier, -0.0), -0.0);
        reject(() -> empty.numberValue(EntityMovementRule.JUMP_ENABLED, 1), "numeric rule");
        reject(() -> empty.booleanValue(multiplier, true), "boolean rule");
        immutable(() -> empty.asMap().clear());
    }

    private static void sourceIdentityAndLimits() {
        EntityRuleSource valid = source("a.lua", "same", 0, EntityMovementRule.GRAVITY_MULTIPLIER, 1);
        reject(() -> EntityRuleResolver.resolve(Arrays.asList(valid, valid)), "Duplicate entity rule source");
        EntityRuleResolver
                .resolve(Arrays.asList(valid, source("b.lua", "same", 0, EntityMovementRule.GRAVITY_MULTIPLIER, 1)));
        ContentKey wrongType = ContentKey.parseForType("test:item/wrong", ContentType.ITEM);
        reject(() -> new EntityRuleSource("a.lua", wrongType, 0, EntityRuleSet.EMPTY), "modifier content key");
        reject(() -> source("", "empty_owner", 0, EntityMovementRule.JUMP_ENABLED, true), "owner");
        reject(() -> source("a.lua", "bad_priority", 10001, EntityMovementRule.JUMP_ENABLED, true), "priority");
        reject(() -> EntityRuleResolver.resolve(null), "sources are required");
        reject(() -> EntityRuleResolver.resolve(Collections.singletonList(null)), "cannot be null");

        List<EntityRuleSource> sources = new ArrayList<>();
        for (int i = 0; i < EntityRuleResolver.MAX_SOURCES; i++) {
            sources.add(source("owner.lua", "source_" + i, 0, EntityMovementRule.JUMP_ENABLED, true));
        }
        require(EntityRuleResolver.resolve(sources).booleanValue(EntityMovementRule.JUMP_ENABLED, true),
                "The declared source capacity must be usable");
        sources.add(source("owner.lua", "excess", 0, EntityMovementRule.JUMP_ENABLED, true));
        reject(() -> EntityRuleResolver.resolve(sources), "source limit");
    }

    private static void numericalReference() {
        Random random = new Random(1173);
        double[] factors = {Double.MIN_VALUE, 0.125, 0.5, 0.9, 1.1, 2, 8};
        for (int sample = 0; sample < 80; sample++) {
            int count = 1 + random.nextInt(EntityRuleResolver.MAX_SOURCES);
            List<EntityRuleSource> sources = new ArrayList<>();
            BigDecimal reference = BigDecimal.ONE;
            for (int i = 0; i < count; i++) {
                double factor = factors[random.nextInt(factors.length)];
                sources.add(source("owner.lua", "source_" + i, 0, EntityMovementRule.GRAVITY_MULTIPLIER, factor));
                reference = reference.multiply(new BigDecimal(factor));
            }
            double expected = Math.min(8, reference.doubleValue());
            Collections.shuffle(sources, random);
            double actual = EntityRuleResolver.resolve(sources).numberValue(EntityMovementRule.GRAVITY_MULTIPLIER, 1);
            near(actual, expected, Math.ulp(expected) * count);
        }
    }

    private static void headlessResolution() throws Exception {
        URL classes = EntityRuleResolver.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes}, null) {
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.") || name.startsWith("org.luaj.")) {
                    throw new ClassNotFoundException("Headless modifier core cannot load " + name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> ruleType = loader.loadClass(EntityMovementRule.class.getName());
            Object rule = ruleType.getField("GRAVITY_MULTIPLIER").get(null);
            Class<?> setType = loader.loadClass(EntityRuleSet.class.getName());
            Object rules = setType.getMethod("with", ruleType, Object.class).invoke(setType.getField("EMPTY").get(null),
                    rule, Double.valueOf(0.5));
            Class<?> sourceType = loader.loadClass(EntityRuleSource.class.getName());
            Object keyType = sourceType.getField("KEY_TYPE").get(null);
            Class<?> keyClass = loader.loadClass(ContentKey.class.getName());
            Object key = keyClass.getMethod("parseForType", String.class, keyType.getClass()).invoke(null,
                    "test:gravity", keyType);
            Object source = sourceType.getConstructor(String.class, keyClass, int.class, setType)
                    .newInstance("headless.lua", key, 0, rules);
            Class<?> resolver = loader.loadClass(EntityRuleResolver.class.getName());
            Object resolved = resolver.getMethod("resolve", Collection.class).invoke(null,
                    Collections.singletonList(source));
            double actual = (Double) resolved.getClass().getMethod("numberValue", ruleType, double.class)
                    .invoke(resolved, rule, 0.08);
            exact(actual, 0.04);
        }
    }

    private static EntityRuleSource source(String owner, String name, int priority, EntityMovementRule rule,
            Object value) {
        return new EntityRuleSource(owner, ContentKey.parseForType("test:" + name, EntityRuleSource.KEY_TYPE), priority,
                EntityRuleSet.EMPTY.with(rule, value));
    }

    private static EntityRuleSet parse(String expression) {
        return EntityRuleDeclarations.parse(lua(expression), "modifier.rules");
    }

    private static LuaValue lua(String expression) {
        return JsePlatform.standardGlobals().load("return " + expression).call();
    }

    private static void reject(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains(message), "Unexpected validation diagnostic: " + expected);
            return;
        }
        throw new AssertionError("Expected validation failure containing " + message);
    }

    private static void rejectLua(Runnable action, String path) {
        try {
            action.run();
        } catch (LuaError expected) {
            require(expected.getMessage().contains(path), "Missing rule field path: " + expected);
            return;
        }
        throw new AssertionError("Expected Lua validation failure at " + path);
    }

    private static void immutable(Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("A retained snapshot was mutable");
    }

    private static void exact(double actual, double expected) {
        require(Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected), actual + " != " + expected);
    }

    private static void near(double actual, double expected, double tolerance) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
