package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.trade.TradeService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What a {@link ServerCase} works with: the test level around the test structure, survival test players, a small
 * "village" (beds and villagers), stalls and trades through the real server code. Positions are absolute.
 * Everything a case creates is removed by {@link #cleanUp()} before the next case.
 */
public final class ServerCtx {
	/** Half the side of the floor laid under each case (the 8x8 test structure sits in its middle). */
	static final int FLOOR = 12;

	private final GameTestHelper helper;
	private final TradeExpansionConfig defaults;
	final List<Step> steps = new ArrayList<>();
	private final List<ServerPlayer> players = new ArrayList<>();
	private final List<Floor> floors = new ArrayList<>();
	private final List<Forced> forced = new ArrayList<>();

	private record Floor(ServerLevel level, BlockPos pos) {
	}

	private record Forced(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
	}

	record Step(int delay, ServerCase.Body action) {
	}

	ServerCtx(GameTestHelper helper, TradeExpansionConfig defaults) {
		this.helper = helper;
		this.defaults = defaults;
		floor(center());
	}

	public GameTestHelper helper() {
		return helper;
	}

	public ServerLevel level() {
		return helper.getLevel();
	}

	public MinecraftServer server() {
		return level().getServer();
	}

	public MarketRegistry registry() {
		return MarketRegistry.get(server());
	}

	/** The level a market is in (markets of the Nether and the End too). */
	public ServerLevel levelOf(Market market) {
		ServerLevel level = server().getLevel(market.dimension());
		return level != null ? level : level();
	}

	/** Middle of the test structure, one block above the floor. */
	public BlockPos center() {
		return helper.absolutePos(new BlockPos(4, 1, 4));
	}

	/** Runs {@code action} {@code delay} ticks after the previous step (POI and entity changes need a tick). */
	public void later(int delay, ServerCase.Body action) {
		steps.add(new Step(delay, action));
	}

	/** Changes the live config for this case; the defaults come back in {@link #cleanUp()}. */
	public void config(Consumer<TradeExpansionConfig> change) {
		TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
		change.accept(config);
		ConfigManager.apply(config);
	}

	// ---- world

	/** Lays a stone floor around {@code pos} (one block below it); cleared again after the case. */
	public void floor(BlockPos pos) {
		floor(level(), pos);
	}

	/**
	 * A stone floor around {@code pos} in {@code level}. Outside the test level (the Nether, the End) the floor is part
	 * of a closed stone room (the terrain there is not empty: netherrack, lava, the void).
	 */
	public void floor(ServerLevel level, BlockPos pos) {
		// Away from the test structure chunks are not kept loaded: villagers spawned there would not be found.
		for (int cx = (pos.getX() - FLOOR - 16) >> 4; cx <= (pos.getX() + FLOOR + 16) >> 4; cx++) {
			for (int cz = (pos.getZ() - FLOOR - 16) >> 4; cz <= (pos.getZ() + FLOOR + 16) >> 4; cz++) {
				if (level.setChunkForced(cx, cz, true)) forced.add(new Forced(level, new net.minecraft.world.level.ChunkPos(cx, cz)));
			}
		}
		BlockPos base = pos.below();
		boolean room = level != level();
		int top = room ? ROOM_HEIGHT : 0;
		for (int dx = -FLOOR - 1; dx <= FLOOR + 1; dx++) {
			for (int dz = -FLOOR - 1; dz <= FLOOR + 1; dz++) {
				boolean edge = Math.abs(dx) > FLOOR || Math.abs(dz) > FLOOR;
				if (edge && !room) continue;
				for (int dy = 0; dy <= top; dy++) {
					boolean solid = dy == 0 || dy == top || edge;
					level.setBlock(base.offset(dx, dy, dz), solid ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), room ? 2 | 16 : 3);
				}
			}
		}
		floors.add(new Floor(level, pos.immutable()));
	}

	/** Height of the stone room {@link #floor(ServerLevel, BlockPos)} builds outside the test level (floor to ceiling). */
	static final int ROOM_HEIGHT = 7;

	/** A level of the test server (the Nether, the End). */
	public ServerLevel dimension(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key) {
		ServerLevel level = server().getLevel(key);
		AutotestCheck.that(level != null, "the test server has no %s", key.identifier());
		return level;
	}

	/** A bed (both halves) with its head at {@code pos}, facing north. */
	public void bed(BlockPos pos) {
		BlockState head = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD);
		level().setBlock(pos, head, 3);
		level().setBlock(pos.south(), head.setValue(BedBlock.PART, BedPart.FOOT), 3);
	}

	/** {@code beds} beds and {@code villagers} villagers around {@code center} (what a new market needs, C-1). */
	public void village(BlockPos center, int beds, int villagers) {
		for (int i = 0; i < beds; i++) bed(center.offset(-8 + 2 * (i % 8), 0, -9 + 3 * (i / 8)));
		for (int i = 0; i < villagers; i++) {
			BlockPos p = center.offset(-6 + 2 * (i % 7), 0, 6 + i / 7);
			net.minecraft.world.entity.EntityTypes.VILLAGER.spawn(level(), p, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		}
	}

	/** {@code n} villagers (without AI) around {@code center} in {@code level}: Nether and End markets count villagers only (D-4). */
	public void villagers(ServerLevel level, BlockPos center, int n) {
		for (int i = 0; i < n; i++) {
			BlockPos p = center.offset(-6 + 2 * (i % 7), 0, 4 + 2 * (i / 7));
			var v = net.minecraft.world.entity.EntityTypes.VILLAGER.spawn(level, p, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (v != null) v.setNoAi(true);
		}
	}

	// ---- players

	/** A survival player at {@code pos} (the vanilla mock player is always creative, and creative cannot trade). */
	public ServerPlayer player(BlockPos pos) {
		return player(level(), pos);
	}

	/** A survival player at {@code pos} in {@code level}. */
	public ServerPlayer player(ServerLevel level, BlockPos pos) {
		return player(level, pos, new GameProfile(UUID.randomUUID(), "test" + players.size()));
	}

	/** A survival player with the given profile (the same profile again = the same player joining again). */
	private ServerPlayer player(ServerLevel level, BlockPos pos, GameProfile profile) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(server(), level(), profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		// Nothing encodes the packets: what the server sends stays in the channel as packet objects (see sent()).
		channels.put(player, new EmbeddedChannel(connection));
		server().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
		players.add(player);
		return player;
	}

	/** {@code player} leaves the server and joins again (as after a restart); returns the new player object. */
	public ServerPlayer rejoin(ServerPlayer player) {
		BlockPos pos = player.blockPosition();
		ServerLevel level = player.level();
		player.closeContainer();
		server().getPlayerList().remove(player);
		players.remove(player);
		channels.remove(player);
		received.remove(player);
		return player(level, pos, player.getGameProfile());
	}

	/** A dead {@code player} respawns (the respawn button); returns the new player object. */
	public ServerPlayer respawn(ServerPlayer player) {
		ServerPlayer fresh = server().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		players.set(players.indexOf(player), fresh);
		EmbeddedChannel channel = channels.remove(player);
		if (channel != null) channels.put(fresh, channel);
		received.remove(player);
		fresh.setGameMode(GameType.SURVIVAL);
		return fresh;
	}

	// ---- what the server sent to a test player

	private final java.util.Map<ServerPlayer, EmbeddedChannel> channels = new java.util.HashMap<>();
	private final java.util.Map<ServerPlayer, List<Object>> received = new java.util.HashMap<>();

	/** Every packet sent to {@code player} since it joined or since {@link #forget}. */
	public List<Object> sent(ServerPlayer player) {
		EmbeddedChannel channel = channels.get(player);
		List<Object> list = received.computeIfAbsent(player, p -> new ArrayList<>());
		Object msg;
		// The server may write without flushing (it suspends flushing while it handles a command); flush to see them.
		if (channel != null) {
			channel.runPendingTasks();
			channel.flushOutbound();
		}
		while (channel != null && (msg = channel.readOutbound()) != null) list.add(msg);
		return list;
	}

	/** Forgets the packets sent so far, so the next checks see only what comes after. */
	public void forget(ServerPlayer player) {
		sent(player).clear();
	}

	/** Mod payloads of {@code type} sent to {@code player}. */
	public <T> List<T> payloads(ServerPlayer player, Class<T> type) {
		List<T> out = new ArrayList<>();
		for (Object msg : sent(player)) {
			if (msg instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket p && type.isInstance(p.payload())) {
				out.add(type.cast(p.payload()));
			}
		}
		return out;
	}

	/** Chat and action bar messages to {@code player}: the translation key, or the text when not translatable. */
	public List<String> messages(ServerPlayer player) {
		List<String> out = new ArrayList<>();
		for (Object msg : sent(player)) {
			if (msg instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket p) {
				out.add(p.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : p.content().getString());
			}
		}
		return out;
	}

	public boolean gotMessage(ServerPlayer player, String translationKey) {
		return messages(player).contains(translationKey);
	}

	/** The result of the last trade request answered to {@code player}, or {@code null}. */
	public com.akf.tradeexpansion.trade.TradeResult lastTradeResult(ServerPlayer player) {
		List<com.akf.tradeexpansion.network.TradeResultS2C> results = payloads(player, com.akf.tradeexpansion.network.TradeResultS2C.class);
		return results.isEmpty() ? null : results.getLast().result();
	}

	/** The last market snapshot sent to {@code player} (what its trade screen shows), or {@code null}. */
	public com.akf.tradeexpansion.network.MarketSnapshotS2C lastSnapshot(ServerPlayer player) {
		var list = payloads(player, com.akf.tradeexpansion.network.MarketSnapshotS2C.class);
		return list.isEmpty() ? null : list.getLast();
	}

	/**
	 * Everything a request could change for {@code player}: money, bank balance, inventory, and every market's goods,
	 * development, requests and the employees. Two equal fingerprints = the request changed nothing.
	 */
	public String fingerprint(ServerPlayer player) {
		// Markets catch up with time when touched (lazy recovery, new requests): do that first, so that only what a
		// request itself changes shows (touching again in the same tick changes nothing).
		for (Market m : registry().all()) MarketUpkeep.touch(levelOf(m), m);
		StringBuilder sb = new StringBuilder();
		sb.append("money=").append(balance(player));
		sb.append(" bank=").append(com.akf.tradeexpansion.bank.BankService.accounts(player).balance(player.getUUID()));
		sb.append(" inv=[");
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) sb.append(i).append(':').append(BuiltInRegistries.ITEM.getKey(s.getItem())).append('x').append(s.getCount())
					.append(s.getComponentsPatch().isEmpty() ? "" : s.getComponentsPatch().toString()).append(' ');
		}
		sb.append(']');
		for (Market m : registry().all().stream().sorted(java.util.Comparator.comparingLong(Market::id)).toList()) {
			sb.append(" market#").append(m.id()).append(" dev=").append(m.development());
			m.goods().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
					.forEach(e -> sb.append(' ').append(e.getKey().getPath()).append('=').append(e.getValue().pressure()).append('/').append(e.getValue().stock()));
			m.requests().forEach(r -> sb.append(" req").append(r.id()).append(r.accepted()));
		}
		sb.append(" staff=").append(registry().employees().size());
		return sb.toString();
	}

	public long balance(Player player) {
		return WalletService.balance(player);
	}

	public void giveMoney(Player player, long amount) {
		WalletService.give(player, amount);
	}

	public int count(Player player, Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory()) if (stack.is(item)) n += stack.getCount();
		return n;
	}

	/** Runs a command as {@code player} with full permission. */
	public void command(ServerPlayer player, String command) {
		CommandSourceStack source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER).withSuppressedOutput();
		server().getCommands().performPrefixedCommand(source, command);
	}

	/** Runs a command as {@code player} with full permission; its feedback goes to the player's chat (see {@link #messages}). */
	public void commandShown(ServerPlayer player, String command) {
		CommandSourceStack source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
		server().getCommands().performPrefixedCommand(source, command);
	}

	// ---- stalls and trades

	/** {@code player} places a crafted stall at {@code pos} as a player would (the stall item used on the block below). */
	public void placeStall(ServerPlayer player, BlockPos pos) {
		ItemStack stack = new ItemStack(ModBlocks.MARKET_STALL.asItem());
		emptyHand(player); // not over the starting money
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		click(player, pos.below(), Direction.UP);
	}

	/** Selects an empty hotbar slot, so that a right click uses no item (and nothing in the hand is lost). */
	public void emptyHand(ServerPlayer player) {
		var inv = player.getInventory();
		if (inv.getSelectedItem().isEmpty()) return;
		for (int i = 0; i < 9; i++) {
			if (inv.getItem(i).isEmpty()) {
				inv.setSelectedSlot(i);
				return;
			}
		}
		throw new AutotestCheck.Failed("no empty hotbar slot to open the stall with");
	}

	/** A right click on the {@code face} of the block at {@code pos} with the main hand, through the server's own handling. */
	public InteractionResult click(ServerPlayer player, BlockPos pos, Direction face) {
		Vec3 at = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(at, face, pos, false));
	}

	public boolean isStall(BlockPos pos) {
		return level().getBlockState(pos).is(ModBlocks.MARKET_STALL);
	}

	/** The market within the merge radius of {@code pos}, or {@code null}. */
	public Market marketAt(BlockPos pos) {
		return marketAt(level(), pos);
	}

	public Market marketAt(ServerLevel level, BlockPos pos) {
		return registry().nearestWithin(level.dimension(), pos, TradeExpansionConfig.get().distance.mergeRadius);
	}

	public boolean isStall(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(ModBlocks.MARKET_STALL);
	}

	/** Right-clicks the stall at {@code pos}: opens the trade screen (menu) on the server. */
	public MarketMenu openStall(ServerPlayer player, BlockPos pos) {
		emptyHand(player);
		click(player, pos, Direction.SOUTH);
		AutotestCheck.that(player.containerMenu instanceof MarketMenu, "the trade screen did not open");
		return (MarketMenu) player.containerMenu;
	}

	public long now() {
		return MarketClock.now(level());
	}

	/**
	 * The most expensive regular good of {@code market} (prices of cheap goods move by less than 1c) that can be bought
	 * {@code buyQty} times and sold {@code sellQty} times, unit 1 and no item components.
	 */
	public Identifier tradableGood(Market market, int buyQty, int sellQty) {
		long now = MarketUpkeep.touch(levelOf(market), market);
		Identifier best = null;
		double bestPrice = -1;
		for (Identifier id : market.regularGoods()) {
			GoodsDefinition def = GoodsCatalog.get(id);
			GoodState state = market.good(id);
			if (def == null || state == null || !def.buy() || !def.sell() || def.unit() != 1) continue;
			if (BuiltInRegistries.ITEM.getValue(def.item()) == Items.AIR || def.components().isPresent()) continue;
			if (state.stock() < buyQty + 1) continue;
			if (PriceModel.sellCapacity(Reputation.params(market, java.util.UUID.randomUUID(), id, now), state) < sellQty) continue;
			if (def.basePrice() > bestPrice) {
				best = id;
				bestPrice = def.basePrice();
			}
		}
		if (best == null) throw new AutotestCheck.Failed("market #" + market.id() + " has no good to buy " + buyQty + " / sell " + sellQty);
		return best;
	}

	public Item item(Identifier goodsId) {
		return BuiltInRegistries.ITEM.getValue(GoodsCatalog.get(goodsId).item());
	}

	/** What the trade screen would show as the total (A-4). */
	public long previewTotal(ServerPlayer player, Market market, Identifier goodsId, boolean buy, int qty) {
		long now = MarketUpkeep.touch(levelOf(market), market);
		var params = Reputation.params(market, player.getUUID(), goodsId, now);
		GoodState state = market.good(goodsId);
		return buy ? PriceModel.sumBuy(params, state.pressure(), qty, GoodsCatalog.get(goodsId).unit(), TradeExpansionConfig.get().trade.bulkDiscount())
				: PriceModel.sumSell(params, state.pressure(), qty);
	}

	public long buyPrice(ServerPlayer player, Market market, Identifier goodsId) {
		long now = MarketUpkeep.touch(levelOf(market), market);
		return PriceModel.buyPrice(Reputation.params(market, player.getUUID(), goodsId, now), market.good(goodsId).pressure());
	}

	public long sellPrice(ServerPlayer player, Market market, Identifier goodsId) {
		long now = MarketUpkeep.touch(levelOf(market), market);
		return PriceModel.sellPrice(Reputation.params(market, player.getUUID(), goodsId, now), market.good(goodsId).pressure());
	}

	/** Sends a trade request exactly as given (for invalid and edge-case requests); returns the answer. */
	public com.akf.tradeexpansion.trade.TradeResult tradeRaw(ServerPlayer player, long marketId, Identifier goodsId, boolean buy, int qty,
			long expected, boolean card, boolean all) {
		forget(player);
		TradeService.handle(player, new TradeRequestC2S(marketId, goodsId, buy, qty, expected, card, all));
		return lastTradeResult(player);
	}

	/** Takes every item out of {@code player}'s inventory (also the starting money). */
	public void emptyInventory(ServerPlayer player) {
		player.getInventory().clearContent();
	}

	/** Sends the trade request the buy / sell button sends, with the previewed total; returns that total. */
	public long trade(ServerPlayer player, Market market, Identifier goodsId, boolean buy, int qty) {
		long expected = previewTotal(player, market, goodsId, buy, qty);
		TradeService.handle(player, new TradeRequestC2S(market.id(), goodsId, buy, qty, expected, false, false));
		return expected;
	}

	// ---- clean-up

	private final List<net.minecraft.world.level.levelgen.structure.BoundingBox> extraBuildings = new ArrayList<>();

	/** Also clears {@code box} after the case (a building no facility points to any more, e.g. before it was rebuilt elsewhere). */
	public void clearAfter(net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		extraBuildings.add(box);
	}

	private final List<Runnable> undo = new ArrayList<>();

	/** Runs {@code action} first thing in {@link #cleanUp()} (latest first): puts back what the case changed outside the floors. */
	public void atCleanUp(Runnable action) {
		undo.addFirst(action);
	}

	/**
	 * Keeps the config file as it is now and writes it back after the case (commands such as {@code consumable add}
	 * and {@code goods set} edit the file itself).
	 */
	public void keepConfigFile() throws java.io.IOException {
		java.nio.file.Path file = ConfigManager.path();
		byte[] saved = java.nio.file.Files.exists(file) ? java.nio.file.Files.readAllBytes(file) : null;
		atCleanUp(() -> {
			try {
				if (saved != null) java.nio.file.Files.write(file, saved);
				else java.nio.file.Files.deleteIfExists(file);
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}

	/**
	 * Changes the biome within {@code radius} blocks around {@code pos} with /fillbiome (within the world's height);
	 * the biome there before comes back after the case.
	 */
	public void fillBiome(BlockPos pos, int radius, String biome) {
		String before = level().getBiome(pos).unwrapKey().orElseThrow().identifier().toString();
		runFillBiome(pos, radius, biome);
		atCleanUp(() -> runFillBiome(pos, radius, before));
	}

	private void runFillBiome(BlockPos pos, int radius, String biome) {
		int y0 = Math.max(level().getMinY(), pos.getY() - 8), y1 = Math.min(level().getMaxY(), pos.getY() + 16);
		String command = String.format(java.util.Locale.ROOT, "fillbiome %d %d %d %d %d %d %s", pos.getX() - radius, y0, pos.getZ() - radius,
				pos.getX() + radius, y1, pos.getZ() + radius, biome);
		try {
			int n = server().getCommands().getDispatcher().execute(command, server().createCommandSourceStack().withSuppressedOutput());
			AutotestCheck.that(n > 0, "/%s changed nothing", command);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new AutotestCheck.Failed("/" + command + " failed: " + e.getMessage());
		}
	}

	/** Runs a command as {@code player} with full permission and returns its result (what the command returned). */
	public int commandResult(ServerPlayer player, String command) {
		CommandSourceStack source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
		try {
			return server().getCommands().getDispatcher().execute(command, source);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new AutotestCheck.Failed("/" + command + " failed: " + e.getMessage());
		}
	}

	/** Removes the case's markets, blocks, entities and players and restores the default config. */
	void cleanUp() {
		RuntimeException failed = null;
		for (Runnable r : undo) {
			try {
				r.run();
			} catch (RuntimeException e) {
				if (failed == null) failed = e;
			}
		}
		undo.clear();
		for (ServerPlayer p : players) {
			p.closeContainer();
			server().getPlayerList().remove(p);
		}
		players.clear();
		channels.clear();
		received.clear();
		MarketRegistry registry = registry();
		// Facility buildings are taller than the cleared layers above the floors and may stand beyond them.
		List<net.minecraft.world.level.levelgen.structure.BoundingBox> buildings = new ArrayList<>(extraBuildings);
		extraBuildings.clear();
		for (Market m : registry.all()) if (m.dimension().equals(level().dimension())) for (var f : m.facilities().values()) buildings.add(f.box());
		for (Market m : List.copyOf(registry.all())) registry.remove(m.id());
		for (var box : buildings) {
			for (BlockPos p : BlockPos.betweenClosed(box.minX() - 1, box.minY(), box.minZ() - 1, box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1)) {
				level().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			AABB around = AABB.of(box).inflate(3);
			for (Entity e : level().getEntities((Entity) null, around, e -> !(e instanceof Player))) e.discard();
		}
		registry.campaigns().clear();
		registry.employees().clear();
		registry.setDirty();
		for (Floor f : floors) {
			BlockPos pos = f.pos();
			AABB box = new AABB(pos).inflate(FLOOR + 4, 8, FLOOR + 4);
			for (Entity e : f.level().getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();
			for (int dx = -FLOOR; dx <= FLOOR; dx++) {
				for (int dz = -FLOOR; dz <= FLOOR; dz++) {
					for (int dy = 0; dy <= 4; dy++) f.level().setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
		}
		// The item entities dropped by the removed blocks.
		for (Floor f : floors) {
			for (Entity e : f.level().getEntities((Entity) null, new AABB(f.pos()).inflate(FLOOR + 4, 8, FLOOR + 4), e -> !(e instanceof Player))) e.discard();
		}
		floors.clear();
		for (Forced f : forced) f.level().setChunkForced(f.chunk().x(), f.chunk().z(), false);
		forced.clear();
		ConfigManager.apply(ConfigManager.copy(defaults));
		if (failed != null) throw failed;
	}
}
