package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Coin price of every offer of the villager trade screen the player opened (design H, stage 14a), by offer index;
 * {@code -1} = paid as in vanilla. An empty array means coin trades are off.
 */
public record VillagerPricesS2C(int containerId, long[] prices) implements CustomPacketPayload {
	public static final Type<VillagerPricesS2C> TYPE = new Type<>(TradeExpansion.id("villager_prices"));
	public static final StreamCodec<ByteBuf, VillagerPricesS2C> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, VillagerPricesS2C::containerId,
			ByteBufCodecs.LONG_ARRAY, VillagerPricesS2C::prices,
			VillagerPricesS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
