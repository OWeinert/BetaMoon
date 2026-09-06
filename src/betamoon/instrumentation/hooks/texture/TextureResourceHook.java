package betamoon.instrumentation.hooks.texture;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Uploads standalone Lua images through Minecraft's common texture cache. */
public final class TextureResourceHook implements HookModule {
    public static final String ID = "betamoon:lua_texture_resource";
    private static final MethodRef TARGET = new MethodRef(
        new ClassRef("net/minecraft/src/RenderEngine"),
        "getTexture", "(Ljava/lang/String;)I");
    private static final String CALLBACK_OWNER =
        "betamoon/instrumentation/hooks/texture/TextureResourceCallbacks";

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(ID, TARGET)
            .capture(HandlerRef.of(CALLBACK_OWNER, "findLuaTexture",
                "(Ljava/lang/String;)Ljava/awt/image/BufferedImage;"), ValueBinding.argument(0))
            .onReturn(HandlerRef.of(CALLBACK_OWNER, "uploadLuaTexture",
                "(Lnet/minecraft/src/RenderEngine;ILjava/awt/image/BufferedImage;)I"),
                ValueBinding.thisValue(), ValueBinding.returnValue(), ValueBinding.capturedValue())
            .build());
    }
}
