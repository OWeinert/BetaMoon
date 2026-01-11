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
    private String armorTexture;
    private static final Field RENDER_INDEX_FIELD = resolveRenderIndexField();

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
        setItemName(name);
        setIconCoord(0, 0);
    }

    /**
     * Sets the base armor texture name (without the layer suffix or extension).
     *
     * @param texture base texture name, e.g. "custom"
     * @return this wrapper for chaining
     */
    public ItemArmorWrapper setArmorTexture(String texture) {
        this.armorTexture = texture;
        return this;
    }

    /**
     * Overrides the armor render index to use a vanilla armor texture prefix.
     *
     * @param renderIndex armor render index (0+)
     * @return this wrapper for chaining
     */
    public ItemArmorWrapper setRenderIndex(int renderIndex) {
        if (RENDER_INDEX_FIELD != null) {
            try {
                RENDER_INDEX_FIELD.setInt(this, renderIndex);
            } catch (IllegalAccessException ignored) {
                LOGGER.log(Level.WARNING, "Failed to set armor render index to " + renderIndex + ".", ignored);
            }
        }
        return this;
    }

    public String getArmorTextureFile() {
        String texture = armorTexture;
        if (texture == null || texture.length() == 0) {
            texture = resolveArmorTextureName(renderIndex);
        }
        return "/armor/" + texture + "_" + (armorType == 2 ? 2 : 1) + ".png";
    }

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

    private static Field resolveRenderIndexField() {
        try {
            Field field = ItemArmor.class.getDeclaredField("renderIndex");
            field.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            return field;
        } catch (Exception e) {
            return null;
        }
    }
}
