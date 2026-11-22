package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Pictures of the buildings (checklist 22-50〜22-56, 28-2〜28-6, 29-2, 29-14; runClientGameTestLooks): every building
 * of every look placed as its construction completes it (the structure with block updates), one look at a time, south
 * of the test stall, each taken from the side of its sign. A person looks at the pictures (materials and shapes); the
 * server cases check that no part falls off and no water runs out.
 */
final class ClientLookCases {
	static final List<String> STYLES = List.of("plains", "desert", "savanna", "snowy", "taiga", "nether", "end");
	/** Distance between the buildings of one look (the largest is 15 wide). */
	private static final int SPACING = 32;
	private static final int COLUMNS = 4;

	private ClientLookCases() {
	}

	private record Placed(Identifier id, BoundingBox box, BlockPos sign) {
	}

	private static BlockPos base(Session s) {
		return s.stall.offset(-48, 0, 60);
	}

	/** Places every building of the look (no rotation), one per cell of the grid, with block updates. */
	private static List<Placed> placeAll(MinecraftServer server, BlockPos base, String style) {
		ServerLevel level = server.overworld();
		List<Placed> placed = new ArrayList<>();
		int i = 0;
		for (var entry : FacilityCatalog.all().entrySet()) {
			FacilityDefinition def = entry.getValue();
			StructureTemplate template = FacilityBuilder.template(server, def, style).orElseThrow();
			BlockPos cell = ground(server, base.getX() + i % COLUMNS * SPACING, base.getZ() + i / COLUMNS * SPACING);
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);
			BlockPos origin = cell.offset(-template.getSize().getX() / 2, 0, -template.getSize().getZ() / 2);
			template.placeInWorld(level, origin, origin, settings, RandomSource.create(i), Block.UPDATE_ALL);
			BoundingBox box = template.getBoundingBox(settings, origin);
			BlockPos sign = null;
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockState(p).is(ModBlocks.FACILITY_SIGN)) {
					sign = p.immutable();
					break;
				}
			}
			placed.add(new Placed(entry.getKey(), box, sign));
			i++;
		}
		return placed;
	}

	private static void clear(MinecraftServer server, List<Placed> placed) {
		ServerLevel level = server.overworld();
		for (Placed p : placed) {
			BoundingBox b = p.box();
			for (BlockPos q : BlockPos.betweenClosed(b.minX() - 4, b.minY(), b.minZ() - 4, b.maxX() + 4, b.maxY() + 3, b.maxZ() + 4)) {
				if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			for (Entity e : level.getEntities((Entity) null, AABB.of(b).inflate(5), e -> !(e instanceof Player))) e.discard();
		}
	}

	/** The spectator's eye: in front of the building (templates face north), a little to its left and above. */
	private static Vec3[] camera(Placed p) {
		BoundingBox b = p.box();
		Vec3 center = new Vec3((b.minX() + b.maxX() + 1) / 2.0, b.minY() + b.getYSpan() * 0.4, (b.minZ() + b.maxZ() + 1) / 2.0);
		double dx = 0, dz = -1;
		double reach = Math.max(Math.max(b.getXSpan(), b.getZSpan()), b.getYSpan()) * 1.25 + 4;
		// Turned 35 degrees to the left of straight in front: two sides of the building in the picture.
		double angle = Math.toRadians(35);
		double ex = dx * Math.cos(angle) - dz * Math.sin(angle);
		double ez = dx * Math.sin(angle) + dz * Math.cos(angle);
		Vec3 eye = center.add(ex * reach, b.getYSpan() * 0.5 + 3, ez * reach);
		return new Vec3[] {eye, center};
	}

	private static void view(Session s, Vec3 eye, Vec3 target) {
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		s.world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.2f %.2f", eye.x, eye.y - 1.62, eye.z, yaw, pitch));
		s.context.waitTicks(5);
		try {
			s.world.getConnection().waitForChunksRender(1200);
		} catch (AssertionError e) {
			AutotestReport.LOGGER.warn("LOOKS chunks not all drawn: {}", e.getMessage());
		}
		s.context.waitTicks(15);
	}

	/**
	 * 22-50〜22-56, 28-2〜28-6, 29-2, 29-14 (pictures): each of the 23 buildings in each of the 7 looks, placed as built,
	 * taken from its front (161 pictures: look_&lt;look&gt;_&lt;building&gt;.png); every building has its sign.
	 */
	static void buildings(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos base = base(s);
		s.world.getServer().runCommand("weather clear");
		s.world.getServer().runCommand("gamemode spectator @a");
		context.getInput().resizeWindow(1280, 720);
		context.runOnClient(client -> {
			client.resizeGui();
			if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
		});
		List<String> noSign = new ArrayList<>();
		try {
			for (String style : STYLES) {
				s.world.getServer().runCommand("time set 6000");
				List<Placed> placed = s.world.getServer().computeOnServer(server -> placeAll(server, base, style));
				try {
					context.waitTicks(10);
					for (Placed p : placed) {
						if (p.sign() == null) noSign.add(style + "/" + p.id().getPath());
						Vec3[] cam = camera(p);
						view(s, cam[0], cam[1]);
						screenshot(context, result, "look_" + style + "_" + p.id().getPath());
					}
				} finally {
					s.world.getServer().runOnServer(server -> clear(server, placed));
				}
			}
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(client -> {
				client.resizeGui();
				if (client.gui.hud.isHidden()) client.gui.hud.toggle();
			});
			s.world.getServer().runCommand("gamemode survival @a");
			BlockPos stall = s.stall;
			teleport(s, stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		}
		check(noSign.isEmpty(), "buildings without their sign: %s", noSign);
	}
}
