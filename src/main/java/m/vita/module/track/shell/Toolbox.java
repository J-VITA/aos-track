package m.vita.module.track.shell;

import android.os.Build.VERSION;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class Toolbox {
    private static final int TOYBOX_SDK = 23;
    private static final Object synchronizer = new Object();
    private static volatile String toybox = null;

    public Toolbox() {
    }

    public static void init() {
        if (toybox == null) {
            if (VERSION.SDK_INT < 23) {
                toybox = "";
            } else {
                if (Debug.getSanityChecksEnabledEffective() && Debug.onMainThread()) {
                    Debug.log("Application attempted to init the Toolbox class from the main thread");
                    throw new ShellOnMainThreadException("Application attempted to init the Toolbox class from the main thread");
                }

                synchronized(synchronizer) {
                    toybox = "";
                    List<String> output = Shell.SH.run("toybox");
                    if (output != null) {
                        toybox = " ";

                        String line;
                        for(Iterator var2 = output.iterator(); var2.hasNext(); toybox = toybox + line.trim() + " ") {
                            line = (String)var2.next();
                        }
                    }
                }
            }

        }
    }

    public static String command(String format, Object... args) {
        if (VERSION.SDK_INT < 23) {
            return String.format(Locale.ENGLISH, "toolbox " + format, args);
        } else {
            if (toybox == null) {
                init();
            }

            format = format.trim();
            int p = format.indexOf(32);
            String applet;
            if (p >= 0) {
                applet = format.substring(0, p);
            } else {
                applet = format;
            }

            return toybox.contains(" " + applet + " ") ? String.format(Locale.ENGLISH, "toybox " + format, args) : String.format(Locale.ENGLISH, "toolbox " + format, args);
        }
    }
}
