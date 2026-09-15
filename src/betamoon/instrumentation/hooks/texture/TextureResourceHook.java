package betamoon.instrumentation.hooks.texture;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;

/** Feeds virtual PNG bytes through native caching, including native refresh. */
public final class TextureResourceHook implements HookModule {
    public static final String ID = "betamoon:lua_texture_resource";
    private static final ClassRef ENGINE = new ClassRef("net/minecraft/src/RenderEngine");
    private static final String CALLBACK = "betamoon/instrumentation/hooks/texture/TextureResourceCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(
                new CallRedirectHookDefinition(ID, new MethodRef(ENGINE, "getTexture", "(Ljava/lang/String;)I"),
                        new MethodRef(new ClassRef("net/minecraft/src/TexturePackBase"), "getResourceAsStream",
                                "(Ljava/lang/String;)Ljava/io/InputStream;"),
                        HandlerRef.of(CALLBACK, "openTexture",
                                "(Lnet/minecraft/src/TexturePackBase;Ljava/lang/String;)Ljava/io/InputStream;"))
                        .inAllMethods());
        registrar
                .register(AroundHookDefinition.builder(ID + ":refresh", new MethodRef(ENGINE, "refreshTextures", "()V"))
                        .capture(HandlerRef.of(CALLBACK, "beforeRefresh", "()I"))
                        .onReturn(HandlerRef.of(CALLBACK, "afterRefresh", "()V")).build());
    }
}
