package betamoon.client.render;

import betamoon.entity.EntityPresentationResources;
import betamoon.entity.EntityTypeDefinition;
import java.io.IOException;

/** Installs the client renderer as the presentation-resource provider. */
public final class ClientEntityPresentationResources {
    private ClientEntityPresentationResources() {
    }

    public static void initialize() {
        EntityPresentationResources.install(new EntityPresentationResources.Provider() {
            @Override
            public void validate(EntityTypeDefinition definition) throws IOException {
                EntityVisuals.validate(definition);
            }

            @Override
            public void prune() {
                EntityVisuals.prune();
            }

            @Override
            public void retryFailed() {
                EntityVisuals.retryFailed();
            }
        });
    }
}
