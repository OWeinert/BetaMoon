package betamoon.assets.model;

/** Per-instance channels. Geometry and other instances are never mutated. */
public final class ModelPose {
    private final ModelGeometry geometry;
    private final ModelVector[] positions;
    private final ModelRotation[] rotations;
    private final ModelVector[] scales;

    public ModelPose(ModelGeometry geometry) {
        this.geometry = geometry;
        positions = new ModelVector[geometry.bones.size()];
        rotations = new ModelRotation[positions.length];
        scales = new ModelVector[positions.length];
        reset();
    }

    public ModelGeometry getGeometry() {
        return geometry;
    }

    public void reset() {
        for (ModelGeometry.Bone bone : geometry.bones) {
            reset(bone.name);
        }
    }

    public void reset(String name) {
        int index = geometry.index(name);
        positions[index] = ModelVector.ZERO;
        rotations[index] = ModelRotation.IDENTITY;
        scales[index] = ModelVector.ONE;
    }

    public ModelVector getPosition(String name) {
        return positions[geometry.index(name)];
    }

    public ModelRotation getRotation(String name) {
        return rotations[geometry.index(name)];
    }

    public ModelVector getScale(String name) {
        return scales[geometry.index(name)];
    }

    public void setPosition(String name, ModelVector position) {
        positions[geometry.index(name)] = position;
    }

    public void setRotation(String name, ModelRotation rotation) {
        rotations[geometry.index(name)] = rotation;
    }

    public void setScale(String name, ModelVector scale) {
        if (scale.x < 0 || scale.y < 0 || scale.z < 0) {
            throw new IllegalArgumentException("Model scale cannot be negative");
        }
        scales[geometry.index(name)] = scale;
    }

    public ModelVector transform(int boneIndex, ModelVector point) {
        while (boneIndex >= 0) {
            ModelGeometry.Bone bone = geometry.bones.get(boneIndex);
            point = bone.rotation.then(rotations[boneIndex])
                    .transform(point.add(bone.pivot.times(-1)).multiply(scales[boneIndex])).add(bone.pivot)
                    .add(positions[boneIndex]);
            boneIndex = bone.parent;
        }
        return point;
    }

    public ModelVector socketPosition(String name) {
        ModelGeometry.Socket socket = geometry.sockets.get(name);
        if (socket == null) {
            throw new IllegalArgumentException("Unknown model socket: " + name);
        }
        return transform(socket.bone, socket.position);
    }

    public ModelRotation socketRotation(String name) {
        ModelGeometry.Socket socket = geometry.sockets.get(name);
        if (socket == null) {
            throw new IllegalArgumentException("Unknown model socket: " + name);
        }
        ModelRotation result = socket.rotation;
        int index = socket.bone;
        while (index >= 0) {
            ModelGeometry.Bone bone = geometry.bones.get(index);
            result = bone.rotation.then(rotations[index]).then(result);
            index = bone.parent;
        }
        return result;
    }
}
