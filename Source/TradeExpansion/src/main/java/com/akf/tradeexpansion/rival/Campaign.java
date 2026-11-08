package com.akf.tradeexpansion.rival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * One move of a rival guild (design O-3): the guild buys up ({@code buying}, prices rise) or sells off one good around a
 * center market, from {@code startDay} to {@code endDay} (exclusive), market-clock days.
 *
 * @param impact m: on the last day the center market's price is ×(1 + m) (buying) or ÷(1 + m) (selling)
 */
public record Campaign(int id, Identifier guild, Identifier goods, long market, boolean buying, long startDay, long endDay, double impact) {
	public static final Codec<Campaign> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Campaign::id),
			Identifier.CODEC.fieldOf("guild").forGetter(Campaign::guild),
			Identifier.CODEC.fieldOf("goods").forGetter(Campaign::goods),
			Codec.LONG.fieldOf("market").forGetter(Campaign::market),
			Codec.BOOL.fieldOf("buying").forGetter(Campaign::buying),
			Codec.LONG.fieldOf("start_day").forGetter(Campaign::startDay),
			Codec.LONG.fieldOf("end_day").forGetter(Campaign::endDay),
			Codec.DOUBLE.fieldOf("impact").forGetter(Campaign::impact)
	).apply(i, Campaign::new));

	public int days() {
		return (int) Math.max(1, endDay - startDay);
	}

	public boolean activeOn(long day) {
		return day >= startDay && day < endDay;
	}

	/** O-5: days left after today (0 = today is the last day). */
	public int daysLeft(long today) {
		return (int) Math.max(0, endDay - 1 - today);
	}
}
