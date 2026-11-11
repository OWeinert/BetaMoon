package betamoon.tileentity;

import betamoon.BetaMoonCommon;
import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.screen.GuiAttachedDocument;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiHostElementBudget;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.screen.GuiScreenDefinition;
import betamoon.gui.screen.GuiScreenDefinitions;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;

/**
 * Inline document lifetime belongs to this actual native tile-container
 * instance.
 */
final class GuiContainerDocument implements GuiAttachedDocument.Host, AutoCloseable {
    private final GuiLuaContainer screen;
    private final Minecraft client;
    private final LuaTileEntity tile;
    private final LuaContainer backend;
    private final GuiScene scene;
    private final World world;
    private final EntityPlayer player;
    private final int window;
    private final String owner;
    private final GuiRowBudget rows;
    private final GuiHostElementBudget elements;
    private final Map<Object, ContainerPresentationRuntime.PreparedReload> legacy = new IdentityHashMap<>();
    private final Map<Object, GuiHostElementBudget.Claim> legacyCapacity = new IdentityHashMap<>();
    final GuiAttachedDocument document;
    private ContainerGuiDefinition selected;
    private ContainerGuiDefinition preparing;
    private boolean closed;
    private boolean ended;
    private boolean preparingInline;

    GuiContainerDocument(GuiLuaContainer screen, Minecraft client, LuaTileEntity tile, LuaContainer backend,
            GuiScene scene, ContainerGuiDefinition definition) {
        this.screen = screen;
        this.client = client;
        this.tile = tile;
        this.backend = backend;
        this.scene = scene;
        rows = screen.rowBudget();
        elements = screen.elementBudget();
        selected = definition;
        world = client.theWorld;
        player = client.thePlayer;
        window = backend.windowId;
        owner = definition.document.owner;
        GuiScreenDefinitions definitions = GuiScreenDefinitions.inline(GuiClientUiRuntime.screens(), definition.name,
                () -> selected.document);
        document = new GuiAttachedDocument(this, owner, definition.name, definitions);
    }

    boolean owner(String candidate) {
        return owner.equals(candidate);
    }

    void select(ContainerGuiDefinition definition) {
        if (definition.container != selected.container) {
            throw new IllegalArgumentException("Container backend changes require reopening");
        }
        selected = definition;
    }

    void tick() {
        if (!closed && !ended) {
            if (document.controller().current() == null) {
                document.open();
            }
            document.tick();
        }
    }

    void input(Runnable input) {
        if (document.usable()) {
            document.page().inputAttached(input);
        } else {
            input.run();
        }
    }

    void paint(Runnable paint) {
        if (document.usable()) {
            document.page().sampleAttached();
            document.page().paintAttached(paint);
        } else {
            paint.run();
        }
    }

    public Minecraft client() {
        return client;
    }

    public GuiScreen screen() {
        return screen;
    }

    public GuiScene scene() {
        return scene;
    }

    public GuiRowBudget rowBudget() {
        return rows;
    }

    public GuiHostElementBudget elementBudget() {
        return elements;
    }

    public Set<GuiCapability> capabilities() {
        return Collections.unmodifiableSet(EnumSet.of(GuiCapability.INPUT, GuiCapability.MODAL_LAYERS,
                GuiCapability.WORLD_DATA, GuiCapability.NATIVE_INVENTORY, GuiCapability.NATIVE_ITEMS,
                GuiCapability.PREPARED_DRAWING, GuiCapability.RAW_DRAWING));
    }

    public boolean available() {
        return !closed && !ended && client.fontRenderer != null && client.renderEngine != null && attached();
    }

    public boolean attached() {
        return !closed && client.currentScreen == screen && client.theWorld == world && client.thePlayer == player
                && backend.windowId == window && player != null && player.craftingInventory == backend
                && tile.worldObj == world;
    }

    public boolean inputAvailable() {
        return available() && worldAvailable();
    }

    public long environment() {
        return attached() ? 1 : 2;
    }

    public boolean worldAvailable() {
        return attached() && world != null && !world.multiplayerWorld && player.worldObj == world && player.health > 0;
    }

    public boolean paused() {
        return client.isGamePaused || screen.doesGuiPauseGame();
    }

    public void context(LuaTable value, LuaCallbackScope scope, GuiScreenActivation activation) {
        // Scoped observations and validated domain intents are added in S5.
    }

    public void failure(String owner, String path, Throwable failure) {
        BetaMoonCommon.LOGGER.warning("Container document " + path + ": " + failure.getMessage());
    }

    public void closed() {
        ended = true;
    }

    public void prepareRevision(Object document, GuiScreenDefinition definition) {
        preparingInline = definition == selected.document;
        if (definition != selected.document || selected == screen.definition()) {
            return;
        }
        preparing = selected;
        legacy.put(document, screen.prepareRetainedRevision(selected));
        legacyCapacity.put(document, elements.prepare(backend, legacyElements(selected)));
    }

    public void validateRevision(Object document) {
        ContainerPresentationRuntime.PreparedReload staged = legacy.get(document);
        if (staged != null) {
            staged.validate();
        }
        if (!attached() || selected.document == null || !selected.document.available()) {
            throw new IllegalStateException("Container document candidate expired during preparation");
        }
    }

    public void commitRevision(Object document) {
        ContainerPresentationRuntime.PreparedReload staged = legacy.remove(document);
        if (staged != null) {
            screen.commitRetainedRevision(preparing, (ContainerPresentationRuntime) staged.commit());
            legacyCapacity.remove(document).commit();
            preparing = null;
        }
    }

    public void abortRevision(Object document) {
        ContainerPresentationRuntime.PreparedReload staged = legacy.remove(document);
        if (staged != null) {
            staged.close();
            preparing = null;
        }
        GuiHostElementBudget.Claim capacity = legacyCapacity.remove(document);
        if (capacity != null) {
            capacity.close();
        }
    }

    public void prepareDocument(GuiPresentationTree tree) {
        if (!preparingInline) {
            scene.prepareDetached(tree);
            return;
        }
        ContainerGuiDefinition definition = preparing == null ? screen.definition() : preparing;
        int x = (screen.width - definition.width) / 2;
        int y = (screen.height - definition.height) / 2;
        scene.prepareDetached(tree, new Rect(x, y, x + definition.width, y + definition.height));
    }

    static int legacyElements(ContainerGuiDefinition definition) {
        int count = 1;
        for (ContainerGuiDefinition.Element element : definition.elements) {
            if (element instanceof ContainerGuiDefinition.ControlElement) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                document.close();
            } finally {
                for (ContainerPresentationRuntime.PreparedReload staged : legacy.values()) {
                    staged.close();
                }
                legacy.clear();
                for (GuiHostElementBudget.Claim capacity : legacyCapacity.values()) {
                    capacity.close();
                }
                legacyCapacity.clear();
            }
        }
    }
}
