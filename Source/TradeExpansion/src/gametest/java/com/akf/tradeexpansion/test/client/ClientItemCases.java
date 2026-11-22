package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.quest.Cargo;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModCreativeTab;
import com.akf.tradeexpansion.registry.ModDataComponents;
import com.akf.tradeexpansion.registry.ModEntities;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the mod's items (checklist 1-2, 1-4, 1-5, 21-2 and 21-8): the row in Mod Menu's list, the creative
 * tab, and the tooltips of the coins, the wallet, the card and the cargo crate as a player sees them in the inventory.
 */
final class ClientItemCases {
	private ClientItemCases() {
	}

	private static final String MOD_NAME = "AK Trade Expansion";
	private static final int RED = 0xFF5555;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** Calls a public method of an object of a class the test cannot compile against (Mod Menu's). */
	private static Object invoke(Object target, String method) {
		try {
			Method m = target.getClass().getMethod(method);
			m.setAccessible(true);
			return m.invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot call " + target.getClass().getSimpleName() + "." + method, e);
		}
	}

	// ---- 1-2 the row in Mod Menu's list

	/**
	 * 1-2: Mod Menu's list (opened from the title screen, searched for "AK Trade") has the row "AK Trade Expansion" with
	 * the mod's own icon (not Mod Menu's "unknown" icon).
	 */
	static void modList(Session s, CaseResult.Builder result) throws Exception {
		ClientGameTestContext context = s.context;
		Class<?> modsScreen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
		Class<?> entryClass = Class.forName("com.terraformersmc.modmenu.gui.widget.entries.ModListEntry");
		Identifier unknown = (Identifier) get(null, entryClass, "UNKNOWN_ICON");
		context.setScreen(() -> {
			try {
				return (Screen) modsScreen.getConstructor(Screen.class).newInstance(new TitleScreen());
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot open Mod Menu's list", e);
			}
		});
		context.waitTicks(5);
		context.runOnClient(client -> ((EditBox) get(client.gui.screen(), modsScreen, "searchBox")).setValue("AK Trade"));
		context.waitTicks(5);
		Object entry = context.computeOnClient(client -> {
			Object list = get(client.gui.screen(), modsScreen, "modList");
			for (Object e : (List<?>) invoke(list, "children")) {
				if ("ak_trade_expansion".equals(invoke(invoke(e, "getMod"), "getId"))) return e;
			}
			return null;
		});
		check(entry != null, "Mod Menu's list has no row of ak_trade_expansion");
		// Mod Menu loads the icon when the row is drawn.
		Identifier icon = context.computeOnClient(client -> (Identifier) invoke(entry, "getIconTexture"));
		check(icon != null && !icon.equals(unknown), "the row of the mod has the icon %s (Mod Menu's unknown icon is %s)", icon, unknown);
		boolean loaded = context.computeOnClient(client -> client.getTextureManager().getTexture(icon) != null);
		check(loaded, "the icon %s of the row is not a loaded texture", icon);
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		check(texts.stream().anyMatch(t -> t.text().equals(MOD_NAME)), "Mod Menu's list does not show \"%s\": %s", MOD_NAME, strings(texts));
		screenshot(context, result, "1-2_mod_list");
	}

	/**
	 * 1-6: Mod Menu without YACL: the title screen comes up, Mod Menu's list has the row of the mod, and the mod gives
	 * Mod Menu no config screen (so its row has no config button) instead of touching YACL's classes.
	 */
	static void modMenuWithoutYacl(Session s, CaseResult.Builder result) throws Exception {
		ClientGameTestContext context = s.context;
		check(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu"), "Mod Menu is not loaded");
		check(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3"), "YACL is loaded (the run should leave it out)");
		context.waitForScreen(TitleScreen.class);
		modList(s, result);
		Class<?> modMenu = Class.forName("com.terraformersmc.modmenu.ModMenu");
		Object config = context.computeOnClient(client -> {
			try {
				return modMenu.getMethod("getConfigScreen", String.class, Screen.class).invoke(null, "ak_trade_expansion", client.gui.screen());
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot ask Mod Menu for the config screen", e);
			}
		});
		check(config == null, "without YACL the mod gives Mod Menu the config screen %s", config);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.gui.screen()) != null, "Mod Menu's list closed");
		screenshot(context, result, "1-6_mod_list_without_yacl");
	}

	// ---- 1-4 the creative tab

	/**
	 * 1-4: in creative mode, the inventory key opens the creative inventory; on its page, clicking the tab "AK Trade
	 * Expansion" selects it; it holds the stall, the five coins, the emerald coin, the wallet and the trader's spawn egg
	 * (in this order), its title is drawn, and each item's tooltip shows its translated name.
	 */
	static void creativeTab(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		s.world.getServer().runCommand("gamemode creative @a");
		context.waitTicks(5);
		try {
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(CreativeModeInventoryScreen.class);
			context.waitTicks(3);
			CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) context.computeOnClient(client -> client.gui.screen());
			CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(ModCreativeTab.KEY);
			// Fabric puts the mod tabs on more pages: go to the page of the tab, then click the tab as a player does.
			FabricCreativeModeInventoryScreen pages = (FabricCreativeModeInventoryScreen) (Object) screen;
			context.runOnClient(client -> pages.switchToPage(pages.getPage(tab)));
			context.waitTicks(2);
			double[] at = context.computeOnClient(client -> {
				int x = leftPos(screen) + (Integer) call(screen, "getTabX", tab) + 13;
				int imageHeight = (Integer) get(screen, AbstractContainerScreen.class, "imageHeight");
				int y = tab.row() == CreativeModeTab.Row.TOP ? topPos(screen) - 16 : topPos(screen) + imageHeight + 16;
				return new double[] {x, y};
			});
			clickAt(context, at[0], at[1]);
			context.waitTicks(3);
			check(context.computeOnClient(client -> pages.getSelectedTab()) == tab, "clicking the tab at %.0f,%.0f selected %s",
					at[0], at[1], context.computeOnClient(client -> pages.getSelectedTab().getDisplayName().getString()));
			mouseAway(context);

			List<Item> expected = new ArrayList<>();
			expected.add(ModBlocks.MARKET_STALL.asItem());
			expected.add(ModBlocks.SIGNPOST.asItem());
			for (CoinType type : CoinType.values()) expected.add(ModItems.coin(type));
			expected.add(ModItems.EMERALD_COIN);
			expected.add(ModItems.WALLET);
			expected.add(ModEntities.TRADER_SPAWN_EGG);
			List<Item> shown = context.computeOnClient(client -> screen.getMenu().items.stream().filter(st -> !st.isEmpty()).map(ItemStack::getItem).toList());
			check(shown.equals(expected), "the creative tab holds %s, not %s", shown, expected);
			String title = text("itemGroup.ak_trade_expansion");
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			check(texts.stream().anyMatch(t -> t.text().equals(title)), "the creative inventory does not show the title \"%s\": %s", title, strings(texts));
			screenshot(context, result, "1-4_creative_tab");

			for (int i = 0; i < expected.size(); i++) {
				int index = i;
				double[] slotAt = context.computeOnClient(client -> {
					Slot slot = screen.getMenu().slots.get(index);
					return new double[] {leftPos(screen) + slot.x + 8, topPos(screen) + slot.y + 8};
				});
				moveTo(context, slotAt[0], slotAt[1]);
				context.waitTicks(2);
				String name = new ItemStack(expected.get(i)).getHoverName().getString();
				List<TextRecorder.Text> tip = TextRecorder.capture(context);
				check(LayoutCheck.untranslated(name) == null && !name.contains("ak_trade_expansion"), "the name of %s is not translated: %s", expected.get(i), name);
				check(tip.stream().anyMatch(t -> t.text().equals(name)), "the tooltip of slot %d does not show \"%s\": %s", i, name, strings(tip));
			}
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runCommand("gamemode survival @a");
			context.waitTicks(3);
		}
	}

	// ---- the inventory screen

	/** Opens the survival inventory with the inventory key. */
	private static InventoryScreen openInventory(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(3);
		return (InventoryScreen) context.computeOnClient(client -> client.gui.screen());
	}

	/** Puts the mouse on the slot showing slot {@code index} of the player's inventory and returns the texts drawn. */
	private static List<TextRecorder.Text> hover(ClientGameTestContext context, AbstractContainerScreen<?> screen, int index) {
		double[] at = context.computeOnClient(client -> {
			for (Slot slot : screen.getMenu().slots) {
				if (slot.container instanceof Inventory && slot.getContainerSlot() == index) {
					return new double[] {leftPos(screen) + slot.x + 8, topPos(screen) + slot.y + 8};
				}
			}
			return null;
		});
		check(at != null, "the inventory screen has no slot for inventory slot %d", index);
		moveTo(context, at[0], at[1]);
		context.waitTicks(2);
		Slot hovered = context.computeOnClient(client -> (Slot) get(screen, AbstractContainerScreen.class, "hoveredSlot"));
		check(hovered != null && hovered.container instanceof Inventory && hovered.getContainerSlot() == index,
				"the mouse at %.0f,%.0f is over %s, not inventory slot %d (holding %s)", at[0], at[1],
				hovered == null ? "no slot" : hovered.container.getClass().getSimpleName() + " " + hovered.getContainerSlot() + " " + hovered.getItem(),
				index, context.computeOnClient(client -> client.player.getInventory().getItem(index)));
		return TextRecorder.capture(context);
	}

	private static void hasLine(List<TextRecorder.Text> texts, String what, String line) {
		check(texts.stream().anyMatch(t -> t.text().equals(line)), "the tooltip of %s has no line \"%s\": %s", what, line, strings(texts));
	}

	/** Empties the inventory and puts {@code stacks} into the main inventory from slot 9 on. */
	private static void fill(Session s, List<ItemStack> stacks) {
		s.world.getServer().runOnServer(server -> {
			// The cases close screens with setScreen(null), which does not tell the server: it would still send the
			// inventory to the menu of the last trade screen, which the client no longer has.
			player(server).closeContainer();
			Inventory inventory = player(server).getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) inventory.setItem(slot, ItemStack.EMPTY);
			for (int i = 0; i < stacks.size(); i++) inventory.setItem(9 + i, stacks.get(i).copy());
			inventory.setChanged();
		});
		Item first = stacks.getFirst().getItem();
		boolean arrived = waitClient(s.context, client -> client.player.getInventory().getItem(9).is(first), 40);
		check(arrived, "the client's inventory slot 9 holds %s, not %s", s.context.computeOnClient(client -> client.player.getInventory().getItem(9)), first);
		s.context.waitTicks(2);
	}

	// ---- 1-5 the tooltips of the coins, the wallet and the card

	/**
	 * 1-5: in the inventory, a copper coin shows its value; 10 gold coins their value and the stack's total, and how to
	 * break them; the emerald coin that it sells in tens; a wallet its balance and capacity; the player's card its owner
	 * and number, and a card without an owner "未登録のカード".
	 */
	static void tooltips(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		List<ItemStack> saved = ClientBuySellCases.saveInventory(s);
		String owner = s.world.getServer().computeOnServer(server -> player(server).getPlainTextName());
		try {
			ItemStack wallet = new ItemStack(ModItems.WALLET);
			WalletItem.setBalance(wallet, 12_345);
			ItemStack card = new ItemStack(ModItems.BANK_CARD);
			java.util.UUID id = s.world.getServer().computeOnServer(server -> player(server).getUUID());
			card.set(ModDataComponents.CARD_OWNER, new ModDataComponents.CardOwner(id, owner, 7));
			fill(s, List.of(new ItemStack(ModItems.COPPER_COIN), new ItemStack(ModItems.GOLD_COIN, 10), new ItemStack(ModItems.EMERALD_COIN, 3),
					wallet, card, new ItemStack(ModItems.BANK_CARD)));
			InventoryScreen screen = openInventory(context);

			List<TextRecorder.Text> copper = hover(context, screen, 9);
			hasLine(copper, "a copper coin", text("tooltip.ak_trade_expansion.coin_value", CoinItem.formatCopper(CoinType.COPPER.value())));
			check(copper.stream().noneMatch(t -> t.text().equals(text("tooltip.ak_trade_expansion.coin_stack_value", CoinItem.formatCopper(CoinType.COPPER.value())))),
					"one copper coin shows a stack total");
			screenshot(context, result, "1-5_copper_coin");

			List<TextRecorder.Text> gold = hover(context, screen, 10);
			hasLine(gold, "10 gold coins", text("tooltip.ak_trade_expansion.coin_value", CoinItem.formatCopper(CoinType.GOLD.value())));
			hasLine(gold, "10 gold coins", text("tooltip.ak_trade_expansion.coin_stack_value", CoinItem.formatCopper(CoinType.GOLD.value() * 10)));
			hasLine(gold, "10 gold coins", text("tooltip.ak_trade_expansion.coin_break_hint"));
			screenshot(context, result, "1-5_gold_coins");

			hasLine(hover(context, screen, 11), "the emerald coin", text("tooltip.ak_trade_expansion.emerald_coin"));

			List<TextRecorder.Text> w = hover(context, screen, 12);
			hasLine(w, "the wallet", text("tooltip.ak_trade_expansion.wallet_balance", CoinItem.formatCopper(12_345), CoinItem.formatCopper(WalletItem.capacity())));
			hasLine(w, "the wallet", text("tooltip.ak_trade_expansion.wallet_hint"));
			screenshot(context, result, "1-5_wallet");

			List<TextRecorder.Text> c = hover(context, screen, 13);
			hasLine(c, "the card", text("tooltip.ak_trade_expansion.card_owner", owner, 7));
			screenshot(context, result, "1-5_card");
			List<TextRecorder.Text> blank = hover(context, screen, 14);
			String line = text("tooltip.ak_trade_expansion.card_blank");
			check(blank.stream().anyMatch(t -> t.text().equals(line) && t.styleColor() == RED), "the card without an owner has no red \"%s\": %s", line, strings(blank));
		} finally {
			context.setScreen(() -> null);
			ClientBuySellCases.restoreInventory(s, saved);
		}
	}

	// ---- 21-2 / 21-8 the cargo crate

	/**
	 * 21-2: a cargo crate's tooltip shows its contents, the destination and where it is (with "（ネザー）" for a market in
	 * the Nether), the reward and the deposit, the market that issued it and the minutes left. 21-8: after the deadline
	 * the minutes are gone and a red "期限切れ（届けられません）" is shown instead.
	 */
	static void cargoTooltip(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		List<ItemStack> saved = ClientBuySellCases.saveInventory(s);
		long now = s.world.getServer().computeOnServer(server -> MarketClock.now(server.overworld()));
		// 20 minutes of the market clock (a day is 20 minutes), less a little for the ticks the client may be behind.
		long deadline = now + PriceModel.TICKS_PER_DAY - 200;
		try {
			fill(s, List.of(crate(deadline, Level.OVERWORLD), crate(deadline, Level.NETHER), crate(now - 100, Level.OVERWORLD)));
			InventoryScreen screen = openInventory(context);
			String what = "the cargo crate";
			List<TextRecorder.Text> tip = hover(context, screen, 9);
			hasLine(tip, what, text("tooltip.ak_trade_expansion.cargo.contents", new ItemStack(Items.WHEAT).getHoverName(), 32));
			hasLine(tip, what, text("tooltip.ak_trade_expansion.cargo.to", "テスト市場B"));
			hasLine(tip, what, text("tooltip.ak_trade_expansion.cargo.where", 123, -456));
			hasLine(tip, what, text("tooltip.ak_trade_expansion.cargo.pay", CoinItem.formatCopper(1_500), CoinItem.formatCopper(300)));
			hasLine(tip, what, text("tooltip.ak_trade_expansion.cargo.from", "テスト市場A"));
			hasLine(tip, what, text("tooltip.ak_trade_expansion.cargo.time_left", 20));
			String expired = text("tooltip.ak_trade_expansion.cargo.expired");
			check(tip.stream().noneMatch(t -> t.text().equals(expired)), "a crate before its deadline says \"%s\"", expired);
			screenshot(context, result, "21-2_cargo_tooltip");
			hasLine(hover(context, screen, 10), "the crate for the Nether", text("tooltip.ak_trade_expansion.cargo.where_nether", 123, -456));

			try {
				List<TextRecorder.Text> late = hover(context, screen, 11);
				check(late.stream().anyMatch(t -> t.text().equals(expired) && t.styleColor() == RED), "the crate after its deadline has no red \"%s\": %s", expired, strings(late));
				String left = text("tooltip.ak_trade_expansion.cargo.time_left", 1);
				String head = left.substring(0, Math.min(4, left.length()));
				check(late.stream().noneMatch(t -> t.text().startsWith(head)), "the crate after its deadline still shows the minutes left: %s", strings(late));
				hasLine(late, "the crate after its deadline", text("tooltip.ak_trade_expansion.cargo.to", "テスト市場B"));
				screenshot(context, also, "21-8_cargo_expired");
			} catch (AssertionError e) {
				throw new ClientChecklistTests.AlsoFailed(e);
			}
		} finally {
			context.setScreen(() -> null);
			ClientBuySellCases.restoreInventory(s, saved);
		}
	}

	private static ItemStack crate(long deadline, net.minecraft.resources.ResourceKey<Level> dimension) {
		ItemStack crate = new ItemStack(ModItems.CARGO_CRATE);
		crate.set(ModDataComponents.CARGO, new Cargo(1, Component.literal("テスト市場A"), 2, Component.literal("テスト市場B"), new BlockPos(123, 64, -456),
				dimension, Identifier.withDefaultNamespace("wheat"), Identifier.withDefaultNamespace("wheat"), 32, 1_500, 300, deadline, 1, 99));
		return crate;
	}
}
