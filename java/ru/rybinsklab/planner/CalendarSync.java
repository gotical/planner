package ru.rybinsklab.planner;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

public class CalendarSync {

    static final String CAL_NAME = "Планировщик";
    static final String ACCOUNT_NAME = "Планировщик";

    static long getOrCreateCalendar(Context ctx) {
        ContentResolver cr = ctx.getContentResolver();
        Cursor c = null;
        try {
            c = cr.query(CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.NAME},
                CalendarContract.Calendars.NAME + "=?", new String[]{CAL_NAME}, null);
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                c.close();
                return id;
            }
        } catch (Exception ignored) { }
        finally { if (c != null) c.close(); }

        try {
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME);
            v.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
            v.put(CalendarContract.Calendars.NAME, CAL_NAME);
            v.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CAL_NAME);
            v.put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4772FA);
            v.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
            v.put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME);
            v.put(CalendarContract.Calendars.VISIBLE, 1);
            v.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
            Uri uri = cr.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), v);
            if (uri != null) return Long.parseLong(uri.getLastPathSegment());
        } catch (Exception ignored) { }
        return -1;
    }

    static Uri asSyncAdapter(Uri uri) {
        return uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build();
    }

    static void syncAll(Context ctx, Store store) {
        long calId = getOrCreateCalendar(ctx);
        if (calId < 0) return;
        ContentResolver cr = ctx.getContentResolver();
        try {
            cr.delete(CalendarContract.Events.CONTENT_URI,
                CalendarContract.Events.CALENDAR_ID + "=?", new String[]{String.valueOf(calId)});
        } catch (Exception ignored) { }

        for (Store.Task t : store.loadTasks()) {
            if (t.parent != null || t.done == 1 || t.deleted == 1 || t.due == 0) continue;
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, calId);
            v.put(CalendarContract.Events.TITLE, t.title);
            String desc = t.notes != null && t.notes.length() > 0 ? t.notes : "Задача из Планировщика";
            v.put(CalendarContract.Events.DESCRIPTION, desc);
            if (t.hasTime == 1 && t.time != null && t.time.length() > 0) {
                long start = t.due;
                v.put(CalendarContract.Events.DTSTART, start);
                v.put(CalendarContract.Events.DTEND, start + 3600000L);
                v.put(CalendarContract.Events.ALL_DAY, 0);
            } else {
                long midnightUtc = midnightUtc(t.due);
                v.put(CalendarContract.Events.DTSTART, midnightUtc);
                v.put(CalendarContract.Events.DTEND, midnightUtc + 86400000L);
                v.put(CalendarContract.Events.ALL_DAY, 1);
            }
            try { cr.insert(CalendarContract.Events.CONTENT_URI, v); } catch (Exception ignored) { }
        }
    }

    static long midnightUtc(long localMillis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(localMillis);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() - java.util.TimeZone.getDefault().getOffset(localMillis);
    }
}
