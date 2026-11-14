package com.farmmacro.mixin;

import com.farmmacro.util.Guard;
import com.farmmacro.visual.PerformanceMode;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class PerformanceFpsMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void kaelix$background(CallbackInfoReturnable<Integer> ci) {
        try {
            ci.setReturnValue(PerformanceMode.backgroundLimit(ci.getReturnValueI()));
        } catch (Throwable t) {
            Guard.report("mixin/perf-fps", t); // ванильное значение остаётся
        }
    }
}
