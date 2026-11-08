package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.network.PriceLookupC2S;
import com.akf.tradeexpansion.network.PriceLookupS2C;
import com.akf.tradeexpansion.network.TradableItemsS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * I (v0.47): what the recipe viewer pages (JEI / REI) show. The items the server trades, and the prices of an item at
 * the nearest markets, asked from the server while a page is shown and refreshed every few seconds. Uses no type of any
 * recipe viewer, so it is always loaded.
 */
public final class PriceLookupCache {
	/** A shown page asks again after this long. */
	private static final long REFRESH_MS = 5_000;
	/** No answer after this long (the server dropped a too-fast request): ask again. */
	private static final long RETRY_MS = 1_500;

	private record Cached(@Nullable PriceLookupS2C result, long receivedAt, long requestedAt) {
	}

	private static Set<Identifier> tradable = Set.of();
	private static final Map<Identifier, Cached> PRICES = new HashMap<>();
	private static final List<Consumer<Set<Identifier>>> LISTENERS = new ArrayList<>();

	private PriceLookupCache() {
	}

	/** Items with a market page (empty on a server without the mod). */
	public static Set<Identifier> tradable() {
		return tradable;
	}

	/** Called with the new set whenever it changes (on the client thread). */
	public static void onTradableChanged(Consumer<Set<Identifier>> listener) {
		LISTENERS.add(listener);
	}

	static void setTradable(TradableItemsS2C payload) {
		tradable = Set.copyOf(payload.items());
		PRICES.clear();
		notifyListeners();
	}

	static void put(PriceLookupS2C payload) {
		Cached old = PRICES.get(payload.item());
		PRICES.put(payload.item(), new Cached(payload, Util.getMillis(), old == null ? 0 : old.requestedAt()));
	}

	/**
	 * The latest prices of an item, asking the server when there are none yet or they are a few seconds old. Call it
	 * while the page is drawn.
	 *
	 * @return the last answer (possibly a few seconds old), or {@code null} while waiting for the first one
	 */
	public static @Nullable PriceLookupS2C get(Identifier item) {
		long now = Util.getMillis();
		Cached cached = PRICES.get(item);
		boolean stale = cached == null || cached.result() == null || now - cached.receivedAt() >= REFRESH_MS;
		boolean waiting = cached != null && cached.requestedAt() > cached.receivedAt() && now - cached.requestedAt() < RETRY_MS;
		if (stale && !waiting && ClientPlayNetworking.canSend(PriceLookupC2S.TYPE)) {
			ClientPlayNetworking.send(new PriceLookupC2S(item));
			cached = new Cached(cached == null ? null : cached.result(), cached == null ? 0 : cached.receivedAt(), now);
			PRICES.put(item, cached);
		}
		return cached == null ? null : cached.result();
	}

	static void clear() {
		PRICES.clear();
		if (!tradable.isEmpty()) {
			tradable = Set.of();
			notifyListeners();
		}
	}

	private static void notifyListeners() {
		for (Consumer<Set<Identifier>> listener : List.copyOf(LISTENERS)) listener.accept(tradable);
	}
}
