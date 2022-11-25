package m.vita.module.track.util;

import android.app.ActivityManager;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/* This detector looks for transitions where one app leaves the foreground and
 * another enters the foreground to detect apps that are legitimately in the
 * foreground.  If no application is known to be legitimate system is returned.
 */

public class ForegroundDetector {
    int lastSize;
    int[] lastUids;
    int nowSize;
    int[] nowUids;

    private BitSet validated;

    private ActivityManager activityManager;

    public ForegroundDetector(ActivityManager activityManager) {
        lastSize = nowSize = 0;
        lastUids = new int[10];
        nowUids = new int[10];
        validated = new BitSet(1 << 16);
        validated.set(android.os.Process.myUid());
        this.activityManager = activityManager;
    }

    // Figure out what uid should be charged for screen usage.
    public int getForegroundUid() {
        SystemInfo sysInfo = SystemInfo.getInstance();
        List<ActivityManager.RunningAppProcessInfo> appProcs = activityManager.getRunningAppProcesses();

        // Move the last iteration to last and resize the other array if needed.
        int[] tmp = lastUids;
        lastUids = nowUids;
        lastSize = nowSize;
        if (tmp != null) {

            if (appProcs != null) {
                if (tmp.length < appProcs.size()) {
                    tmp = new int[appProcs.size()];
                }

                nowUids = tmp;

                // Fill in the uids from appProcs.
                nowSize = 0;
                for (ActivityManager.RunningAppProcessInfo app : appProcs) {
                    if (app.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        int uid = sysInfo.getUidForPid(app.pid);
                        if (SystemInfo.AID_APP <= uid && uid < 1 << 16) {
                            nowUids[nowSize++] = uid;
                        }
                    }
                }
                Arrays.sort(nowUids, 0, nowSize);

                // Find app-exit app-enter transitions.
                int appExit = -1;
                int appEnter = -1;
                int indNow = 0;
                int indLast = 0;
                while (indNow < nowSize && indLast < lastSize) {
                    if (nowUids[indNow] == lastUids[indLast]) {
                        indNow++;
                        indLast++;
                    } else if (nowUids[indNow] < lastUids[indLast]) {
                        appEnter = nowUids[indNow++];
                    } else {
                        appExit = lastUids[indLast++];
                    }
                }
                if (indNow < nowSize)
                    appEnter = nowUids[indNow];
                if (indLast < lastSize)
                    appExit = lastUids[indLast];

                // Found an interesting transition. Validate both applications.
                if (appEnter != -1 && appExit != -1) {
                    validated.set(appEnter);
                    validated.set(appExit);
                }

                // Now find a valid application now. Hopefully there is only one. If
                // there
                // are none return system. If there are several return the one with the
                // highest uid.
                for (int i = nowSize - 1; i >= 0; i--) {
                    if (validated.get(nowUids[i])) {
                        return nowUids[i];
                    }
                }

            }
        }
        return SystemInfo.AID_SYSTEM;
    }
}