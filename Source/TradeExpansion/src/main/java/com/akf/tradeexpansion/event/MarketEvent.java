package com.akf.tradeexpansion.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * One market event (design V-3): the base price of {@code goods} is multiplied around a center market from
 * {@code startDay} to {@code endDay} (exclusive), market-clock days. It is rolled the day before it starts (the forecast).
 *
 * @param event      the definition ({@code data/<ns>/ak_trade_expansion/market_event/<name>.json})
 * @param goods      the goods it moves, chosen when it was rolled
 * @param multiplier m: the multiplier of B at the center at full strength
 */
public record MarketEvent(int id, Identifier event, long market, List<Identifier> goods, long startDay, long endDay, double multiplier) {
	public static final Codec<MarketEvent> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(MarketEvent::id),
			Identifier.CODEC.fieldOf("event").forGetter(MarketEvent::event),
			Codec.LONG.fieldOf("market").forGetter(MarketEvent::market),
			Identifier.CODEC.listOf().optionalFieldOf("goods", List.of()).forGetter(MarketEvent::goods),
			Codec.LONG.fieldOf("start_day").forGetter(MarketEvent::startDay),
			Codec.LONG.fieldOf("end_day").forGetter(MarketEvent::endDay),
			Codec.DOUBLE.fieldOf("multiplier").forGetter(MarketEvent::multiplier)
	).apply(i, MarketEvent::new));

	public boolean raises() {
		return multiplier > 1;
	}

	public boolean activeOn(long day) {
		return day >= startDay && day < endDay;
	}

	/** Running on {@code day} or starting the next day (the forecast). */
	public boolean currentOrForecast(long day) {
		return day + 1 >= startDay && day < endDay;
	}

	/** Days in the same period: overlapping running time. */
	public boolean overlaps(long start, long end) {
		return start < endDay && startDay < end;
	}

	/** Days left after today (0 = today is the last day). */
	public int daysLeft(long today) {
		return (int) Math.max(0, endDay - 1 - today);
	}

	public int days() {
		return (int) Math.max(1, endDay - startDay);
	}
}
