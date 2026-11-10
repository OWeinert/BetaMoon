package betamoon.gui;

import betamoon.gui.interop.GuiResourceBudget;
import com.sun.management.ThreadMXBean;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * Bounded local observations, without machine-independent timing pass/fail
 * thresholds.
 */
final class GuiRenderProfile {
    interface Frame {
        void draw() throws Exception;
    }

    static void measure(Minecraft client, File output, String name, Frame frame) throws Exception {
        for (int warmup = 0; warmup < 20; warmup++) {
            frame.draw();
            GL11.glFinish();
        }
        Object base = ManagementFactory.getThreadMXBean();
        ThreadMXBean allocation = base instanceof ThreadMXBean
                && ((ThreadMXBean) base).isThreadAllocatedMemorySupported() ? (ThreadMXBean) base : null;
        boolean enable = allocation != null && !allocation.isThreadAllocatedMemoryEnabled();
        if (enable) {
            allocation.setThreadAllocatedMemoryEnabled(true);
        }
        long[] samples = new long[120];
        long thread = Thread.currentThread().getId();
        long before = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread);
        long allocated;
        try {
            for (int sample = 0; sample < samples.length; sample++) {
                long started = System.nanoTime();
                frame.draw();
                GL11.glFinish();
                samples[sample] = System.nanoTime() - started;
            }
            allocated = before < 0 ? -1 : (allocation.getThreadAllocatedBytes(thread) - before) / samples.length;
        } finally {
            if (enable) {
                allocation.setThreadAllocatedMemoryEnabled(false);
            }
        }
        Arrays.sort(samples);
        String result = String.format(Locale.ROOT,
                "{\"scene\":\"%s\",\"frames\":120,\"p50_ms\":%.6f,\"p95_ms\":%.6f,\"p99_ms\":%.6f,"
                        + "\"allocated_bytes_per_frame\":%d,\"owned_resource_bytes\":%d,"
                        + "\"framebuffer\":[%d,%d],\"gui_scale_setting\":%d,\"java\":\"%s\",\"renderer\":\"%s\"}",
                name, samples[59] / 1E6, samples[113] / 1E6, samples[118] / 1E6, allocated,
                GuiResourceBudget.allocated(), client.displayWidth, client.displayHeight, client.gameSettings.guiScale,
                escape(System.getProperty("java.version")), escape(GL11.glGetString(GL11.GL_RENDERER)));
        Files.write(new File(output, "ui-profile-" + name + ".json").toPath(), result.getBytes(StandardCharsets.UTF_8));
        System.out.println("UI offscreen profile (render + driver finish; no pixel readback): " + result);
    }

    private static String escape(String value) {
        return value == null ? "unavailable" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
