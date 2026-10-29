package betamoon.gui;

import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiLink;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.UnexpectedThrowable;
import org.luaj.vm2.LuaValue;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Actual scene interaction and raster checks in the showcase's off-screen GL
 * context.
 */
final class ScriptIssueRenderChecks {
    private ScriptIssueRenderChecks() {
    }

    static void run(Minecraft base, FontRenderer font, File output) throws Exception {
        NavigationMinecraft minecraft = new NavigationMinecraft();
        minecraft.gameSettings = base.gameSettings;
        minecraft.renderEngine = base.renderEngine;
        minecraft.fontRenderer = font;
        Path root = Files.createTempDirectory("betamoon-issue-ui-");
        String[] copied = {null};
        Path[] opened = {null};
        GuiScriptIssueActions actions = new GuiScriptIssueActions(text -> copied[0] = text, path -> opened[0] = path,
                () -> root, System::currentTimeMillis);
        LuaScriptErrors.clear();
        LuaScriptRegistry.clear();
        try {
            ScriptMod script = LuaScriptRegistry.updateParsed("bm010_worlds/main.lua", "BM010 World Generation Probes",
                    Collections.emptyList(), LuaValue.NIL, LuaValue.NIL, LuaValue.NIL,
                    "World generation probes for testing saved profiles.", "1.0", null);
            LuaScriptRegistry.markFailedByFile(script.getSourceFileName(), "load failed");
            LuaScriptErrors.add(script.getDisplayName(), "@bm010_worlds/main.lua:8 BM010 fluid ID occupied: 125");
            GuiPopupScriptErrors popup = new GuiPopupScriptErrors(new GuiScreen(), true, actions);
            minecraft.displayWidth = 640;
            minecraft.displayHeight = 360;
            popup.setWorldAndResolution(minecraft, 640, 360);
            draw(popup, 640, 360);
            GuiScene scene = scene(popup);
            GuiButton dismiss = button(scene.getContent(), "Dismiss");
            GuiButton copy = button(scene.getContent(), "Copy all");
            GuiButton export = button(scene.getContent(), "Save report");
            GuiButton close = button(scene.getContent(), "Close Game");
            require(copy.getLeft() == dismiss.getLeft() && export.getRight() == dismiss.getRight(),
                    "Utility buttons must align with Dismiss's outer edges");
            require(copy.getTop() == export.getTop() && export.getRight() < close.getLeft(),
                    "Utility buttons should occupy only the left footer column at normal width");
            require(scene.getFocusedElement() == dismiss, "Dismiss must be the initial keyboard action");
            ImageIO.write(GuiShowcaseRenderTest.capture(640, 360), "png",
                    new File(output, "script-issues-popup-wide.png"));
            GuiScriptIssueEntries entries = find(scene.getContent(), GuiScriptIssueEntries.class);
            GuiElement first = entries.getChildren().get(0);
            int x = first.getLeft() + 8;
            int y = first.getTop() + 8;
            scene.mousePressed(x, y, 0);
            require(copied[0] == null, "Entry copying must wait for a completed click");
            scene.mouseReleased(x, y, 0);
            require(copied[0] != null && copied[0].contains("BM010 fluid ID occupied: 125"),
                    "Clicking an entry must copy its full diagnostic");
            copied[0] = null;
            scene.mousePressed(x, y, 0);
            scene.mouseDragged(x + 10, y, true);
            scene.mouseReleased(x + 10, y, 0);
            require(copied[0] == null, "A drag must not accidentally copy an entry");
            require(scene.keyTyped(' ', Keyboard.KEY_SPACE, false) && copied[0] != null,
                    "Keyboard activation must copy the focused entry");
            click(scene, copy);
            require(copied[0].startsWith("ERROR | ") && !copied[0].contains("Runtime environment"),
                    "Copy all must remain a formatted issue list without a diagnostic report header");
            click(scene, export);
            require(actions.report() != null && Files.isRegularFile(actions.report()), "Export did not write a report");
            require(minecraft.currentScreen instanceof GuiPopupScriptIssueExport,
                    "Export must display a result popup with the clickable file path");
            GuiPopupScriptIssueExport exported = (GuiPopupScriptIssueExport) minecraft.currentScreen;
            draw(exported, 640, 360);
            GuiScene exportedScene = scene(exported);
            GuiLink reportPath = find(exportedScene.getContent(), GuiLink.class);
            GuiButton reportClose = button(exportedScene.getContent(), "Close");
            require(reportPath != null && exportedScene.getFocusedElement() == reportClose,
                    "Report popup must provide a clickable path and focus its Close button");
            require(reportPath.getHeight() > exportedScene.getContext().getRenderer().lineHeight(),
                    "The full exported path should wrap rather than being truncated");
            click(exportedScene, reportPath);
            require(actions.report().equals(opened[0]), "The popup path must open the actual exported file");
            opened[0] = null;
            exportedScene.keyTyped('\t', Keyboard.KEY_TAB, false);
            require(exportedScene.getFocusedElement() == reportClose, "Report popup tab order should return to Close");
            exportedScene.keyTyped('\t', Keyboard.KEY_TAB, false);
            exportedScene.keyTyped('\n', Keyboard.KEY_RETURN, false);
            require(actions.report().equals(opened[0]), "The popup path must also support keyboard activation");
            draw(exported, 640, 360);
            ImageIO.write(GuiShowcaseRenderTest.capture(640, 360), "png",
                    new File(output, "script-issues-export-popup.png"));
            exported.keyTyped('\u001b', Keyboard.KEY_ESCAPE);
            require(minecraft.currentScreen == popup && LuaScriptErrors.shouldShowPopup(true),
                    "Closing the export popup must retain the unacknowledged issue list");
            draw(popup, 640, 360);
            GuiScriptIssueFooter footer = find(scene.getContent(), GuiScriptIssueFooter.class);
            GuiElement link = footer.getChildren().get(2);
            scene.mousePressed(link.getRight() - 5, link.getTop() + link.getHeight() / 2, 0);
            require(actions.report().equals(opened[0]), "The report link must open the actual exported file");
            ImageIO.write(GuiShowcaseRenderTest.capture(640, 360), "png",
                    new File(output, "script-issues-exported.png"));
            exportPopupFailures(minecraft, popup, root, output);

            minecraft.displayWidth = 320;
            minecraft.displayHeight = 240;
            popup.setWorldAndResolution(minecraft, 320, 240);
            draw(popup, 320, 240);
            require(copy.getBounds().intersect(export.getBounds()).isEmpty(),
                    "Utility buttons overlap at small GUI sizes");
            require(copy.getLeft() >= 0 && close.getRight() <= 320 && dismiss.getBottom() <= 240,
                    "Small-screen actions must remain on screen");
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                    new File(output, "script-issues-popup-small.png"));

            for (int index = 0; index < 25; index++) {
                LuaScriptErrors.addWarning(script.getSourceFileName(),
                        "Warning " + index + ": wrapped diagnostic details");
            }
            draw(popup, 320, 240);
            GuiScrollView scroll = find(scene.getContent(), GuiScrollView.class);
            int footerTop = copy.getTop();
            require(scene.mouseScrolled(scroll.getLeft() + 8, scroll.getTop() + 8, -120, false),
                    "Long diagnostics must be scrollable");
            draw(popup, 320, 240);
            require(scroll.getScrollY() > 0 && copy.getTop() == footerTop,
                    "Scrolling diagnostic content must not move the footer");
            GuiElement last = entries.getChildren().get(entries.getChildren().size() - 1);
            for (int index = 0; index < 40 && scene.getFocusedElement() != last; index++) {
                scene.keyTyped('\t', Keyboard.KEY_TAB, false);
                draw(popup, 320, 240);
                draw(popup, 320, 240);
            }
            require(scene.getFocusedElement() == last && last.getTop() >= scroll.getTop()
                    && last.getTop() < scroll.getBottom(), "Tab must reveal off-screen diagnostics");
            copied[0] = null;
            scene.keyTyped('\n', Keyboard.KEY_RETURN, false);
            require(copied[0] != null && copied[0].contains("Warning 24"), "Focused warnings must copy with Enter");
            click(scene, copy);
            require(copied[0].contains("Warning 24"), "Copy all must include off-screen entries");

            GuiPopupScriptErrors errorsOnly = new GuiPopupScriptErrors(new GuiScreen(), false, actions);
            errorsOnly.setWorldAndResolution(minecraft, 320, 240);
            draw(errorsOnly, 320, 240);
            GuiScene filtered = scene(errorsOnly);
            click(filtered, button(filtered.getContent(), "Copy all"));
            require(!copied[0].contains("Warning 24"), "Copy all must honor the popup's visible-warning filter");
            Path previous = actions.report();
            click(filtered, button(filtered.getContent(), "Save report"));
            require(new String(Files.readAllBytes(actions.report()), StandardCharsets.UTF_8).contains("Warning 24"),
                    "The full report must include recorded warnings even when the popup filters them out");
            Files.delete(previous);
            ((GuiPopupScriptIssueExport) minecraft.currentScreen).keyTyped('\u001b', Keyboard.KEY_ESCAPE);
            errorsOnly.keyTyped('\u001b', Keyboard.KEY_ESCAPE);
            require(!LuaScriptErrors.shouldShowPopup(true), "Escape must acknowledge the same issues as Dismiss");
            details(minecraft, font, script, actions, output);
            debugMenu(minecraft, output);
        } finally {
            LuaScriptErrors.clear();
            LuaScriptRegistry.clear();
            if (actions.report() != null) {
                Files.deleteIfExists(actions.report());
            }
            Files.delete(root);
        }
        System.out.println("Script issue rendering passed: footer alignment, copy/export, keyboard, "
                + "scrolling, consecutive detail sections, and Debug Tools report navigation.");
    }

    private static void debugMenu(NavigationMinecraft minecraft, File output) throws Exception {
        Path root = Files.createTempDirectory("betamoon-debug-report-ui-");
        List<Path> reports = new ArrayList<>();
        Path[] opened = {null};
        GuiScriptIssueActions actions = new GuiScriptIssueActions(text -> {
        }, path -> opened[0] = path, () -> root, System::currentTimeMillis);
        try {
            LuaScriptErrors.clear();
            for (int index = 0; index < 30; index++) {
                LuaScriptRegistry.updateParsed("report-test-" + index + "/main.lua", "Report Test " + index,
                        Collections.emptyList(), LuaValue.NIL, LuaValue.NIL, LuaValue.NIL, "Test description", "1",
                        null);
            }
            minecraft.displayWidth = 640;
            minecraft.displayHeight = 360;
            GuiScreenScripts scripts = new GuiScreenScripts(new GuiScreen());
            minecraft.displayGuiScreen(scripts);
            draw(scripts, 640, 360);
            GuiScene scriptsScene = scene(scripts);
            GuiScrollView scriptsScroll = find(scriptsScene.getContent(), GuiScrollView.class);
            scriptsScroll.setScrollPosition(0, 24);
            draw(scripts, 640, 360);
            int scriptScrollY = scriptsScroll.getScrollY();
            ScriptMod selected = find(scriptsScene.getContent(), GuiPanelScriptList.class).getSelectedEntry();
            click(scriptsScene, button(scriptsScene.getContent(), "Debug"));
            require(minecraft.currentScreen instanceof GuiPopupDebugMenu,
                    "The Scripts screen must expose the Debug Tools route");
            draw(minecraft.currentScreen, 640, 360);
            GuiScene defaultMenu = scene(minecraft.currentScreen);
            require(button(defaultMenu.getContent(), "Save report") != null,
                    "The Scripts screen's Debug menu must offer the full report action");
            click(defaultMenu, button(defaultMenu.getContent(), "Close"));
            require(minecraft.currentScreen == scripts, "Closing Debug Tools must return to the Scripts screen");

            GuiPopupDebugMenu menu = new GuiPopupDebugMenu(scripts, actions);
            minecraft.displayGuiScreen(menu);
            draw(menu, 640, 360);
            GuiScene menuScene = scene(menu);
            GuiButton save = button(menuScene.getContent(), "Save report");
            GuiScrollView categories = find(menuScene.getContent(), GuiScrollView.class);
            require(save != null && save.isEnabled() && save.getBottom() < categories.getTop(),
                    "The report action must be pinned above the individual exports and enabled without errors");
            require(button(categories.getContent(), "Save report") == null,
                    "The pinned report must not also appear inside the scrolling export list");
            GuiLink folder = find(menuScene.getContent(), GuiLink.class);
            require(folder.getTop() > save.getBottom() && folder.getBottom() < categories.getTop(),
                    "The export-folder link must sit beside the individual-export heading");
            int saveTop = save.getTop();
            int saveLeft = save.getLeft();
            int footerTop = button(menuScene.getContent(), "Export All").getTop();
            categories.setScrollPosition(0, Integer.MAX_VALUE);
            draw(menu, 640, 360);
            require(categories.getScrollY() > 0 && save.getTop() == saveTop && save.getLeft() == saveLeft
                    && button(menuScene.getContent(), "Export All").getTop() == footerTop,
                    "Scrolling individual exports must keep the full-report action and footer fixed");
            ImageIO.write(GuiShowcaseRenderTest.capture(640, 360), "png",
                    new File(output, "script-report-debug-tools-scrolled.png"));
            categories.resetScroll();
            draw(menu, 640, 360);
            ImageIO.write(GuiShowcaseRenderTest.capture(640, 360), "png",
                    new File(output, "script-report-debug-tools-wide.png"));
            click(menuScene, save);
            reports.add(actions.report());
            require(minecraft.currentScreen instanceof GuiPopupScriptIssueExport,
                    "The Debug report action must reuse the clickable-path result popup");
            String empty = new String(Files.readAllBytes(actions.report()), StandardCharsets.UTF_8);
            require(empty.contains("Errors: 0") && empty.contains("Warnings: 0")
                    && empty.contains("Discovered scripts"),
                    "The Debug action must save a useful full report even when no issues exist");
            GuiPopupScriptIssueExport result = (GuiPopupScriptIssueExport) minecraft.currentScreen;
            draw(result, 640, 360);
            click(scene(result), find(scene(result).getContent(), GuiLink.class));
            require(actions.report().equals(opened[0]), "The Debug report link must open its exact saved file");
            result.keyTyped('\u001b', Keyboard.KEY_ESCAPE);
            require(minecraft.currentScreen == menu, "Closing the report result must return to Debug Tools");

            LuaScriptErrors.add("report-test-0/main.lua", "Retain this recorded error");
            LuaScriptErrors.addWarning("report-test-0/main.lua", "Include this warning too");
            categories.setScrollPosition(0, 16);
            draw(menu, 640, 360);
            int categoryScrollY = categories.getScrollY();
            click(menuScene, save);
            reports.add(actions.report());
            result = (GuiPopupScriptIssueExport) minecraft.currentScreen;
            draw(result, 640, 360);
            click(scene(result), button(scene(result).getContent(), "Close"));
            draw(menu, 640, 360);
            require(LuaScriptErrors.shouldShowPopup(true) && LuaScriptErrors.getEntries().size() == 2
                    && categories.getScrollY() == categoryScrollY,
                    "Report saves must preserve issues, acknowledgement and Debug Tools scroll position");
            click(menuScene, button(menuScene.getContent(), "Close"));
            draw(scripts, 640, 360);
            require(minecraft.currentScreen == scripts && scriptsScroll.getScrollY() == scriptScrollY
                    && find(scriptsScene.getContent(), GuiPanelScriptList.class).getSelectedEntry() == selected,
                    "The report route must preserve the Scripts screen's selection and scroll position");

            minecraft.displayWidth = 320;
            minecraft.displayHeight = 240;
            GuiPopupDebugMenu small = new GuiPopupDebugMenu(scripts, actions);
            minecraft.displayGuiScreen(small);
            draw(small, 320, 240);
            GuiScene smallScene = scene(small);
            GuiButton smallSave = button(smallScene.getContent(), "Save report");
            GuiScrollView smallCategories = find(smallScene.getContent(), GuiScrollView.class);
            require(smallSave.getTop() >= 0 && smallSave.getBottom() < smallCategories.getTop()
                    && smallCategories.getHeight() >= 58
                    && button(smallScene.getContent(), "Export All").getBottom() <= 240,
                    "Small screens must keep the report pinned, leave a usable export list, and retain the footer");
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                    new File(output, "script-report-debug-tools-small.png"));

            Path blocker = reports.get(0);
            GuiScriptIssueActions unavailable = new GuiScriptIssueActions(text -> {
            }, path -> {
            }, () -> blocker, System::currentTimeMillis);
            GuiPopupDebugMenu failedMenu = new GuiPopupDebugMenu(scripts, unavailable);
            minecraft.displayGuiScreen(failedMenu);
            draw(failedMenu, 320, 240);
            click(scene(failedMenu), button(scene(failedMenu).getContent(), "Save report"));
            require(minecraft.currentScreen instanceof GuiPopupScriptIssueExport && unavailable.failed(),
                    "A failed Debug report save must display an actionable result");
            result = (GuiPopupScriptIssueExport) minecraft.currentScreen;
            draw(result, 320, 240);
            require(find(scene(result).getContent(), GuiLink.class) == null,
                    "A failed save must not offer a misleading file link");
            click(scene(result), button(scene(result).getContent(), "Close"));
            require(minecraft.currentScreen == failedMenu && LuaScriptErrors.shouldShowPopup(true),
                    "Closing a save failure must return to Debug Tools and retain recorded issues");
        } finally {
            for (Path report : reports) {
                Files.deleteIfExists(report);
            }
            Files.delete(root);
        }
    }

    private static void exportPopupFailures(NavigationMinecraft minecraft, GuiPopupScriptErrors parent, Path root,
            File output) throws Exception {
        boolean[] desktopAvailable = {false};
        Path[] opened = {null};
        GuiScriptIssueActions unavailable = new GuiScriptIssueActions(text -> {
        }, path -> {
            if (!desktopAvailable[0]) {
                throw new IOException("No desktop application is available for text reports");
            }
            opened[0] = path;
        }, () -> root.resolve("not-a-directory"), System::currentTimeMillis);
        Path longPath = root;
        for (int index = 0; index < 20; index++) {
            longPath = longPath.resolve("a-long-directory-name-for-a-wrapped-report-path");
        }
        Path report = longPath.resolve("script-issues.txt");
        GuiPopupScriptIssueExport popup = new GuiPopupScriptIssueExport(parent, report, unavailable);
        minecraft.displayWidth = 320;
        minecraft.displayHeight = 240;
        minecraft.displayGuiScreen(popup);
        draw(popup, 320, 240);
        GuiScene scene = scene(popup);
        GuiScrollView scroll = find(scene.getContent(), GuiScrollView.class);
        GuiLink path = find(scene.getContent(), GuiLink.class);
        GuiButton close = button(scene.getContent(), "Close");
        require(path.getHeight() > scroll.getHeight() && close.getBottom() <= 240,
                "Long export paths must scroll while Close stays visible on small screens");
        scene.keyTyped('\t', Keyboard.KEY_TAB, false);
        scene.keyTyped('\n', Keyboard.KEY_RETURN, false);
        draw(popup, 320, 240);
        require(!((String) field(popup, "failure")).isEmpty() && minecraft.currentScreen == popup,
                "Opening failures must stay in the result popup with readable feedback");
        desktopAvailable[0] = true;
        scene.keyTyped('\n', Keyboard.KEY_RETURN, false);
        require(report.equals(opened[0]) && ((String) field(popup, "failure")).isEmpty() && !unavailable.failed(),
                "Retrying must use this popup's exact report path and clear an earlier opening failure");
        draw(popup, 320, 240);
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                new File(output, "script-issues-export-popup-long-path.png"));
        click(scene, close);
        require(minecraft.currentScreen == parent && LuaScriptErrors.shouldShowPopup(true),
                "The export result's Close button must return without acknowledging script issues");
        Path blocker = Files.createTempFile(root, "blocked-export-", ".txt");
        try {
            GuiScriptIssueActions failedExport = new GuiScriptIssueActions(text -> {
            }, pathToOpen -> {
            }, () -> blocker, System::currentTimeMillis);
            GuiPopupScriptErrors errors = new GuiPopupScriptErrors(new GuiScreen(), true, failedExport);
            minecraft.displayGuiScreen(errors);
            draw(errors, 320, 240);
            GuiScene errorsScene = scene(errors);
            click(errorsScene, button(errorsScene.getContent(), "Save report"));
            require(minecraft.currentScreen == errors && failedExport.failed() && failedExport.report() == null,
                    "A failed export must remain in the issue list instead of opening a success popup");
        } finally {
            Files.delete(blocker);
        }
        minecraft.displayGuiScreen(parent);
        minecraft.displayWidth = 640;
        minecraft.displayHeight = 360;
        parent.setWorldAndResolution(minecraft, 640, 360);
    }

    private static void details(Minecraft minecraft, FontRenderer font, ScriptMod script, GuiScriptIssueActions actions,
            File output) throws Exception {
        LuaScriptRegistry.markLoadedByFile(script.getSourceFileName());
        GuiPanelScriptInfo info = new GuiPanelScriptInfo(actions);
        GuiContainer root = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                info.arrange(context, new Rect(8, 8, 312, 232));
            }
        };
        root.add(info);
        GuiScene scene = new GuiScene();
        scene.setContent(root);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        LuaScriptErrors.clear();
        LuaScriptErrors.addWarning(script.getSourceFileName(), "A warning only");
        info.setSelected(script);
        draw(scene);
        Object layout = field(info, "currentLayout");
        int firstY = (Integer) field(layout, "warningY");
        require((Integer) field(layout, "errorY") == -1, "Warnings-only details must omit the Errors section");
        LuaScriptErrors.clear();
        LuaScriptErrors.add(script.getSourceFileName(), "An error only");
        info.setSelected(script);
        draw(scene);
        layout = field(info, "currentLayout");
        require((Integer) field(layout, "errorY") == firstY && (Integer) field(layout, "warningY") == -1,
                "Errors-only and warnings-only sections must start at the same position");
        LuaScriptErrors.addWarning(script.getSourceFileName(), "A following warning");
        info.setSelected(script);
        draw(scene);
        layout = field(info, "currentLayout");
        require((Integer) field(layout, "errorY") == firstY
                && (Integer) field(layout, "warningY") == firstY + (Integer) field(layout, "errorHeight") + 16 + 18,
                "Warnings must immediately follow the measured Errors section with a normal section gap");
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png", new File(output, "script-issues-details.png"));
    }

    private static void draw(GuiScreen screen, int width, int height) {
        GuiShowcaseRenderTest.setupFrame(width, height);
        screen.updateScreen();
        screen.drawScreen(-1, -1, 0);
        screen.drawScreen(-1, -1, 0);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Script issue popup leaked GL state");
    }

    private static void draw(GuiScene scene) {
        GuiShowcaseRenderTest.setupFrame(320, 240);
        scene.update();
        scene.render(-1, -1, 0);
        scene.update();
        scene.render(-1, -1, 0);
    }

    private static void click(GuiScene scene, GuiElement element) {
        int x = element.getLeft() + element.getWidth() / 2;
        int y = element.getTop() + element.getHeight() / 2;
        scene.mousePressed(x, y, 0);
        scene.mouseReleased(x, y, 0);
    }

    private static GuiScene scene(GuiScreen screen) throws Exception {
        Field field = GuiComponentScreen.class.getDeclaredField("scene");
        field.setAccessible(true);
        return (GuiScene) field.get(screen);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static GuiButton button(GuiElement root, String label) {
        if (root instanceof GuiButton && ((GuiButton) root).getLabel().equals(label)) {
            return (GuiButton) root;
        }
        if (root instanceof GuiContainer) {
            for (GuiElement child : ((GuiContainer) root).getChildren()) {
                GuiButton found = button(child, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends GuiElement> T find(GuiElement root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof GuiContainer) {
            for (GuiElement child : ((GuiContainer) root).getChildren()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final class NavigationMinecraft extends Minecraft {
        private NavigationMinecraft() {
            super(null, null, null, 640, 360, false);
        }

        @Override
        public void displayGuiScreen(GuiScreen screen) {
            currentScreen = screen;
            if (screen != null) {
                screen.setWorldAndResolution(this, displayWidth, displayHeight);
            }
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
