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

package net.fabricmc.fabric.impl.event.lifecycle;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class LifecycleEventsImpl implements ModInitializer {
    @Override
    public void onInitialize() {
        // Part of impl for block entity events
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            ((LoadedChunksCache) world).fabric_markLoaded(chunk);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            ((LoadedChunksCache) world).fabric_markUnloaded(chunk);
        });

        // Fire block entity unload events.
        // This handles the edge case where going through a portal will cause block entities to unload without warning.
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(blockEntity, world);
            }
        });

        // We use the world unload event so worlds that are dynamically hot(un)loaded get (block) entity unload events fired when shut down.
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            for (LevelChunk chunk : ((LoadedChunksCache) world).fabric_getLoadedChunks()) {
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(blockEntity, world);
                }
            }

            for (Entity entity : world.getAllEntities()) {
                ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(entity, world);
            }
        });

        // Sinytra impl
        NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent.class, ev -> CommonLifecycleEvents.TAGS_LOADED.invoker().onTagsLoaded(ev.getRegistryAccess(), ev.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED));
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, ev -> {
            if (ev.getLevel() instanceof ServerLevel sw && ev.getChunk() instanceof LevelChunk wc) {
                ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(sw, wc);
                if (ev.isNewChunk()) {
                    ServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(sw, wc);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, ev -> {
            if (ev.getLevel() instanceof ServerLevel sw && ev.getChunk() instanceof LevelChunk wc) {
                ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(sw, wc);
            }
        });
        NeoForge.EVENT_BUS.addListener(LivingEquipmentChangeEvent.class, ev -> ServerEntityEvents.EQUIPMENT_CHANGE.invoker().onChange(ev.getEntity(), ev.getSlot(), ev.getFrom(), ev.getTo()));
        // Server events
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, ev -> ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(ev.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, ev -> ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(ev.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, ev -> ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(ev.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, ev -> ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(ev.getServer()));
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, ev -> {
            if (ev.getPlayer() != null) {
                ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(ev.getPlayer(), true);
            } else {
                for (ServerPlayer player : ev.getPlayerList().getPlayers()) {
                    ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, false);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, ev -> ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(ev.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ev -> ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(ev.getServer()));
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, ev -> {
            if (ev.getLevel() instanceof ServerLevel sw) {
                ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(sw);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, ev -> {
            if (ev.getLevel() instanceof ServerLevel sw) {
                ServerWorldEvents.LOAD.invoker().onWorldLoad(sw.getServer(), sw);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, ev -> {
            if (ev.getLevel() instanceof ServerLevel sw) {
                ServerWorldEvents.UNLOAD.invoker().onWorldUnload(sw.getServer(), sw);
            }
        });
    }
}
