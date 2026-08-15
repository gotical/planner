package ru.rybinsklab.planner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class DailyReceiver extends BroadcastReceiver {
    public void onReceive(Context ctx, Intent intent) {
        Store store = new Store(ctx);
        long today = Store.todayStart();
        int total = 0, done = 0;
        for (Store.Task t : store.loadTasks()) {
            if (t.parent != null || t.deleted == 1) continue;
            if (t.due > 0 && Store.sameDay(t.due, today)) { total++; if (t.done == 1) done++; }
        }

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("daily", "Ежедневный обзор", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 777, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = total == 0 ? "На сегодня задач нет. Запланируйте день!" : "На сегодня " + total + " задач, выполнено " + done + ".";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, "daily") : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_stat)
         .setContentTitle("Обзор дня")
         .setContentText(text)
         .setAutoCancel(true)
         .setContentIntent(pi);
        try { nm.notify(778, b.build()); } catch (Exception ignored) { }

        // ежедневная синхронизация задач в системный календарь
        try {
            if (ctx.getSharedPreferences("planner", 0).getBoolean("cal_sync", false)) {
                CalendarSync.syncAll(ctx, store);
            }
        } catch (Exception ignored) { }
    }
}
