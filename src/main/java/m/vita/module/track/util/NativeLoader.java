package m.vita.module.track.util;

import android.util.Log;

public class NativeLoader {
    private static final String TAG = "NT1NativeLoader";

    private static boolean loadOk = false;

    static {
        try {
            System.loadLibrary("bindings");
            loadOk = true;
        } catch (SecurityException e) {
            Log.w(TAG, "Failed to load jni dll, will fall back on pure java");
            loadOk = false;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Failed to load jni dll, will fall back on pure java");
            loadOk = false;
        }
    }

    public static boolean jniLoaded() {
        return loadOk;
    }
}
