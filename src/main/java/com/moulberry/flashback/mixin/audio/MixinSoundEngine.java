package com.moulberry.flashback.mixin.audio;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.windows.ExportDoneWindow;
import com.moulberry.flashback.exporting.ExportFinalizer;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.sound.FlashbackAudioManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {

    @Unique
    private boolean wasExportingAudio = false;

    @Inject(method = "destroy", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Library;cleanup()V"))
    public void destroy(CallbackInfo ci) {
        FlashbackAudioManager.invalidateLoadedBuffers();
    }

    @Inject(method = "shouldChangeDevice", at = @At("HEAD"), cancellable = true)
    public void shouldChangeDevice(CallbackInfoReturnable<Boolean> cir) {
        boolean isExportingAudio = Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().recordAudio();

        // Keep the loopback device while more queued jobs are about to start, or while the last
        // export is still being written out. Otherwise every job boundary does a full OpenAL
        // destroy/reload (very slow), and the final restore also blocks the client tick before
        // ExportDoneWindow can render.
        if (!isExportingAudio && wasExportingAudio) {
            if (ExportJobQueue.drainingQueue || !ExportJobQueue.queuedJobs.isEmpty() ||
                    ExportFinalizer.isFinalizing() || ExportDoneWindow.isDone()) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (wasExportingAudio != isExportingAudio) {
            wasExportingAudio = isExportingAudio;
            cir.setReturnValue(true);
        } else if (isExportingAudio) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    public void play(SoundInstance soundInstance, CallbackInfo ci) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null && Flashback.EXPORT_JOB == null && replayServer.replayPaused) {
            ci.cancel();
        }
    }

}
