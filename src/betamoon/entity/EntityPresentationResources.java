package betamoon.entity;

import java.io.IOException;

/**
 * Side-neutral boundary for validating and refreshing entity presentation assets.
 * Dedicated servers intentionally retain the no-op provider.
 */
public final class EntityPresentationResources {
    public interface Provider {
        void validate(EntityTypeDefinition definition) throws IOException;

        void prune();

        void retryFailed();
    }

    private static final Provider HEADLESS = new Provider() {
        @Override
        public void validate(EntityTypeDefinition definition) {
        }

        @Override
        public void prune() {
        }

        @Override
        public void retryFailed() {
        }
    };

    private static volatile Provider provider = HEADLESS;

    private EntityPresentationResources() {
    }

    public static void install(Provider next) {
        provider = next == null ? HEADLESS : next;
    }

    public static void validate(EntityTypeDefinition definition) throws IOException {
        provider.validate(definition);
    }

    public static void prune() {
        provider.prune();
    }

    public static void retryFailed() {
        provider.retryFailed();
    }
}
