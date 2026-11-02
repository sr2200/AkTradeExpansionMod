package com.akf.tradeexpansion.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu's "Configure" button (design G, Q35). Only loaded when Mod Menu is installed; the YACL screen class is
 * only touched when YACL is installed too, otherwise Mod Menu shows no button.
 */
public class ModMenuIntegration implements ModMenuApi {
	private static final String YACL_MOD_ID = "yet_another_config_lib_v3";

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (!FabricLoader.getInstance().isModLoaded(YACL_MOD_ID)) return parent -> null;
		return ConfigScreen::create;
	}
}
