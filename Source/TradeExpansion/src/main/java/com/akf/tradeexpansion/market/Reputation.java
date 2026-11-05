package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.ReputationMath;
import com.akf.tradeexpansion.entity.TraderEntity;
import com.akf.tradeexpansion.quest.Request;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.UUID;

/**
 * Reputation of each player with each market (design M, Q39). Raised by trading at the market, delivering its requests,
 * selling goods it is short of and trading with villagers nearby; lowered over time, by hurting villagers or traders
 * nearby and by letting accepted requests expire. Higher levels give better prices (per player), higher request rewards,
 * 3-star requests and extra specials.
 */
public final class Reputation {
	private Reputation() {
	}

	private static TradeExpansionConfig.Reputation config() {
		return TradeExpansionConfig.get().reputation;
	}

	/** Points of {@code player} with {@code market} at {@code now}, decay applied (and stored). */
	public static double points(Market market, UUID player, long now) {
		Market.Standing standing = market.standing(player);
		if (standing == null || !config().enabled) return 0;
		double days = (now - standing.since()) / (double) PriceModel.TICKS_PER_DAY;
		if (days <= 0) return standing.points();
		double decayed = ReputationMath.decay(standing.points(), days, config().halfLifeDays);
		market.setStanding(new Market.Standing(player, decayed, now));
		return decayed;
	}

	/** Adds (or with a negative {@code delta} removes) points; never below 0. The caller marks the registry dirty. */
	public static void add(Market market, UUID player, double delta, long now) {
		if (!config().enabled || delta == 0) return;
		double points = Math.max(0, points(market, player, now) + delta);
		market.setStanding(new Market.Standing(player, points, now));
	}

	public static int level(Market market, UUID player, long now) {
		if (!config().enabled) return 0;
		return ReputationMath.level(points(market, player, now), config().levelThresholdArray());
	}

	/** Price bonus d of a level (buy × (1 − d), sell × (1 + d); capped per good by {@link ReputationMath#safeBonus}). */
	public static double priceBonus(int level) {
		return level * config().priceBonusPerLevel;
	}

	public static double rewardBonus(int level) {
		return level * config().rewardBonusPerLevel;
	}

	/** Params of a good as {@code player} trades it (price bonus applied), or {@code null} if the market has no such good. */
	public static GoodParams params(Market market, UUID player, Identifier goodsId, long now) {
		GoodParams params = market.params(goodsId);
		if (params == null) return null;
		return ReputationMath.withBonus(params, priceBonus(level(market, player, now)));
	}

	/** Bonus specials are shown only from {@code reputation.specialBonusLevel}. */
	public static boolean canSee(Market market, UUID player, Identifier goodsId, long now) {
		return !market.bonusSpecials().contains(goodsId) || level(market, player, now) >= config().specialBonusLevel;
	}

	/** 3-star requests need {@code reputation.hardQuestLevel}. */
	public static boolean canDeliver(Market market, UUID player, Request request, long now) {
		return request.difficulty() < 3 || level(market, player, now) >= config().hardQuestLevel;
	}

	/** Reward of a request for {@code player} (reward bonus applied). */
	public static long reward(Market market, UUID player, Request request, long now) {
		return Math.round(request.reward() * (1 + rewardBonus(level(market, player, now))));
	}

	/** Points for a trade of {@code total} copper ({@code shortage}: selling a good the market is short of). */
	public static void onTrade(Market market, UUID player, long total, boolean shortage, long now) {
		double points = total * config().pointsPerCoin * (shortage ? config().shortageMultiplier : 1);
		add(market, player, points, now);
	}

	/** Points for a delivered request. */
	public static void onDeliver(Market market, UUID player, Request request, long now) {
		int index = Math.clamp(request.difficulty() - 1, 0, config().questPoints.size() - 1);
		double points = config().questPoints.isEmpty() ? 0 : config().questPoints.get(index);
		if (request.accepted().contains(player)) points *= config().acceptedQuestMultiplier;
		add(market, player, points, now);
	}

	/** Accepted request expired undelivered: every player who accepted it loses points. */
	public static void onExpired(Market market, Request request, long now) {
		for (UUID player : request.accepted()) add(market, player, -config().questFailPenalty * request.difficulty(), now);
	}

	/** The market a villager or trader at {@code pos} belongs to: the nearest within the level radius, or {@code null}. */
	public static Market marketNear(ServerLevel level, BlockPos pos) {
		return MarketRegistry.get(level.getServer()).nearestWithin(level.dimension(), pos, TradeExpansionConfig.get().market.levelRadius);
	}

	/** A trade with a villager (any trade, coins or emeralds) raises the reputation with the nearby market. */
	public static void onVillagerTrade(ServerPlayer player, Villager villager) {
		if (!config().enabled) return;
		ServerLevel level = (ServerLevel) villager.level();
		Market market = marketNear(level, villager.blockPosition());
		if (market == null) return;
		add(market, player.getUUID(), config().villagerTradePoints, MarketClock.now(level));
		MarketRegistry.get(level.getServer()).setDirty();
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0) penalize(entity, source, config().attackPenalty);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> penalize(entity, source, config().killPenalty));
	}

	/** Hurting or killing a villager or trader lowers the attacker's reputation with the nearby market. */
	private static void penalize(LivingEntity entity, DamageSource source, double penalty) {
		if (!config().enabled || penalty <= 0) return;
		if (!(entity instanceof Villager) && !(entity instanceof TraderEntity)) return;
		if (!(source.getEntity() instanceof ServerPlayer player) || !(entity.level() instanceof ServerLevel level)) return;
		Market market = marketNear(level, entity.blockPosition());
		if (market == null) return;
		add(market, player.getUUID(), -penalty, MarketClock.now(level));
		MarketRegistry.get(level.getServer()).setDirty();
	}
}
