package betamoon.fluid.contact;

import betamoon.fluid.FluidRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable body and eye fluid resolution for one entity position. */
public final class FluidContactSnapshot {
    private final List<FluidBodyContact> bodyContacts;
    private final FluidBodyContact dominantBody;
    private final FluidRegistration eyeFluid;
    private final boolean approximate;
    private final int cellsRead;

    FluidContactSnapshot(List<FluidBodyContact> bodyContacts, FluidBodyContact dominantBody,
            FluidRegistration eyeFluid, boolean approximate, int cellsRead) {
        this.bodyContacts = Collections.unmodifiableList(new ArrayList<FluidBodyContact>(bodyContacts));
        this.dominantBody = dominantBody;
        this.eyeFluid = eyeFluid;
        this.approximate = approximate;
        this.cellsRead = cellsRead;
    }

    public List<FluidBodyContact> bodyContacts() {
        return bodyContacts;
    }

    public FluidBodyContact dominantBody() {
        return dominantBody;
    }

    public FluidRegistration eyeFluid() {
        return eyeFluid;
    }

    public boolean approximate() {
        return approximate;
    }

    public int cellsRead() {
        return cellsRead;
    }
}
