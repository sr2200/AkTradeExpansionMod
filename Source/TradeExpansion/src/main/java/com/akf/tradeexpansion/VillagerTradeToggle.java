package com.akf.tradeexpansion;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/** {@code vanilla.disableVillagerTrades} (design A-2, Q5): villagers and wandering traders refuse to trade. */
public final class VillagerTradeToggle {
	private VillagerTradeToggle() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				entity instanceof AbstractVillager && TradeExpansionConfig.get().vanilla.disableVillagerTrades
						? InteractionResult.FAIL : InteractionResult.PASS);
	}
}
