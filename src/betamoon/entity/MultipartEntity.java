package betamoon.entity;

/** A typed entity whose optional collision parts can be repositioned at runtime. */
public interface MultipartEntity extends TypedEntity {
    boolean setPartOffset(String name, double x, double y, double z);

    boolean setInteractionOffset(String name, double x, double y, double z);
}
