package mobvolume.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import mobvolume.MobSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales every mob sound by its configured volume. The factor is applied to the final gain (after the game's
 * own master/category volume), so 200% really is twice as loud and the hearing distance does not change.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void mobvolume$skipMuted(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (MobSounds.onPlay(instance)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }

    /** Volume a new sound starts with. */
    @ModifyExpressionValue(method = "play", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float mobvolume$scaleNew(float volume, @Local(argsOnly = true) SoundInstance instance) {
        return volume * MobSounds.factor(instance);
    }

    /** Volume of a sound that is already playing (looping sounds every tick, and after a volume change). */
    @ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
    private float mobvolume$scalePlaying(float volume, @Local(argsOnly = true) SoundInstance instance) {
        return volume * MobSounds.factor(instance);
    }
}
