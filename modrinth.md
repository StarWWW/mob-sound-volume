Mob Sound Volume lets you set how loud each mob is, from silent (0%) to twice as loud (200%). Turn down the zombies groaning around your base, mute the bees in your farm or make the warden easier to hear, without touching any other sound.

**Features**

- **Categories:** mobs are sorted into hostile mobs, bosses, animals, water creatures, villagers, golems and helpers, players and others, objects (boats, minecarts, arrows, item frames, paintings, fireworks, TNT, lightning, XP orbs...) and sounds shared by many mobs. Each category has its own slider.
- **Per mob:** a 0–200% slider for every mob in the game.
- **Per kind of sound:** a mob's idle, hurt, death, step, movement, attack, eating and other sounds can each be set separately.
- **Every single sound:** every entity sound of the game (929 in 26.3) has a slider of its own, named after the game's own subtitles ("Zombie groans").
- **The levels multiply:** category × mob × kind × sound. The volume that is really played is shown on each row, and each row has its own reset button that leaves the levels under it alone.
- **Configured:** every mob you changed is listed at the top, so all your changes are in one place.
- **Recently heard:** mobs heard in the last 2 minutes are listed at the top, so the one that annoys you is easy to find.
- **Preview:** each row plays a sample at the volume you set.
- **Live:** sounds that are already playing change while you move a slider.
- **Search** by mob name, id or sound name.
- **A real boost:** above 100% the sound really gets louder; how far away it can be heard does not change.
- **Modded mobs** are picked up automatically.
- Opens from Options > Music & Sounds > Mob Sounds..., from Mod Menu, or with a key you can bind in Controls.
- 19 languages.

**How it works**

When a mob makes a sound on a server, the client only gets the sound's name and position, not which mob made it, so the mod tells mobs apart by the sound name (`entity.zombie.ambient`, `entity.cow.hurt`...). The volume is applied on top of the game's own Master and category sliders.

Some mobs use another mob's sounds in the game itself, so they cannot be set apart: cave spiders use spider sounds, trader llamas use llama sounds and mooshrooms use the cow's idle sound. Block, music, weather and interface sounds are not entity sounds, and most mob footsteps are the sound of the block they walk on, so those keep using the game's own sliders.

**Requirements**

- Minecraft 26.1.x, 26.2 or 26.3, Fabric Loader 0.19 or newer, Java 25
- Fabric API
- Optional: Mod Menu

Client-side only: it works on any server and in singleplayer, and the server needs nothing.
