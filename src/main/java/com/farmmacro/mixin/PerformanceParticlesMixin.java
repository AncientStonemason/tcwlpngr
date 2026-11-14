package com.farmmacro.mixin;

import com.farmmacro.util.Guard;
import com.farmmacro.visual.PerformanceMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only local block-breaking visual debris. Never suppresses server particles or Tracker evidence. */
@Mixin(ClientLevel.class)
public class PerformanceParticlesMixin {
    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void kaelix$destroy(BlockPos at, BlockState state, CallbackInfo ci) {
        try {
            if (PerformanceMode.hideBlockParticles()) ci.cancel();
        } catch (Throwable t) {
            Guard.report("mixin/perf-destroy", t);
        }
    }

    @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
    private void kaelix$crack(BlockPos at, Direction face, CallbackInfo ci) {
        try {
            if (PerformanceMode.hideBlockParticles()) ci.cancel();
        } catch (Throwable t) {
            Guard.report("mixin/perf-crack", t);
        }
    }
}
