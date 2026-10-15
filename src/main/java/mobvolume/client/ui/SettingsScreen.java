package mobvolume.client.ui;

import mobvolume.compat.Compat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The settings screen layout shared by Mob Sound Volume, Dimension Render Distance and Spawn Radius Rings, so all
 * three look and work the same: the title (and optional controls such as a search box) at the top, one scrolling
 * {@link SettingsList} with gold section headers in the middle, and at the bottom optional info lines above a
 * [Reset] [Done] pair. Reset always asks first. Settings are saved whenever the screen is left.
 */
public abstract class SettingsScreen extends Screen {
    public static final int HEADER_COLOR = 0xFFE8C66A;
    public static final int INFO_COLOR = 0xFFA0A0A0;
    private static final int TITLE_HEADER = 33;
    private static final int FOOTER = 33;
    private static final int INFO_LINE = 11;

    protected final @Nullable Screen parent;
    protected final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, TITLE_HEADER, FOOTER);
    protected @Nullable SettingsList list;

    protected SettingsScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Fills the list. Called again whenever the list is rebuilt. */
    protected abstract void buildList(SettingsList list);

    /** Writes the settings to disk. */
    protected abstract void save();

    /** Text of the reset button, or null to have only Done. */
    protected abstract @Nullable Component resetLabel();

    /** The question and explanation of the "are you sure" screen. */
    protected Component resetQuestion() {
        return resetLabel() == null ? Component.empty() : resetLabel();
    }

    protected Component resetWarning() {
        return Component.empty();
    }

    /** Puts the settings back to their defaults (after the player confirmed). */
    protected void reset() {
    }

    /** Adds controls under the title, e.g. a search box, and returns how much taller the header must be. */
    protected int addHeaderControls(LinearLayout header) {
        return 0;
    }

    /** Grey lines shown above the buttons, e.g. the current state. Asked every frame. */
    protected List<Component> infoLines() {
        return List.of();
    }

    protected @Nullable Tooltip doneTooltip() {
        return null;
    }

    /** Runs when the screen is closed with Done or Escape, before going back. */
    protected void afterClose() {
    }

    @Override
    protected void init() {
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(6));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(this.title, this.font));
        layout.setHeaderHeight(TITLE_HEADER + addHeaderControls(header));

        list = layout.addToContents(new SettingsList(this.minecraft, this.width, layout.getContentHeight(),
            layout.getHeaderHeight(), this::buildList));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8),
            s -> s.alignVerticallyBottom().paddingBottom(7));
        Component reset = resetLabel();
        if (reset != null) {
            footer.addChild(Button.builder(reset, b -> confirmReset()).width(150).build());
        }
        Button done = footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .width(reset != null ? 150 : 200).build());
        Tooltip tooltip = doneTooltip();
        if (tooltip != null) done.setTooltip(tooltip);

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.setFooterHeight(footerHeight(infoLines().size()));
        layout.arrangeElements();
        if (list != null) list.updateSize(this.width, layout);
    }

    private static int footerHeight(int infoLines) {
        return FOOTER + (infoLines == 0 ? 0 : infoLines * INFO_LINE + 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        List<Component> info = infoLines();
        // e.g. a server limit line appears: make room for it
        if (layout.getFooterHeight() != footerHeight(info.size())) repositionElements();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int top = this.height - layout.getFooterHeight() + 5;
        for (int i = 0; i < info.size(); i++) {
            g.centeredText(this.font, info.get(i), this.width / 2, top + i * INFO_LINE, INFO_COLOR);
        }
    }

    private void confirmReset() {
        Compat.setScreen(this.minecraft, new ConfirmScreen(yes -> {
            if (yes) {
                reset();
                save();
                if (list != null) list.rebuild();
            }
            Compat.setScreen(this.minecraft, this);
        }, resetQuestion(), resetWarning()));
    }

    @Override
    public void removed() {
        save();
    }

    @Override
    public void onClose() {
        afterClose();
        Compat.setScreen(this.minecraft, parent);
    }
}
