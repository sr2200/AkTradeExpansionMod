package com.akf.tradeexpansion.item;

import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.quest.Cargo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Cargo crate (design J-2, v0.35): the goods of a transport request, taken at one market (deposit paid) and delivered at
 * another (reward + deposit). It cannot be opened or crafted; all its data is the {@code cargo} component.
 */
public class CargoCrateItem extends Item {
	public CargoCrateItem(Properties properties) {
		super(properties);
	}

	/**
	 * Tooltip lines, added on the client through Fabric's ItemTooltipCallback (E-1).
	 *
	 * @param now the market clock on the client, or {@code null} if unknown
	 */
	public void addTooltip(ItemStack stack, Long now, Consumer<Component> tooltip) {
		Cargo cargo = Cargo.of(stack);
		if (cargo == null) {
			tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.cargo.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		ItemStack goods = new ItemStack(BuiltInRegistries.ITEM.getValue(cargo.item()));
		tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.cargo.contents", goods.getHoverName(), cargo.quantity())
				.withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.cargo.to", cargo.destinationName()).withStyle(ChatFormatting.GOLD));
		Component where = Component.translatable("tooltip.ak_trade_expansion.cargo.where", cargo.destinationCenter().getX(),
				cargo.destinationCenter().getZ());
		if (cargo.destinationDimension().equals(Level.NETHER)) {
			where = Component.translatable("tooltip.ak_trade_expansion.cargo.where_nether", cargo.destinationCenter().getX(),
					cargo.destinationCenter().getZ());
		}
		tooltip.accept(where.copy().withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.cargo.pay", CoinItem.formatCopper(cargo.reward()),
				CoinItem.formatCopper(cargo.deposit())).withStyle(ChatFormatting.YELLOW));
		tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.cargo.from", cargo.sourceName()).withStyle(ChatFormatting.DARK_GRAY));
		if (now == null) return;
		if (cargo.expired(now)) {
			tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.cargo.expired").withStyle(ChatFormatting.RED));
		} else {
			long minutes = Math.max(1, (long) Math.ceil((cargo.deadline() - now) / (double) PriceModel.TICKS_PER_DAY * 20));
			tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.cargo.time_left", minutes).withStyle(ChatFormatting.AQUA));
		}
	}
}
