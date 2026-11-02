package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.currency.CoinType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TradeExpansion.id("main"));

	private ModCreativeTab() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.trade_expansion"))
				.icon(() -> new ItemStack(ModItems.GOLD_COIN))
				.displayItems((parameters, output) -> {
					output.accept(ModBlocks.MARKET_STALL);
					for (CoinType type : CoinType.values()) {
						output.accept(ModItems.coin(type));
					}
				})
				.build());
	}
}
