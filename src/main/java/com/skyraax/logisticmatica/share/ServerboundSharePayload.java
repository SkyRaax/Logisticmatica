package com.skyraax.logisticmatica.share;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.skyraax.logisticmatica.Logisticmatica;

/** One versioned, bounded envelope for every client-to-server sharing action. */
public record ServerboundSharePayload(
		int protocolVersion,
		ShareProtocol.ServerboundAction action,
		UUID requestId,
		byte[] body) implements CustomPacketPayload {
	public static final Type<ServerboundSharePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Logisticmatica.MOD_ID, "sharing_c2s_v1"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSharePayload> CODEC = StreamCodec.of(
			(buffer, payload) -> payload.write(buffer),
			ServerboundSharePayload::read);

	public ServerboundSharePayload {
		body = body.clone();
	}

	public static ServerboundSharePayload of(ShareProtocol.ServerboundAction action, byte[] body) {
		return new ServerboundSharePayload(ShareProtocol.VERSION, action, UUID.randomUUID(), body);
	}

	public static ServerboundSharePayload empty(ShareProtocol.ServerboundAction action) {
		return of(action, new byte[0]);
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
		buffer.writeVarInt(this.action.ordinal());
		buffer.writeUUID(this.requestId);
		buffer.writeByteArray(this.body);
	}

	private static ServerboundSharePayload read(RegistryFriendlyByteBuf buffer) {
		int protocolVersion = buffer.readVarInt();
		ShareProtocol.ServerboundAction action = ShareProtocol.ServerboundAction.byId(buffer.readVarInt());
		UUID requestId = buffer.readUUID();
		byte[] body = buffer.readByteArray(ShareProtocol.MAX_ENVELOPE_BYTES);
		return new ServerboundSharePayload(protocolVersion, action, requestId, body);
	}
}
