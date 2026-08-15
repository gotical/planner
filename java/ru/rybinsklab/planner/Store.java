package ru.rybinsklab.planner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Store {

    static class Task {
        long id = 0;
        String title = "";
        String notes = "";
        String time = "";          // "HH:mm" when hasTime
        String repeat = "";        // "", "daily", "weekly", "weekly2", "weekly3", "monthly", "yearly"
        long listId = 0;           // 0 = inbox
        long due = 0;              // midnight of due date (or with time if hasTime)
        long reminder = 0;         // earliest absolute millis, 0 = none (for indicator)
        long createdAt = 0;
        long doneAt = 0;
        int hasTime = 0;
        int priority = 0;          // 0 none, 1 low, 2 medium, 3 high
        int done = 0;
        int pinned = 0;
        int dismissed = 0;
        int deleted = 0;
        long deletedAt = 0;
        ArrayList<Long> tagIds = new ArrayList<>();
        ArrayList<Integer> remOffsets = new ArrayList<>();  // minutes before due
        ArrayList<Task> subs = new ArrayList<>();
        Task parent = null;
    }

    static class TList {
        long id = 0;
        String name = "";
        int color = 0xFF4772FA;
    }

    static class Tag {
        long id = 0;
        String name = "";
        int color = 0xFF4772FA;
    }

    static class Habit {
        long id = 0;
        String name = "";
        int color = 0xFF4772FA;
        long created = 0;
    }

    static class Template {
        long id = 0;
        String name = "";
        String json = "";
    }

    final Context ctx;
    final SQLiteDatabase db;

    Store(Context c) {
        ctx = c.getApplicationContext();
        db = new DB(ctx).getWritableDatabase();
        purgeTrash();
    }

    static class DB extends SQLiteOpenHelper {
        DB(Context c) { super(c, "planner.db", null, 4); }
        public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, notes TEXT, time TEXT, repeat TEXT, list_id INTEGER, due INTEGER, reminder INTEGER, created INTEGER, done_at INTEGER, has_time INTEGER, priority INTEGER, done INTEGER, parent INTEGER, sort INTEGER, pinned INTEGER DEFAULT 0, dismissed INTEGER DEFAULT 0, tags TEXT DEFAULT '', reminders TEXT DEFAULT '', deleted INTEGER DEFAULT 0, deleted_at INTEGER DEFAULT 0)");
            d.execSQL("CREATE TABLE lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, color INTEGER, sort INTEGER)");
            d.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, color INTEGER, sort INTEGER)");
            d.execSQL("CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, color INTEGER, created INTEGER, sort INTEGER)");
            d.execSQL("CREATE TABLE habit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, habit_id INTEGER, date TEXT, done INTEGER)");
            d.execSQL("CREATE TABLE focus_log (id INTEGER PRIMARY KEY AUTOINCREMENT, minutes INTEGER, started INTEGER, task_id INTEGER)");
            d.execSQL("CREATE TABLE templates (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, json TEXT)");
        }
        public void onUpgrade(SQLiteDatabase d, int o, int n) {
            if (o < 3) {
                try { d.execSQL("ALTER TABLE tasks ADD COLUMN pinned INTEGER DEFAULT 0"); } catch (Exception ignored) { }
                try { d.execSQL("ALTER TABLE tasks ADD COLUMN dismissed INTEGER DEFAULT 0"); } catch (Exception ignored) { }
            }
            if (o < 4) {
                try { d.execSQL("ALTER TABLE tasks ADD COLUMN tags TEXT DEFAULT ''"); } catch (Exception ignored) { }
                try { d.execSQL("ALTER TABLE tasks ADD COLUMN reminders TEXT DEFAULT ''"); } catch (Exception ignored) { }
                try { d.execSQL("ALTER TABLE tasks ADD COLUMN deleted INTEGER DEFAULT 0"); } catch (Exception ignored) { }
                try { d.execSQL("ALTER TABLE tasks ADD COLUMN deleted_at INTEGER DEFAULT 0"); } catch (Exception ignored) { }
                try { d.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, color INTEGER, sort INTEGER)"); } catch (Exception ignored) { }
                try { d.execSQL("CREATE TABLE focus_log (id INTEGER PRIMARY KEY AUTOINCREMENT, minutes INTEGER, started INTEGER, task_id INTEGER)"); } catch (Exception ignored) { }
                try { d.execSQL("CREATE TABLE templates (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, json TEXT)"); } catch (Exception ignored) { }
            }
        }
    }

    // ---------- tasks ----------
    ArrayList<Task> loadTasks() {
        ArrayList<Task> all = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM tasks ORDER BY sort, id", null);
        while (c.moveToNext()) {
            Task t = new Task();
            t.id = c.getLong(c.getColumnIndex("id"));
            t.title = c.getString(c.getColumnIndex("title"));
            t.notes = c.getString(c.getColumnIndex("notes"));
            t.time = c.getString(c.getColumnIndex("time"));
            t.repeat = c.getString(c.getColumnIndex("repeat"));
            t.listId = c.getLong(c.getColumnIndex("list_id"));
            t.due = c.getLong(c.getColumnIndex("due"));
            t.reminder = c.getLong(c.getColumnIndex("reminder"));
            t.createdAt = c.getLong(c.getColumnIndex("created"));
            t.doneAt = c.getLong(c.getColumnIndex("done_at"));
            t.hasTime = c.getInt(c.getColumnIndex("has_time"));
            t.priority = c.getInt(c.getColumnIndex("priority"));
            t.done = c.getInt(c.getColumnIndex("done"));
            t.pinned = c.getInt(c.getColumnIndex("pinned"));
            t.dismissed = c.getInt(c.getColumnIndex("dismissed"));
            t.deleted = c.getInt(c.getColumnIndex("deleted"));
            t.deletedAt = c.getLong(c.getColumnIndex("deleted_at"));
            parseCsvLong(c.getString(c.getColumnIndex("tags")), t.tagIds);
            parseCsvInt(c.getString(c.getColumnIndex("reminders")), t.remOffsets);
            long parent = c.getLong(c.getColumnIndex("parent"));
            t.parent = null;
            if (parent != 0) for (Task p : all) if (p.id == parent) { t.parent = p; break; }
            if (t.parent != null) t.parent.subs.add(t); else all.add(t);
        }
        c.close();
        return all;
    }

    static void parseCsvLong(String s, ArrayList<Long> out) {
        if (s == null || s.length() == 0) return;
        for (String p : s.split(",")) { try { long v = Long.parseLong(p.trim()); if (v > 0) out.add(v); } catch (Exception ignored) { } }
    }

    static void parseCsvInt(String s, ArrayList<Integer> out) {
        if (s == null || s.length() == 0) return;
        for (String p : s.split(",")) { try { out.add(Integer.parseInt(p.trim())); } catch (Exception ignored) { } }
    }

    static String csvLong(ArrayList<Long> list) {
        StringBuilder s = new StringBuilder();
        for (long v : list) { if (s.length() > 0) s.append(','); s.append(v); }
        return s.toString();
    }

    static String csvInt(ArrayList<Integer> list) {
        StringBuilder s = new StringBuilder();
        for (int v : list) { if (s.length() > 0) s.append(','); s.append(v); }
        return s.toString();
    }

    void saveTask(Task t) {
        ContentValues v = new ContentValues();
        v.put("title", t.title);
        v.put("notes", t.notes);
        v.put("time", t.time);
        v.put("repeat", t.repeat);
        v.put("list_id", t.listId);
        v.put("due", t.due);
        v.put("reminder", t.reminder);
        v.put("created", t.createdAt == 0 ? System.currentTimeMillis() : t.createdAt);
        v.put("done_at", t.doneAt);
        v.put("has_time", t.hasTime);
        v.put("priority", t.priority);
        v.put("done", t.done);
        v.put("pinned", t.pinned);
        v.put("dismissed", t.dismissed);
        v.put("deleted", t.deleted);
        v.put("deleted_at", t.deletedAt);
        v.put("tags", csvLong(t.tagIds));
        v.put("reminders", csvInt(t.remOffsets));
        v.put("parent", t.parent != null ? t.parent.id : 0);
        if (t.id == 0) { t.id = db.insert("tasks", null, v); }
        else { db.update("tasks", v, "id=?", new String[]{String.valueOf(t.id)}); }
    }

    // soft delete -> recycle bin
    void deleteTask(Task t) {
        t.deleted = 1;
        t.deletedAt = System.currentTimeMillis();
        saveTask(t);
        Reminders.cancel(ctx, t.id);
    }

    void hardDelete(Task t) {
        db.delete("tasks", "id=? OR parent=?", new String[]{String.valueOf(t.id), String.valueOf(t.id)});
        Reminders.cancel(ctx, t.id);
    }

    void restoreTask(Task t) {
        t.deleted = 0;
        t.deletedAt = 0;
        saveTask(t);
    }

    void purgeTrash() {
        long cutoff = System.currentTimeMillis() - 30L * 86400000L;
        db.delete("tasks", "deleted=1 AND deleted_at>0 AND deleted_at<?", new String[]{String.valueOf(cutoff)});
    }

    void emptyTrash() {
        db.delete("tasks", "deleted=1", null);
    }

    void setDone(Task t, boolean done) {
        t.done = done ? 1 : 0;
        t.doneAt = done ? System.currentTimeMillis() : 0;
        saveTask(t);
        if (done) Reminders.cancel(ctx, t.id); else if (t.reminder > 0 && t.reminder > System.currentTimeMillis()) Reminders.schedule(ctx, t);
    }

    // ---------- lists ----------
    ArrayList<TList> loadLists() {
        ArrayList<TList> l = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM lists ORDER BY sort, id", null);
        while (c.moveToNext()) {
            TList x = new TList();
            x.id = c.getLong(c.getColumnIndex("id"));
            x.name = c.getString(c.getColumnIndex("name"));
            x.color = c.getInt(c.getColumnIndex("color"));
            l.add(x);
        }
        c.close();
        return l;
    }

    void saveList(TList l) {
        ContentValues v = new ContentValues();
        v.put("name", l.name);
        v.put("color", l.color);
        v.put("sort", l.id);
        if (l.id == 0) l.id = db.insert("lists", null, v);
        else db.update("lists", v, "id=?", new String[]{String.valueOf(l.id)});
    }

    void deleteList(long id) {
        db.delete("lists", "id=?", new String[]{String.valueOf(id)});
        ContentValues v = new ContentValues();
        v.put("list_id", 0);
        db.update("tasks", v, "list_id=?", new String[]{String.valueOf(id)});
    }

    // ---------- tags ----------
    ArrayList<Tag> loadTags() {
        ArrayList<Tag> l = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM tags ORDER BY sort, id", null);
        while (c.moveToNext()) {
            Tag t = new Tag();
            t.id = c.getLong(c.getColumnIndex("id"));
            t.name = c.getString(c.getColumnIndex("name"));
            t.color = c.getInt(c.getColumnIndex("color"));
            l.add(t);
        }
        c.close();
        return l;
    }

    void saveTag(Tag t) {
        ContentValues v = new ContentValues();
        v.put("name", t.name);
        v.put("color", t.color);
        v.put("sort", t.id);
        if (t.id == 0) t.id = db.insert("tags", null, v);
        else db.update("tags", v, "id=?", new String[]{String.valueOf(t.id)});
    }

    void deleteTag(long id) {
        db.delete("tags", "id=?", new String[]{String.valueOf(id)});
        // remove tag from all tasks
        Cursor c = db.rawQuery("SELECT id, tags FROM tasks WHERE tags != ''", null);
        while (c.moveToNext()) {
            long tid = c.getLong(0);
            String tags = c.getString(1);
            ArrayList<Long> ids = new ArrayList<>();
            parseCsvLong(tags, ids);
            ids.remove(Long.valueOf(id));
            ContentValues v = new ContentValues();
            v.put("tags", csvLong(ids));
            db.update("tasks", v, "id=?", new String[]{String.valueOf(tid)});
        }
        c.close();
    }

    // ---------- focus ----------
    void addFocusLog(int minutes) {
        ContentValues v = new ContentValues();
        v.put("minutes", minutes);
        v.put("started", System.currentTimeMillis());
        v.put("task_id", 0);
        db.insert("focus_log", null, v);
    }

    int focusMinutesForDay(long dayStart) {
        int total = 0;
        Cursor c = db.rawQuery("SELECT minutes, started FROM focus_log", null);
        while (c.moveToNext()) {
            long started = c.getLong(1);
            if (sameDay(started, dayStart)) total += c.getInt(0);
        }
        c.close();
        return total;
    }

    int totalFocusMinutes() {
        int total = 0;
        Cursor c = db.rawQuery("SELECT SUM(minutes) FROM focus_log", null);
        if (c.moveToFirst()) total = c.getInt(0);
        c.close();
        return total;
    }

    // ---------- templates ----------
    ArrayList<Template> loadTemplates() {
        ArrayList<Template> l = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM templates ORDER BY id", null);
        while (c.moveToNext()) {
            Template t = new Template();
            t.id = c.getLong(c.getColumnIndex("id"));
            t.name = c.getString(c.getColumnIndex("name"));
            t.json = c.getString(c.getColumnIndex("json"));
            l.add(t);
        }
        c.close();
        return l;
    }

    void saveTemplate(Template t) {
        ContentValues v = new ContentValues();
        v.put("name", t.name);
        v.put("json", t.json);
        if (t.id == 0) t.id = db.insert("templates", null, v);
        else db.update("templates", v, "id=?", new String[]{String.valueOf(t.id)});
    }

    void deleteTemplate(long id) {
        db.delete("templates", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------- habits ----------
    ArrayList<Habit> loadHabits() {
        ArrayList<Habit> l = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM habits ORDER BY sort, id", null);
        while (c.moveToNext()) {
            Habit h = new Habit();
            h.id = c.getLong(c.getColumnIndex("id"));
            h.name = c.getString(c.getColumnIndex("name"));
            h.color = c.getInt(c.getColumnIndex("color"));
            h.created = c.getLong(c.getColumnIndex("created"));
            l.add(h);
        }
        c.close();
        return l;
    }

    void saveHabit(Habit h) {
        ContentValues v = new ContentValues();
        v.put("name", h.name);
        v.put("color", h.color);
        v.put("created", h.created == 0 ? System.currentTimeMillis() : h.created);
        v.put("sort", h.id);
        if (h.id == 0) h.id = db.insert("habits", null, v);
        else db.update("habits", v, "id=?", new String[]{String.valueOf(h.id)});
    }

    void deleteHabit(long id) {
        db.delete("habits", "id=?", new String[]{String.valueOf(id)});
        db.delete("habit_log", "habit_id=?", new String[]{String.valueOf(id)});
    }

    boolean habitChecked(long habitId, String date) {
        Cursor c = db.rawQuery("SELECT done FROM habit_log WHERE habit_id=? AND date=?", new String[]{String.valueOf(habitId), date});
        boolean r = c.moveToFirst() && c.getInt(0) == 1;
        c.close();
        return r;
    }

    void setHabitChecked(long habitId, String date, boolean checked) {
        db.delete("habit_log", "habit_id=? AND date=?", new String[]{String.valueOf(habitId), date});
        if (checked) {
            ContentValues v = new ContentValues();
            v.put("habit_id", habitId);
            v.put("date", date);
            v.put("done", 1);
            db.insert("habit_log", null, v);
        }
    }

    // ---------- helpers ----------
    static String dateStr(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }

    static long todayStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long addDays(long base, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(base);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(); x.setTimeInMillis(a);
        Calendar y = Calendar.getInstance(); y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    static String weekdayShort(long millis) {
        String[] w = {"Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"};
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(millis);
        return w[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    static String monthName(int m) {
        String[] mo = {"января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря"};
        return mo[m];
    }

    static String fullDate(long millis) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(millis);
        return c.get(Calendar.DAY_OF_MONTH) + " " + monthName(c.get(Calendar.MONTH)) + " " + c.get(Calendar.YEAR);
    }

    static String shortDate(long millis) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(millis);
        return c.get(Calendar.DAY_OF_MONTH) + " " + monthName(c.get(Calendar.MONTH));
    }
}
