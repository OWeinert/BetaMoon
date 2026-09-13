package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Insets;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import java.util.ArrayList;
import java.util.List;

/** Reusable retained layout containers and typed sizing rules. */
public final class GuiLayouts {
    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    public static final class Length {
        private enum Kind {
            CONTENT,
            FIXED,
            FILL
        }

        private static final Length CONTENT = new Length(Kind.CONTENT, 0, 0);
        private final Kind kind;
        private final int value;
        private final int weight;

        private Length(Kind kind, int value, int weight) {
            this.kind = kind;
            this.value = value;
            this.weight = weight;
        }

        public static Length content() {
            return CONTENT;
        }

        public static Length fixed(int pixels) {
            return new Length(Kind.FIXED, Math.max(0, pixels), 0);
        }

        public static Length fill() {
            return fill(1);
        }

        public static Length fill(int weight) {
            return new Length(Kind.FILL, 0, Math.max(1, weight));
        }
    }

    public static class Stack extends GuiContainer {
        private final List<Entry> entries = new ArrayList<Entry>();
        private Axis axis;
        private Insets padding = Insets.NONE;
        private int gap;

        public Stack(Axis axis) {
            if (axis == null) {
                throw new IllegalArgumentException("Stack axis cannot be null");
            }
            this.axis = axis;
        }

        public Stack setAxis(Axis axis) {
            if (axis != null && this.axis != axis) {
                this.axis = axis;
                requestLayout();
            }
            return this;
        }

        public Stack setPadding(Insets padding) {
            this.padding = padding == null ? Insets.NONE : padding;
            requestLayout();
            return this;
        }

        public Stack setGap(int gap) {
            this.gap = Math.max(0, gap);
            requestLayout();
            return this;
        }

        public <T extends GuiElement> T addItem(T child, Length width, Length height) {
            add(child);
            entries.add(new Entry(child, width == null ? Length.content() : width,
                    height == null ? Length.content() : height));
            requestLayout();
            return child;
        }

        @Override
        public void remove(GuiElement child) {
            super.remove(child);
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (entries.get(i).element == child) {
                    entries.remove(i);
                }
            }
        }

        @Override
        public void clear() {
            super.clear();
            entries.clear();
        }

        @Override
        protected Size measureElement(GuiContext context, Constraints constraints) {
            int primary = 0;
            int cross = 0;
            int childWidth = Math.max(0, constraints.getMaxWidth() - padding.getHorizontal());
            int childHeight = Math.max(0, constraints.getMaxHeight() - padding.getVertical());
            Constraints childConstraints = Constraints.upTo(childWidth, childHeight);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                Size size = entry.element.measure(context, childConstraints);
                primary += primarySize(entry, size);
                cross = Math.max(cross, crossSize(entry, size));
            }
            primary += gap * Math.max(0, entries.size() - 1);
            if (axis == Axis.VERTICAL) {
                return constraints.constrain(
                        new Size(cross + padding.getHorizontal(), primary + padding.getVertical()));
            }
            return constraints.constrain(new Size(primary + padding.getHorizontal(), cross + padding.getVertical()));
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            Rect content = getBounds().inset(padding);
            int availablePrimary = axis == Axis.VERTICAL ? content.getHeight() : content.getWidth();
            int fixedPrimary = gap * Math.max(0, entries.size() - 1);
            int totalWeight = 0;
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                Length primaryLength = axis == Axis.VERTICAL ? entry.height : entry.width;
                if (primaryLength.kind == Length.Kind.FILL) {
                    totalWeight += primaryLength.weight;
                } else {
                    fixedPrimary += resolve(primaryLength,
                            axis == Axis.VERTICAL ? entry.element.getMeasuredSize().getHeight()
                                    : entry.element.getMeasuredSize().getWidth(),
                            availablePrimary);
                }
            }
            int remaining = Math.max(0, availablePrimary - fixedPrimary);
            int cursor = axis == Axis.VERTICAL ? content.getTop() : content.getLeft();
            int remainingWeight = totalWeight;
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                Length primaryLength = axis == Axis.VERTICAL ? entry.height : entry.width;
                int primarySize;
                if (primaryLength.kind == Length.Kind.FILL) {
                    primarySize = remainingWeight == 0 ? 0 : remaining * primaryLength.weight / remainingWeight;
                    remaining -= primarySize;
                    remainingWeight -= primaryLength.weight;
                } else {
                    primarySize = resolve(primaryLength,
                            axis == Axis.VERTICAL ? entry.element.getMeasuredSize().getHeight()
                                    : entry.element.getMeasuredSize().getWidth(),
                            availablePrimary);
                }
                int crossAvailable = axis == Axis.VERTICAL ? content.getWidth() : content.getHeight();
                Length crossLength = axis == Axis.VERTICAL ? entry.width : entry.height;
                int measuredCross = axis == Axis.VERTICAL ? entry.element.getMeasuredSize().getWidth()
                        : entry.element.getMeasuredSize().getHeight();
                int crossSize = resolve(crossLength, measuredCross, crossAvailable);
                Rect bounds = axis == Axis.VERTICAL
                        ? Rect.fromPositionAndSize(content.getLeft(), cursor, crossSize, primarySize)
                        : Rect.fromPositionAndSize(cursor, content.getTop(), primarySize, crossSize);
                entry.element.arrange(context, bounds);
                cursor += primarySize + gap;
            }
        }

        private int primarySize(Entry entry, Size measured) {
            Length length = axis == Axis.VERTICAL ? entry.height : entry.width;
            int contentSize = axis == Axis.VERTICAL ? measured.getHeight() : measured.getWidth();
            return length.kind == Length.Kind.FILL ? 0 : resolve(length, contentSize, Integer.MAX_VALUE);
        }

        private int crossSize(Entry entry, Size measured) {
            Length length = axis == Axis.VERTICAL ? entry.width : entry.height;
            int contentSize = axis == Axis.VERTICAL ? measured.getWidth() : measured.getHeight();
            return length.kind == Length.Kind.FILL ? contentSize : resolve(length, contentSize, Integer.MAX_VALUE);
        }

        private static int resolve(Length length, int contentSize, int available) {
            if (length.kind == Length.Kind.FIXED) {
                return Math.min(available, length.value);
            }
            if (length.kind == Length.Kind.FILL) {
                return available;
            }
            return Math.min(available, Math.max(0, contentSize));
        }

        private static final class Entry {
            private final GuiElement element;
            private final Length width;
            private final Length height;

            private Entry(GuiElement element, Length width, Length height) {
                this.element = element;
                this.width = width;
                this.height = height;
            }
        }
    }

    /** Two-child layout with a clamped fractional size for the first child. */
    public static final class Split extends GuiContainer {
        private final Axis axis;
        private final GuiElement first;
        private final GuiElement second;
        private final int minimumFirstSize;
        private final int maximumFirstSize;
        private final float firstFraction;
        private final int gap;

        public Split(Axis axis, GuiElement first, GuiElement second, int minimumFirstSize, float firstFraction,
                int maximumFirstSize, int gap) {
            if (axis == null || first == null || second == null) {
                throw new IllegalArgumentException("Split layout requires an axis and two children");
            }
            this.axis = axis;
            this.first = add(first);
            this.second = add(second);
            this.minimumFirstSize = Math.max(0, minimumFirstSize);
            this.maximumFirstSize = Math.max(this.minimumFirstSize, maximumFirstSize);
            this.firstFraction = Math.max(0.0F, Math.min(1.0F, firstFraction));
            this.gap = Math.max(0, gap);
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            Rect bounds = getBounds();
            int available = axis == Axis.HORIZONTAL ? bounds.getWidth() : bounds.getHeight();
            int firstSize = Math.max(minimumFirstSize,
                    Math.min(maximumFirstSize, (int) (available * firstFraction)));
            firstSize = Math.min(firstSize, Math.max(0, available - gap));
            if (axis == Axis.HORIZONTAL) {
                first.arrange(context, new Rect(bounds.getLeft(), bounds.getTop(), bounds.getLeft() + firstSize,
                        bounds.getBottom()));
                second.arrange(context, new Rect(bounds.getLeft() + firstSize + gap, bounds.getTop(),
                        bounds.getRight(), bounds.getBottom()));
            } else {
                first.arrange(context, new Rect(bounds.getLeft(), bounds.getTop(), bounds.getRight(),
                        bounds.getTop() + firstSize));
                second.arrange(context, new Rect(bounds.getLeft(), bounds.getTop() + firstSize + gap,
                        bounds.getRight(), bounds.getBottom()));
            }
        }
    }

    private GuiLayouts() {
    }
}
