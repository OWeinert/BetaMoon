package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Script selector with stable selection and retained scroll state. */
final class GuiPanelScriptList extends GuiContainer {
    private static final int ROW_HEIGHT = 16;
    private static final int PADDING = 10;
    private static final int WARNING_SIZE = 12;
    private static final int WARNING_GAP = 3;

    private final ScriptListContent content = new ScriptListContent();
    private final GuiScrollView scrollView = new GuiScrollView(content, GuiScrollView.Policy.VERTICAL);
    private final List<GuiScriptRestartIndicator> warningIndicators = new ArrayList<GuiScriptRestartIndicator>();
    private List<ScriptMod> entries = Collections.emptyList();
    private String selectedSourceFile;
    private int selectedIndex = -1;
    private int listTop;
    private int listBottom;
    private int listContentRight;
    private int separatorX;
    private int headerTextY;
    private float headerScale = 1.0F;

    GuiPanelScriptList() {
        add(scrollView);
    }

    void reset() {
        scrollView.resetScroll();
        selectedSourceFile = null;
        selectedIndex = -1;
    }

    void setEntries(List<ScriptMod> newEntries) {
        List<ScriptMod> safeEntries = newEntries == null ? Collections.<ScriptMod>emptyList() : newEntries;
        if (sameEntries(entries, safeEntries)) {
            return;
        }
        entries = new ArrayList<ScriptMod>(safeEntries);
        synchronizeIndicators();
        restoreSelection();
        requestLayout();
    }

    void setHeaderScale(float headerScale) {
        this.headerScale = headerScale;
    }

    ScriptMod getSelectedEntry() {
        if (entries.isEmpty()) {
            selectedIndex = -1;
            selectedSourceFile = null;
            return null;
        }
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            selectedIndex = 0;
            selectedSourceFile = key(entries.get(0));
        }
        return entries.get(selectedIndex);
    }

    int getSeparatorX() {
        return separatorX;
    }

    int getHeaderTextY() {
        return headerTextY;
    }

    int getListTop() {
        return listTop;
    }

    int getListBottom() {
        return listBottom;
    }

    static int getPanelPadding() {
        return PADDING;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        headerTextY = getTop() + 4;
        int headerLineY = getTop() + 20;
        listTop = headerLineY + 6;
        listBottom = getBottom();
        if (listBottom - listTop < 80) {
            listBottom = context.getScreenHeight() - PADDING;
        }
        listContentRight = getRight() - 6;
        separatorX = getRight() + 6;
        int contentHeight = entries.isEmpty() ? 0 : entries.size() * ROW_HEIGHT - 2;
        scrollView.setContentSize(Math.max(0, getWidth()), contentHeight);
        scrollView.arrange(context, new Rect(getLeft(), listTop, getRight(), listBottom));
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        int headerLineY = getTop() + 20;
        context.getRenderer().drawScaledText("Scripts", getLeft(), headerTextY,
                context.getRenderer().getTheme().textPrimary, headerScale);
        context.getRenderer().drawHorizontalLine(PADDING, context.getScreenWidth() - PADDING, headerLineY,
                context.getRenderer().getTheme().line);
        context.getRenderer().drawVerticalLine(listTop - 6, listBottom + 2, separatorX,
                context.getRenderer().getTheme().line);
    }

    private void synchronizeIndicators() {
        while (warningIndicators.size() > entries.size()) {
            warningIndicators.remove(warningIndicators.size() - 1);
        }
        for (int i = 0; i < entries.size(); i++) {
            if (i == warningIndicators.size()) {
                warningIndicators.add(new GuiScriptRestartIndicator(entries.get(i).getSourceFileName()));
            } else {
                warningIndicators.get(i).setSourceFileName(entries.get(i).getSourceFileName());
            }
        }
    }

    private void restoreSelection() {
        selectedIndex = -1;
        if (selectedSourceFile != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (selectedSourceFile.equals(key(entries.get(i)))) {
                    selectedIndex = i;
                    return;
                }
            }
        }
        if (!entries.isEmpty()) {
            selectedIndex = 0;
            selectedSourceFile = key(entries.get(0));
        }
    }

    private void select(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        selectedIndex = index;
        selectedSourceFile = key(entries.get(index));
    }

    private static String key(ScriptMod entry) {
        return entry == null || entry.getSourceFileName() == null ? "" : entry.getSourceFileName();
    }

    private static boolean sameEntries(List<ScriptMod> first, List<ScriptMod> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i) != second.get(i)) {
                return false;
            }
        }
        return true;
    }

    private final class ScriptListContent extends GuiElement {
        @Override
        protected void renderElement(GuiContext context) {
            int y = getTop();
            int hoveredIndex = rowAt(context.getMouseX(), context.getMouseY());
            for (int i = 0; i < entries.size(); i++) {
                ScriptMod entry = entries.get(i);
                int color = entry.isFailed() ? context.getRenderer().getTheme().textError
                        : context.getRenderer().getTheme().textPrimary;
                if (!entry.isFailed()
                        && LuaScriptErrors.hasWarningFor(entry.getDisplayName(), entry.getSourceFileName())) {
                    color = context.getRenderer().getTheme().textWarning;
                }
                String fullName = entry.getDisplayName();
                GuiScriptRestartIndicator indicator = warningIndicators.get(i);
                boolean restartRequired = indicator.shouldRender();
                int nameLeft = getLeft() + 4 + (restartRequired ? WARNING_SIZE + WARNING_GAP : 0);
                int nameWidth = listContentRight - nameLeft;
                String displayName = context.getRenderer().trimToWidth(fullName, nameWidth);
                int blockHeight = ROW_HEIGHT - 2;
                if (i == selectedIndex) {
                    context.getRenderer().drawRect(getLeft() + 1, y - 1, listContentRight - 1,
                            y + blockHeight - 1, context.getRenderer().getTheme().listSelectedBackground);
                } else if (i == hoveredIndex) {
                    context.getRenderer().drawRect(getLeft() + 1, y - 1, listContentRight - 1,
                            y + blockHeight - 1, context.getRenderer().getTheme().listHoverBackground);
                }
                int textHeight = context.getRenderer().lineHeight();
                int textY = y + (blockHeight - textHeight) / 2;
                if (restartRequired) {
                    int iconY = y + (blockHeight - WARNING_SIZE - 2) / 2;
                    indicator.arrange(context,
                            Rect.fromPositionAndSize(getLeft() + 4, iconY, WARNING_SIZE, WARNING_SIZE));
                    indicator.render(context);
                }
                context.getRenderer().drawText(displayName, nameLeft, textY, color);
                int nameRight = nameLeft + context.getRenderer().textWidth(displayName);
                if (!displayName.equals(fullName) && context.getMouseX() >= nameLeft
                        && context.getMouseX() < nameRight && i == hoveredIndex) {
                    context.showTooltip(fullName);
                }
                y += ROW_HEIGHT;
            }
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() != GuiInputEvent.Type.POINTER_DOWN || event.getButton() != 0) {
                return false;
            }
            int index = rowAt(event.getMouseX(), event.getMouseY());
            if (index >= 0) {
                select(index);
                return true;
            }
            return false;
        }

        private int rowAt(int mouseX, int mouseY) {
            if (entries.isEmpty() || mouseX < getLeft() || mouseX >= listContentRight || mouseY < listTop
                    || mouseY >= listBottom) {
                return -1;
            }
            int index = (mouseY - getTop()) / ROW_HEIGHT;
            if (index < 0 || index >= entries.size()) {
                return -1;
            }
            int rowTop = getTop() + index * ROW_HEIGHT;
            return mouseY < rowTop + ROW_HEIGHT - 2 ? index : -1;
        }
    }
}
