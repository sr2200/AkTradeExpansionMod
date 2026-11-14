package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Predicate;

/** Helpers shared by the client cases: reflection, the mouse, the server side of the test world, screenshots. */
final class ClientUtil {
	private ClientUtil() {
	}

	// ---- checks and reflection

	static void check(boolean condition, String message, Object... args) {
		if (!condition) throw new AssertionError(args.length == 0 ? message : String.format(message, args));
	}

	static Object get(Object target, String field) {
		return get(target, target.getClass(), field);
	}

	static Object get(Object target, Class<?> owner, String field) {
		try {
			Field f = owner.getDeclaredField(field);
			f.setAccessible(true);
			return f.get(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read " + owner.getSimpleName() + "." + field, e);
		}
	}

	static int constant(Class<?> owner, String field) {
		return (Integer) get(null, owner, field);
	}

	static void set(Object target, String field, Object value) {
		try {
			Field f = target.getClass().getDeclaredField(field);
			f.setAccessible(true);
			f.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot set " + target.getClass().getSimpleName() + "." + field, e);
		}
	}

	static Object call(Object target, String method, Object... args) {
		for (Method m : target.getClass().getDeclaredMethods()) {
			if (!m.getName().equals(method) || m.getParameterCount() != args.length) continue;
			try {
				m.setAccessible(true);
				return m.invoke(target, args);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot call " + target.getClass().getSimpleName() + "." + method, e);
			}
		}
		throw new AssertionError("no method " + target.getClass().getSimpleName() + "." + method + " with " + args.length + " arguments");
	}

	// ---- the mouse and the keyboard

	/** Moves the mouse to a point in GUI pixels (and waits a tick: the move is applied with the next frame). */
	static void moveTo(ClientGameTestContext context, double guiX, double guiY) {
		double[] at = context.computeOnClient(client -> {
			// GUI pixels -> window coordinates (MouseHandler.getScaledXPos the other way round).
			double sx = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
			double sy = (double) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
			return new double[] {guiX * sx, guiY * sy};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(1);
	}

	/** Left click at a point in GUI pixels, as a player does. */
	static void clickAt(ClientGameTestContext context, double guiX, double guiY) {
		moveTo(context, guiX, guiY);
		// 26.3 reads the mouse through SDL: the left button is 1, not GLFW's 0 (0 is ignored by the widgets).
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(1);
	}

	/** Clicks the visible widget showing exactly {@code text} with the mouse. */
	static void clickWidget(ClientGameTestContext context, String text) {
		double[] at = context.computeOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof AbstractWidget w && w.visible && w.active && w.getMessage().getString().equals(text)) {
					return new double[] {w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0};
				}
			}
			return null;
		});
		check(at != null, "no button \"%s\" on the screen", text);
		clickAt(context, at[0], at[1]);
	}

	/** Clicks the centre of a widget (an edit box, ...). */
	static void clickWidget(ClientGameTestContext context, AbstractWidget w) {
		clickAt(context, w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0);
	}

	static void mouseAway(ClientGameTestContext context) {
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
	}

	static void pressEscape(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(2);
	}

	// ---- the trade screen

	static MarketScreen screen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof MarketScreen m ? m : null);
	}

	static MarketSnapshotS2C snapshot(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(client -> (MarketSnapshotS2C) get(screen, "snapshot"));
	}

	/** Waits (at most 2 seconds) until the screen has a snapshot other than {@code old}. */
	static MarketSnapshotS2C newSnapshot(ClientGameTestContext context, MarketScreen screen, MarketSnapshotS2C old) {
		MarketSnapshotS2C now = snapshot(context, screen);
		for (int i = 0; i < 40 && (now == null || now == old); i++) {
			context.waitTick();
			now = snapshot(context, screen);
		}
		check(now != null && now != old, "no new snapshot arrived");
		return now;
	}

	static int leftPos(AbstractContainerScreen<?> screen) {
		return (Integer) get(screen, AbstractContainerScreen.class, "leftPos");
	}

	static int topPos(AbstractContainerScreen<?> screen) {
		return (Integer) get(screen, AbstractContainerScreen.class, "topPos");
	}

	/** The goods of the current tab, in the order of the list. */
	@SuppressWarnings("unchecked")
	static List<MarketSnapshotS2C.GoodView> visibleGoods(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(client -> (List<MarketSnapshotS2C.GoodView>) call(screen, "visibleGoods"));
	}

	static MarketSnapshotS2C.GoodView view(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		return visibleGoods(context, screen).stream().filter(v -> v.goods().equals(good)).findFirst()
				.orElseThrow(() -> new AssertionError(good + " is not on this tab"));
	}

	/** Selects a good of the current tab (as a click on its row does). */
	static void select(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		context.runOnClient(client -> {
			set(screen, "selected", good);
			set(screen, "quantity", 1);
			call(screen, "updateButtons");
		});
		context.waitTicks(1);
	}

	/**
	 * Scrolls the list so the good's row is visible and returns the top of the row in GUI pixels (screen
	 * coordinates).
	 */
	static int showRow(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		List<MarketSnapshotS2C.GoodView> goods = visibleGoods(context, screen);
		int index = -1;
		for (int i = 0; i < goods.size(); i++) if (goods.get(i).goods().equals(good)) index = i;
		check(index >= 0, "%s is not on this tab", good);
		int rows = context.computeOnClient(client -> (Integer) get(screen, "rows"));
		int scroll = Math.max(0, Math.min(index, goods.size() - rows));
		int row = index - scroll;
		context.runOnClient(client -> set(screen, "scroll", scroll));
		context.waitTicks(2);
		return context.computeOnClient(client -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y") + row * constant(MarketScreen.class, "ROW_H");
	}

	/** Left edge of the goods list in GUI pixels. */
	static int listX(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(client -> leftPos(screen)) + constant(MarketScreen.class, "LIST_X");
	}

	/** Texts of one goods row (top {@code rowY}). */
	static List<TextRecorder.Text> rowTexts(List<TextRecorder.Text> texts, int rowY) {
		return texts.stream().filter(t -> t.y0() >= rowY && t.y0() < rowY + constant(MarketScreen.class, "ROW_H")).toList();
	}

	static List<TextRecorder.Text> tooltip(List<TextRecorder.Text> texts) {
		int base = TextRecorder.baseStratum(texts);
		return texts.stream().filter(t -> t.stratum() > base).toList();
	}

	static List<String> strings(List<TextRecorder.Text> texts) {
		return texts.stream().map(TextRecorder.Text::text).toList();
	}

	/** Moves an item stack of the player's inventory into the sell slot with two clicks. */
	static void putInSellSlot(ClientGameTestContext context, MarketScreen screen, Item item) {
		Slot from = context.computeOnClient(client -> {
			for (Slot slot : screen.getMenu().slots) {
				if (slot.container == client.player.getInventory() && slot.getItem().is(item)) return slot;
			}
			return null;
		});
		check(from != null, "no %s in the inventory slots of the screen", BuiltInRegistries.ITEM.getKey(item));
		Slot sell = context.computeOnClient(client -> screen.getMenu().slots.get((Integer) get(screen.getMenu(), "sellSlotIndex")));
		int left = context.computeOnClient(client -> leftPos(screen));
		int top = context.computeOnClient(client -> topPos(screen));
		clickAt(context, left + from.x + 8, top + from.y + 8);
		clickAt(context, left + sell.x + 8, top + sell.y + 8);
		mouseAway(context);
	}

	/** Buys with the screen's buttons: the "64" (or "1") button, then "buy"; waits until the money moved. */
	static long buyWithButtons(Session s, MarketScreen screen, Identifier good, String quantityButton) {
		ClientGameTestContext context = s.context;
		select(context, screen, good);
		clickWidget(context, quantityButton);
		mouseAway(context);
		MarketSnapshotS2C before = snapshot(context, screen);
		long money = serverMoney(s);
		context.clickScreenButton("screen.ak_trade_expansion.confirm.buy");
		long after = money;
		for (int i = 0; i < 40 && after == money; i++) {
			context.waitTick();
			after = serverMoney(s);
		}
		check(after < money, "buying %s with the \"%s\" button took no money", good, quantityButton);
		newSnapshot(context, screen, before);
		context.waitTicks(2);
		return money - after;
	}

	// ---- the server side

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static long serverMoney(Session s) {
		return s.world.getServer().computeOnServer(server -> WalletService.balance(player(server)));
	}

	static int serverCount(Session s, Identifier good) {
		return s.world.getServer().computeOnServer(server -> count(player(server), BuiltInRegistries.ITEM.getValue(GoodsCatalog.get(good).item())));
	}

	static int count(ServerPlayer player, Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory()) if (stack.is(item)) n += stack.getCount();
		return n;
	}

	static void giveItems(ServerPlayer player, Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		for (int left = count; left > 0; left -= max) player.getInventory().add(new ItemStack(item, Math.min(max, left)));
	}

	static Item item(Identifier good) {
		return BuiltInRegistries.ITEM.getValue(GoodsCatalog.get(good).item());
	}

	static Item itemOf(MarketSnapshotS2C.GoodView view) {
		return BuiltInRegistries.ITEM.getValue(view.item());
	}

	/** The market of the stall, brought up to date (recovery and restocking up to now). */
	static Market market(MinecraftServer server, BlockPos stall) {
		Market market = MarketRegistry.get(server).nearestWithin(server.overworld().dimension(), stall, 64);
		check(market != null, "no market at %s", stall);
		MarketUpkeep.touch(server.overworld(), market);
		return market;
	}

	/** The settings every world case runs with: every market has a bank tab (so the layout check sees it too). */
	static void applyTestConfig(TestServerContext server) {
		server.runOnServer(s -> {
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			config.bank.minMarketLevel = 1;
			ConfigManager.apply(config);
		});
	}

	/**
	 * A small village in front of the first player (C-1: 3 beds and 3 villagers) and a stall in the hand (hotbar slot
	 * 9). Returns where the stall goes: 2 blocks north of the player's feet.
	 */
	static BlockPos villageAndStall(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			ServerLevel level = player.level();
			BlockPos feet = player.blockPosition();
			village(level, feet);
			holdStall(player);
			return feet.north(2);
		});
	}

	static void village(ServerLevel level, BlockPos feet) {
		for (int i = 0; i < 3; i++) bed(level, feet.offset(-6 + 3 * i, 0, 5));
		for (int i = 0; i < 3; i++) EntityTypes.VILLAGER.spawn(level, feet.offset(-6 + 3 * i, 0, 9), EntitySpawnReason.COMMAND);
	}

	static void holdStall(ServerPlayer player) {
		player.getInventory().setSelectedSlot(8);
		player.getInventory().setItem(8, new ItemStack(ModBlocks.MARKET_STALL.asItem()));
	}

	private static void bed(ServerLevel level, BlockPos head) {
		BlockState state = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD);
		level.setBlock(head, state, 3);
		level.setBlock(head.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 3);
	}

	/** The ground (first free block) at x / z of the overworld; generates the chunk when needed. */
	static BlockPos ground(MinecraftServer server, int x, int z) {
		ServerLevel level = server.overworld();
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** Teleports the player (yaw / pitch kept) and waits until the chunks around are drawn. */
	static void teleport(Session s, double x, double y, double z) {
		float[] rot = s.world.getServer().computeOnServer(server -> new float[] {player(server).getYRot(), player(server).getXRot()});
		s.world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, rot[0], rot[1]));
		s.context.waitTicks(3);
		s.world.getConnection().waitForChunksRender();
	}

	/** Waits (at most {@code ticks}) until the client matches. */
	static boolean waitClient(ClientGameTestContext context, Predicate<Minecraft> condition, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (context.computeOnClient(condition::test)) return true;
			context.waitTick();
		}
		return context.computeOnClient(condition::test);
	}

	// ---- screenshots

	/** Takes a screenshot and copies it to build/autotest/screenshots/&lt;name&gt;.png. */
	static void screenshot(ClientGameTestContext context, CaseResult.Builder result, String name) {
		String file = name.replaceAll("[^A-Za-z0-9_.-]", "_");
		// Advancement toasts would cover the right side of the screen.
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTicks(1);
		Path taken = context.takeScreenshot(file);
		Path target = AutotestReport.directory().resolve("screenshots").resolve(file + ".png");
		try {
			Files.createDirectories(target.getParent());
			Files.copy(taken, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("Could not copy the screenshot " + taken, e);
		}
		result.screenshot("screenshots/" + file + ".png");
	}
}
