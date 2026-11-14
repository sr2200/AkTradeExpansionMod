package com.akf.tradeexpansion.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Writes the results of one run (server or client) to build/autotest/&lt;side&gt;-results.json, read by
 * tools/checklist/record.js. The run directories are build/run/gameTest and build/run/clientGameTest.
 */
public final class AutotestReport {
	public static final Logger LOGGER = LoggerFactory.getLogger("AKTradeExpansion/autotest");

	private AutotestReport() {
	}

	/** build/autotest (the parent of the run directories is build/run). */
	public static Path directory() {
		return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().getParent().getParent().resolve("autotest");
	}

	public static void write(String side, List<CaseResult> results) {
		JsonObject root = new JsonObject();
		root.addProperty("side", side);
		root.addProperty("version", FabricLoader.getInstance().getModContainer("ak_trade_expansion")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
		root.addProperty("finished", OffsetDateTime.now().toString());
		JsonArray cases = new JsonArray();
		for (CaseResult r : results) {
			JsonObject c = new JsonObject();
			c.addProperty("name", r.name());
			JsonArray ids = new JsonArray();
			r.checklist().forEach(ids::add);
			c.add("checklist", ids);
			if (r.partial() != null) c.addProperty("partial", r.partial());
			c.addProperty("ok", r.ok());
			if (r.message() != null) c.addProperty("message", r.message());
			JsonArray shots = new JsonArray();
			r.screenshots().forEach(shots::add);
			c.add("screenshots", shots);
			cases.add(c);
		}
		root.add("cases", cases);
		Path file = directory().resolve(side + "-results.json");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
		long failed = results.stream().filter(r -> !r.ok()).count();
		LOGGER.info("Autotest {}: {} cases, {} failed. Results: {}", side, results.size(), failed, file);
		for (CaseResult r : results) {
			if (!r.ok()) LOGGER.error("FAILED {} {}: {}", r.checklist(), r.name(), r.message());
		}
	}
}
