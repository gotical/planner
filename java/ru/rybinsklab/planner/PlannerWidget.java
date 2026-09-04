package ru.rybinsklab.planner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

public class PlannerWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(ctx, appWidgetManager, id);
        }
    }

    @Override
    public void onEnabled(Context ctx) {
        // First widget added
    }

    @Override
    public void onDisabled(Context ctx) {
        // Last widget removed
    }

    static void updateWidget(Context ctx, AppWidgetManager manager, int id) {
        // Get today's tasks count
        final Store store = new Store(ctx);
        final java.util.ArrayList<Store.Task> tasks = store.loadTasks();
        
        long today = Store.todayStart();
        final int[] todayTasks = {0};
        final int[] todayDone = {0};
        final int[] overdue = {0};
        
        for (Store.Task t : tasks) {
            if (t.parent != null || t.deleted == 1) continue;
            if (t.due > 0 && Store.sameDay(t.due, today)) {
                todayTasks[0]++;
                if (t.done == 1) todayDone[0]++;
            }
            if (t.due > 0 && t.due < today && t.done == 0 && t.dismissed == 0) {
                overdue[0]++;
            }
        }
        
        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_planner);
        
        // Set counts
        views.setTextViewText(R.id.widget_title, "Планировщик");
        views.setTextViewText(R.id.widget_today_count, String.valueOf(todayTasks[0]));
        views.setTextViewText(R.id.widget_done_count, String.valueOf(todayDone[0]));
        views.setTextViewText(R.id.widget_overdue_count, String.valueOf(overdue[0]));
        
        // Progress bar calculation
        int progress = todayTasks[0] > 0 ? (todayDone[0] * 100 / todayTasks[0]) : 0;
        views.setProgressBar(R.id.widget_progress, 100, progress, false);
        
        // Set progress text
        views.setTextViewText(R.id.widget_progress_text, todayDone[0] + "/" + todayTasks[0] + " выполнено");
        
        // Click intent - open app
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(ctx, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pending);
        
        // Update widget
        manager.updateAppWidget(id, views);
    }
    
    public static void refreshAllWidgets(Context ctx) {
        AppWidgetManager manager = AppWidgetManager.getInstance(ctx);
        int[] ids = manager.getAppWidgetIds(new ComponentName(ctx, PlannerWidget.class));
        for (int id : ids) {
            updateWidget(ctx, manager, id);
        }
    }
}
