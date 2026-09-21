package betamoon.network.transport;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.src.Packet;

/** Collision-checked registration against Beta 1.7.3's private packet maps. */
public final class PacketRegistration {
    public static final String PACKET_ID_PROPERTY = "betamoon.network.packetId";
    public static final int DEFAULT_PACKET_ID = 250;

    private static Integer registeredId;

    private PacketRegistration() {
    }

    public static synchronized int register() {
        int packetId = configuredPacketId();
        if (registeredId != null) {
            if (registeredId.intValue() != packetId) {
                throw new IllegalStateException("BetaMoon packet ID changed after registration");
            }
            return packetId;
        }
        Packet occupied = Packet.getNewPacket(packetId);
        if (occupied != null) {
            if (occupied.getClass() == PacketBetaMoon.class) {
                registeredId = Integer.valueOf(packetId);
                return packetId;
            }
            throw new IllegalStateException("BetaMoon packet ID " + packetId + " is already used by "
                    + occupied.getClass().getName() + "; configure " + PACKET_ID_PROPERTY);
        }
        Method registrar = findRegistrar();
        try {
            registrar.setAccessible(true);
            registrar.invoke(null, Integer.valueOf(packetId), Boolean.TRUE, Boolean.TRUE, PacketBetaMoon.class);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot access the Minecraft packet registry", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            throw new IllegalStateException("Cannot register BetaMoon packet ID " + packetId + ": "
                    + cause.getMessage(), cause);
        }
        registeredId = Integer.valueOf(packetId);
        return packetId;
    }

    public static PacketBetaMoon packet(byte[] encodedMessage) {
        if (registeredId == null) {
            throw new IllegalStateException("BetaMoon network packet is not registered");
        }
        return new PacketBetaMoon(encodedMessage);
    }

    private static int configuredPacketId() {
        String configured = System.getProperty(PACKET_ID_PROPERTY);
        int packetId = DEFAULT_PACKET_ID;
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                packetId = Integer.parseInt(configured.trim());
            } catch (NumberFormatException error) {
                throw new IllegalStateException("Invalid " + PACKET_ID_PROPERTY + ": " + configured);
            }
        }
        if (packetId < 201 || packetId > 254 || packetId == 230) {
            throw new IllegalStateException("BetaMoon packet ID must be 201..254 except reserved ModLoaderMP ID 230");
        }
        return packetId;
    }

    private static Method findRegistrar() {
        for (Method method : Packet.class.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Void.TYPE
                    && parameters.length == 4 && parameters[0] == Integer.TYPE
                    && parameters[1] == Boolean.TYPE && parameters[2] == Boolean.TYPE
                    && parameters[3] == Class.class) {
                return method;
            }
        }
        throw new IllegalStateException("Minecraft packet registration method was not found");
    }
}
