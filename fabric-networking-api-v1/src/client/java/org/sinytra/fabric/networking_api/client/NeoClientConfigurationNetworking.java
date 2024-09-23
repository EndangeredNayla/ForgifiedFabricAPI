package org.sinytra.fabric.networking_api.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.sinytra.fabric.networking_api.NeoCommonNetworking;

import java.util.Objects;
import java.util.Set;

public class NeoClientConfigurationNetworking {
    private static ICommonPacketListener configurationPacketListener;

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, ClientConfigurationNetworking.ConfigurationPayloadHandler<T> handler) {
        NeoCommonNetworking.assertPayloadType(PayloadTypeRegistryImpl.CONFIGURATION_S2C, type.id(), PacketFlow.CLIENTBOUND, ConnectionProtocol.CONFIGURATION);
        return NeoCommonNetworking.CONFIGURATION_REGISTRY.registerGlobalReceiver(type, PacketFlow.CLIENTBOUND, handler, ClientConfigNeoContextWrapper::new, ClientConfigurationNetworking.ConfigurationPayloadHandler::receive);
    }

    public static ClientConfigurationNetworking.ConfigurationPayloadHandler<?> unregisterGlobalReceiver(ResourceLocation id) {
        return NeoCommonNetworking.CONFIGURATION_REGISTRY.unregisterGlobalReceiver(id, PacketFlow.CLIENTBOUND);
    }

    public static Set<ResourceLocation> getGlobalReceivers() {
        return NeoCommonNetworking.CONFIGURATION_REGISTRY.getGlobalReceivers(PacketFlow.CLIENTBOUND);
    }

    public static <T extends CustomPacketPayload> boolean registerReceiver(CustomPacketPayload.Type<T> type, ClientConfigurationNetworking.ConfigurationPayloadHandler<T> handler) {
        NeoCommonNetworking.assertPayloadType(PayloadTypeRegistryImpl.CONFIGURATION_S2C, type.id(), PacketFlow.CLIENTBOUND, ConnectionProtocol.CONFIGURATION);
        ICommonPacketListener listener = Objects.requireNonNull(configurationPacketListener, "Cannot register receiver while not configuring!");
        return NeoCommonNetworking.CONFIGURATION_REGISTRY.registerLocalReceiver(type, listener, handler, ClientConfigNeoContextWrapper::new, ClientConfigurationNetworking.ConfigurationPayloadHandler::receive);
    }

    public static ClientConfigurationNetworking.ConfigurationPayloadHandler<?> unregisterReceiver(ResourceLocation id) {
        ICommonPacketListener listener = Objects.requireNonNull(configurationPacketListener, "Cannot unregister receiver while not configuring!");
        return NeoCommonNetworking.CONFIGURATION_REGISTRY.unregisterLocalReceiver(id, listener);
    }

    public static Set<ResourceLocation> getReceived() throws IllegalStateException {
        ICommonPacketListener listener = Objects.requireNonNull(configurationPacketListener, "Cannot get a list of channels the client can receive packets on while not configuring!");
        return NeoCommonNetworking.CONFIGURATION_REGISTRY.getLocalReceivers(listener);
    }

    public static Set<ResourceLocation> getSendable() throws IllegalStateException {
        ICommonPacketListener listener = Objects.requireNonNull(configurationPacketListener, "Cannot get a list of channels the server can receive packets on while not configuring!");
        return NeoCommonNetworking.CONFIGURATION_REGISTRY.getLocalSendable(listener);
    }

    public static boolean canSend(ResourceLocation channelName) throws IllegalArgumentException {
//        ICommonPacketListener listener = Objects.requireNonNull(configurationPacketListener, "Cannot get a list of channels the server can receive packets on while not configuring!");
        return NeoCommonNetworking.CONFIGURATION_REGISTRY.getGlobalReceivers(PacketFlow.SERVERBOUND).contains(channelName);
    }

    public static PacketSender getSender() {
        ICommonPacketListener listener = Objects.requireNonNull(configurationPacketListener, "Cannot get PacketSender while not configuring!");
        return new NeoClientPacketSender(listener.getConnection());
    }

    public static void send(CustomPacketPayload payload) {
        Objects.requireNonNull(payload, "Payload cannot be null");
        Objects.requireNonNull(payload.type(), "CustomPayload#getId() cannot return null for payload class: " + payload.getClass());

        if (configurationPacketListener != null) {
            new NeoClientPacketSender(configurationPacketListener.getConnection()).sendPacket(payload);
            return;
        }

        throw new IllegalStateException("Cannot send packet while not configuring!");
    }

    public static void setClientConfigurationAddon(ICommonPacketListener listener) {
        configurationPacketListener = listener;
    }

    private record ClientConfigNeoContextWrapper(IPayloadContext context) implements ClientConfigurationNetworking.Context {
        @Override
        public Minecraft client() {
            return Minecraft.getInstance();
        }

        @Override
        public ClientConfigurationPacketListenerImpl networkHandler() {
            return (ClientConfigurationPacketListenerImpl) configurationPacketListener;
        }

        @Override
        public PacketSender responseSender() {
            return new NeoClientPacketSender(context.connection());
        }
    }
}
