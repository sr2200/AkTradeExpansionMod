package com.akf.tradeexpansion.config;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.currency.CoinType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes {@code config/ak_trade_expansion.json} (design chapter G) and applies it.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private ConfigManager() {
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(TradeExpansion.MOD_ID + ".json");
	}

	/** Loads the file (writing the defaults if it does not exist yet) and applies it. */
	public static TradeExpansionConfig load() {
		TradeExpansionConfig config = read(path());
		apply(config);
		return config;
	}

	/** Reads and validates a config file; a missing or broken file yields the defaults. */
	public static TradeExpansionConfig read(Path file) {
		if (!Files.exists(file)) {
			TradeExpansionConfig defaults = TradeExpansionConfig.defaults();
			write(file, defaults);
			TradeExpansion.LOGGER.info("Created {}", file);
			return defaults;
		}
		try {
			JsonElement json = JsonParser.parseString(Files.readString(file));
			migrate(json, file);
			TradeExpansionConfig config = TradeExpansionConfig.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(error -> TradeExpansion.LOGGER.error("config: {} in {}", error, file))
					.orElseGet(TradeExpansionConfig::defaults);
			return ConfigValidator.validate(config);
		} catch (IOException | RuntimeException e) {
			TradeExpansion.LOGGER.error("Could not read {}, using defaults", file, e);
			return TradeExpansionConfig.defaults();
		}
	}

	/** Old default of {@code development.siteSearchLimit} (before v0.67): it only reached about 18 blocks from the center. */
	static final int OLD_SITE_SEARCH_LIMIT = 256;

	/**
	 * Changes old default values still written in a file from an older version (the file is only rewritten when saved).
	 * v0.67: a file without {@code development.siteRadiusPerLevel} was written before it; its {@code siteSearchLimit} of
	 * 256 was the old default and becomes the new one, so that the whole site radius is searched.
	 */
	static void migrate(JsonElement json, Path file) {
		if (!json.isJsonObject() || !(json.getAsJsonObject().get("development") instanceof com.google.gson.JsonObject dev)) return;
		if (dev.has("siteRadiusPerLevel") || !(dev.get("siteSearchLimit") instanceof com.google.gson.JsonPrimitive limit)) return;
		if (!limit.isNumber() || limit.getAsDouble() != OLD_SITE_SEARCH_LIMIT) return;
		int now = new TradeExpansionConfig.Development().siteSearchLimit;
		dev.addProperty("siteSearchLimit", now);
		TradeExpansion.LOGGER.info("config: development.siteSearchLimit {} (the old default) is read as {} in {}", OLD_SITE_SEARCH_LIMIT, now, file);
	}

	public static void write(Path file, TradeExpansionConfig config) {
		try {
			JsonElement json = TradeExpansionConfig.CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow();
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(json));
		} catch (IOException | RuntimeException e) {
			TradeExpansion.LOGGER.error("Could not write {}", file, e);
		}
	}

	/** Validates, saves and applies (config screen). */
	public static void save(TradeExpansionConfig config) {
		ConfigValidator.validate(config);
		write(path(), config);
		apply(config);
	}

	/** Makes {@code config} the live settings; coin ratios follow unless locked by the world (E-4, see {@code RatioLock}). */
	public static void apply(TradeExpansionConfig config) {
		TradeExpansionConfig.set(config);
		CoinType.setRatios(config.currency.ratioArray());
		com.akf.tradeexpansion.data.GoodsCatalog.applyOverrides(); // D-2 / G: goods values of the config file
	}

	/** Deep copy through the codec (the config screen edits a copy). */
	public static TradeExpansionConfig copy(TradeExpansionConfig config) {
		JsonElement json = TradeExpansionConfig.CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow();
		return TradeExpansionConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
	}
}
