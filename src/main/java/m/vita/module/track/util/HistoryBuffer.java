package m.vita.module.track.util;

import android.util.SparseArray;

import java.util.LinkedList;
import java.util.ListIterator;

public class HistoryBuffer {
    private static class UidData {
        public LinkedList<HistoryDatum> queue;
        public Counter sum;
        public Counter count;

        public UidData() {
            queue = new LinkedList<HistoryDatum>();
            sum = new Counter();
            count = new Counter();
        }
    }

    private static class HistoryDatum {
        public HistoryDatum() {
        }

        public void init(long iteration, int power) {
            this.iteration = iteration;
            this.power = power;
        }

        public long iteration;
        public int power;
    }

    private int maxSize;
    private SparseArray<UidData> uidData;

    public HistoryBuffer(int maxSize) {
        this.maxSize = maxSize;
        uidData = new SparseArray<UidData>();
    }

    /* The iteration should only increase across successive adds. */
    public synchronized void add(int uid, long iteration, int power) {
        UidData data = uidData.get(uid);
        if (data == null) {
            data = new UidData();
            uidData.put(uid, data);
        }
        data.count.add(1);
        if (power == 0) {
            return;
        }
        data.sum.add(power);
        if (maxSize == 0) {
            return;
        }

        LinkedList<HistoryDatum> queue = data.queue;
        HistoryDatum datum;
        if (maxSize <= queue.size()) {
            datum = queue.getLast();
            queue.removeLast();
        } else {
            datum = new HistoryDatum();
        }
        datum.init(iteration, power);
        queue.addFirst(datum);
    }

    /*
     * Fills in the previous number timestamps starting from a timestamp and
     * working backwards. Any timestamp with no information is just treated as
     * using no power.
     */
    public synchronized int[] get(int uid, long timestamp, int number) {
        int ind = 0;
        if (number < 0)
            number = 0;
        if (number > maxSize)
            number = maxSize;
        int[] ret = new int[number];
        UidData data = uidData.get(uid);
        LinkedList<HistoryDatum> queue = data == null ? null : data.queue;
        if (queue == null || queue.isEmpty()) {
            return ret;
        }
        if (timestamp == -1) {
            timestamp = queue.getFirst().iteration;
        }
        for (ListIterator<HistoryDatum> iter = queue.listIterator(); iter
                .hasNext();) {
            HistoryDatum datum = iter.next();
            while (datum.iteration < timestamp && ind < number) {
                ind++;
                timestamp--;
            }
            if (ind == number) {
                break;
            }
            if (datum.iteration == timestamp) {
                ret[ind++] = datum.power;
                timestamp--;
            } else {
                /* datum happened after requested interval. */
            }
        }
        return ret;
    }

    public synchronized long getTotal(int uid, int windowType) {
        UidData data = uidData.get(uid);
        return data == null ? 0 : data.sum.get(windowType);
    }

    public synchronized long getCount(int uid, int windowType) {
        UidData data = uidData.get(uid);
        return data == null ? 0 : data.count.get(windowType);
    }
}
