package com.skyraax.logisticmatica.share;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.skyraax.logisticmatica.Logisticmatica;

/** One versioned, bounded envelope for every server-to-client sharing event. */
public record ClientboundSharePayload(
		int protocolVersion,
		ShareProtocol.ClientboundEvent event,
		UUID requestId,
		byte[] body) implements CustomPacketPayload {
	public static final Type<ClientboundSharePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Logisticmatica.MOD_ID, "sharing_s2c_v1"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSharePayload> CODEC = StreamCodec.of(
			(buffer, payload) -> payload.write(buffer),
			ClientboundSharePayload::read);

	public ClientboundSharePayload {
		body = body.clone();
	}

	public static ClientboundSharePayload of(ShareProtocol.ClientboundEvent event, UUID requestId, byte[] body) {
		return new ClientboundSharePayload(ShareProtocol.VERSION, event, requestId, body);
	}

	@Override
	public byte[] body() {
		return this.body.clone();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(this.protocolVersion);
		buffer.writeVarInt(this.event.ordinal());
		buffer.writeUUID(this.requestId);
		buffer.writeByteArray(this.body);
	}

	private static ClientboundSharePayload read(RegistryFriendlyByteBuf buffer) {
		int protocolVersion = buffer.readVarInt();
		ShareProtocol.ClientboundEvent event = ShareProtocol.ClientboundEvent.byId(buffer.readVarInt());
		UUID requestId = buffer.readUUID();
		byte[] body = buffer.readByteArray(ShareProtocol.MAX_ENVELOPE_BYTES);
		return new ClientboundSharePayload(protocolVersion, event, requestId, body);
	}
}
