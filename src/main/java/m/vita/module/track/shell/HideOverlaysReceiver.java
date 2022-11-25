package m.vita.module.track.shell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public abstract class HideOverlaysReceiver extends BroadcastReceiver {
    public static final String ACTION_HIDE_OVERLAYS = "JEB.track.supersu.action.HIDE_OVERLAYS";
    public static final String CATEGORY_HIDE_OVERLAYS = "android.intent.category.INFO";
    public static final String EXTRA_HIDE_OVERLAYS = "JEB.track.supersu.extra.HIDE";

    public HideOverlaysReceiver() {
    }

    public final void onReceive(Context context, Intent intent) {
        if (intent.hasExtra("JEB.track.supersu.extra.HIDE")) {
            this.onHideOverlays(intent.getBooleanExtra("JEB.track.supersu.extra.HIDE", false));
        }

    }

    public abstract void onHideOverlays(boolean var1);
}