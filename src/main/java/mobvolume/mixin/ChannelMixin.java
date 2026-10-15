package mobvolume.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OpenAL caps a sound's gain at AL_MAX_GAIN, 1.0 by default. Vanilla never goes above 1.0, so this only
 * changes anything for a mob turned above 100%: it raises the cap so the sound really gets louder.
 */
@Mixin(Channel.class)
abstract class ChannelMixin {
    @Shadow @Final private int source;

    @Inject(method = "setVolume", at = @At("HEAD"))
    private void mobvolume$allowBoost(float volume, CallbackInfo ci) {
        if (volume > 1.0f) {
            AL10.alSourcef(this.source, AL10.AL_MAX_GAIN, volume);
        }
    }
}
