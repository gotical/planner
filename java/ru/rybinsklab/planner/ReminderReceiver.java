package ru.rybinsklab.planner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra("id", 0);
        String title = intent.getStringExtra("title");
        String notes = intent.getStringExtra("notes");
        if (title == null) title = "Напоминание";

        int snoozeMin = intent.getIntExtra("snooze", 0);
        if (snoozeMin > 0) {
            Reminders.snooze(ctx, id, title, notes, snoozeMin * 60000L);
            return;
        }

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("reminders", "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, (int) id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent snooze10 = PendingIntent.getBroadcast(ctx, (int) id * 10 + 50, snoozeIntent(ctx, id, title, notes, 10), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent snooze60 = PendingIntent.getBroadcast(ctx, (int) id * 10 + 60, snoozeIntent(ctx, id, title, notes, 60), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(ctx, "reminders")
            : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_stat)
         .setContentTitle(title)
         .setContentText(notes != null && notes.length() > 0 ? notes : "Пора выполнить задачу")
         .setAutoCancel(true)
         .setContentIntent(pi)
         .setPriority(Notification.PRIORITY_HIGH)
         .addAction(R.drawable.ic_stat, "Через 10 мин", snooze10)
         .addAction(R.drawable.ic_stat, "Через 1 час", snooze60);
        try { nm.notify((int) id, b.build()); } catch (Exception ignored) { }
    }

    static Intent snoozeIntent(Context ctx, long id, String title, String notes, int minutes) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("notes", notes);
        i.putExtra("snooze", minutes);
        return i;
    }
}
