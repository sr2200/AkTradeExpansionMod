package com.akf.tradeexpansion.test.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The particle and sound packets the server sends to this client (ClientPacketListenerMixin), while a case records
 * them: what they are and where.
 */
public final class ParticleRecorder {
	private ParticleRecorder() {
	}

	/**
	 * One particle packet. {@code color}: 0xRRGGBB of a dust particle, otherwise -1. {@code block}: the block of a
	 * block particle (e.g. "minecraft:oak_planks"), otherwise {@code null}.
	 */
	public record Particle(Identifier type, int color, String block, double x, double y, double z, int count) {
		public boolean dust() {
			return color >= 0;
		}
	}

	/** One sound packet (a sound at a place). */
	public record Sound(Identifier id, double x, double y, double z) {
	}

	private static final List<Particle> PARTICLES = new ArrayList<>();
	private static final List<Sound> SOUNDS = new ArrayList<>();
	private static volatile boolean recording;

	/** Called on the render thread for every particle packet. */
	public static void onPacket(ClientboundLevelParticlesPacket packet) {
		if (!recording || !Minecraft.getInstance().isSameThread()) return;
		int color = -1;
		String block = null;
		if (packet.particle() instanceof DustParticleOptions dust) {
			Vector3f c = dust.getColor();
			color = Math.round(c.x() * 255) << 16 | Math.round(c.y() * 255) << 8 | Math.round(c.z() * 255);
		} else if (packet.particle() instanceof BlockParticleOption b) {
			block = BuiltInRegistries.BLOCK.getKey(b.getState().getBlock()).toString();
		}
		Particle p = new Particle(BuiltInRegistries.PARTICLE_TYPE.getKey(packet.particle().getType()), color, block,
				packet.x(), packet.y(), packet.z(), packet.count());
		synchronized (PARTICLES) {
			PARTICLES.add(p);
		}
	}

	/** Called on the render thread for every sound packet. */
	public static void onSound(ClientboundSoundPacket packet) {
		if (!recording || !Minecraft.getInstance().isSameThread()) return;
		Identifier id = packet.getSound().value().location();
		synchronized (PARTICLES) {
			SOUNDS.add(new Sound(id, packet.getX(), packet.getY(), packet.getZ()));
		}
	}

	/** Forgets what was recorded and records from now on. */
	public static void start() {
		synchronized (PARTICLES) {
			PARTICLES.clear();
			SOUNDS.clear();
		}
		recording = true;
	}

	/** The particles recorded since {@link #start}. */
	public static List<Particle> particles() {
		synchronized (PARTICLES) {
			return new ArrayList<>(PARTICLES);
		}
	}

	/** The dust particles recorded since {@link #start}. */
	public static List<Particle> dust() {
		return particles().stream().filter(Particle::dust).toList();
	}

	/** The sounds recorded since {@link #start}. */
	public static List<Sound> sounds() {
		synchronized (PARTICLES) {
			return new ArrayList<>(SOUNDS);
		}
	}

	public static void stop() {
		recording = false;
		synchronized (PARTICLES) {
			PARTICLES.clear();
			SOUNDS.clear();
		}
	}
}
