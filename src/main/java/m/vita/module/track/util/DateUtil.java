package m.vita.module.track.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class DateUtil {

    private static Calendar calendar;
    private static String TAG = "service.Tools";

    public static String getSimpleDate() {

        calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String strNow = String.format("%s", dateFormat.format(calendar.getTime()));

        return strNow;
    }

    public static String getDate() {
        calendar = Calendar.getInstance();
        int j = 1 + calendar.get(2);
        int k = calendar.get(5);
        int m = calendar.get(11);
        int n = calendar.get(12);
        int i1 = calendar.get(13);

        Object[] arrayOfObject2 = new Object[1];
        arrayOfObject2[0] = Integer.valueOf(j);
        StringBuilder localStringBuilder2 = new StringBuilder(String.format("%02d", arrayOfObject2));
        Object[] arrayOfObject3 = new Object[1];
        arrayOfObject3[0] = Integer.valueOf(k);
        StringBuilder localStringBuilder3 = localStringBuilder2.append(String.format("%02d.", arrayOfObject3));
        Object[] arrayOfObject4 = new Object[1];
        arrayOfObject4[0] = Integer.valueOf(m);
        StringBuilder localStringBuilder4 = localStringBuilder3.append(String.valueOf(String.format("%02d", arrayOfObject4)));
        Object[] arrayOfObject5 = new Object[1];
        arrayOfObject5[0] = Integer.valueOf(n);
        StringBuilder localStringBuilder5 = localStringBuilder4.append(String.format("%02d.", arrayOfObject5));
        Object[] arrayOfObject6 = new Object[1];
        arrayOfObject6[0] = Integer.valueOf(i1);
        StringBuilder localStringBuilder6 = localStringBuilder5.append(String.format("%02d", arrayOfObject6));

        String strNow = String.format("%s", localStringBuilder6);

        return strNow;
    }

}
