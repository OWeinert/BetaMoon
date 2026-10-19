package betamoon.instrumentation.hooks.dimension;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldReadRedirectHookDefinition;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Native dimension boundaries. Inert unless an explicitly prepared teleport
 * session owns the operation.
 */
public final class DimensionHook implements HookModule {
    public static final String ID = "betamoon:dimensions";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/dimension/DimensionCallbacks";

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        ClassRef loader = new ClassRef("net/minecraft/src/ChunkLoader");
        String protection = "betamoon/worldgen/structure/StructureProtection";
        registrar.register(AroundHookDefinition
                .builder(ID + ":structure_protection_write", new MethodRef(loader, "storeChunkInCompound",
                        "(Lnet/minecraft/src/Chunk;Lnet/minecraft/src/World;Lnet/minecraft/src/NBTTagCompound;)V"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(
                        HandlerRef.of(protection, "write",
                                "(Lnet/minecraft/src/Chunk;Lnet/minecraft/src/NBTTagCompound;)V"),
                        ValueBinding.argument(0), ValueBinding.argument(2))
                .build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":structure_protection_read", new MethodRef(loader, "loadChunkIntoWorldFromCompound",
                        "(Lnet/minecraft/src/World;Lnet/minecraft/src/NBTTagCompound;)Lnet/minecraft/src/Chunk;"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(HandlerRef.of(protection, "read",
                        "(Lnet/minecraft/src/Chunk;Lnet/minecraft/src/NBTTagCompound;)Lnet/minecraft/src/Chunk;"),
                        ValueBinding.returnValue(), ValueBinding.argument(1))
                .build());
        ClassRef cache = new ClassRef("net/minecraft/src/ChunkProvider");
        ClassRef provider = new ClassRef("net/minecraft/src/IChunkProvider");
        registrar
                .register(AroundHookDefinition
                        .builder(ID + ":chunk_access",
                                new MethodRef(provider, "provideChunk", "(II)Lnet/minecraft/src/Chunk;")
                                        .implementedBy(cache))
                        .capture(
                                HandlerRef.of("betamoon/teleport/ChunkPreparation", "beforeAccess",
                                        "(Lnet/minecraft/src/ChunkProvider;II)I"),
                                ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1))
                        .onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V")).build());
        registrar
                .register(AroundHookDefinition
                        .builder(ID + ":chunk_budget",
                                new MethodRef(provider, "prepareChunk", "(II)Lnet/minecraft/src/Chunk;")
                                        .implementedBy(cache))
                        .capture(HandlerRef.of(CALLBACKS, "beforeLoad", "(Lnet/minecraft/src/ChunkProvider;II)I"),
                                ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1))
                        .onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V")).build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":population_budget",
                        new MethodRef(provider, "populate", "(Lnet/minecraft/src/IChunkProvider;II)V")
                                .implementedBy(cache))
                .capture(HandlerRef.of(CALLBACKS, "deferPopulation", "()I")).skipWhenCapturedNonZero()
                .onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V")).build());
        field(registrar, "sky", "RenderGlobal", "renderSky", "(F)V", "isNether", "hideSky");
        field(registrar, "clouds", "RenderGlobal", "renderClouds", "(F)V", "isNether", "hideClouds");
        field(registrar, "compass", "TextureCompassFX", "onTick", "()V", "isNether", "randomCompass");
        field(registrar, "clock", "TextureWatchFX", "onTick", "()V", "isNether", "randomClock");
        field(registrar, "water", "ItemBucket", "onItemRightClick",
                "(Lnet/minecraft/src/ItemStack;Lnet/minecraft/src/World;Lnet/minecraft/src/EntityPlayer;)Lnet/minecraft/src/ItemStack;",
                "isHellWorld", "evaporatesWater");
        field(registrar, "lava", "BlockFlowing", "updateTick", "(Lnet/minecraft/src/World;IIILjava/util/Random;)V",
                "isHellWorld", "fastLava");
        field(registrar, "weather", "World", "updateWeather", "()V", "hasNoSky", "weatherDisabled");
        field(registrar, "sleep", "EntityPlayer", "sleepInBedAt", "(III)Lnet/minecraft/src/EnumStatus;", "isNether",
                "sleepDenied");
        ClassRef player = new ClassRef("net/minecraft/src/EntityPlayer");
        registrar
                .register(
                        AroundHookDefinition
                                .builder(ID + ":portal_charge",
                                        new MethodRef(new ClassRef("net/minecraft/src/EntityLiving"), "onLivingUpdate",
                                                "()V").implementedBy(new ClassRef("net/minecraft/src/EntityPlayerSP")))
                                .capture(HandlerRef.of("betamoon/dimension/DimensionPlayerState",
                                        "suppressPortalCharge", "(Lnet/minecraft/src/EntityPlayerSP;)I"),
                                        ValueBinding.thisValue())
                                .onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V")).build());
        for (String operation : new String[]{"read", "write"}) {
            registrar.register(AroundHookDefinition
                    .builder(ID + ":player_" + operation,
                            new MethodRef(new ClassRef("net/minecraft/src/Entity"),
                                    operation.equals("read") ? "readEntityFromNBT" : "writeEntityToNBT",
                                    "(Lnet/minecraft/src/NBTTagCompound;)V").implementedBy(player))
                    .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                    .onReturn(
                            HandlerRef.of(CALLBACKS, operation + "Player",
                                    "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/NBTTagCompound;)V"),
                            ValueBinding.thisValue(), ValueBinding.argument(0))
                    .build());
        }
        registrar
                .register(
                        AroundHookDefinition
                                .builder(ID + ":bed_record",
                                        new MethodRef(player, "setPlayerSpawnCoordinate",
                                                "(Lnet/minecraft/src/ChunkCoordinates;)V"))
                                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                                .onReturn(HandlerRef.of(CALLBACKS, "bedChanged",
                                        "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/ChunkCoordinates;)V"),
                                        ValueBinding.thisValue(), ValueBinding.argument(0))
                                .build());
        MethodRef bed = new MethodRef(new ClassRef("net/minecraft/src/Block"), "blockActivated",
                "(Lnet/minecraft/src/World;IIILnet/minecraft/src/EntityPlayer;)Z")
                .implementedBy(new ClassRef("net/minecraft/src/BlockBed"));
        registrar.register(new CallRedirectHookDefinition(ID + ":bed_explosion", bed,
                new MethodRef(new ClassRef("net/minecraft/src/WorldProvider"), "canRespawnHere", "()Z"),
                HandlerRef.of("betamoon/dimension/DimensionRules", "bedDoesNotExplode",
                        "(Lnet/minecraft/src/WorldProvider;)Z")));
        registrar.register(AroundHookDefinition.builder(ID + ":bed_disabled", bed)
                .capture(HandlerRef.of(CALLBACKS, "disabledBed", "(Lnet/minecraft/src/World;)I"),
                        ValueBinding.argument(0))
                .skipWhenCapturedNonZero().onReturn(HandlerRef.of(CALLBACKS, "bedResult", "(ZI)Z"),
                        ValueBinding.returnValue(), ValueBinding.capturedValue())
                .build());
        ClassRef minecraft = new ClassRef("net/minecraft/client/Minecraft");
        registrar
                .register(
                        AroundHookDefinition
                                .builder(ID + ":load_screen",
                                        new MethodRef(minecraft, "displayGuiScreen",
                                                "(Lnet/minecraft/src/GuiScreen;)V"))
                                .capture(
                                        HandlerRef.of("betamoon/teleport/SavedDimensionLoad", "suppressScreenClose",
                                                "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/src/GuiScreen;)I"),
                                        ValueBinding.thisValue(), ValueBinding.argument(0))
                                .skipWhenCapturedNonZero()
                                .onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V")).build());
        registrar.register(AroundHookDefinition.builder(ID + ":respawn", new MethodRef(minecraft, "respawn", "(ZI)V"))
                .capture(
                        HandlerRef.of("betamoon/teleport/TeleportRuntime", "respawn",
                                "(Lnet/minecraft/client/Minecraft;Z)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0))
                .skipWhenCapturedNonZero().onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V"))
                .build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":prewarm", new MethodRef(minecraft, "func_6255_d", "(Ljava/lang/String;)V"))
                .capture(HandlerRef.of(CALLBACKS, "skipPrewarm", "()I")).skipWhenCapturedNonZero()
                .onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V")).build());
        registrar.register(new CallRedirectHookDefinition(ID + ":source_save",
                new MethodRef(minecraft, "changeWorld",
                        "(Lnet/minecraft/src/World;Ljava/lang/String;Lnet/minecraft/src/EntityPlayer;)V"),
                new MethodRef(new ClassRef("net/minecraft/src/World"), "saveWorldIndirectly",
                        "(Lnet/minecraft/src/IProgressUpdate;)V"),
                HandlerRef.of(CALLBACKS, "saveBeforeNativeChange",
                        "(Lnet/minecraft/src/World;Lnet/minecraft/src/IProgressUpdate;)V"))
                .allCallsInTarget());
        for (String method : new String[]{"updateEntities", "joinEntityInSurroundings", "randomDisplayUpdates"}) {
            String descriptor = method.equals("updateEntities")
                    ? "()V"
                    : method.equals("joinEntityInSurroundings") ? "(Lnet/minecraft/src/Entity;)V" : "(III)V";
            registrar.register(AroundHookDefinition
                    .builder(ID + ":freeze_" + method,
                            new MethodRef(new ClassRef("net/minecraft/src/World"), method, descriptor))
                    .capture(HandlerRef.of(CALLBACKS, "frozen", "()I")).skipWhenCapturedNonZero()
                    .onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V")).build());
        }
        registrar.register(
                AroundHookDefinition.builder(ID + ":native_portal", new MethodRef(minecraft, "usePortal", "()V"))
                        .capture(HandlerRef.of(CALLBACKS, "suppressPortal", "(Lnet/minecraft/client/Minecraft;)I"),
                                ValueBinding.thisValue())
                        .skipWhenCapturedNonZero()
                        .onReturn(HandlerRef.of(CALLBACKS, "dimensionBoundaryCompleted", "()V")).build());
    }

    private static void field(HookRegistrar registrar, String id, String owner, String method, String descriptor,
            String field, String handler) {
        String declaring = owner.startsWith("Texture")
                ? "TextureFX"
                : owner.equals("ItemBucket") ? "Item" : owner.equals("BlockFlowing") ? "Block" : owner;
        registrar.register(new FieldReadRedirectHookDefinition(ID + ":" + id,
                new MethodRef(new ClassRef("net/minecraft/src/" + declaring), method, descriptor)
                        .implementedBy(new ClassRef("net/minecraft/src/" + owner)),
                new FieldRef(new ClassRef("net/minecraft/src/WorldProvider"), field, "Z"),
                HandlerRef.of("betamoon/dimension/DimensionRules", handler, "(Lnet/minecraft/src/WorldProvider;)Z")));
    }
}
