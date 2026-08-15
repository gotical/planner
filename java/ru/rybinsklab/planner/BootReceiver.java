package ru.rybinsklab.planner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            Reminders.rescheduleAll(ctx, new Store(ctx));
        }
    }
}
