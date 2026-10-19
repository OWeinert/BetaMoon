package betamoon.client.atmosphere;

import betamoon.assets.AssetKey;
import betamoon.atmosphere.AtmosphereProfile;
import betamoon.atmosphere.AtmosphereRegistry;
import betamoon.client.audio.ClientAudio;
import betamoon.client.render.FluidCameraRuntime;
import betamoon.luaapi.audio.SoundEvents;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;

/**
 * Tick snapshots feed rendering and bounded audio voices without per-frame Lua
 * callbacks.
 */
public final class AtmosphereRuntime {
    private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(4);
    private static final Map<AssetKey, AmbientVoice> VOICES = new HashMap<AssetKey, AmbientVoice>();
    private static AtmosphereRegistry.Presentation presentation;
    private static World currentWorld;
    private static long tick;
    private static int whiteTexture;
    private static ContextCapabilities textureContext;

    private AtmosphereRuntime() {
    }

    public static void tick(Minecraft minecraft) {
        World world = minecraft == null ? null : minecraft.theWorld;
        AtmosphereRegistry.tick(world);
        tick++;
        if (world != currentWorld) {
            for (AmbientVoice voice : VOICES.values()) {
                voice.close();
            }
            VOICES.clear();
            currentWorld = world;
        }
        EntityLiving view = minecraft == null ? null : minecraft.renderViewEntity;
        presentation = world == null || view == null
                ? null
                : AtmosphereRegistry.queryPresentation(world, view.posX, view.posY, view.posZ);
        if (world != null && view != null) {
            audio(world, view);
        }
    }

    private static List<AtmosphereRegistry.Contribution> active(int channel) {
        return presentation == null
                ? Collections.<AtmosphereRegistry.Contribution>emptyList()
                : presentation.channel(channel);
    }

    public static void fogColor(boolean cloud) {
        if (protectedFog(cloud)) {
            return;
        }
        float[] color = blendedColor(0);
        if (color != null) {
            FloatBuffer nativeColor = COLOR;
            nativeColor.clear();
            GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, nativeColor);
            float strength = color[3];
            GL11.glClearColor(nativeColor.get(0) * (1 - strength) + color[0],
                    nativeColor.get(1) * (1 - strength) + color[1], nativeColor.get(2) * (1 - strength) + color[2],
                    nativeColor.get(3));
        }
    }

    public static void fog(boolean cloud) {
        if (protectedFog(cloud)) {
            return;
        }
        float[] color = blendedColor(0);
        if (color != null) {
            COLOR.clear();
            GL11.glGetFloat(GL11.GL_FOG_COLOR, COLOR);
            float strength = color[3];
            float r = COLOR.get(0) * (1 - strength) + color[0];
            float g = COLOR.get(1) * (1 - strength) + color[1];
            float b = COLOR.get(2) * (1 - strength) + color[2];
            COLOR.clear();
            COLOR.put(r).put(g).put(b).put(1).flip();
            GL11.glFog(GL11.GL_FOG_COLOR, COLOR);
        }
        float strength = 0;
        float near = 0;
        float far = 0;
        float density = 0;
        for (AtmosphereRegistry.Contribution value : active(0)) {
            if (value.profile.hasFog) {
                strength += value.weight;
                near += value.profile.near * value.weight;
                far += value.profile.far * value.weight;
                density += value.profile.density * value.weight;
            }
        }
        if (strength > 0) {
            if (density > 0) {
                GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
                GL11.glFogf(GL11.GL_FOG_DENSITY, density);
            } else {
                float nativeNear = GL11.glGetFloat(GL11.GL_FOG_START);
                float nativeFar = GL11.glGetFloat(GL11.GL_FOG_END);
                GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
                GL11.glFogf(GL11.GL_FOG_START, nativeNear * (1 - strength) + near);
                GL11.glFogf(GL11.GL_FOG_END, nativeFar * (1 - strength) + far);
            }
        }
    }

    public static Vec3D sky(World world, Vec3D original) {
        if (world != currentWorld) {
            return original;
        }
        float[] tint = blendedColor(1);
        return tint == null
                ? original
                : Vec3D.createVector(original.xCoord * (1 - tint[3] + tint[0]),
                        original.yCoord * (1 - tint[3] + tint[1]), original.zCoord * (1 - tint[3] + tint[2]));
    }

    public static int beginWorld() {
        float[] tint = blendedColor(2);
        if (tint == null || !GLContext.getCapabilities().OpenGL13) {
            return 0;
        }
        int previousTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        try {
            configureTone(tint);
            GL13.glActiveTexture(previousTextureUnit);
            return previousTextureUnit;
        } catch (RuntimeException | Error failure) {
            try {
                endWorld(previousTextureUnit);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static void configureTone(float[] tint) {
        ContextCapabilities context = GLContext.getCapabilities();
        if (textureContext != context) {
            whiteTexture = 0;
            textureContext = context;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        if (whiteTexture == 0) {
            whiteTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, whiteTexture);
            ByteBuffer white = BufferUtils.createByteBuffer(4);
            white.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, white);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, whiteTexture);
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE, 1);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL13.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        COLOR.clear();
        COLOR.put(1 - tint[3] + tint[0]).put(1 - tint[3] + tint[1]).put(1 - tint[3] + tint[2]).put(1).flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, COLOR);
    }

    public static void endWorld(int capturedTextureUnit) {
        if (capturedTextureUnit != 0) {
            try {
                GL11.glPopAttrib();
            } finally {
                GL13.glActiveTexture(capturedTextureUnit);
            }
        }
    }

    private static boolean protectedFog(boolean cloud) {
        if (cloud || FluidCameraRuntime.eyeFluid() != null) {
            return true;
        }
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        EntityLiving view = minecraft == null ? null : minecraft.renderViewEntity;
        return view == null || view.isInsideOfMaterial(Material.water) || view.isInsideOfMaterial(Material.lava);
    }

    private static float[] blendedColor(int channel) {
        float[] result = new float[4];
        for (AtmosphereRegistry.Contribution value : active(channel)) {
            float[] color = channel == 0
                    ? value.profile.fogColor
                    : channel == 1 ? value.profile.sky : value.profile.lightTone;
            if (color != null) {
                double weight = value.weight * color[3];
                for (int i = 0; i < 3; i++) {
                    result[i] += color[i] * weight;
                }
                result[3] += weight;
            }
        }
        return result[3] > 0 ? result : null;
    }

    private static void audio(World world, EntityLiving view) {
        Map<AssetKey, TargetVoice> desired = new HashMap<AssetKey, TargetVoice>();
        for (AtmosphereRegistry.Contribution contribution : active(3)) {
            sound(desired, contribution.profile.ambient, contribution.weight);
        }
        float rain = world.func_27162_g(1);
        for (AtmosphereRegistry.Contribution contribution : active(4)) {
            sound(desired, contribution.profile.weather, contribution.weight * rain);
        }
        for (Map.Entry<AssetKey, TargetVoice> entry : desired.entrySet()) {
            if (entry.getValue().volume > 0 && !VOICES.containsKey(entry.getKey())) {
                VOICES.put(entry.getKey(), new AmbientVoice(entry.getValue().sound));
            }
        }
        Iterator<Map.Entry<AssetKey, AmbientVoice>> iterator = VOICES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<AssetKey, AmbientVoice> entry = iterator.next();
            AmbientVoice voice = entry.getValue();
            TargetVoice target = desired.get(entry.getKey());
            if (target != null) {
                voice.sound = target.sound;
            }
            float wanted = target == null ? 0 : (float) target.volume;
            voice.volume = voice.ramp.tick(wanted, voice.sound.fade);
            if (voice.playback != null) {
                voice.playback.volume(voice.volume);
            }
            if ((target == null || target.volume <= 0) && voice.volume <= 0) {
                voice.close();
                iterator.remove();
                continue;
            }
            if (target != null && target.volume > 0 && tick >= voice.nextPlay) {
                voice.close();
                try {
                    voice.playback = SoundEvents.playControlled(entry.getKey(), view.posX, view.posY, view.posZ,
                            voice.volume);
                } catch (IOException | RuntimeException exception) {
                    // Retry only at the declared interval; a missing audio asset never affects
                    // simulation.
                }
                voice.nextPlay = tick + voice.sound.interval;
            }
        }
    }

    private static void sound(Map<AssetKey, TargetVoice> desired, AtmosphereProfile.Sound sound, double weight) {
        if (sound == null || weight <= 0) {
            return;
        }
        TargetVoice current = desired.get(sound.key);
        if (current == null) {
            desired.put(sound.key, new TargetVoice(sound, Math.min(4, sound.volume * weight)));
        } else {
            current.volume = Math.min(4, current.volume + sound.volume * weight);
        }
    }

    private static final class TargetVoice {
        private final AtmosphereProfile.Sound sound;
        private double volume;

        private TargetVoice(AtmosphereProfile.Sound sound, double volume) {
            this.sound = sound;
            this.volume = volume;
        }
    }

    private static final class AmbientVoice {
        private AtmosphereProfile.Sound sound;
        private ClientAudio.ControlledVoice playback;
        private long nextPlay;
        private float volume;
        private final AtmosphereVolumeRamp ramp = new AtmosphereVolumeRamp();

        private AmbientVoice(AtmosphereProfile.Sound sound) {
            this.sound = sound;
        }

        private void close() {
            if (playback != null) {
                playback.close();
                playback = null;
            }
        }
    }
}
