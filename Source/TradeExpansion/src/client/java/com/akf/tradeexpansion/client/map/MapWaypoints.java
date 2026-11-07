package com.akf.tradeexpansion.client.map;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Map waypoints for markets (I, v0.34). With JourneyMap the market becomes a JourneyMap waypoint (through its API, see
 * {@code compat.journeymap}); without it, the coordinates go to the player's own chat (click to copy), with Xaero's
 * waypoint share text. Uses no type of any map mod, so it loads without them.
 */
public final class MapWaypoints {
	private MapWaypoints() {
	}

	/** JourneyMap is installed and has initialised our plugin. */
	public static boolean journeyMapReady() {
		// isModLoaded first: the compat class (which uses JourneyMap's types) is only touched when JourneyMap is there.
		return FabricLoader.getInstance().isModLoaded("journeymap") && com.akf.tradeexpansion.client.compat.journeymap.JourneyMapCompat.ready();
	}

	/**
	 * Registers a market on the map.
	 *
	 * @return {@code true} if it was added to JourneyMap, {@code false} if the coordinates were written to the chat
	 */
	public static boolean register(long marketId, Component name, BlockPos pos, ResourceKey<Level> dimension) {
		if (journeyMapReady() && com.akf.tradeexpansion.client.compat.journeymap.JourneyMapCompat.addMarket(marketId, name.getString(), pos, dimension)) {
			return true;
		}
		toChat(name, pos, dimension);
		return false;
	}

	/** Stage 1: the coordinates in the player's own chat, clickable to copy, plus Xaero's share text. */
	private static void toChat(Component name, BlockPos pos, ResourceKey<Level> dimension) {
		Minecraft mc = Minecraft.getInstance();
		String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
		Component copy = Component.translatable("message.ak_trade_expansion.map.coords", name, pos.getX(), pos.getY(), pos.getZ())
				.withStyle(style -> style.withColor(ChatFormatting.GOLD)
						.withClickEvent(new ClickEvent.CopyToClipboard(coords))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.ak_trade_expansion.map.copy"))));
		if (mc.player == null) return;
		mc.player.sendSystemMessage(copy); // only in this player's own chat
		mc.player.sendSystemMessage(Component.literal(xaeroShare(name.getString(), pos, dimension)).withStyle(ChatFormatting.GRAY));
	}

	/**
	 * Xaero's Minimap waypoint share text ({@code xaero-waypoint:name:initials:x:y:z:color:false:0:set}). Not an official
	 * API; Xaero's turns such chat lines into an "add waypoint" link.
	 */
	static String xaeroShare(String name, BlockPos pos, ResourceKey<Level> dimension) {
		String clean = name.replace(':', ' ').trim();
		String initials = clean.isEmpty() ? "M" : clean.substring(0, Math.min(1, clean.length()));
		String set = dimension.equals(Level.NETHER) ? "Internal-the-nether-waypoints"
				: dimension.equals(Level.END) ? "Internal-the-end-waypoints" : "Internal-overworld-waypoints";
		return "xaero-waypoint:" + clean + ":" + initials + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ() + ":6:false:0:" + set;
	}
}
