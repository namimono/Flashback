package com.moulberry.flashback.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.ItemInHandRendererExt;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Iris draws hands outside GameRenderer's vanilla path, so it needs the same replay substitution. */
@IfModLoaded("iris")
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public class MixinIrisHandRenderer {
    @WrapOperation(method = "canRender", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;", remap = true))
    private GameType flashback$playerMode(MultiPlayerGameMode gameMode, Operation<GameType> original) {
        var player = Flashback.getSpectatingPlayer();
        if (player == null) return original.call(gameMode);
        var info = player.getPlayerInfo();
        return info == null ? GameType.SURVIVAL : info.getGameMode();
    }

    @WrapOperation(method = {"renderSolid", "renderTranslucent"}, remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V", remap = true))
    private void flashback$renderHands(ItemInHandRenderer renderer, float partialTick, PoseStack poseStack,
                                      MultiBufferSource.BufferSource buffers, LocalPlayer localPlayer, int light,
                                      Operation<Void> original) {
        var player = Flashback.getSpectatingPlayer();
        if (player == null) {
            original.call(renderer, partialTick, poseStack, buffers, localPlayer, light);
            return;
        }
        var minecraft = Minecraft.getInstance();
        float frame = minecraft.level.tickRateManager().isEntityFrozen(player) ? 1.0f : partialTick;
        ((ItemInHandRendererExt) renderer).flashback$renderHandsWithItems(frame, poseStack, buffers, player, light, null);
    }

    // Iris separates solid and translucent hands; classify the recorded inventory, not the viewer's.
    @WrapOperation(method = "isHandTranslucent", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", remap = true))
    private ItemStack flashback$handItem(LocalPlayer localPlayer, EquipmentSlot slot, Operation<ItemStack> original) {
        var player = Flashback.getSpectatingPlayer();
        return player == null ? original.call(localPlayer, slot) : player.getItemBySlot(slot);
    }
}
