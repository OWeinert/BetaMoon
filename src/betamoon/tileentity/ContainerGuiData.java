package betamoon.tileentity;

import betamoon.data.DataField;

/**
 * The existing native container transport exposes synchronized
 * integers/booleans only.
 */
final class ContainerGuiData {
    static boolean readable(TileEntityDefinition.Field field) {
        return field != null && field.sync
                && (field.schema.type == DataField.Type.INTEGER || field.schema.type == DataField.Type.BOOLEAN);
    }

    private ContainerGuiData() {
    }
}
