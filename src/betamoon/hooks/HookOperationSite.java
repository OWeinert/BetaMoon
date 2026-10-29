package betamoon.hooks;

import betamoon.interop.catalog.CatalogValues;
import java.util.Map;
import org.objectweb.asm.Opcodes;

/**
 * Exact observed operation shape; immutable after successful transformation.
 */
public final class HookOperationSite {
    public final int id;
    public final String kind;
    public final int opcodeIndex;
    public final int opcode;
    public final String owner;
    public final String name;
    public final String descriptor;
    private final String hookDescriptor;
    private final Map<String, Object> transport;

    public HookOperationSite(int id, String kind, int opcodeIndex, int opcode, String owner, String name,
            String descriptor) {
        this.id = id;
        this.kind = kind;
        this.opcodeIndex = opcodeIndex;
        this.opcode = opcode;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        hookDescriptor = kind.equals("field_read")
                ? "()" + descriptor
                : kind.equals("field_write") ? "(" + descriptor + ")V" : descriptor;
        transport = CatalogValues.object(
                CatalogValues.freeze(CatalogValues.record("id", id, "kind", kind, "opcodeIndex", opcodeIndex, "opcode",
                        opcode, "owner", owner, "name", name, "descriptor", descriptor, "opcodeName", opcodeName())));
    }

    public boolean before() {
        return kind.equals("invoke_before") || kind.equals("field_write");
    }

    public boolean instance() {
        return opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.GETSTATIC && opcode != Opcodes.PUTSTATIC;
    }

    public String hookDescriptor() {
        return hookDescriptor;
    }

    public Map<String, Object> transport() {
        return transport;
    }

    public String opcodeName() {
        switch (opcode) {
            case Opcodes.INVOKESTATIC:
                return "invokestatic";
            case Opcodes.INVOKEVIRTUAL:
                return "invokevirtual";
            case Opcodes.INVOKEINTERFACE:
                return "invokeinterface";
            case Opcodes.INVOKESPECIAL:
                return "invokespecial";
            case Opcodes.GETFIELD:
                return "getfield";
            case Opcodes.GETSTATIC:
                return "getstatic";
            case Opcodes.PUTFIELD:
                return "putfield";
            case Opcodes.PUTSTATIC:
                return "putstatic";
            default:
                return "unknown";
        }
    }

    public static HookOperationSite transport(Map<String, Object> value) {
        return new HookOperationSite(CatalogValues.integer(value, "id"), CatalogValues.text(value, "kind"),
                CatalogValues.integer(value, "opcodeIndex"), CatalogValues.integer(value, "opcode"),
                CatalogValues.text(value, "owner"), CatalogValues.text(value, "name"),
                CatalogValues.text(value, "descriptor"));
    }
}
