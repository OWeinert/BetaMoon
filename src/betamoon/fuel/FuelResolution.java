package betamoon.fuel;

/** Result of resolving a stack against a fuel set. */
public final class FuelResolution {
    private static final FuelResolution NONE = new FuelResolution(0, null, false);

    public final int burnTime;
    public final FuelRegistration registration;
    public final boolean nativeFuel;

    private FuelResolution(int burnTime, FuelRegistration registration, boolean nativeFuel) {
        this.burnTime = burnTime;
        this.registration = registration;
        this.nativeFuel = nativeFuel;
    }

    public static FuelResolution none() {
        return NONE;
    }

    static FuelResolution registered(FuelRegistration registration) {
        return new FuelResolution(registration.burnTime, registration, false);
    }

    static FuelResolution nativeFuel(int burnTime) {
        return burnTime <= 0 ? NONE : new FuelResolution(burnTime, null, true);
    }

    public boolean isFuel() {
        return burnTime > 0;
    }
}
