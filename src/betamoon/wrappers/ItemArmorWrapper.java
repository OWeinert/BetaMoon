package betamoon.wrappers;

import forge.IArmorTextureProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.src.ItemArmor;
import net.minecraft.src.RenderPlayer;

public class ItemArmorWrapper extends ItemArmor implements IArmorTextureProvider {
    private static final Logger LOGGER = Logger.getLogger("BetaMoon");
    private static Field RENDER_INDEX_FIELD = resolveRenderIndexField();
    private int armorRenderIndex;

    /**
     * Creates an armor wrapper with the provided id, material, render index, slot type, and internal name.
     *
     * @param id numeric item id (unshifted)
     * @param material armor material index
     * @param renderIndex armor render index
     * @param armorType armor slot type (0-3)
     * @param name internal armor name (unlocalized)
     */
    public ItemArmorWrapper(int id, int material, int renderIndex, int armorType, String name) {
        super(id, material, renderIndex, armorType);
        this.armorRenderIndex = renderIndex;
        setItemName(name);
        setIconCoord(0, 0);
    }

    /**
     * Overrides the armor render index to use a vanilla armor texture prefix.
     *
     * @param renderIndex armor render index (0+)
     * @return this wrapper for chaining
     */
    public ItemArmorWrapper setRenderIndex(int renderIndex) {
        this.armorRenderIndex = renderIndex;
        if (RENDER_INDEX_FIELD == null) {
            RENDER_INDEX_FIELD = resolveRenderIndexField();
        }
        if (RENDER_INDEX_FIELD != null) {
            try {
                RENDER_INDEX_FIELD.setInt(this, renderIndex);
            } catch (IllegalAccessException ignored) {
                LOGGER.log(Level.WARNING, "Failed to set armor render index to " + renderIndex + ".", ignored);
            }
        }
        return this;
    }

    /**
     * Returns the armor texture path used by the renderer.
     *
     * @return texture path including layer suffix
     */
    public String getArmorTextureFile() {
        String texture = "armor/" + resolveArmorTextureName(armorRenderIndex);
        return "/" + texture + "_" + (armorType == 2 ? 2 : 1) + ".png";
    }

    /**
     * Resolves the vanilla armor texture base name for a render index.
     *
     * @param index vanilla render index
     * @return texture base name (e.g. "iron")
     */
    private static String resolveArmorTextureName(int index) {
        String[] names = null;
        try {
            Field field = RenderPlayer.class.getDeclaredFields()[3];
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof String[]) {
                names = (String[]) value;
            }
        } catch (Exception e) {
            names = null;
        }
        if (names == null || names.length == 0) {
            names = new String[]{"cloth", "chain", "iron", "diamond", "gold"};
        }
        if (index < 0 || index >= names.length) {
            index = 0;
        }
        return names[index];
    }

    /**
     * Attempts to resolve the render index field across mapped and obfuscated names.
     *
     * @return resolved field or null when unavailable
     */
    private static Field resolveRenderIndexField() {
        final String[] candidates = new String[] {
            "renderIndex",
            "field_77883_b",
            "b",
            "c",
            "d"
        };
        for (int i = 0; i < candidates.length; i++) {
            Field field = tryResolveRenderIndexField(candidates[i]);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    /**
     * Resolves and prepares a render index field by name.
     *
     * @param name candidate field name
     * @return prepared field or null
     */
    private static Field tryResolveRenderIndexField(String name) {
        try {
            Field field = ItemArmor.class.getDeclaredField(name);
            if (field.getType() != Integer.TYPE) {
                return null;
            }
            field.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

}
