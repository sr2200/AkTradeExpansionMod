package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server settings the client needs for its previews (design G "クライアントに同期する値").
 * Sent on join and after {@code /aktradeex reload}; without it previews would disagree with the server.
 *
 * @param ratios      effective coin ratios (the world's locked ratios when {@code currency.ratioLocked}, E-4)
 * @param clockOffset market clock offset of {@code /aktradeex clock advance} (ticks, {@link MarketClock})
 */
public record ConfigSyncS2C(int[] ratios, boolean sneakUseExchange, double halfLifeDays, double restockMultiplier,
		TradeExpansionConfig.RecoveryClock clock, int maxPerTransaction, double maxDistance,
		long walletCapacity, int bankMinMarketLevel, long cardFee, double cardTransactionFee, boolean allowShiftAll,
		double interestRatePerDay, long interestMaxBalance, double exchangeFee, long clockOffset) implements CustomPacketPayload {
	public static final Type<ConfigSyncS2C> TYPE = new Type<>(TradeExpansion.id("config_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncS2C> CODEC = StreamCodec.ofMember(ConfigSyncS2C::write, ConfigSyncS2C::read);

	public static ConfigSyncS2C fromServer(MinecraftServer server) {
		TradeExpansionConfig c = TradeExpansionConfig.get();
		return new ConfigSyncS2C(CoinType.ratios(), c.currency.sneakUseExchange, c.recovery.halfLifeDays, c.recovery.restockMultiplier,
				c.recovery.clock, c.trade.maxPerTransaction, c.trade.maxDistance,
				c.wallet.capacity, c.bank.minMarketLevel, c.bank.cardFee, c.bank.cardTransactionFee, c.trade.allowShiftAll,
				c.bank.interestRatePerDay, c.bank.interestMaxBalance, c.currency.exchangeFee, MarketRegistry.get(server).clockOffset());
	}

	/** Overlays the server's values on a copy of the local settings and makes it live (client side). */
	public void applyOnClient() {
		TradeExpansionConfig c = ConfigManager.copy(TradeExpansionConfig.get());
		c.currency.sneakUseExchange = sneakUseExchange;
		c.recovery.halfLifeDays = halfLifeDays;
		c.recovery.restockMultiplier = restockMultiplier;
		c.recovery.clock = clock;
		c.trade.maxPerTransaction = maxPerTransaction;
		c.trade.maxDistance = maxDistance;
		c.wallet.capacity = walletCapacity;
		c.bank.minMarketLevel = bankMinMarketLevel;
		c.bank.cardFee = cardFee;
		c.bank.cardTransactionFee = cardTransactionFee;
		c.trade.allowShiftAll = allowShiftAll;
		c.bank.interestRatePerDay = interestRatePerDay;
		c.bank.interestMaxBalance = interestMaxBalance;
		c.currency.exchangeFee = exchangeFee;
		TradeExpansionConfig.set(c);
		CoinType.setRatios(ratios);
		MarketClock.setClientOffset(clockOffset);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarIntArray(ratios);
		buf.writeBoolean(sneakUseExchange);
		buf.writeDouble(halfLifeDays);
		buf.writeDouble(restockMultiplier);
		buf.writeVarInt(clock.ordinal());
		buf.writeVarInt(maxPerTransaction);
		buf.writeDouble(maxDistance);
		buf.writeVarLong(walletCapacity);
		buf.writeVarInt(bankMinMarketLevel);
		buf.writeVarLong(cardFee);
		buf.writeDouble(cardTransactionFee);
		buf.writeBoolean(allowShiftAll);
		buf.writeDouble(interestRatePerDay);
		buf.writeVarLong(interestMaxBalance);
		buf.writeDouble(exchangeFee);
		buf.writeVarLong(clockOffset);
	}

	private static ConfigSyncS2C read(RegistryFriendlyByteBuf buf) {
		return new ConfigSyncS2C(buf.readVarIntArray(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
				TradeExpansionConfig.RecoveryClock.values()[buf.readVarInt()], buf.readVarInt(), buf.readDouble(),
				buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readDouble(), buf.readBoolean(), buf.readDouble(), buf.readVarLong(),
				buf.readDouble(), buf.readVarLong());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
