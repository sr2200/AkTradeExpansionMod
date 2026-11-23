package com.akf.tradeexpansion.currency;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.CoinMath;
import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.MintRequestC2S;
import com.akf.tradeexpansion.network.MintResultS2C;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.TradeResult;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Minting at the mint (design N-4, E-2): a market with a completed mint turns copper and iron blocks into coins on the
 * exchange tab, 9 blocks at a time like the minting recipe, worth the recipe's value × {@code development.mintValueMultiplier}.
 * The same switches as the recipes apply: {@code currency.allowMinting}, and {@code currency.allowIronMinting} for iron.
 * The coins are the fewest for the value (E-3), into the inventory or dropped at the player's feet.
 */
public final class MintService {
	private MintService() {
	}

	/** A metal the mint takes; {@code value} is the copper value of 9 blocks by the recipe (E-2, Q20). */
	public record Metal(net.minecraft.resources.Identifier id, long value) {
		public Item block() {
			return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
		}

		/** Whether the configuration allows minting it (the recipe's load conditions). */
		public boolean enabled() {
			TradeExpansionConfig.Currency c = TradeExpansionConfig.get().currency;
			return c.allowMinting && (block() != Items.IRON_BLOCK || c.allowIronMinting);
		}

		/** Copper value of one minting (9 blocks) at the mint of {@code market} (N-11: more with its level). */
		public long mintValue(Market market) {
			return DevelopmentMath.mintValue(value, com.akf.tradeexpansion.development.FacilityEffects.raise(market, FacilityKind.MINT, 0, TradeExpansionConfig.get().development.mintValueMultiplier), 1);
		}
	}

	/** Same values as {@code copper_coin_minting.json} and {@code iron_coin_minting.json}. */
	public static final List<Metal> METALS = List.of(
			new Metal(net.minecraft.resources.Identifier.withDefaultNamespace("copper_block"), 64),
			new Metal(net.minecraft.resources.Identifier.withDefaultNamespace("iron_block"), 640));

	/** Mintings one request may ask for (64 × 9 blocks). */
	public static final int MAX_BATCHES = 64;

	public static void handle(ServerPlayer player, MintRequestC2S request) {
		long[] minted = new long[1];
		TradeResult result = process(player, request, minted);
		ServerPlayNetworking.send(player, new MintResultS2C(result, minted[0]));
	}

	private static TradeResult process(ServerPlayer player, MintRequestC2S request, long[] minted) {
		Market market = MarketRegistry.get(player.level().getServer()).get(request.marketId());
		if (MarketMenu.gone(player, request.marketId(), market)) return TradeResult.MARKET_GONE;
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != request.marketId()
				|| request.metal() < 0 || request.metal() >= METALS.size() || request.batches() <= 0 || request.batches() > MAX_BATCHES) {
			return TradeResult.INVALID_REQUEST;
		}
		if (!menu.stillValid(player)) return TradeResult.TOO_FAR;
		if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) return TradeResult.CREATIVE_NOT_ALLOWED;
		if (!market.hasFacility(FacilityKind.MINT)) return TradeResult.NO_MINT;
		Metal metal = METALS.get(request.metal());
		if (!metal.enabled()) return TradeResult.MINTING_DISABLED;
		int blocks = request.batches() * DevelopmentMath.MINT_BATCH;
		if (InventoryUtil.countPlain(player, metal.block()) < blocks) return TradeResult.INSUFFICIENT_ITEMS;
		long value = metal.mintValue(market) * request.batches();
		InventoryUtil.removePlain(player, metal.block(), blocks);
		WalletService.giveCoins(player, CoinMath.decompose(value, CoinType.valueTable()));
		minted[0] = value;
		return TradeResult.SUCCESS;
	}
}
