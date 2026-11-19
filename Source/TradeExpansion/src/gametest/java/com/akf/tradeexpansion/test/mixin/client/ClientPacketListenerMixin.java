package com.akf.tradeexpansion.test.mixin.client;

import com.akf.tradeexpansion.test.client.ParticleRecorder;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every particle and sound packet the client handles goes to {@link ParticleRecorder} (on the render thread). */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleParticleEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER))
	private void ak_trade_expansion_test$recordParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		ParticleRecorder.onPacket(packet);
	}

	@Inject(method = "handleSoundEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER))
	private void ak_trade_expansion_test$recordSound(ClientboundSoundPacket packet, CallbackInfo ci) {
		ParticleRecorder.onSound(packet);
	}
}
