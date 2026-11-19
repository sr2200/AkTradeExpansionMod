package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.TraderRenderer;
import com.akf.tradeexpansion.core.TraderPalette;
import com.akf.tradeexpansion.entity.TraderEntity;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/** Client cases of the stall block and the trader NPC (checklist 2-6, 2-7, 6-10b). */
final class ClientTraderCases {
	private ClientTraderCases() {
	}

	private static final int TRADERS = 24;
	private static final String TAG = "ak_robe_test";

	/**
	 * 6-10b: 24 traders summoned away from every market get robe colours at random (at least 6 of the 8 among them),
	 * the client sees each one's colour, the colour stays when their chunks are unloaded (saved) and loaded again, and
	 * after a resource reload (F3+T) the recoloured textures are still the trader's own and the colours stay.
	 */
	static void robeColours(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		double[] home = s.world.getServer().computeOnServer(server -> {
			var p = player(server);
			return new double[] {p.getX(), p.getY(), p.getZ()};
		});
		// Away from the markets (farther than mergeRadius, so they join none) and from the other cases' places.
		BlockPos at = s.world.getServer().computeOnServer(server -> ground(server, stall.getX() - 140, stall.getZ() + 40));
		try {
			teleport(s, at.getX() + 0.5, at.getY(), at.getZ() - 6.5);
			for (int i = 0; i < TRADERS; i++) {
				s.world.getServer().runCommand(String.format("summon ak_trade_expansion:trader %d %d %d {Tags:[\"%s\"],NoAI:1b}",
						at.getX() + i % 6 * 2, at.getY(), at.getZ() + i / 6 * 2, TAG));
			}
			context.waitTicks(10);
			Map<UUID, Integer> server = serverVariants(s, at);
			check(server.size() == TRADERS, "%d of the %d summoned traders are there", server.size(), TRADERS);
			long colours = server.values().stream().distinct().count();
			check(colours >= 6, "the %d traders have only %d robe colours: %s", TRADERS, colours, server.values());
			check(server.values().stream().allMatch(v -> v >= 0 && v < TraderPalette.VARIANT_COUNT), "a robe colour out of 0..%d: %s",
					TraderPalette.VARIANT_COUNT - 1, server.values());
			check(waitClient(context, c -> clientVariants(c, at).size() == TRADERS, 60), "the client does not see the %d traders", TRADERS);
			check(clientVariantsNow(context, at).equals(server), "the client's robe colours differ from the server's: %s / %s",
					clientVariantsNow(context, at), server);
			context.getInput().lookAt(at.offset(5, 0, 6));
			context.waitTicks(5);
			screenshot(context, result, "6-10b_robe_colours");

			// Saved and loaded again: go far away until the traders are unloaded, then come back.
			BlockPos far = s.world.getServer().computeOnServer(sv -> ground(sv, at.getX() + 2000, at.getZ()));
			teleport(s, far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			boolean unloaded = false;
			for (int i = 0; i < 60 && !unloaded; i++) {
				context.waitTicks(10);
				unloaded = s.world.getServer().computeOnServer(sv -> server.keySet().stream().noneMatch(id -> sv.overworld().getEntity(id) != null));
			}
			check(unloaded, "the traders were not unloaded 2000 blocks away");
			teleport(s, at.getX() + 0.5, at.getY(), at.getZ() - 6.5);
			boolean back = false;
			for (int i = 0; i < 60 && !back; i++) {
				context.waitTicks(5);
				back = serverVariants(s, at).size() == TRADERS;
			}
			check(back, "the traders did not come back (%d of %d)", serverVariants(s, at).size(), TRADERS);
			check(serverVariants(s, at).equals(server), "the robe colours changed after unloading and loading: %s / %s", serverVariants(s, at), server);
			check(waitClient(context, c -> clientVariants(c, at).equals(server), 60), "the client's robe colours after loading again differ: %s / %s",
					clientVariantsNow(context, at), server);

			// F3+T.
			CompletableFuture<Void> reload = context.computeOnClient(c -> c.reloadResourcePacks());
			for (int i = 0; i < 600 && !reload.isDone(); i++) context.waitTick();
			check(reload.isDone() && !reload.isCompletedExceptionally(), "the resource reload did not finish");
			context.waitTicks(10);
			Identifier[] textures = (Identifier[]) get(null, TraderRenderer.class, "TEXTURES");
			List<String> lost = context.computeOnClient(c -> {
				List<String> list = new java.util.ArrayList<>();
				for (int i = 1; i < textures.length; i++) {
					var texture = c.getTextureManager().getTexture(textures[i]);
					if (!texture.getClass().getName().endsWith("VariantTexture")) list.add(textures[i] + " " + texture.getClass().getSimpleName());
				}
				return list;
			});
			check(lost.isEmpty(), "after the resource reload the robe textures are not the recoloured ones: %s", lost);
			check(clientVariantsNow(context, at).equals(server), "the client's robe colours changed after the resource reload");
			context.getInput().lookAt(at.offset(5, 0, 6));
			context.waitTicks(5);
			screenshot(context, result, "6-10b_robe_colours_after_reload");
		} finally {
			s.world.getServer().runCommand("kill @e[tag=" + TAG + "]");
			teleport(s, home[0], home[1], home[2]);
		}
	}

	/**
	 * 2-6 / 2-7: stalls placed with the use key to the north, east, south and west of the player face the player; the
	 * stall does not hide the faces of the blocks next to it (it does not occlude: no see-through or black neighbours)
	 * and its shape is not a full block. A screenshot of each from the front.
	 */
	static void stallFacing(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		BlockPos feet = s.world.getServer().computeOnServer(server -> player(server).blockPosition());
		net.minecraft.core.Direction[] sides = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
				net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};
		List<BlockPos> placed = new java.util.ArrayList<>();
		float[] rot = s.world.getServer().computeOnServer(server -> new float[] {player(server).getYRot(), player(server).getXRot()});
		try {
			for (net.minecraft.core.Direction side : sides) {
				// The shared stall is 2 north of the player: the north one goes a little to the east of it.
				BlockPos column = side == net.minecraft.core.Direction.NORTH ? feet.north(3).east(2) : feet.relative(side, 3);
				BlockPos pos = s.world.getServer().computeOnServer(server -> {
					holdStall(player(server));
					return ground(server, column.getX(), column.getZ());
				});
				// The middle of the ground block's top face (its centre may be behind a side face seen from the player).
				float[] aim = context.computeOnClient(c -> {
					var eye = c.player.getEyePosition();
					double dx = pos.getX() + 0.5 - eye.x, dy = pos.getY() - eye.y, dz = pos.getZ() + 0.5 - eye.z;
					return new float[] {(float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
				});
				context.getInput().lookAt(aim[0], aim[1]);
				context.waitTicks(3);
				context.getInput().pressKey(options -> options.keyUse);
				context.waitTicks(5);
				var state = s.world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos));
				check(state.is(com.akf.tradeexpansion.registry.ModBlocks.MARKET_STALL), "no stall was placed to the %s at %s (%s)", side, pos.toShortString(), state);
				placed.add(pos);
				var facing = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
				check(facing == side.getOpposite(), "the stall placed to the %s faces %s, not the player (%s)", side, facing, side.getOpposite());
				boolean[] look = context.computeOnClient(c -> {
					var shape = state.getShape(c.level, pos);
					return new boolean[] {state.canOcclude(), net.minecraft.world.level.block.Block.isShapeFullBlock(shape), shape.isEmpty()};
				});
				check(!look[0], "the stall occludes the blocks next to it (their faces are not drawn)");
				check(!look[1] && !look[2], "the stall's shape is %s", look[1] ? "a full block" : "empty");
				context.waitTicks(5);
				screenshot(context, result, "2-6_stall_facing_" + side.getSerializedName());
			}
		} finally {
			s.world.getServer().runOnServer(server -> placed.forEach(p -> server.overworld().destroyBlock(p, false)));
			context.getInput().lookAt(rot[0], rot[1]);
			context.waitTicks(3);
		}
	}

	private static AABB area(BlockPos at) {
		return new AABB(at).inflate(16);
	}

	private static Map<UUID, Integer> serverVariants(Session s, BlockPos at) {
		return s.world.getServer().computeOnServer(server -> {
			Map<UUID, Integer> map = new HashMap<>();
			for (TraderEntity t : server.overworld().getEntitiesOfClass(TraderEntity.class, area(at), e -> e.entityTags().contains(TAG))) {
				map.put(t.getUUID(), t.variant());
			}
			return map;
		});
	}

	private static Map<UUID, Integer> clientVariants(net.minecraft.client.Minecraft client, BlockPos at) {
		Map<UUID, Integer> map = new HashMap<>();
		if (client.level == null) return map;
		for (TraderEntity t : client.level.getEntitiesOfClass(TraderEntity.class, area(at))) map.put(t.getUUID(), t.variant());
		return map;
	}

	private static Map<UUID, Integer> clientVariantsNow(ClientGameTestContext context, BlockPos at) {
		return context.computeOnClient(c -> clientVariants(c, at));
	}
}
