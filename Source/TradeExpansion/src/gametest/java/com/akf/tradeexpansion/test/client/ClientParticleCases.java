package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/** Client cases of particles and sounds the server sends (checklist 11-2, 22-24b). */
final class ClientParticleCases {
	private ClientParticleCases() {
	}

	private static final int TAPE = 0xFF9900;
	private static final Identifier CONSTRUCTION_SOUND = Identifier.fromNamespaceAndPath("ak_trade_expansion", "facility.construction");

	/**
	 * 22-24b: a site under construction (a facility of the stall's market made on the server, 7 × 7 and 5 high, in the
	 * open next to the stall) sends orange dots on its outline at floor level (minY + 0.15), wood chips (oak planks)
	 * inside it and now and then the construction sound, whose subtitle is "工事の音". Nothing comes farther than 48
	 * blocks away, after development.constructionEffects is set off and /aktradeex reload, nor once it is complete.
	 */
	static void constructionEffects(Session s, CaseResult.Builder result) throws java.io.IOException {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		Identifier id = ClientTownCases.id("inn");
		BoundingBox box = s.world.getServer().computeOnServer(server -> {
			BlockPos g = ground(server, stall.getX() + 6, stall.getZ() - 3);
			return new BoundingBox(g.getX(), g.getY(), g.getZ(), g.getX() + 6, g.getY() + 4, g.getZ() + 6);
		});
		double[] home = s.world.getServer().computeOnServer(server -> {
			var p = player(server);
			return new double[] {p.getX(), p.getY(), p.getZ()};
		});
		java.nio.file.Path config = com.akf.tradeexpansion.config.ConfigManager.path();
		byte[] file = java.nio.file.Files.readAllBytes(config);
		// An inn an earlier case made comes back afterwards.
		com.akf.tradeexpansion.development.Facility before = s.world.getServer().computeOnServer(server -> market(server, stall).facilities().get(id));
		try {
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				long now = com.akf.tradeexpansion.market.MarketClock.now(server.overworld());
				com.akf.tradeexpansion.development.Facility f = new com.akf.tradeexpansion.development.Facility(id,
						com.akf.tradeexpansion.development.FacilityKind.INN);
				BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
				f.start(origin, net.minecraft.world.level.block.Rotation.NONE, origin, box, "plains", now,
						now + 2 * com.akf.tradeexpansion.core.PriceModel.TICKS_PER_DAY);
				market.facilities().put(id, f);
				MarketRegistry.get(server).setDirty();
			});
			ParticleRecorder.start();
			// The outline is drawn every 20 ticks; the sound comes at random (1 in 6 every 10 ticks): wait for it, at most 30 seconds.
			context.waitTicks(25);
			for (int i = 0; i < 60 && ParticleRecorder.sounds().stream().noneMatch(x -> x.id().equals(CONSTRUCTION_SOUND) && near(box, x.x(), x.z())); i++) {
				context.waitTicks(10);
			}
			// Only what comes to this site (another case's site may be under construction too).
			List<ParticleRecorder.Particle> all = ParticleRecorder.particles().stream().filter(p -> near(box, p.x(), p.z())).toList();
			List<ParticleRecorder.Particle> tape = all.stream().filter(p -> p.color() == TAPE).toList();
			check(!tape.isEmpty(), "no orange dots around the site: %s", kinds(all));
			double x0 = box.minX(), x1 = box.maxX() + 1, z0 = box.minZ(), z1 = box.maxZ() + 1;
			for (ParticleRecorder.Particle p : tape) {
				boolean onEdge = (Math.abs(p.x() - x0) < 1e-6 || Math.abs(p.x() - x1) < 1e-6) && p.z() >= z0 - 1e-6 && p.z() <= z1 + 1e-6
						|| (Math.abs(p.z() - z0) < 1e-6 || Math.abs(p.z() - z1) < 1e-6) && p.x() >= x0 - 1e-6 && p.x() <= x1 + 1e-6;
				check(onEdge, "the orange dot %.2f, %.2f is not on the outline %s", p.x(), p.z(), box);
				check(Math.abs(p.y() - (box.minY() + 0.15)) < 1e-6, "the orange dot is at y %.2f, not at the floor %d + 0.15", p.y(), box.minY());
			}
			// One outline of 7 × 7 is 2 × (7 + 1) + 2 × (7 - 1) = 28 dots (one per block edge, corners once).
			long distinct = tape.stream().map(p -> Math.round(p.x() * 10) + "," + Math.round(p.z() * 10)).distinct().count();
			check(distinct == 28, "the outline has %d dots, not 28", distinct);
			List<ParticleRecorder.Particle> chips = all.stream().filter(p -> "minecraft:oak_planks".equals(p.block())).toList();
			check(!chips.isEmpty(), "no wood chips (oak planks) at the site: %s", kinds(all));
			for (ParticleRecorder.Particle p : chips) {
				check(p.x() >= box.minX() && p.x() <= box.maxX() + 1 && p.z() >= box.minZ() && p.z() <= box.maxZ() + 1
						&& p.y() >= box.minY() && p.y() <= box.maxY() + 1, "a wood chip at %.1f, %.1f, %.1f is outside the site %s", p.x(), p.y(), p.z(), box);
			}
			List<ParticleRecorder.Sound> sounds = ParticleRecorder.sounds().stream().filter(x -> x.id().equals(CONSTRUCTION_SOUND) && near(box, x.x(), x.z())).toList();
			check(!sounds.isEmpty(), "no construction sound in 30 seconds");
			for (ParticleRecorder.Sound x : sounds) {
				check(x.x() >= box.minX() - 0.5 && x.x() <= box.maxX() + 1.5 && x.z() >= box.minZ() - 0.5 && x.z() <= box.maxZ() + 1.5,
						"the construction sound at %.1f, %.1f is outside the site %s", x.x(), x.z(), box);
			}
			String subtitle = context.computeOnClient(c -> {
				var event = c.getSoundManager().getSoundEvent(CONSTRUCTION_SOUND);
				return event == null || event.getSubtitle() == null ? null : event.getSubtitle().getString();
			});
			check("工事の音".equals(subtitle), "the subtitle of the construction sound is %s, not 工事の音", subtitle);
			screenshot(context, result, "22-24b_site_under_construction");

			// Farther than 48 blocks.
			double cx = (box.minX() + box.maxX() + 1) / 2.0;
			double cz = (box.minZ() + box.maxZ() + 1) / 2.0;
			BlockPos away = s.world.getServer().computeOnServer(server -> ground(server, (int) cx + 52, (int) cz));
			teleport(s, away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			nothingFor(context, box, 40, "52 blocks away from the site");
			teleport(s, home[0], home[1], home[2]);

			// constructionEffects off in the file and /aktradeex reload.
			TradeExpansionConfig off = com.akf.tradeexpansion.config.ConfigManager.read(config);
			off.development.constructionEffects = false;
			com.akf.tradeexpansion.config.ConfigManager.write(config, off);
			s.world.getServer().runCommand("aktradeex reload");
			context.waitTicks(3);
			nothingFor(context, box, 40, "after development.constructionEffects false and reload");
			java.nio.file.Files.write(config, file);
			s.world.getServer().runCommand("aktradeex reload");
			applyTestConfig(s.world.getServer());
			context.waitTicks(3);
			ParticleRecorder.start();
			context.waitTicks(25);
			check(ParticleRecorder.particles().stream().anyMatch(p -> p.color() == TAPE && near(box, p.x(), p.z())), "the outline did not come back with constructionEffects true");

			// Complete.
			s.world.getServer().runOnServer(server -> market(server, stall).facilities().get(id).complete(null));
			context.waitTicks(2);
			nothingFor(context, box, 40, "after the building is complete");
		} finally {
			ParticleRecorder.stop();
			java.nio.file.Files.write(config, file);
			s.world.getServer().runCommand("aktradeex reload");
			applyTestConfig(s.world.getServer());
			s.world.getServer().runOnServer(server -> {
				if (before == null) market(server, stall).facilities().remove(id);
				else market(server, stall).facilities().put(id, before);
				MarketRegistry.get(server).setDirty();
			});
			teleport(s, home[0], home[1], home[2]);
		}
	}

	private static final int STAKE_OK = 0x55FF55;
	private static final int STAKE_BLOCKED = 0xFF5555;

	/**
	 * 22-36b / 28-10: right-clicking a stake next to the stall (the market's buildings set aside, so none is built)
	 * lists every building in the chat with its size, and draws one outline per size: the four corner posts of a box of
	 * that size (either way round), green when a building of that size can be built there and red otherwise, with the
	 * stake in the middle of its front side. The fountain square (11×11) and the park (13×13) are among them. The
	 * outlines stop after 30 seconds.
	 */
	static void stakeOutlines(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		BlockPos stake = s.world.getServer().computeOnServer(server -> ground(server, stall.getX() + 3, stall.getZ()));
		Map<Identifier, com.akf.tradeexpansion.development.Facility> saved = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			Map<Identifier, com.akf.tradeexpansion.development.Facility> copy = new java.util.LinkedHashMap<>(market.facilities());
			market.facilities().clear();
			server.overworld().setBlock(stake, com.akf.tradeexpansion.registry.ModBlocks.CONSTRUCTION_MARKER.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.WEST), 3);
			return copy;
		});
		try {
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			ParticleRecorder.start();
			// The player's right-click on the stake, done on the server (a villager of the test village may stand in the way of
			// the crosshair).
			s.world.getServer().runOnServer(server -> {
				var player = player(server);
				var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(stake),
						net.minecraft.core.Direction.UP, stake, false);
				player.gameMode.useItemOn(player, server.overworld(), player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
			});
			context.waitTicks(25); // drawn every 10 ticks

			// The chat: one line per building with its size.
			List<Object[]> lines = context.computeOnClient(c -> ClientScreenCases.chat(c).stream()
					.map(m -> m.content().getContents())
					.filter(x -> x instanceof net.minecraft.network.chat.contents.TranslatableContents t
							&& (t.getKey().endsWith(".facility.preview.ok") || t.getKey().endsWith(".facility.preview.blocked")))
					.map(x -> {
						var t = (net.minecraft.network.chat.contents.TranslatableContents) x;
						return new Object[] {t.getKey().endsWith(".ok"), String.valueOf(t.getArgs()[1]), t.getArgs()[0] instanceof net.minecraft.network.chat.Component n ? n.getString() : String.valueOf(t.getArgs()[0])};
					}).toList());
			int buildings = s.world.getServer().computeOnServer(server -> com.akf.tradeexpansion.data.FacilityCatalog.all().size());
			check(lines.size() == buildings, "the chat lists %d buildings, not all %d: %s", lines.size(), buildings,
					lines.stream().map(l -> l[2] + " " + l[1]).toList());
			List<String> sizes = lines.stream().map(l -> (String) l[1]).toList();
			check(sizes.contains("11×11"), "the fountain square (11×11) is not listed: %s", sizes);
			check(sizes.contains("13×13"), "the park (13×13) is not listed: %s", sizes);

			// The outlines: corner posts (dots above the floor) found by size.
			List<ParticleRecorder.Particle> dots = ParticleRecorder.dust().stream()
					.filter(p -> p.color() == STAKE_OK || p.color() == STAKE_BLOCKED).toList();
			check(!dots.isEmpty(), "no green or red outline came");
			double floor = dots.stream().mapToDouble(ParticleRecorder.Particle::y).min().orElseThrow();
			Map<String, java.util.Set<Integer>> posts = new java.util.HashMap<>();
			for (ParticleRecorder.Particle p : dots) {
				if (p.y() > floor + 0.4) posts.computeIfAbsent(key(p.x(), p.z()), k -> new java.util.HashSet<>()).add(p.color());
			}
			List<double[]> corners = posts.keySet().stream().map(k -> java.util.Arrays.stream(k.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
			double sx = stake.getX() + 0.5, sz = stake.getZ() + 0.5;
			for (String size : new java.util.LinkedHashSet<>(sizes)) {
				String[] wd = size.split("×");
				int w = Integer.parseInt(wd[0]), d = Integer.parseInt(wd[1]);
				boolean ok = lines.stream().anyMatch(l -> l[1].equals(size) && (Boolean) l[0]);
				int color = ok ? STAKE_OK : STAKE_BLOCKED;
				boolean found = false;
				for (double[] c : corners) {
					for (int[] span : new int[][] {{w, d}, {d, w}}) {
						double x1 = c[0] + span[0], z1 = c[1] + span[1];
						String[] four = {key(c[0], c[1]), key(x1, c[1]), key(c[0], z1), key(x1, z1)};
						if (!java.util.Arrays.stream(four).allMatch(k -> posts.containsKey(k) && posts.get(k).contains(color))) continue;
						// The stake is at the middle of one side of the box (within a block of that side).
						boolean front = Math.min(Math.abs(sx - c[0]), Math.abs(sx - x1)) <= 1 && Math.abs(sz - (c[1] + z1) / 2) <= 1
								|| Math.min(Math.abs(sz - c[1]), Math.abs(sz - z1)) <= 1 && Math.abs(sx - (c[0] + x1) / 2) <= 1;
						if (front) found = true;
					}
				}
				check(found, "no %s outline of %s with the stake %s in the middle of its front (corners %s)", ok ? "green" : "red", size,
						stake.toShortString(), posts.keySet());
			}
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			context.getInput().lookAt(stake.below(2));
			context.waitTicks(3);
			screenshot(context, result, "22-36b_stake_outlines");

			// 30 seconds.
			context.waitTicks(30 * 20 - 25 + 15);
			ParticleRecorder.start();
			context.waitTicks(25);
			long after = ParticleRecorder.dust().stream().filter(p -> p.color() == STAKE_OK || p.color() == STAKE_BLOCKED).count();
			check(after == 0, "%d outline dots came more than 30 seconds after the right-click", after);
		} finally {
			ParticleRecorder.stop();
			s.world.getServer().runOnServer(server -> {
				server.overworld().setBlock(stake, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				Market market = market(server, stall);
				market.facilities().clear();
				market.facilities().putAll(saved);
				MarketRegistry.get(server).setDirty();
			});
		}
	}

	/**
	 * 22-36b: placing a stake (the player's use of the stake item, on the server: setPlacedBy) shows the outlines at once
	 * and writes nothing in the chat; a right-click on it then lists the buildings, without the ones the market already
	 * has (one completed here); a right-click on a second stake says "先に置いた杭（…）がある間は、そちらに建ちます" with
	 * the first one's position.
	 */
	static void stakePlaced(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		Identifier builtId = com.akf.tradeexpansion.TradeExpansion.id("quest_hall");
		BlockPos[] stakes = s.world.getServer().computeOnServer(server -> new BlockPos[] {ground(server, stall.getX() + 3, stall.getZ()),
				ground(server, stall.getX() - 4, stall.getZ() + 3)});
		Map<Identifier, com.akf.tradeexpansion.development.Facility> saved = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			Map<Identifier, com.akf.tradeexpansion.development.Facility> copy = new java.util.LinkedHashMap<>(market.facilities());
			market.facilities().clear();
			market.markers().clear();
			// A completed quest hall far off (made on the server, without its blocks).
			var built = new com.akf.tradeexpansion.development.Facility(builtId, com.akf.tradeexpansion.development.FacilityKind.QUEST_HALL);
			BlockPos o = stall.offset(200, 0, 200);
			long now = com.akf.tradeexpansion.market.MarketClock.now(server.overworld());
			built.start(o, net.minecraft.world.level.block.Rotation.NONE, o, BoundingBox.fromCorners(o, o.offset(4, 6, 4)), "plains", now, now);
			built.complete(o);
			market.facilities().put(builtId, built);
			MarketRegistry.get(server).setDirty();
			return copy;
		});
		String builtName = s.world.getServer().computeOnServer(server -> com.akf.tradeexpansion.development.FacilityBuilder
				.name(com.akf.tradeexpansion.development.FacilityKind.QUEST_HALL).getString());
		try {
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			ParticleRecorder.start();
			place(s, stakes[0]);
			context.waitTicks(25); // drawn every 10 ticks
			List<TranslatableContents> placedChat = translated(context);
			check(placedChat.stream().noneMatch(t -> t.getKey().contains(".facility.")), "placing the stake wrote in the chat: %s", keys(placedChat));
			long dots = ParticleRecorder.dust().stream().filter(p -> p.color() == STAKE_OK || p.color() == STAKE_BLOCKED).count();
			check(dots > 0, "no green or red outline came after placing the stake");
			use(s, stakes[0]);
			context.waitTicks(5);
			List<TranslatableContents> chat = translated(context);
			check(chat.stream().anyMatch(t -> t.getKey().endsWith(".facility.preview.header")), "the right-click on the stake did not list the buildings: %s", keys(chat));
			List<TranslatableContents> lines = chat.stream()
					.filter(t -> t.getKey().endsWith(".facility.preview.ok") || t.getKey().endsWith(".facility.preview.blocked")).toList();
			int buildings = s.world.getServer().computeOnServer(server -> com.akf.tradeexpansion.data.FacilityCatalog.all().size());
			check(lines.size() == buildings - 1, "the right-click on the stake listed %d buildings, not the %d not built yet", lines.size(), buildings - 1);
			check(lines.stream().noneMatch(t -> t.getArgs()[0] instanceof net.minecraft.network.chat.Component n && n.getString().equals(builtName)),
					"the completed %s is listed for the stake", builtName);
			check(chat.stream().noneMatch(t -> t.getKey().endsWith(".facility.preview.not_first")), "the first stake says another stake comes first");
			context.getInput().lookAt(stakes[0].below(2));
			context.waitTicks(3);
			screenshot(context, result, "22-36b_stake_placed");

			// A second stake: the first one is used.
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			place(s, stakes[1]);
			context.waitTicks(5);
			List<TranslatableContents> secondPlaced = translated(context);
			check(secondPlaced.stream().noneMatch(t -> t.getKey().contains(".facility.")), "placing the second stake wrote in the chat: %s", keys(secondPlaced));
			use(s, stakes[1]);
			context.waitTicks(5);
			List<TranslatableContents> second = translated(context);
			TranslatableContents notFirst = second.stream().filter(t -> t.getKey().endsWith(".facility.preview.not_first")).findFirst()
					.orElseThrow(() -> new AssertionError("the second stake does not say the first one is used: " + keys(second)));
			BlockPos first = stakes[0];
			check(List.of(String.valueOf(notFirst.getArgs()[0]), String.valueOf(notFirst.getArgs()[1]), String.valueOf(notFirst.getArgs()[2]))
					.equals(List.of(String.valueOf(first.getX()), String.valueOf(first.getY()), String.valueOf(first.getZ()))),
					"the second stake names %s, not the first stake %s", java.util.Arrays.toString(notFirst.getArgs()), first.toShortString());
			List<Long> markers = s.world.getServer().computeOnServer(server -> market(server, stall).markers().stream().map(BlockPos::asLong).toList());
			check(markers.size() == 2 && markers.get(0) == first.asLong(), "the market's stakes are %s", markers);
		} finally {
			ParticleRecorder.stop();
			s.world.getServer().runOnServer(server -> {
				for (BlockPos p : stakes) server.overworld().setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				Market market = market(server, stall);
				market.markers().clear();
				market.facilities().clear();
				market.facilities().putAll(saved);
				MarketRegistry.get(server).setDirty();
				player(server).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
			});
		}
	}

	/** The player uses a stake on the top of the ground below {@code pos} (on the server, as the use key does). */
	private static void place(Session s, BlockPos pos) {
		boolean placed = s.world.getServer().computeOnServer(server -> {
			var player = player(server);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(
					com.akf.tradeexpansion.registry.ModBlocks.CONSTRUCTION_MARKER.asItem()));
			var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()).add(0, 0.5, 0),
					net.minecraft.core.Direction.UP, pos.below(), false);
			player.gameMode.useItemOn(player, server.overworld(), player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
			return server.overworld().getBlockState(pos).is(com.akf.tradeexpansion.registry.ModBlocks.CONSTRUCTION_MARKER);
		});
		check(placed, "the stake was not placed at %s", pos.toShortString());
	}

	/** The player's right-click with an empty hand on the stake at {@code pos} (on the server, as the use key does). */
	private static void use(Session s, BlockPos pos) {
		s.world.getServer().runOnServer(server -> {
			var player = player(server);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
			var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
					net.minecraft.core.Direction.UP, pos, false);
			player.gameMode.useItemOn(player, server.overworld(), player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		});
	}

	private static List<TranslatableContents> translated(ClientGameTestContext context) {
		return context.computeOnClient(c -> ClientScreenCases.chat(c).stream().map(m -> m.content().getContents())
				.filter(x -> x instanceof TranslatableContents).map(x -> (TranslatableContents) x).toList());
	}

	private static List<String> keys(List<TranslatableContents> chat) {
		return chat.stream().map(TranslatableContents::getKey).toList();
	}

	private static String key(double x, double z) {
		return Math.round(x * 2) / 2.0 + "," + Math.round(z * 2) / 2.0;
	}

	/** At the site or within 1.5 blocks of it (horizontally). */
	private static boolean near(BoundingBox box, double x, double z) {
		return x >= box.minX() - 1.5 && x <= box.maxX() + 2.5 && z >= box.minZ() - 1.5 && z <= box.maxZ() + 2.5;
	}

	/** No orange dot, wood chip or construction sound comes to the site for {@code ticks}. */
	private static void nothingFor(ClientGameTestContext context, BoundingBox box, int ticks, String when) {
		ParticleRecorder.start();
		context.waitTicks(ticks);
		long tape = ParticleRecorder.particles().stream().filter(p -> p.color() == TAPE && near(box, p.x(), p.z())).count();
		long chips = ParticleRecorder.particles().stream().filter(p -> "minecraft:oak_planks".equals(p.block()) && near(box, p.x(), p.z())).count();
		long sounds = ParticleRecorder.sounds().stream().filter(x -> x.id().equals(CONSTRUCTION_SOUND) && near(box, x.x(), x.z())).count();
		check(tape + chips + sounds == 0, "%s: %d orange dots, %d wood chips and %d construction sounds came", when, tape, chips, sounds);
	}

	private static List<String> kinds(List<ParticleRecorder.Particle> particles) {
		return particles.stream().map(p -> p.dust() ? String.format("dust %06X", p.color()) : p.block() != null ? p.block() : p.type().toString())
				.distinct().toList();
	}

	// The colours of PlacementGuide.
	private static final int VILLAGE = 0x55FF55;
	private static final int MERGE = 0xFFD700;
	private static final int TOO_CLOSE = 0xFF5555;
	private static final int COUNT = 0x55AAFF;
	/** How far a dot may be from its line (the circles are drawn with straight steps of about 1 block). */
	private static final double ON_LINE = 0.6;

	/**
	 * 11-2: {@code /aktradeex where show} at the stall draws yellow dots on the circle of mergeRadius around each market
	 * and blue ones on the circle of market.levelRadius around the player, each on the ground (the top block + 0.3);
	 * {@code where hide} stops them. Standing near the circle of minNewMarketDistance around the market, the red dots are
	 * on that circle, and they stop by themselves after 30 seconds. The green box needs a village structure, which the
	 * flat test world has none of (/place structure does not record it as one): no green dots come.
	 */
	static void whereShow(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		TradeExpansionConfig config = TradeExpansionConfig.get();
		BlockPos stall = s.stall;
		double[] home = s.world.getServer().computeOnServer(server -> {
			var p = player(server);
			return new double[] {p.getX(), p.getY(), p.getZ()};
		});
		try {
			// At the stall, in the village.
			Map<Integer, List<ParticleRecorder.Particle>> near = show(s);
			check(near.containsKey(MERGE), "no yellow dots (the range of the same market) at the stall: colours %s", colours(near));
			check(near.containsKey(COUNT), "no blue dots (the range counted for the level) at the stall: colours %s", colours(near));
			check(!near.containsKey(VILLAGE), "green dots in a world without village structures");
			check(!near.containsKey(TOO_CLOSE), "red dots at the stall, %d blocks inside the red circle", (int) config.distance.minNewMarketDistance);
			BlockPos origin = s.world.getServer().computeOnServer(server -> player(server).blockPosition());
			List<BlockPos> centers = centers(s, origin, config.distance.minNewMarketDistance + 64);
			onCircle(near.get(COUNT), List.of(origin), config.market.levelRadius, "blue");
			onCircle(near.get(MERGE), centers, config.distance.mergeRadius, "yellow");
			onGround(s, near);
			screenshot(context, result, "11-2_where_show_at_stall");

			s.world.getServer().runCommand("execute as @p at @p run aktradeex where hide");
			context.waitTicks(2);
			ParticleRecorder.start();
			context.waitTicks(25);
			check(ParticleRecorder.dust().isEmpty(), "%d dots came after where hide", ParticleRecorder.dust().size());

			// Just inside the red circle of the stall's market: the red dots, which stop after 30 seconds.
			double r = config.distance.minNewMarketDistance - 8;
			teleport(s, stall.getX() + 0.5 + r, s.world.getServer().computeOnServer(server -> ground(server, (int) (stall.getX() + r), stall.getZ())).getY(), stall.getZ() + 0.5);
			BlockPos far = s.world.getServer().computeOnServer(server -> player(server).blockPosition());
			Map<Integer, List<ParticleRecorder.Particle>> edge = show(s);
			check(edge.containsKey(TOO_CLOSE), "no red dots (where no new market can be made) near the red circle: colours %s", colours(edge));
			onCircle(edge.get(TOO_CLOSE), centers(s, far, config.distance.minNewMarketDistance + 64), config.distance.minNewMarketDistance, "red");
			onCircle(edge.get(COUNT), List.of(far), config.market.levelRadius, "blue");
			onGround(s, edge);
			screenshot(context, result, "11-2_where_show_red");
			// show() waited about 1 second of the 30.
			context.waitTicks(30 * 20 - 25 + 15);
			ParticleRecorder.start();
			context.waitTicks(25);
			check(ParticleRecorder.dust().isEmpty(), "%d dots came more than 30 seconds after where show", ParticleRecorder.dust().size());
		} finally {
			ParticleRecorder.stop();
			s.world.getServer().runCommand("execute as @p at @p run aktradeex where hide");
			teleport(s, home[0], home[1], home[2]);
		}
	}

	/** Runs where show as the player and returns the dots of about one second, by colour. */
	private static Map<Integer, List<ParticleRecorder.Particle>> show(Session s) {
		ParticleRecorder.start();
		s.world.getServer().runCommand("execute as @p at @p run aktradeex where show");
		s.context.waitTicks(25); // drawn every 10 ticks
		return ParticleRecorder.dust().stream().collect(Collectors.groupingBy(ParticleRecorder.Particle::color));
	}

	private static List<String> colours(Map<Integer, List<ParticleRecorder.Particle>> dots) {
		List<String> list = new ArrayList<>();
		dots.forEach((color, d) -> list.add(String.format("%06X×%d", color, d.size())));
		return list;
	}

	private static List<BlockPos> centers(Session s, BlockPos at, double reach) {
		return s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).within(server.overworld().dimension(), at, reach)
				.stream().map(Market::center).toList());
	}

	/** Every dot is on the circle of {@code radius} around one of the centers (horizontally, block centers). */
	private static void onCircle(List<ParticleRecorder.Particle> dots, List<BlockPos> centers, double radius, String name) {
		check(dots.size() >= 8, "only %d %s dots", dots.size(), name);
		for (ParticleRecorder.Particle d : dots) {
			boolean on = centers.stream().anyMatch(c -> Math.abs(Math.hypot(d.x() - (c.getX() + 0.5), d.z() - (c.getZ() + 0.5)) - radius) <= ON_LINE);
			check(on, "the %s dot %.1f, %.1f is not on a circle of %.0f blocks around %s", name, d.x(), d.z(), radius, centers);
		}
	}

	/** Every dot (up to 50 of each colour) is 0.3 above the top block where it is. */
	private static void onGround(Session s, Map<Integer, List<ParticleRecorder.Particle>> dots) {
		List<ParticleRecorder.Particle> some = new ArrayList<>();
		dots.values().forEach(d -> some.addAll(d.subList(0, Math.min(50, d.size()))));
		List<String> off = s.world.getServer().computeOnServer(server -> {
			List<String> bad = new ArrayList<>();
			for (ParticleRecorder.Particle d : some) {
				int top = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(d.x()), (int) Math.floor(d.z()));
				if (Math.abs(d.y() - (top + 0.3)) > 0.01) bad.add(String.format("%06X at %.1f, %.2f, %.1f (top %d)", d.color(), d.x(), d.y(), d.z(), top));
			}
			return bad;
		});
		check(off.isEmpty(), "dots not on the ground: %s", off.subList(0, Math.min(5, off.size())));
	}
}
