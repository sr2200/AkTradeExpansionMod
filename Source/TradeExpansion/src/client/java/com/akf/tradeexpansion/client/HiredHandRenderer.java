package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.entity.HiredHandEntity;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * An employee at its home market (design Q-6, v0.83): the villager model, dressed as a plains villager with a
 * profession's clothes by role (carrier: leatherworker, clerk: librarian). The vanilla textures are put together into
 * one at load time (so resource packs that change them change the employees too), reloaded with the other textures.
 */
public class HiredHandRenderer extends MobRenderer<HiredHandEntity, HiredHandRenderer.State, VillagerModel> {
	private static final Identifier BASE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
	private static final Identifier TYPE = Identifier.withDefaultNamespace("textures/entity/villager/type/plains.png");
	private static final Identifier[] PROFESSIONS = {
			Identifier.withDefaultNamespace("textures/entity/villager/profession/leatherworker.png"),
			Identifier.withDefaultNamespace("textures/entity/villager/profession/librarian.png")};
	private static final Identifier[] TEXTURES = {TradeExpansion.id("textures/entity/hired_hand/carrier"), TradeExpansion.id("textures/entity/hired_hand/clerk")};
	private static boolean registered;

	public HiredHandRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HiredHandEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.role = entity.role();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		registerTextures();
		return TEXTURES[Math.clamp(state.role, 0, TEXTURES.length - 1)];
	}

	/** Registers the put-together textures on first use (render thread); the texture manager reloads them afterwards. */
	private static void registerTextures() {
		if (registered) return;
		registered = true;
		var manager = Minecraft.getInstance().getTextureManager();
		for (int i = 0; i < TEXTURES.length; i++) manager.registerAndLoad(TEXTURES[i], new LayeredTexture(TEXTURES[i], PROFESSIONS[i]));
	}

	public static class State extends VillagerRenderState {
		public int role;
	}

	/** The base villager with the plains clothes and a profession's clothes drawn over it (alpha blended). */
	private static class LayeredTexture extends ReloadableTexture {
		private final Identifier profession;

		LayeredTexture(Identifier id, Identifier profession) {
			super(id);
			this.profession = profession;
		}

		@Override
		public TextureContents loadContents(ResourceManager resources) throws IOException {
			TextureContents contents = TextureContents.load(resources, BASE);
			NativeImage image = contents.image();
			for (Identifier layer : new Identifier[] {TYPE, profession}) {
				try (TextureContents over = TextureContents.load(resources, layer)) {
					NativeImage top = over.image();
					int w = Math.min(image.getWidth(), top.getWidth());
					int h = Math.min(image.getHeight(), top.getHeight());
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) image.setPixel(x, y, blend(image.getPixel(x, y), top.getPixel(x, y)));
					}
				}
			}
			return contents;
		}

		/** {@code top} over {@code bottom} (ARGB). */
		private static int blend(int bottom, int top) {
			int a = top >>> 24;
			if (a == 0) return bottom;
			if (a == 255) return top;
			int ba = bottom >>> 24;
			int outA = a + ba * (255 - a) / 255;
			int r = mix(top >> 16 & 0xFF, bottom >> 16 & 0xFF, a);
			int g = mix(top >> 8 & 0xFF, bottom >> 8 & 0xFF, a);
			int b = mix(top & 0xFF, bottom & 0xFF, a);
			return outA << 24 | r << 16 | g << 8 | b;
		}

		private static int mix(int top, int bottom, int a) {
			return (top * a + bottom * (255 - a)) / 255;
		}
	}
}
