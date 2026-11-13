package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.TraderPalette;
import com.akf.tradeexpansion.entity.TraderEntity;
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
 * Trader NPC (design A-5): the villager model with the wandering trader's robe. Since v0.60 the robe comes in
 * {@link TraderPalette#VARIANT_COUNT} colours; each colour is the wandering trader's texture recoloured at load time
 * (so resource packs that change that texture change the traders too), reloaded with the other textures.
 */
public class TraderRenderer extends MobRenderer<TraderEntity, TraderRenderer.State, VillagerModel> {
	private static final Identifier SOURCE = Identifier.withDefaultNamespace("textures/entity/wandering_trader/wandering_trader.png");
	private static final Identifier[] TEXTURES = new Identifier[TraderPalette.VARIANT_COUNT];
	private static boolean registered;

	static {
		TEXTURES[0] = SOURCE;
		for (int i = 1; i < TEXTURES.length; i++) TEXTURES[i] = TradeExpansion.id("textures/entity/trader/variant_" + i);
	}

	public TraderRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel(context.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TraderEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.variant = entity.variant();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		registerTextures();
		return TEXTURES[TraderPalette.clamp(state.variant)];
	}

	/** Registers the recoloured textures on first use (render thread); the texture manager reloads them afterwards. */
	private static void registerTextures() {
		if (registered) return;
		registered = true;
		var manager = Minecraft.getInstance().getTextureManager();
		for (int i = 1; i < TEXTURES.length; i++) manager.registerAndLoad(TEXTURES[i], new VariantTexture(TEXTURES[i], i));
	}

	public static class State extends VillagerRenderState {
		public int variant;
	}

	private static class VariantTexture extends ReloadableTexture {
		private final int variant;

		VariantTexture(Identifier id, int variant) {
			super(id);
			this.variant = variant;
		}

		@Override
		public TextureContents loadContents(ResourceManager resources) throws IOException {
			TextureContents contents = TextureContents.load(resources, SOURCE);
			NativeImage image = contents.image();
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) image.setPixel(x, y, TraderPalette.recolor(image.getPixel(x, y), variant));
			}
			return contents;
		}
	}
}
