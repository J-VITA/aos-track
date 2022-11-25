package m.vita.module.track.service;

public abstract class PowerData {
    private int cachedPower;

    public PowerData() {
    }

    public void setCachedPower(int power) {
        cachedPower = power;
    }

    public int getCachedPower() {
        return cachedPower;
    }

    /*
     * To be called when the PowerData object is no longer in use so that it can
     * be used again in the next iteration if it chooses to be.
     */
    public void recycle() {
    }
}
