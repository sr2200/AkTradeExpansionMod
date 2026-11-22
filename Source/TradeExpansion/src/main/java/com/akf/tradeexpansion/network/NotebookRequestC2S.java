package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * T (v0.81): the notebook screen asks the server: turn the morning report on / off ({@link #REPORT_ON} /
 * {@link #REPORT_OFF}). The server answers with a fresh {@link NotebookS2C}. Only with a notebook in the inventory.
 */
public record NotebookRequestC2S(int action) implements CustomPacketPayload {
	public static final int REPORT_ON = 1;
	public static final int REPORT_OFF = 2;

	public static final Type<NotebookRequestC2S> TYPE = new Type<>(TradeExpansion.id("notebook_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NotebookRequestC2S> CODEC =
			ByteBufCodecs.VAR_INT.map(NotebookRequestC2S::new, NotebookRequestC2S::action).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
