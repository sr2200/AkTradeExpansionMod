package com.akf.tradeexpansion.weather;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.WeatherMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.RainGoods;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.quest.Cargo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * The weather moves the market (design U, v0.82): the overworld's rain is counted per game day; in a rainy spell the
 * rain goods (crops) of overworld markets get cheaper (a multiplier of B, copied to a market when it is touched), and a
 * cargo crate delivered at an overworld market while it thunders pays more.
 */
public final class WeatherService {
	/** Ticks between two samples of the weather. */
	static final int SAMPLE_TICKS = 20;

	private static int tickCounter;
	/** Last state seen by {@link #sample}, to tell the players when it changes ({@code null} = not known yet). */
	private static Boolean lastRainy;
	private static Boolean lastThundering;

	private WeatherService() {
	}

	private static TradeExpansionConfig.Weather config() {
		return TradeExpansionConfig.get().weather;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> reset());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter % SAMPLE_TICKS == 0) sample(server, SAMPLE_TICKS);
		});
	}

	static void reset() {
		lastRainy = null;
		lastThundering = null;
	}

	/** Adds {@code ticks} of the overworld's current weather to today's record and tells the players about changes. */
	public static void sample(MinecraftServer server, int ticks) {
		ServerLevel overworld = server.overworld();
		MarketRegistry registry = MarketRegistry.get(server);
		long today = WeatherMath.day(overworld.getGameTime());
		Map<Long, Integer> rain = registry.rain();
		boolean changed = rain.keySet().removeIf(day -> !WeatherMath.inWindow(day, today));
		if (overworld.isRaining()) {
			rain.merge(today, ticks, Integer::sum);
			changed = true;
		}
		if (changed) registry.setDirty();
		boolean rainy = config().enabled && rainySpell(server);
		boolean thundering = config().enabled && overworld.isThundering();
		if (lastRainy != null && rainy != lastRainy) announceRain(overworld, rainy);
		if (lastThundering != null && thundering && !lastThundering) announceStorm(overworld);
		lastRainy = rainy;
		lastThundering = thundering;
	}

	/** Days of rain today and the 2 days before (game days of the overworld). */
	public static double rainDays(MinecraftServer server) {
		return WeatherMath.rainDays(MarketRegistry.get(server).rain(), WeatherMath.day(server.overworld().getGameTime()));
	}

	public static boolean rainySpell(MinecraftServer server) {
		return WeatherMath.rainySpell(rainDays(server), config().rainDays);
	}

	/** Whether the weather reaches this dimension (only the overworld has the weather that is counted). */
	public static boolean hasWeather(Market market) {
		return market.dimension().equals(Level.OVERWORLD);
	}

	/** The multiplier of B of the rain goods at this market now. */
	public static double rainMultiplier(MinecraftServer server, Market market) {
		if (!config().enabled || !hasWeather(market)) return 1.0;
		return WeatherMath.rainMultiplier(rainySpell(server), config().rainMultiplier);
	}

	/** Copies the rain multiplier to the market (before its prices are used). @return {@code true} if it changed */
	public static boolean apply(MinecraftServer server, Market market) {
		double m = rainMultiplier(server, market);
		if (m == market.rainMultiplier()) return false;
		market.setRainMultiplier(m);
		return true;
	}

	/** Multiplier of the reward of a cargo crate delivered at {@code market} now (1 = no bonus). */
	public static double stormMultiplier(MinecraftServer server, Market market) {
		if (!config().enabled || !hasWeather(market) || !server.overworld().isThundering()) return 1.0;
		return Math.max(1.0, config().stormRewardMultiplier);
	}

	private static void announceRain(ServerLevel overworld, boolean rainy) {
		if (!config().notifyChat || RainGoods.all().isEmpty()) return;
		Component message = rainy
				? Component.translatable("message.ak_trade_expansion.weather.rain_start", goodsNames(), multiplier(config().rainMultiplier))
						.withStyle(ChatFormatting.AQUA)
				: Component.translatable("message.ak_trade_expansion.weather.rain_end").withStyle(ChatFormatting.GRAY);
		for (ServerPlayer player : overworld.players()) player.sendSystemMessage(message);
	}

	private static void announceStorm(ServerLevel overworld) {
		if (!config().notifyChat || config().stormRewardMultiplier <= 1.0) return;
		Component message = Component.translatable("message.ak_trade_expansion.weather.storm_start", multiplier(config().stormRewardMultiplier))
				.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : overworld.players()) {
			if (carriesCargo(player)) player.sendSystemMessage(message);
		}
	}

	private static boolean carriesCargo(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (Cargo.of(inventory.getItem(i)) != null) return true;
		}
		return false;
	}

	/** "小麦・ニンジンなど" (the first two rain goods). */
	private static Component goodsNames() {
		MutableComponent names = Component.empty();
		int n = 0;
		for (var id : RainGoods.all()) {
			GoodsDefinition def = GoodsCatalog.get(id);
			if (def == null) continue;
			if (n == 2) return Component.translatable("message.ak_trade_expansion.weather.goods_more", names);
			if (n > 0) names.append(Component.translatable("message.ak_trade_expansion.weather.goods_separator"));
			names.append(new ItemStack(BuiltInRegistries.ITEM.getValue(def.item())).getHoverName());
			n++;
		}
		return names;
	}

	/** "×0.85". */
	public static String multiplier(double m) {
		return "×" + (m == Math.rint(m) ? String.valueOf((long) m) : String.valueOf(Math.round(m * 100) / 100.0));
	}
}
