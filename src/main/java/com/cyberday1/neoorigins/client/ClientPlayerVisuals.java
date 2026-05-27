package com.cyberday1.neoorigins.client;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPlayerVisuals {
    private static final Map<Integer, ModelColor> MODEL_COLORS = new ConcurrentHashMap<>();

    private ClientPlayerVisuals() {}

    public static void setModelColor(int entityId, Optional<String> encoded) {
        if (encoded.isEmpty() || encoded.get().isBlank()) {
            MODEL_COLORS.remove(entityId);
            return;
        }
        ModelColor.parse(encoded.get()).ifPresentOrElse(
            color -> MODEL_COLORS.put(entityId, color),
            () -> MODEL_COLORS.remove(entityId));
    }

    public static ModelColor modelColor(int entityId) {
        return MODEL_COLORS.get(entityId);
    }

    public static void clear() {
        MODEL_COLORS.clear();
    }

    public record ModelColor(float red, float green, float blue, float alpha) {
        static Optional<ModelColor> parse(String encoded) {
            String[] parts = encoded.split(":");
            if (parts.length < 3) return Optional.empty();
            try {
                float red = Float.parseFloat(parts[0]);
                float green = Float.parseFloat(parts[1]);
                float blue = Float.parseFloat(parts[2]);
                float alpha = parts.length >= 4 ? Float.parseFloat(parts[3]) : 1.0F;
                return Optional.of(new ModelColor(red, green, blue, alpha));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        public int argb() {
            int a = channel(alpha);
            int r = channel(red);
            int g = channel(green);
            int b = channel(blue);
            return a << 24 | r << 16 | g << 8 | b;
        }

        private static int channel(float value) {
            return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
        }
    }
}
