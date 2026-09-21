package betamoon.client.network;

import betamoon.network.protocol.EntityResyncRequestMessage;

/** Client transport boundary used when an entity data revision gap is detected. */
public interface EntityResyncSender {
    void send(EntityResyncRequestMessage request);
}
