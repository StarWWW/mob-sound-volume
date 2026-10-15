package mobvolume;

import mobvolume.client.MobVolumeScreen;
import mobvolume.compat.Compat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class MobVolumeMod implements ClientModInitializer {
    public static final String MOD_ID = "mobvolume";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping.Category KEY_CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "keybindings"));
    /** Unbound by default so it never clashes with another mod; set it in Controls. */
    public static final KeyMapping SETTINGS_KEY = new KeyMapping("key.mobvolume.settings", -1, KEY_CATEGORY);

    @Override
    public void onInitializeClient() {
        MobVolumeConfig.get();
        KeyMappingHelper.registerKeyMapping(SETTINGS_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (SETTINGS_KEY.consumeClick()) {
                Compat.setScreen(client, new MobVolumeScreen(Compat.screen(client)));
            }
        });
        // a "Mob Sounds..." button at the bottom of Options > Music & Sounds
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof SoundOptionsScreen)) return;
            for (GuiEventListener child : screen.children()) {
                // this also runs on resize, when the screen keeps its list
                if (child instanceof OptionsList list && LISTS_WITH_BUTTON.add(list)) {
                    list.addSmall(Button.builder(Component.translatable("mobvolume.screen.open"),
                            b -> Compat.setScreen(client, new MobVolumeScreen(screen)))
                        .width(310)
                        .tooltip(Tooltip.create(Component.translatable("mobvolume.screen.open.tooltip")))
                        .build(), null);
                    return;
                }
            }
        });
    }

    private static final Set<OptionsList> LISTS_WITH_BUTTON = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean logged;

    public static void logOnce(Throwable t) {
        if (!logged) {
            logged = true;
            LOGGER.error("Mob sound volume failed (further errors are not logged)", t);
        }
    }
}
