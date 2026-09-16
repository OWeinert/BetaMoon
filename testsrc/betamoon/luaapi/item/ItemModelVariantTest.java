package betamoon.luaapi.item;

import betamoon.client.render.ModelAppearanceSet;
import org.luaj.vm2.Globals;
import static betamoon.luaapi.block.ModelVariantTest.require;

/**
 * Item and block-item display adapters share the same metadata selection
 * contract.
 */
public final class ItemModelVariantTest {
    private ItemModelVariantTest() {
    }

    public static void run(Globals lua) throws Exception {
        lua.load("item={id=29002,key='variant_item',appearance=base,render={variants={"
                + "[1]={appearance=shifted},[2]={appearance=false,icon=7},[3]={color=0x123456},"
                + "[32767]={appearance=shifted}}}}").call();
        ItemDeclaration declaration = new ItemDeclaration(lua.get("item"));
        try (ModelAppearanceSet appearances = new ModelAppearanceSet(declaration.appearance,
                declaration.callbacks.visual.appearances())) {
            ItemModelRegistry.install(29002, appearances);
            require(ItemModelRenderer.appearance(29002, 0) == appearances.select(0), "Default item appearance");
            require(ItemModelRenderer.appearance(29002, 1) == appearances.select(1), "Variant item appearance");
            require(ItemModelRenderer.appearance(29002, 2) == null, "False item variant restores ordinary icon");
            require(ItemModelRenderer.appearance(29002, 3) == appearances.select(0), "Item variant inherits default");
            require(ItemModelRenderer.appearance(29002, 32767) == appearances.select(32767),
                    "Upper item metadata bound");
            require(declaration.callbacks.visual.icon(2, 0) == 7, "Native icon remains available for fallback");
            ItemModelRegistry.install(29002, null);
            require(ItemModelRenderer.appearance(29002, 1) == null, "Removing appearances clears retained bindings");
        }
    }
}
