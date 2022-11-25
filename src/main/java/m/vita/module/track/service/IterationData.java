package m.vita.module.track.service;

import android.util.SparseArray;

import m.vita.module.track.util.Recycler;
import m.vita.module.track.util.SystemInfo;

/* Class that encloses physical hardware power data as well as estimated power
 * data for each uid that contributes a non-negligable amount of power for this
 * component.
 */
public class IterationData {
    private static Recycler<IterationData> recycler = new Recycler<IterationData>();

    private SparseArray<PowerData> uidPower;

    public static IterationData obtain() {
        IterationData result = recycler.obtain();
        if (result != null)
            return result;
        return new IterationData();
    }

    private IterationData() {
        uidPower = new SparseArray<PowerData>();
    }

    /*
     * Initialize the members of this structure. Remember that this class may
     * not have just been instantiated and may have been used in past
     * iterations.
     */
    public void init() {
        uidPower.clear();
    }

    /*
     * Allow this class to be recycled and to recycle all of the PowerData
     * PowerData elements contained within it.
     */
    public void recycle() {
        for (int i = 0; i < uidPower.size(); i++) {
            uidPower.valueAt(i).recycle();
        }
        uidPower.clear();
        recycler.recycle(this);
    }

    public void setPowerData(PowerData power) {
        addUidPowerData(SystemInfo.AID_ALL, power);
    }

    public void addUidPowerData(int uid, PowerData power) {
        uidPower.put(uid, power);
    }

    public SparseArray<PowerData> getUidPowerData() {
        return uidPower;
    }
}