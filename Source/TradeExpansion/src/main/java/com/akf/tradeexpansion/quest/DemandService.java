package com.akf.tradeexpansion.quest;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DemandMath;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.QuestMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketNames;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shortages (design K): every {@code demand.durationDays} a market re-rolls up to {@code demand.maxShortages} of its
 * sellable goods whose base price is multiplied. The multiplier is applied in {@link Market#params}.
 * <p>
 * K-2 (v0.78): selling {@code demand.fulfillCount} units of a shortage good in its period fulfills it: the shortage
 * ends, and where markets develop (N) the village gets development points and vigor, and every
 * {@code demand.villagersEvery} fulfilled shortages a villager comes if a bed is free. A shortage left unfulfilled at
 * the end of its period lowers the vigor; a low vigor slightly lowers the development points ({@link Development#add}).
 */
public final class DemandService {
	/** Players this close to the market are told when a shortage is fulfilled. */
	private static final double NOTIFY_RADIUS = 128;

	private DemandService() {
	}

	private static TradeExpansionConfig.Demand config() {
		return TradeExpansionConfig.get().demand;
	}

	/** Re-rolls the shortages when a new period has started. @return {@code true} if they changed */
	public static boolean refresh(ServerLevel level, Market market, long now) {
		TradeExpansionConfig.Demand config = config();
		long period = QuestMath.period(now, config.durationDays);
		if (period == market.shortagePeriod()) return false;
		// K-2: shortages of the period that ended without being fulfilled (only once, however many periods passed).
		if (market.shortagePeriod() != Long.MIN_VALUE && config.fulfillCount > 0 && !market.shortages().isEmpty() && Development.enabled(market)) {
			market.setVigor(DemandMath.vigor(market.vigor(), -config.vigorLoss * market.shortages().size()));
		}
		market.setShortagePeriod(period);
		roll(level, market, period);
		return true;
	}

	/** Chooses new shortages for {@code period} (also used by {@code market reroll}). */
	public static void roll(ServerLevel level, Market market, long period) {
		TradeExpansionConfig.Demand config = config();
		market.clearShortages();
		List<Identifier> candidates = new ArrayList<>();
		for (Identifier goodsId : market.goods().keySet()) {
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			// Sellable goods only; tokens such as the emerald coin (unit > 1 / auto_add) are excluded.
			// Today's specials (D-5) leave the next day, so they are not short either.
			if (def != null && def.sell() && def.unit() == 1 && def.autoAdd().isEmpty() && !market.isSpecial(goodsId)) candidates.add(goodsId);
		}
		Random random = new Random(MarketFactory.seed(level, market.center()) ^ (period * 0x632BE59BD9B4E019L));
		int slots = config.maxShortages;
		if (market.hasFacility(FacilityKind.WHOLESALER)) slots += Math.max(0, TradeExpansionConfig.get().development.wholesalerExtraShortages); // v0.79
		for (int i = 0; i < slots && !candidates.isEmpty(); i++) {
			Identifier pick = candidates.remove(random.nextInt(candidates.size()));
			market.shortages().put(pick, QuestMath.shortageMultiplier(random.nextDouble(), config.minMultiplier, config.maxMultiplier));
		}
	}

	/** K-2: units still to sell to fulfill the shortage of {@code goodsId} (0 if it is not short or cannot be fulfilled). */
	public static int left(Market market, Identifier goodsId) {
		int required = config().fulfillCount;
		if (required <= 0 || !market.shortages().containsKey(goodsId)) return 0;
		return DemandMath.left(market.shortageSold().getOrDefault(goodsId, 0), required);
	}

	/**
	 * K-2: {@code player} sold {@code units} of {@code goodsId} at {@code market} (after the sale was priced and counted
	 * for reputation and development). Fulfills the shortage when enough were sold. The caller marks the registry dirty
	 * and sends the snapshots.
	 */
	public static void onSold(ServerLevel level, Market market, ServerPlayer player, Identifier goodsId, int units, long now) {
		TradeExpansionConfig.Demand config = config();
		Double multiplier = market.shortages().get(goodsId);
		if (config.fulfillCount <= 0 || multiplier == null || units <= 0) return;
		int sold = market.shortageSold().merge(goodsId, units, Integer::sum);
		if (DemandMath.left(sold, config.fulfillCount) > 0) return;

		GoodParams params = market.params(goodsId);
		double base = params == null ? 0 : params.basePrice() / multiplier; // B without the shortage
		market.shortages().remove(goodsId);
		market.shortageSold().remove(goodsId);
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		Component good = def == null ? Component.literal(goodsId.toString()) : new ItemStack(BuiltInRegistries.ITEM.getValue(def.item())).getHoverName();
		TradeExpansion.LOGGER.info("{} fulfilled the shortage of {} at market #{}", player.getPlainTextName(), goodsId, market.id());

		if (!Development.enabled(market)) { // the shortage ends, but the village does not grow here
			tell(level, market, player, Component.translatable("message.ak_trade_expansion.shortage.fulfilled", MarketNames.name(market), good)
					.withStyle(ChatFormatting.GREEN));
			return;
		}
		market.setVigor(DemandMath.vigor(market.vigor(), config.vigorGain));
		market.setShortagesFulfilled(market.shortagesFulfilled() + 1);
		double ratio = config.fulfillPointsRatio;
		if (market.hasFacility(FacilityKind.WHOLESALER)) ratio *= TradeExpansionConfig.get().development.wholesalerFulfillMultiplier; // v0.79
		double points = Development.add(market, player, DemandMath.fulfillPoints(base, config.fulfillCount, ratio), now);
		tell(level, market, player, Component.translatable("message.ak_trade_expansion.shortage.fulfilled_grow", MarketNames.name(market), good,
				(long) Math.floor(points), (long) Math.floor(market.vigor())).withStyle(ChatFormatting.GREEN));
		if (DemandMath.villagerDue(market.shortagesFulfilled(), config.villagersEvery)) villager(level, market, player);
	}

	/** K-2: a villager comes if a bed is free (not in the Nether / End, whose level counts villagers only). */
	private static void villager(ServerLevel level, Market market, ServerPlayer player) {
		if (MarketFactory.usesVillagers(level)) return;
		if (MarketFactory.countBeds(level, market.center()) <= MarketFactory.countVillagers(level, market.center())) {
			tell(level, market, player, Component.translatable("message.ak_trade_expansion.shortage.no_bed", MarketNames.name(market))
					.withStyle(ChatFormatting.YELLOW));
			return;
		}
		FacilityBuilder.spawnVillagers(level, market.center().above(), 1);
		TradeExpansion.LOGGER.info("A villager came to market #{} (shortages fulfilled: {})", market.id(), market.shortagesFulfilled());
		tell(level, market, player, Component.translatable("message.ak_trade_expansion.shortage.villager", MarketNames.name(market))
				.withStyle(ChatFormatting.GREEN));
	}

	/** The seller and the players near the market. */
	private static void tell(ServerLevel level, Market market, ServerPlayer seller, Component message) {
		seller.sendSystemMessage(message);
		for (ServerPlayer p : level.players()) {
			if (p != seller && market.horizontalDistanceTo(p.blockPosition()) <= NOTIFY_RADIUS) p.sendSystemMessage(message);
		}
	}
}
