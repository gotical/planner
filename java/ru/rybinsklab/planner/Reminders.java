package ru.rybinsklab.planner;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class Reminders {

    static long taskBase(Store.Task t) {
        long base = t.due;
        if (t.hasTime == 1 && t.time != null && t.time.length() >= 5) {
            try {
                String[] p = t.time.split(":");
                base += (Long.parseLong(p[0]) * 60L + Long.parseLong(p[1])) * 60000L;
            } catch (Exception ignored) { }
        }
        return base;
    }

    static void schedule(Context ctx, Store.Task t) {
        if (t.due == 0 || t.remOffsets.isEmpty()) return;
        long base = taskBase(t);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (int idx = 0; idx < t.remOffsets.size(); idx++) {
            long when = base - t.remOffsets.get(idx) * 60000L;
            if (when <= System.currentTimeMillis()) continue;
            Intent i = new Intent(ctx, ReminderReceiver.class);
            i.putExtra("id", t.id);
            i.putExtra("idx", idx);
            i.putExtra("title", t.title);
            i.putExtra("notes", t.notes);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) t.id * 10 + idx, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } catch (SecurityException e) {
                am.set(AlarmManager.RTC_WAKEUP, when, pi);
            }
        }
    }

    static void cancel(Context ctx, long taskId) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            for (int idx = 0; idx < 10; idx++) {
                Intent i = new Intent(ctx, ReminderReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) taskId * 10 + idx, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.cancel(pi);
            }
        } catch (Exception ignored) { }
    }

    static void snooze(Context ctx, long taskId, String title, String notes, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(ctx, ReminderReceiver.class);
            i.putExtra("id", taskId);
            i.putExtra("idx", 99);
            i.putExtra("title", title);
            i.putExtra("notes", notes);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) taskId * 10 + 99, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long when = System.currentTimeMillis() + delayMs;
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } catch (SecurityException e) {
                am.set(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (Exception ignored) { }
    }

    static void scheduleDailyReview(Context ctx, int hour, int minute) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, DailyReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 777777, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, hour);
        c.set(java.util.Calendar.MINUTE, minute);
        c.set(java.util.Calendar.SECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    static void cancelDailyReview(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, DailyReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 777777, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    static void rescheduleAll(Context ctx, Store store) {
        for (Store.Task t : store.loadTasks()) {
            if (t.done == 0 && t.deleted == 0 && t.due > 0 && !t.remOffsets.isEmpty()) schedule(ctx, t);
        }
    }
}
