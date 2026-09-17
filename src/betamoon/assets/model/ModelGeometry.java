package betamoon.assets.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable imported geometry, independent of Minecraft and graphics resources.
 */
public final class ModelGeometry {
    public final int textureWidth;
    public final int textureHeight;
    public final List<Bone> bones;
    public final Map<String, Socket> sockets;
    private final Map<String, Integer> parts;

    ModelGeometry(int width, int height, List<Bone> bones, Map<String, Socket> sockets) {
        textureWidth = width;
        textureHeight = height;
        this.bones = Collections.unmodifiableList(new ArrayList<>(bones));
        this.sockets = Collections.unmodifiableMap(new LinkedHashMap<>(sockets));
        Map<String, Integer> names = new LinkedHashMap<>();
        for (int i = 0; i < bones.size(); i++) {
            names.put(bones.get(i).name, i);
        }
        parts = Collections.unmodifiableMap(names);
    }

    public int index(String name) {
        Integer index = parts.get(name);
        if (index == null) {
            throw new IllegalArgumentException("Unknown model part: " + name);
        }
        return index;
    }

    public boolean hasPart(String name) {
        return parts.containsKey(name);
    }

    public static final class Bone {
        public final String name;
        public final int parent;
        public final ModelVector pivot;
        public final ModelVector sourceRotation;
        public final ModelRotation rotation;
        public final List<Quad> faces;

        Bone(String name, int parent, ModelVector pivot, ModelVector sourceRotation, List<Quad> faces) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.sourceRotation = sourceRotation;
            rotation = ModelRotation.bedrock(sourceRotation);
            this.faces = Collections.unmodifiableList(new ArrayList<>(faces));
        }
    }

    public static final class Quad {
        public final List<ModelVector> vertices;
        public final List<ModelVector> uv;
        public final String material;
        public final boolean plane;

        Quad(List<ModelVector> vertices, List<ModelVector> uv, String material, boolean plane) {
            this.vertices = Collections.unmodifiableList(new ArrayList<>(vertices));
            this.uv = Collections.unmodifiableList(new ArrayList<>(uv));
            this.material = material;
            this.plane = plane;
        }
    }

    public static final class Socket {
        public final int bone;
        public final ModelVector position;
        public final ModelRotation rotation;

        Socket(int bone, ModelVector position, ModelRotation rotation) {
            this.bone = bone;
            this.position = position;
            this.rotation = rotation;
        }
    }
}
