/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.resource.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.sinytra.fabric.resource_loader.generated.GeneratedEntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.fabricmc.fabric.api.resource.ModResourcePack;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * Internal utilities for managing resource packs.
 */
public final class ModResourcePackUtil {
	public static final Gson GSON = new Gson();
	private static final Logger LOGGER = LoggerFactory.getLogger(ModResourcePackUtil.class);

	private ModResourcePackUtil() {
	}

	/**
	 * Appends mod resource packs to the given list.
	 *
	 * @param packs   the resource pack list to append
	 * @param type    the type of resource
	 * @param subPath the resource pack sub path directory in mods, may be {@code null}
	 */
	public static void appendModResourcePacks(List<ModResourcePack> packs, PackType type, @Nullable String subPath) {
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			if (container.getMetadata().getType().equals("builtin")) {
				continue;
			}

			ModResourcePack pack = ModNioResourcePack.create(container.getMetadata().getId(), container, subPath, type, ResourcePackActivationType.ALWAYS_ENABLED, true);

			if (pack != null) {
				packs.add(pack);
			}
		}
	}

	public static void refreshAutoEnabledPacks(List<Pack> enabledProfiles, Map<String, Pack> allProfiles) {
		LOGGER.debug("[Fabric] Starting internal pack sorting with: {}", enabledProfiles.stream().map(Pack::getId).toList());
		enabledProfiles.removeIf(profile -> ((FabricResourcePackProfile) profile).fabric_isHidden());
		LOGGER.debug("[Fabric] Removed all internal packs, result: {}", enabledProfiles.stream().map(Pack::getId).toList());
		ListIterator<Pack> it = enabledProfiles.listIterator();
		Set<String> seen = new LinkedHashSet<>();

		while (it.hasNext()) {
			Pack profile = it.next();
			seen.add(profile.getId());

			for (Pack p : allProfiles.values()) {
				FabricResourcePackProfile fp = (FabricResourcePackProfile) p;

				if (fp.fabric_isHidden() && fp.fabric_parentsEnabled(seen) && seen.add(p.getId())) {
					it.add(p);
					LOGGER.debug("[Fabric] cur @ {}, auto-enabled {}, currently enabled: {}", profile.getId(), p.getId(), seen);
				}
			}
		}

		LOGGER.debug("[Fabric] Final sorting result: {}", enabledProfiles.stream().map(Pack::getId).toList());
	}

	public static boolean containsDefault(String filename, boolean modBundled) {
		return "pack.mcmeta".equals(filename) || (modBundled && "pack.png".equals(filename));
	}

	public static InputStream getDefaultIcon() throws IOException {
		Optional<Path> loaderIconPath = FabricLoader.getInstance().getModContainer(GeneratedEntryPoint.MOD_ID)
				.flatMap(resourceLoaderContainer -> resourceLoaderContainer.getMetadata().getIconPath(512).flatMap(resourceLoaderContainer::findPath));

		if (loaderIconPath.isPresent()) {
			return Files.newInputStream(loaderIconPath.get());
		}

		// Should never happen in practice
		return null;
	}

	public static InputStream openDefault(ModContainer container, PackType type, String filename) throws IOException {
		switch (filename) {
		case "pack.mcmeta":
			String description = Objects.requireNonNullElse(container.getMetadata().getId(), "");
			String metadata = serializeMetadata(SharedConstants.getCurrentVersion().getPackVersion(type), description);
			return IOUtils.toInputStream(metadata, Charsets.UTF_8);
		case "pack.png":
			Optional<Path> path = container.getMetadata().getIconPath(512).flatMap(container::findPath);

			if (path.isPresent()) {
				return Files.newInputStream(path.get());
			} else {
				return getDefaultIcon();
			}
		default:
			return null;
		}
	}

	public static PackMetadataSection getMetadataPack(int packVersion, Component description) {
		return new PackMetadataSection(description, packVersion, Optional.empty());
	}

	public static JsonObject getMetadataPackJson(int packVersion, Component description) {
		return PackMetadataSection.TYPE.toJson(getMetadataPack(packVersion, description));
	}

	public static String serializeMetadata(int packVersion, String description) {
		// This seems to be still manually deserialized
		JsonObject pack = getMetadataPackJson(packVersion, Component.literal(description));
		JsonObject metadata = new JsonObject();
		metadata.add("pack", pack);
		return GSON.toJson(metadata);
	}

	public static Component getName(ModMetadata info) {
		if (info.getId() != null) {
			return Component.literal(info.getId());
		} else {
			return Component.translatable("pack.name.fabricMod", info.getId());
		}
	}

	/**
	 * Creates the default data pack settings that replaces
	 * {@code DataPackSettings.SAFE_MODE} used in vanilla.
	 * @return the default data pack settings
	 */
	public static WorldDataConfiguration createDefaultDataConfiguration() {
		ModResourcePackCreator modResourcePackCreator = new ModResourcePackCreator(PackType.SERVER_DATA);
		List<Pack> moddedResourcePacks = new ArrayList<>();
		modResourcePackCreator.loadPacks(moddedResourcePacks::add);

		List<String> enabled = new ArrayList<>(DataPackConfig.DEFAULT.getEnabled());
		List<String> disabled = new ArrayList<>(DataPackConfig.DEFAULT.getDisabled());

		// This ensures that any built-in registered data packs by mods which needs to be enabled by default are
		// as the data pack screen automatically put any data pack as disabled except the Default data pack.
		for (Pack profile : moddedResourcePacks) {
			if (profile.getPackSource() == ModResourcePackCreator.RESOURCE_PACK_SOURCE) {
				enabled.add(profile.getId());
				continue;
			}

			try (PackResources pack = profile.open()) {
				if (pack instanceof ModNioResourcePack && ((ModNioResourcePack) pack).getActivationType().isEnabledByDefault()) {
					enabled.add(profile.getId());
				} else {
					disabled.add(profile.getId());
				}
			}
		}

		return new WorldDataConfiguration(
				new DataPackConfig(enabled, disabled),
				FeatureFlags.DEFAULT_FLAGS
		);
	}

	/**
	 * Vanilla enables all available datapacks automatically in TestServer#create, but it does so in alphabetical order,
	 * which means the Vanilla pack has higher precedence than modded, breaking our tests.
	 * To fix this, we move all modded pack profiles to the end of the list.
	 */
	public static DataPackConfig createTestServerSettings(List<String> enabled, List<String> disabled) {
		// Collect modded profiles
		Set<String> moddedProfiles = new HashSet<>();
		ModResourcePackCreator modResourcePackCreator = new ModResourcePackCreator(PackType.SERVER_DATA);
		modResourcePackCreator.loadPacks(profile -> moddedProfiles.add(profile.getId()));

		// Remove them from the enabled list
		List<String> moveToTheEnd = new ArrayList<>();

		for (Iterator<String> it = enabled.iterator(); it.hasNext();) {
			String profile = it.next();

			if (moddedProfiles.contains(profile)) {
				moveToTheEnd.add(profile);
				it.remove();
			}
		}

		// Add back at the end
		enabled.addAll(moveToTheEnd);

		return new DataPackConfig(enabled, disabled);
	}

	/**
	 * Creates the ResousePackManager used by the ClientDataPackManager and replaces
	 * {@code VanillaDataPackProvider.createClientManager} used by vanilla.
	 */
	public static PackRepository createClientManager() {
		return new PackRepository(new ServerPacksSource(new DirectoryValidator((path) -> true)), new ModResourcePackCreator(PackType.SERVER_DATA, true));
	}
}
