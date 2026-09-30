package betamoon.content;

/**
 * Verifies canonical content identity without Minecraft or Lua dependencies.
 */
public final class ContentKeyTest {
    private ContentKeyTest() {
    }

    public static void main(String[] arguments) {
        verifyCanonicalKeys();
        verifyTypedShorthand();
        verifyExpandableTypes();
        verifyValidationReasons();
        verifyLengthLimits();
        verifyReservedNamespaces();
        System.out.println("Central content-key checks passed.");
    }

    private static void verifyCanonicalKeys() {
        ContentKey key = ContentKey.parseCanonical("studio.pack:block/machines/copper_press");
        require("studio.pack".equals(key.namespace()), "Namespace is retained");
        require(ContentType.BLOCK.equals(key.type()), "Known type constants are reused");
        require("machines/copper_press".equals(key.name()), "Nested names are retained");
        require("studio.pack:block/machines/copper_press".equals(key.toString()), "Canonical keys round-trip exactly");
        require(key.equals(ContentKey.parseCanonical(key.toString())), "Equal canonical keys compare equally");
        require(key.hashCode() == ContentKey.parseCanonical(key.toString()).hashCode(),
                "Equal canonical keys share a hash code");
        require(!key.equals(ContentKey.parseCanonical("other:block/machines/copper_press")),
                "Namespaces remain part of identity");
        require(!ContentKey.parseCanonical("mymod:block/copper").equals(ContentKey.parseCanonical("mymod:item/copper")),
                "Types remain part of identity");
        require("my-mod:block/.internal/path.with-dots/name-value_2"
                .equals(ContentKey.parseCanonical("my-mod:block/.internal/path.with-dots/name-value_2").toString()),
                "The established namespace and name punctuation remains valid");

        ContentKey assembled = ContentKey.of("mymod", ContentType.ITEM, "tools/copper_hammer");
        require("mymod:item/tools/copper_hammer".equals(assembled.toString()),
                "Component construction produces a canonical key");
    }

    private static void verifyTypedShorthand() {
        ContentKey shorthand = ContentKey.parseForType("mymod:slate", ContentType.BLOCK);
        ContentKey canonical = ContentKey.parseForType("mymod:block/slate", ContentType.BLOCK);
        require(shorthand.equals(canonical), "Typed shorthand expands to the expected canonical key");
        require("mymod:block/slate".equals(shorthand.toString()), "Expansion includes the expected type");

        ContentKeyException mismatch = expectReason(ContentKeyException.Reason.UNEXPECTED_TYPE,
                () -> ContentKey.parseForType("mymod:item/slate", ContentType.BLOCK));
        require(ContentType.BLOCK.equals(mismatch.getExpectedType()), "A mismatch carries the expected type");
        require(ContentType.ITEM.equals(mismatch.getActualType()), "A mismatch carries the supplied type");
        expectReason(ContentKeyException.Reason.UNEXPECTED_TYPE,
                () -> ContentKey.parseForType("mymod:machines/copper_press", ContentType.BLOCK));
    }

    private static void verifyExpandableTypes() {
        ContentType custom = ContentType.of("quest_entry");
        require("quest_entry".equals(custom.value()), "New valid content types remain possible");
        require(custom.equals(ContentType.of("quest_entry")), "Content type equality is value based");
        require(ContentType.RECIPE_TYPE == ContentType.of("recipe_type"), "Known factories reuse constants");
        require(ContentType.INPUT_MAP == ContentType.of("input_map"), "Input maps use a shared known type");
        require(ContentType.HOTKEY == ContentType.of("hotkey"), "Hotkeys use a shared known type");
        require("mymod:quest_entry/first_steps".equals(ContentKey.parseForType("mymod:first_steps", custom).toString()),
                "Expandable types work with typed shorthand");
    }

    private static void verifyValidationReasons() {
        expectReason(ContentKeyException.Reason.MISSING_NAMESPACE, () -> ContentKey.parseCanonical(null));
        expectReason(ContentKeyException.Reason.MISSING_NAMESPACE, () -> ContentKey.parseCanonical(""));
        expectReason(ContentKeyException.Reason.MISSING_NAMESPACE, () -> ContentKey.parseCanonical("block/slate"));
        expectReason(ContentKeyException.Reason.MISSING_NAMESPACE, () -> ContentKey.parseCanonical(":block/slate"));
        expectReason(ContentKeyException.Reason.MISSING_TYPE, () -> ContentKey.parseCanonical("mymod:slate"));
        expectReason(ContentKeyException.Reason.MISSING_TYPE, () -> ContentKey.parseCanonical("mymod:/slate"));
        expectReason(ContentKeyException.Reason.MISSING_NAME, () -> ContentKey.parseCanonical("mymod:block/"));
        expectReason(ContentKeyException.Reason.MISSING_NAME,
                () -> ContentKey.parseForType("mymod:", ContentType.BLOCK));
        expectReason(ContentKeyException.Reason.INVALID_CHARACTER,
                () -> ContentKey.parseCanonical("mymod:block/slate:extra"));

        expectReason(ContentKeyException.Reason.UPPERCASE, () -> ContentKey.parseCanonical("MyMod:block/slate"));
        expectReason(ContentKeyException.Reason.UPPERCASE, () -> ContentKey.parseCanonical("mymod:Block/slate"));
        expectReason(ContentKeyException.Reason.UPPERCASE, () -> ContentKey.parseCanonical("mymod:block/Slate"));
        expectReason(ContentKeyException.Reason.INVALID_CHARACTER,
                () -> ContentKey.parseCanonical("my mod:block/slate"));
        expectReason(ContentKeyException.Reason.INVALID_CHARACTER,
                () -> ContentKey.parseCanonical("mymod:block-name/slate"));
        expectReason(ContentKeyException.Reason.INVALID_CHARACTER,
                () -> ContentKey.parseCanonical("mymod:block/slate?"));
        expectReason(ContentKeyException.Reason.INVALID_CHARACTER,
                () -> ContentKey.parseCanonical("mymod:block/slate\u0000"));

        expectReason(ContentKeyException.Reason.EMPTY_SEGMENT, () -> ContentKey.parseCanonical("my..mod:block/slate"));
        expectReason(ContentKeyException.Reason.EMPTY_SEGMENT, () -> ContentKey.parseCanonical("mymod.:block/slate"));
        expectReason(ContentKeyException.Reason.EMPTY_SEGMENT, () -> ContentKey.parseCanonical("mymod:block//slate"));
        expectReason(ContentKeyException.Reason.EMPTY_SEGMENT, () -> ContentKey.parseCanonical("mymod:block/slate/"));
        expectReason(ContentKeyException.Reason.UNSAFE_SEGMENT, () -> ContentKey.parseCanonical("mymod:block/./slate"));
        expectReason(ContentKeyException.Reason.UNSAFE_SEGMENT,
                () -> ContentKey.parseCanonical("mymod:block/../slate"));
        expectReason(ContentKeyException.Reason.UNSAFE_SEGMENT,
                () -> ContentKey.parseCanonical("mymod:block/slate./part"));
    }

    private static void verifyLengthLimits() {
        String namespace64 = repeat('n', ContentKey.MAX_NAMESPACE_LENGTH);
        String type32 = repeat('t', ContentKey.MAX_TYPE_LENGTH);
        String name192 = repeat('a', ContentKey.MAX_NAME_LENGTH);
        ContentKey.parseCanonical(namespace64 + ":block/slate");
        ContentKey.parseCanonical("mymod:" + type32 + "/slate");
        ContentKey.parseCanonical("mymod:block/" + name192);

        ContentKeyException namespace = expectReason(ContentKeyException.Reason.TOO_LONG,
                () -> ContentKey.parseCanonical(repeat('n', ContentKey.MAX_NAMESPACE_LENGTH + 1) + ":block/slate"));
        require("namespace".equals(namespace.getComponent()), "Namespace limit identifies its component");
        expectReason(ContentKeyException.Reason.TOO_LONG,
                () -> ContentKey.parseCanonical("mymod:" + repeat('t', ContentKey.MAX_TYPE_LENGTH + 1) + "/slate"));
        expectReason(ContentKeyException.Reason.TOO_LONG,
                () -> ContentKey.parseCanonical("mymod:block/" + repeat('a', ContentKey.MAX_NAME_LENGTH + 1)));

        String type30 = repeat('t', 30);
        ContentKey.parseCanonical(repeat('n', 31) + ":" + type30 + "/" + name192);
        ContentKeyException wholeKey = expectReason(ContentKeyException.Reason.TOO_LONG,
                () -> ContentKey.parseCanonical(repeat('n', 32) + ":" + type30 + "/" + name192));
        require("key".equals(wholeKey.getComponent()), "Whole-key limit identifies the complete key");
    }

    private static void verifyReservedNamespaces() {
        require(ContentNamespaces.isReserved("minecraft"), "Minecraft namespace is reserved");
        require(ContentNamespaces.isReserved("betamoon"), "BetaMoon namespace is reserved");
        require(ContentNamespaces.isReserved("example"), "Bundled-example namespace is reserved");
        require(!ContentNamespaces.isReserved("mymod"), "User namespaces remain available");
        ContentNamespaces.requireUserNamespace(ContentKey.parseCanonical("mymod:block/slate"));
        expectReason(ContentKeyException.Reason.RESERVED_NAMESPACE,
                () -> ContentNamespaces.requireUserNamespace(ContentKey.parseCanonical("minecraft:block/stone")));
    }

    private static ContentKeyException expectReason(ContentKeyException.Reason reason, Runnable action) {
        try {
            action.run();
        } catch (ContentKeyException error) {
            require(reason == error.getReason(), "Expected " + reason + ", got " + error.getReason());
            require(error.getComponent() != null, "Validation errors identify a component");
            return error;
        }
        throw new AssertionError("Expected content-key failure " + reason);
    }

    private static String repeat(char character, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            value.append(character);
        }
        return value.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
