package fr.citizen.eclipse.network;

import fr.citizen.eclipse.CitizenEclipse;
import fr.citizen.eclipse.schedule.Eclipse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serveur → client : horaires de l'éclipse en cours ou à venir (0 = aucune),
 * plus l'heure du serveur pour corriger le décalage d'horloge du joueur.
 */
public record EclipseStatePayload(long serverNow, long approachStart, long totalityStart, long totalityEnd, long recedeEnd)
        implements CustomPacketPayload {

    public static final Type<EclipseStatePayload> TYPE = new Type<>(CitizenEclipse.id("state"));

    public static final StreamCodec<ByteBuf, EclipseStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, EclipseStatePayload::serverNow,
            ByteBufCodecs.VAR_LONG, EclipseStatePayload::approachStart,
            ByteBufCodecs.VAR_LONG, EclipseStatePayload::totalityStart,
            ByteBufCodecs.VAR_LONG, EclipseStatePayload::totalityEnd,
            ByteBufCodecs.VAR_LONG, EclipseStatePayload::recedeEnd,
            EclipseStatePayload::new);

    public static EclipseStatePayload of(Eclipse e, long now) {
        if (e == null) return new EclipseStatePayload(now, 0, 0, 0, 0);
        return new EclipseStatePayload(now, e.approachStart, e.totalityStart, e.totalityEnd, e.recedeEnd);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Ne référence aucune classe client : sûr à charger sur le serveur dédié. */
    public static void handle(EclipseStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientEclipseState.update(payload));
    }
}
