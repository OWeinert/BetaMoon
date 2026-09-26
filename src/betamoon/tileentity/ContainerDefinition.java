package betamoon.tileentity;

import betamoon.assets.AssetKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/** Immutable slot layout for a Lua container. */
public final class ContainerDefinition {
    public final String name;
    public final String owner;
    public final TileEntityDefinition tileEntity;
    public final List<SlotDefinition> slots;
    public final int playerX;
    public final int playerY;
    public final boolean includeHotbar;
    public final Map<String, SessionField> session;
    public final Map<String, ContainerControlDefinition> controls;
    public final LuaValue closeAction;

    public ContainerDefinition(String name, String owner, TileEntityDefinition tileEntity, List<SlotDefinition> slots,
            int playerX, int playerY, boolean includeHotbar) {
        this(name, owner, tileEntity, slots, playerX, playerY, includeHotbar,
                Collections.<String, SessionField>emptyMap(),
                Collections.<String, ContainerControlDefinition>emptyMap(), LuaValue.NIL);
    }

    public ContainerDefinition(String name, String owner, TileEntityDefinition tileEntity, List<SlotDefinition> slots,
            int playerX, int playerY, boolean includeHotbar, Map<String, SessionField> session,
            Map<String, ContainerControlDefinition> controls, LuaValue closeAction) {
        this.name = name;
        this.owner = owner;
        this.tileEntity = tileEntity;
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
        this.playerX = playerX;
        this.playerY = playerY;
        this.includeHotbar = includeHotbar;
        this.session = Collections.unmodifiableMap(new LinkedHashMap<String, SessionField>(session));
        this.controls = Collections.unmodifiableMap(
                new LinkedHashMap<String, ContainerControlDefinition>(controls));
        this.closeAction = closeAction;
    }

    /** One visible tile-inventory slot. */
    public static final class SlotDefinition {
        public final String name;
        public final int index;
        public final int x;
        public final int y;
        public final boolean outputOnly;
        public final AssetKey acceptedFuelSet;

        public SlotDefinition(String name, int index, int x, int y, boolean outputOnly) {
            this(name, index, x, y, outputOnly, null);
        }

        public SlotDefinition(String name, int index, int x, int y, boolean outputOnly, AssetKey acceptedFuelSet) {
            this.name = name;
            this.index = index;
            this.x = x;
            this.y = y;
            this.outputOnly = outputOnly;
            this.acceptedFuelSet = acceptedFuelSet;
        }
    }

    /** One typed value whose lifetime is limited to a container session. */
    public static final class SessionField {
        public final String name;
        public final TileDataType type;
        public final Object defaultValue;
        public final int maximumLength;

        public SessionField(String name, TileDataType type, Object defaultValue, int maximumLength) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.maximumLength = maximumLength;
        }
    }
}
