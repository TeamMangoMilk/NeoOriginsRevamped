package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side handler for visual power effects: overlay, model_color,
 * lava_vision, and shader.
 *
 * <p>Each visual power emits a capability string with encoded parameters
 * (e.g. {@code "overlay:minecraft:textures/misc/pumpkinblur.png:0.5"}).
 * This handler parses those strings from {@link ClientActivePowers} and
 * applies the corresponding visual effects.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public final class VisualEffectsHandler {

    /** Tracks which shader we applied so we don't clobber other shaders. */
    private static ResourceLocation appliedShader = null;
    private static final float DEFAULT_PHASING_VIEW_DISTANCE = 50.0F;
    private static final float DEFAULT_PHASING_TERRAIN_FADE_START = 0.85F;
    private static final float DEFAULT_PHASING_FOG_COLOR = 0.22F;
    private static final float PHASING_FOG_START = 0.0F;
    private static final FogShape PHASING_FOG_SHAPE = FogShape.SPHERE;
    private static boolean distantHorizonsRenderingSuppressed = false;
    private static boolean distantHorizonsReflectionFailed = false;
    private static Object previousDistantHorizonsRenderingValue = null;

    private VisualEffectsHandler() {}

    // ---- Overlay ----

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiEvent.Post event) {
        String data = findCapabilityData("overlay");
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Parse "texture:strength"
        int lastColon = data.lastIndexOf(':');
        String texturePath;
        float strength;
        if (lastColon > 0) {
            texturePath = data.substring(0, lastColon);
            try {
                strength = Float.parseFloat(data.substring(lastColon + 1));
            } catch (NumberFormatException e) {
                texturePath = data;
                strength = 1.0f;
            }
        } else {
            texturePath = data;
            strength = 1.0f;
        }
        if (strength <= 0.0f) return;

        ResourceLocation texture = ResourceLocation.parse(texturePath);
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        g.setColor(1.0f, 1.0f, 1.0f, strength);
        g.blit(texture, 0, 0, -90, 0.0f, 0.0f, w, h, w, h);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    // ---- Lava Vision ----

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PhasingVisual phasing = currentPhasingVisual();
        if (applyPhasingFog(event.getCamera(), event.getMode(), phasing)) {
            float end = phasing.viewDistance();
            float start = event.getMode() == FogRenderer.FogMode.FOG_SKY ? PHASING_FOG_START : end * DEFAULT_PHASING_TERRAIN_FADE_START;
            if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
                end *= 1.0F;
            }
            event.setNearPlaneDistance(start);
            event.setFarPlaneDistance(end);
            event.setFogShape(PHASING_FOG_SHAPE);
            event.setCanceled(true);
            return;
        }

        if (!mc.player.isInLava()) return;

        String data = findCapabilityData("lava_vision");
        float multiplier;
        if (data != null) {
            try {
                multiplier = Float.parseFloat(data);
            } catch (NumberFormatException e) {
                multiplier = 3.0f;
            }
        } else if (ClientActivePowers.hasCapability("lava_vision")) {
            multiplier = 3.0f;
        } else {
            return;
        }

        event.setFarPlaneDistance(event.getFarPlaneDistance() * multiplier);
        event.setCanceled(true);
    }
    // ---- Shader ----

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!hasPhasingFog()) {
            return;
        }

        event.setRed(DEFAULT_PHASING_FOG_COLOR);
        event.setGreen(DEFAULT_PHASING_FOG_COLOR);
        event.setBlue(DEFAULT_PHASING_FOG_COLOR);
    }

    @SubscribeEvent
    public static void onClientTick(PlayerTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            updateDistantHorizonsSuppression(false);
            return;
        }
        // Only run on the local player's tick
        if (event.getEntity() != mc.player) return;

        updateDistantHorizonsSuppression(hasPhasingFog());

        String data = findCapabilityData("shader");
        if (data != null) {
            ResourceLocation shaderId = ResourceLocation.parse(data);
            if (!shaderId.equals(appliedShader)) {
                try {
                    mc.gameRenderer.loadEffect(shaderId);
                    appliedShader = shaderId;
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn("Failed to load shader '{}': {}", shaderId, e.getMessage());
                    appliedShader = shaderId; // don't retry every tick
                }
            }
        } else if (appliedShader != null) {
            mc.gameRenderer.shutdownEffect();
            appliedShader = null;
        }
    }

    // ---- Helpers ----

    public static boolean applyPhasingFog(Camera camera) {
        return applyPhasingFog(camera, FogRenderer.FogMode.FOG_TERRAIN);
    }

    public static boolean applyPhasingFog(Camera camera, FogRenderer.FogMode fogMode) {
        return applyPhasingFog(camera, fogMode, currentPhasingVisual());
    }

    private static boolean applyPhasingFog(Camera camera, FogRenderer.FogMode fogMode, PhasingVisual phasing) {
        if (phasing == null || !isPhasingHeadSubmerged(camera)) {
            return false;
        }

        float end = phasing.viewDistance();
        float start = fogMode == FogRenderer.FogMode.FOG_SKY ? PHASING_FOG_START : end * DEFAULT_PHASING_TERRAIN_FADE_START;
        if (fogMode == FogRenderer.FogMode.FOG_SKY) {
            end *= 1.0F;
        }

        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(PHASING_FOG_SHAPE);
        return true;
    }

    /**
     * Finds a capability string starting with {@code prefix:} and returns
     * the data portion after the prefix. Returns null if no match.
     */
    private static String findCapabilityData(String prefix) {
        String needle = prefix + ":";
        for (String cap : ClientActivePowers.activeCapabilities()) {
            if (cap.startsWith(needle)) {
                return cap.substring(needle.length());
            }
        }
        return null;
    }

    private static PhasingVisual currentPhasingVisual() {
        String data = findCapabilityData("phasing_visual");
        if (data == null) {
            boolean phasing = ClientActivePowers.hasCapability("phantom_phase")
                || ClientActivePowers.hasCapability("wall_phase");
            return phasing ? new PhasingVisual(DEFAULT_PHASING_VIEW_DISTANCE) : null;
        }

        float viewDistance = DEFAULT_PHASING_VIEW_DISTANCE;
        int separator = data.lastIndexOf(':');
        String encodedDistance = separator >= 0 ? data.substring(separator + 1) : data;
        try {
            viewDistance = Float.parseFloat(encodedDistance);
        } catch (NumberFormatException ignored) {}
        return new PhasingVisual(viewDistance);
    }

    public static boolean hasPhasingFog() {
        return currentPhasingVisual() != null
            && isPhasingHeadSubmerged(Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    private static boolean isPhasingHeadSubmerged(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || camera == null) {
            return false;
        }

        BlockPos pos = BlockPos.containing(camera.getPosition());
        BlockState state = mc.level.getBlockState(pos).getStateAtViewpoint(mc.level, pos, camera.getPosition());
        return !state.isAir()
            && !isDefaultBlocked(state)
            && !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private static boolean isDefaultBlocked(BlockState state) {
        return state.is(Blocks.BEDROCK)
            || state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.CRYING_OBSIDIAN);
    }

    private static void updateDistantHorizonsSuppression(boolean suppress) {
        if (distantHorizonsReflectionFailed) {
            return;
        }
        if (suppress == distantHorizonsRenderingSuppressed
            && (suppress || readDistantHorizonsRestoreMarker() == null)) {
            return;
        }

        try {
            Object renderingEnabled = distantHorizonsRenderingEnabledConfig();
            if (renderingEnabled == null) {
                return;
            }

            if (suppress) {
                Boolean pendingRestore = readDistantHorizonsRestoreMarker();
                previousDistantHorizonsRenderingValue = pendingRestore == null
                    ? renderingEnabled.getClass().getMethod("getValue").invoke(renderingEnabled)
                    : pendingRestore;
                if (!Boolean.TRUE.equals(previousDistantHorizonsRenderingValue)) {
                    return;
                }
                writeDistantHorizonsRestoreMarker();
                renderingEnabled.getClass().getMethod("setValue", Object.class).invoke(renderingEnabled, false);
                distantHorizonsRenderingSuppressed = true;
            } else {
                Boolean pendingRestore = readDistantHorizonsRestoreMarker();
                Object restoreValue = previousDistantHorizonsRenderingValue != null
                    ? previousDistantHorizonsRenderingValue
                    : pendingRestore;
                if (Boolean.TRUE.equals(restoreValue)) {
                    renderingEnabled.getClass().getMethod("setValue", Object.class).invoke(renderingEnabled, true);
                }
                deleteDistantHorizonsRestoreMarker();
                previousDistantHorizonsRenderingValue = null;
                distantHorizonsRenderingSuppressed = false;
            }
        } catch (ClassNotFoundException ignored) {
            distantHorizonsReflectionFailed = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            distantHorizonsReflectionFailed = true;
            NeoOrigins.LOGGER.debug("Could not update Distant Horizons phasing fog compatibility hook", e);
        }
    }

    private static Object distantHorizonsRenderingEnabledConfig() throws ReflectiveOperationException {
        Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
        Object configs = delayed.getField("configs").get(null);
        if (configs == null) {
            return null;
        }

        Object graphics = configs.getClass().getMethod("graphics").invoke(configs);
        return graphics.getClass().getMethod("renderingEnabled").invoke(graphics);
    }

    private static Path distantHorizonsRestoreMarkerPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config")
            .resolve("neoorigins-dh-phasing-restore.marker");
    }

    private static void writeDistantHorizonsRestoreMarker() {
        try {
            Path path = distantHorizonsRestoreMarkerPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, "true", StandardCharsets.UTF_8);
        } catch (Exception e) {
            NeoOrigins.LOGGER.debug("Could not write Distant Horizons phasing restore marker", e);
        }
    }

    private static Boolean readDistantHorizonsRestoreMarker() {
        try {
            Path path = distantHorizonsRestoreMarkerPath();
            if (!Files.exists(path)) {
                return null;
            }
            return Boolean.parseBoolean(Files.readString(path, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            NeoOrigins.LOGGER.debug("Could not read Distant Horizons phasing restore marker", e);
            return null;
        }
    }

    private static void deleteDistantHorizonsRestoreMarker() {
        try {
            Files.deleteIfExists(distantHorizonsRestoreMarkerPath());
        } catch (Exception e) {
            NeoOrigins.LOGGER.debug("Could not delete Distant Horizons phasing restore marker", e);
        }
    }

    private record PhasingVisual(float viewDistance) {}
}
