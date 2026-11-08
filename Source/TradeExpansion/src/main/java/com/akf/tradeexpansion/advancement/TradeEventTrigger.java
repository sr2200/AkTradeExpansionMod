package com.akf.tradeexpansion.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/**
 * Criterion trigger {@code ak_trade_expansion:trade_event} (design R): one trigger for every economy event. The server
 * reports an event name with a value; a criterion matches when the name is equal and the value is at least {@code min}.
 * <pre>{"trigger": "ak_trade_expansion:trade_event", "conditions": {"event": "trade", "min": 1000}}</pre>
 */
public final class TradeEventTrigger extends SimpleCriterionTrigger<TradeEventTrigger.Instance> {
	@Override
	public Codec<Instance> codec() {
		return Instance.CODEC;
	}

	public void trigger(ServerPlayer player, String event, long value) {
		trigger(player, instance -> instance.event().equals(event) && value >= instance.min());
	}

	public record Instance(Optional<Holder<LootItemCondition>> player, String event, long min) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
				LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
				Codec.STRING.fieldOf("event").forGetter(Instance::event),
				Codec.LONG.optionalFieldOf("min", 0L).forGetter(Instance::min)
		).apply(i, Instance::new));
	}
}
