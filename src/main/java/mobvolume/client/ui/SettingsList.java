package mobvolume.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The scrolling list of a {@link SettingsScreen}: section headers, rows of ordinary widgets, and any custom
 * {@link Row}. Rows can be kept and added again after a {@link #rebuild()}; they are synced when added.
 */
public final class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {
    public static final int ROW_WIDTH = 310;
    private static final int GAP = 4;

    private final Consumer<SettingsList> builder;

    SettingsList(Minecraft minecraft, int width, int height, int y, Consumer<SettingsList> builder) {
        super(minecraft, width, height, y, 24);
        this.centerListVertically = false;
        this.builder = builder;
        rebuild();
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    /** Builds the rows again (e.g. after something was opened up or filtered), keeping the scroll position. */
    public void rebuild() {
        double scroll = scrollAmount();
        clearEntries();
        builder.accept(this);
        setScrollAmount(scroll);
    }

    public void add(Row row) {
        row.sync();
        addEntry(row, row.height());
    }

    /** Gold section title with a thin line under it. */
    public void header(Component text) {
        add(new HeaderRow(text, children().isEmpty()));
    }

    /** Widgets side by side, sharing the row's width (a checkbox keeps its own width). */
    public void row(AbstractWidget... widgets) {
        add(new WidgetRow(List.of(widgets), List.of()));
    }

    /** One widget filling the row, with smaller ones that keep their width at its right end. */
    public void rowWithEnd(AbstractWidget main, AbstractWidget... end) {
        add(new WidgetRow(List.of(main), List.of(end)));
    }

    public Checkbox checkbox(Component label, boolean value, Consumer<Boolean> setter, @Nullable Component tooltip) {
        Checkbox.Builder builder = Checkbox.builder(label, minecraft.font)
            .maxWidth(ROW_WIDTH)
            .selected(value)
            .onValueChange((box, v) -> setter.accept(v));
        if (tooltip != null) builder.tooltip(Tooltip.create(tooltip));
        return builder.build();
    }

    /** Tells every shown row that a setting changed (texts, reset buttons...). */
    public void refreshAll() {
        children().forEach(Row::refresh);
    }

    /** A line of the list. Custom rows draw their own widgets in {@code extractContent}. */
    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        /** Height of the row including 2 px padding above and below. */
        protected int height() {
            return 24;
        }

        /** Updates texts and buttons after a setting changed. */
        public void refresh() {
        }

        /** Moves sliders and the like to the saved values, then refreshes. */
        public void sync() {
            refresh();
        }
    }

    private static final class HeaderRow extends Row {
        private final Component text;
        private final boolean first;

        HeaderRow(Component text, boolean first) {
            this.text = text;
            this.first = first;
        }

        @Override
        protected int height() {
            return first ? 20 : 26;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int y = getContentBottom() - 12;
            g.text(Minecraft.getInstance().font, text, getContentX(), y, SettingsScreen.HEADER_COLOR);
            g.fill(getContentX(), y + 10, getContentRight(), y + 11, 0x55FFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private static final class WidgetRow extends Row {
        private final List<AbstractWidget> main;
        private final List<AbstractWidget> end;
        private final List<AbstractWidget> all = new ArrayList<>();

        WidgetRow(List<AbstractWidget> main, List<AbstractWidget> end) {
            this.main = main;
            this.end = end;
            all.addAll(main);
            all.addAll(end);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int x = getContentX();
            int right = getContentRight();
            for (int i = end.size() - 1; i >= 0; i--) {
                AbstractWidget w = end.get(i);
                right -= w.getWidth();
                place(w, right);
                right -= GAP;
            }
            int each = (right - x - GAP * (main.size() - 1)) / main.size();
            for (int i = 0; i < main.size(); i++) {
                AbstractWidget w = main.get(i);
                if (!(w instanceof Checkbox)) w.setWidth(each);
                place(w, x + i * (each + GAP));
            }
            for (AbstractWidget w : all) w.extractRenderState(g, mouseX, mouseY, a);
        }

        private void place(AbstractWidget w, int x) {
            w.setPosition(x, getContentY() + (20 - w.getHeight()) / 2);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return all;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return all;
        }
    }
}
