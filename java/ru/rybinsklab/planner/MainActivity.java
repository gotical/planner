package ru.rybinsklab.planner;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class MainActivity extends Activity {

    Store store;
    ArrayList<Store.Task> tasks = new ArrayList<>();
    ArrayList<Store.TList> lists = new ArrayList<>();
    ArrayList<Store.Habit> habits = new ArrayList<>();
    ArrayList<Store.Tag> tags = new ArrayList<>();
    ArrayList<Store.Wish> wishes = new ArrayList<>();

    int tab = 0;
    String view = "today";

    FrameLayout root, content, fabLayer;
    LinearLayout nav;
    FrameLayout drawerLayer;
    ArrayList<View> stack = new ArrayList<>();

    final int[] LIST_COLORS = {0xFF4772FA, 0xFF1E88E5, 0xFF43A047, 0xFFF57C00, 0xFFE53935, 0xFF8E24AA, 0xFF00897B, 0xFF6D4C41, 0xFF546E7A, 0xFFD81B60};

    // focus
    int focusState = 0; // 0 idle, 1 running, 2 paused
    long focusEnd = 0, focusRemain = 25 * 60 * 1000L;
    int focusMode = 25;
    TextView focusTimeTv, focusHintTv;
    Handler h = new Handler(Looper.getMainLooper());
    Runnable focusTicker = new Runnable() { public void run() { updateFocus(); h.postDelayed(this, 250); } };

    int dp(int v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().setStatusBarColor(Ui.BG);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(Ui.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        store = new Store(this);
        reload();
        buildShell();
        showTab(0);
    }

    void reload() {
        tasks = store.loadTasks();
        lists = store.loadLists();
        habits = store.loadHabits();
        tags = store.loadTags();
        wishes = store.loadWishes();
    }

    // ================= SHELL =================
    void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        content = new FrameLayout(this);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
        cp.bottomMargin = dp(60);
        root.addView(content, cp);

        fabLayer = new FrameLayout(this);
        root.addView(fabLayer, new FrameLayout.LayoutParams(-1, -1));

        nav = Ui.row(this);
        nav.setBackgroundColor(Ui.CARD);
        nav.setPadding(0, dp(4), 0, dp(6));
        nav.setGravity(Gravity.CENTER);

        nav.addView(navItem(R.drawable.ic_tasks, "Задачи", 0), Ui.weight(1));
        nav.addView(navItem(R.drawable.ic_calendar, "Календарь", 1), Ui.weight(1));
        nav.addView(navItem(R.drawable.ic_focus, "Фокус", 2), Ui.weight(1));
        nav.addView(navItem(R.drawable.ic_habit, "Привычки", 3), Ui.weight(1));
        nav.addView(navItem(R.drawable.ic_more, "Ещё", 4), Ui.weight(1));

        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, dp(60), Gravity.BOTTOM);
        root.addView(nav, np);

        View navLine = new View(this);
        navLine.setBackgroundColor(Ui.DIVIDER);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM);
        nl.bottomMargin = dp(60);
        root.addView(navLine, nl);

        setContentView(root);
    }

    View navItem(int resId, String label, int id) {
        LinearLayout x = Ui.col(this);
        x.setGravity(Gravity.CENTER);
        ImageView i = Ui.icon(this, resId, 24, id == tab ? Ui.ACCENT : Ui.FAINT);
        TextView l = Ui.tv(this, label, 10, id == tab ? Ui.ACCENT : Ui.FAINT);
        l.setGravity(Gravity.CENTER);
        x.addView(i);
        x.addView(l);
        x.setTag(new View[]{i, l});
        x.setOnClickListener(v -> { tab = id; closeDrawer(); showTab(id); });
        return x;
    }

    void showTab(int id) {
        if (focusState == 1 && id != 2) { /* keep running */ }
        content.removeAllViews();
        fabLayer.removeAllViews();
        stack.clear();
        updateNav();
        switch (id) {
            case 0: tasksScreen(); break;
            case 1: calendarScreen(); break;
            case 2: focusScreen(); break;
            case 3: habitsScreen(); break;
            default: moreScreen(); break;
        }
    }

    void updateNav() {
        for (int i = 0; i < nav.getChildCount(); i++) {
            View v = nav.getChildAt(i);
            if (v.getTag() instanceof View[]) {
                View[] p = (View[]) v.getTag();
                int c = i == tab ? Ui.ACCENT : Ui.FAINT;
                ((ImageView) p[0]).setColorFilter(c, PorterDuff.Mode.SRC_IN);
                ((TextView) p[1]).setTextColor(c);
            }
        }
    }

    // ================= DRAWER =================
    void openDrawer() {
        if (drawerLayer != null) root.removeView(drawerLayer);
        drawerLayer = new FrameLayout(this);
        drawerLayer.setBackgroundColor(0x99000000);
        drawerLayer.setClickable(true);
        drawerLayer.setOnClickListener(v -> closeDrawer());

        LinearLayout panel = Ui.col(this);
        panel.setBackgroundColor(Ui.CARD);
        panel.setPadding(0, dp(18), 0, dp(18));

        ScrollView sv = Ui.scroll(this);
        LinearLayout col = Ui.col(this);

        // profile
        LinearLayout prof = Ui.row(this);
        prof.setPadding(dp(20), dp(8), dp(20), dp(16));
        TextView av = Ui.tv(this, "Р", 22, Color.WHITE, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Ui.oval(Ui.ACCENT));
        int as = dp(46);
        av.setLayoutParams(new LinearLayout.LayoutParams(as, as));
        prof.addView(av);
        LinearLayout pn = Ui.col(this);
        pn.setPadding(dp(12), 0, 0, 0);
        pn.addView(Ui.tv(this, "РыбинскLAB", 16, Ui.TEXT, true));
        pn.addView(Ui.tv(this, "rybinsklab.ru", 12, Ui.SUB));
        prof.addView(pn, Ui.weight(1));
        col.addView(prof);

        Ui.divider(col, this, dp(20));

        col.addView(drawerItem(R.drawable.ic_today, "Сегодня", "today", null));
        col.addView(drawerItem(R.drawable.ic_event, "Завтра", "tomorrow", null));
        col.addView(drawerItem(R.drawable.ic_date_range, "Следующие 7 дней", "next7", null));
        col.addView(drawerItem(R.drawable.ic_list, "Все задачи", "all", null));
        col.addView(drawerItem(R.drawable.ic_done_all, "Завершённые", "completed", null));
        col.addView(drawerItem(R.drawable.ic_block, "Пропущенные", "skipped", null));
        col.addView(drawerItem(R.drawable.ic_delete, "Корзина", "trash", null));
        col.addView(drawerItem(R.drawable.ic_favorite, "Желания", "wish", null));

        Ui.divider(col, this, dp(20));

        TextView lh = Ui.tv(this, "Списки", 12, Ui.SUB, true);
        lh.setPadding(dp(20), dp(14), dp(20), dp(6));
        col.addView(lh);
        col.addView(drawerItem(R.drawable.ic_inbox, "Входящие", "inbox", null));
        for (Store.TList l : lists) {
            col.addView(drawerItem(R.drawable.ic_list, l.name, "list:" + l.id, l.color));
        }
        TextView addList = Ui.tv(this, "  +  Добавить список", 14, Ui.ACCENT);
        addList.setPadding(dp(20), dp(12), dp(20), dp(12));
        addList.setOnClickListener(v -> { closeDrawer(); addListDialog(); });
        col.addView(addList);

        Ui.divider(col, this, dp(20));
        if (tags.size() > 0) {
            TextView th = Ui.tv(this, "Теги", 12, Ui.SUB, true);
            th.setPadding(dp(20), dp(14), dp(20), dp(6));
            col.addView(th);
            for (Store.Tag g : tags) {
                col.addView(drawerItem(R.drawable.ic_tag, g.name, "tag:" + g.id, g.color));
            }
        }
        TextView addTag = Ui.tv(this, "  +  Добавить тег", 14, Ui.ACCENT);
        addTag.setPadding(dp(20), dp(12), dp(20), dp(12));
        addTag.setOnClickListener(v -> { closeDrawer(); addTagDialog(); });
        col.addView(addTag);

        Ui.divider(col, this, dp(20));
        col.addView(drawerItem(R.drawable.ic_settings, "Настройки", "__settings", null));
        col.addView(drawerItem(R.drawable.ic_info, "О приложении", "__about", null));

        sv.addView(col);
        panel.addView(sv, new LinearLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(292), -1, Gravity.LEFT);
        drawerLayer.addView(panel, plp);
        root.addView(drawerLayer, new FrameLayout.LayoutParams(-1, -1));
    }

    View drawerItem(int resId, String label, final String target, Integer color) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(20), dp(12), dp(16), dp(12));
        int c = color != null ? color : Ui.SUB;
        ImageView ic = Ui.icon(this, resId, 22, c);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        r.addView(ic);
        boolean sel = view.equals(target);
        r.addView(Ui.tv(this, label, 15, sel ? Ui.ACCENT : Ui.TEXT, sel), Ui.weight(1));
        r.setOnClickListener(v -> {
            closeDrawer();
            if ("__settings".equals(target)) { pushSettings(); return; }
            if ("__about".equals(target)) { pushAbout(); return; }
            view = target;
            tab = 0;
            showTab(0);
        });
        return r;
    }

    void closeDrawer() {
        if (drawerLayer != null) { root.removeView(drawerLayer); drawerLayer = null; }
    }

    // ================= PUSH / BACK =================
    void push(View screen) {
        LinearLayout wrap = Ui.col(this);
        wrap.setBackgroundColor(Ui.BG);
        wrap.addView(screen, new LinearLayout.LayoutParams(-1, 0, 1));
        stack.add(wrap);
        content.removeAllViews();
        fabLayer.removeAllViews();
        content.addView(wrap, new FrameLayout.LayoutParams(-1, -1));
    }

    View pushHeader(String title, Runnable onBack) {
        LinearLayout tb = Ui.row(this);
        tb.setPadding(dp(8), dp(10), dp(16), dp(10));
        tb.setBackgroundColor(Ui.CARD);
        ImageView bk = Ui.iconTouch(this, R.drawable.ic_back, 40, Ui.ACCENT);
        bk.setOnClickListener(v -> onBack.run());
        tb.addView(bk);
        TextView ti = Ui.tv(this, title, 18, Ui.TEXT, true);
        ti.setPadding(dp(8), 0, 0, 0);
        tb.addView(ti, Ui.weight(1));
        return tb;
    }

    void pop() {
        if (!stack.isEmpty()) {
            View v = stack.remove(stack.size() - 1);
            content.removeView(v);
        }
        if (stack.isEmpty()) { showTab(tab); }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayer != null) { closeDrawer(); return; }
        if (!stack.isEmpty()) { pop(); return; }
        if (tab != 0) { tab = 0; showTab(0); return; }
        super.onBackPressed();
    }

    // ================= HEADER =================
    LinearLayout topBar(String title, boolean hamburger, int rightIcon, Runnable onRight, Runnable onLeft) {
        LinearLayout tb = Ui.row(this);
        tb.setPadding(dp(8), dp(8), dp(12), dp(8));
        tb.setBackgroundColor(Ui.CARD);
        if (hamburger) {
            ImageView ham = Ui.iconTouch(this, R.drawable.ic_menu, 40, Ui.TEXT);
            ham.setOnClickListener(v -> { if (onLeft != null) onLeft.run(); else openDrawer(); });
            tb.addView(ham);
        }
        TextView ti = Ui.tv(this, title, 20, Ui.TEXT, true);
        ti.setPadding(dp(6), 0, dp(6), 0);
        tb.addView(ti, Ui.weight(1));
        if (rightIcon != 0) {
            ImageView r = Ui.iconTouch(this, rightIcon, 40, Ui.ACCENT);
            r.setOnClickListener(v -> onRight.run());
            tb.addView(r);
        }
        return tb;
    }

    // ================= TASKS =================
    void tasksScreen() {
        content.removeAllViews();
        fabLayer.removeAllViews();
        if ("wish".equals(view)) { wishScreen(); return; }
        LinearLayout col = Ui.col(this);
        col.addView(tasksHeader());

        LinearLayout list = Ui.col(this);
        ScrollView sv = Ui.scroll(this);
        sv.addView(list);

        ArrayList<Store.Task> shown = filterTasks();
        if ("today".equals(view)) {
            list.addView(todayProgress());
            list.addView(postponeOverdueButton());
            list.addView(suggestedTasksCard());
        }
        renderTaskGroups(list, shown);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        col.addView(sv, lp);
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
        addFab();
    }

    LinearLayout tasksHeader() {
        LinearLayout tb = Ui.row(this);
        tb.setBackgroundColor(Ui.CARD);
        tb.setPadding(dp(6), dp(8), dp(6), dp(8));
        ImageView ham = Ui.iconTouch(this, R.drawable.ic_menu, 40, Ui.TEXT);
        ham.setOnClickListener(v -> openDrawer());
        tb.addView(ham);
        LinearLayout titles = Ui.col(this);
        TextView ti = Ui.tv(this, viewTitle(), 21, Ui.TEXT, true);
        titles.addView(ti);
        TextView sub = Ui.tv(this, todayLine(), 12, Ui.SUB);
        titles.addView(sub);
        tb.addView(titles, Ui.weight(1));
        ImageView search = Ui.iconTouch(this, R.drawable.ic_search, 40, Ui.TEXT);
        search.setOnClickListener(v -> openSearch());
        tb.addView(search);
        ImageView add = Ui.iconTouch(this, R.drawable.ic_add, 40, Ui.ACCENT);
        add.setOnClickListener(v -> openEditorNew());
        tb.addView(add);
        return tb;
    }

    String todayLine() {
        String[] mo = {"января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря"};
        Calendar c = Calendar.getInstance();
        String[] wd = {"воскресенье","понедельник","вторник","среда","четверг","пятница","суббота"};
        return wd[c.get(Calendar.DAY_OF_WEEK)-1] + ", " + c.get(Calendar.DAY_OF_MONTH) + " " + mo[c.get(Calendar.MONTH)];
    }

    View todayProgress() {
        long today = Store.todayStart();
        int remaining = 0, doneToday = 0;
        for (Store.Task t : tasks) {
            if (t.parent != null) continue;
            if (t.done == 0 && (t.due == 0 || t.due <= today)) remaining++;
            if (t.done == 1 && t.doneAt > 0 && Store.sameDay(t.doneAt, today)) doneToday++;
        }
        int total = remaining + doneToday;
        LinearLayout card = Ui.col(this);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Ui.bg(this, Ui.CARD, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), dp(4), dp(16), dp(12));
        card.setLayoutParams(lp);
        if (total == 0) {
            TextView t = Ui.tv(this, "Планов на сегодня нет", 14, Ui.SUB);
            card.addView(t);
            return card;
        }
        LinearLayout top = Ui.row(this);
        top.addView(Ui.tv(this, "Прогресс дня", 14, Ui.TEXT, true), Ui.weight(1));
        top.addView(Ui.tv(this, doneToday + " из " + total, 13, Ui.SUB));
        card.addView(top);
        card.addView(Ui.spacer(this, dp(10)));
        FrameLayout bar = new FrameLayout(this);
        View bg = new View(this);
        bg.setBackgroundColor(Ui.ACCENT_SOFT);
        bar.addView(bg, new FrameLayout.LayoutParams(-1, dp(8)));
        View fill = new View(this);
        fill.setBackgroundColor(Ui.ACCENT);
        int pct = total == 0 ? 0 : (int)(doneToday * 100L / total);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(0, dp(8));
        fp.width = Math.max(dp(8), (int)((screenW() - dp(64)) * pct / 100f));
        bar.addView(fill, fp);
        card.addView(bar);
        return card;
    }

    int screenW() { return getResources().getDisplayMetrics().widthPixels; }

    View postponeOverdueButton() {
        long today = Store.todayStart();
        int overdue = 0;
        for (Store.Task t : tasks) if (t.parent == null && t.done == 0 && t.dismissed == 0 && t.due > 0 && t.due < today) overdue++;
        if (overdue == 0) return Ui.spacer(this, 0);
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        r.setBackground(Ui.bg(this, Ui.CARD, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), 0, dp(16), dp(12));
        r.setLayoutParams(lp);
        r.addView(Ui.tv(this, "⚠ Просрочено: " + overdue, 14, Ui.ORANGE, true), Ui.weight(1));
        TextView act = Ui.tv(this, "На сегодня", 14, Ui.ACCENT, true);
        act.setPadding(dp(10), dp(4), dp(2), dp(4));
        act.setOnClickListener(v -> {
            for (Store.Task t : tasks) if (t.parent == null && t.done == 0 && t.due > 0 && t.due < today) { t.due = today; if (t.hasTime == 1) t.due += timeMillis(t.time); store.saveTask(t); }
            reload();
            tasksScreen();
            toast("Перенесено на сегодня");
        });
        r.addView(act);
        return r;
    }

    View suggestedTasksCard() {
        long today = Store.todayStart();
        ArrayList<Store.Task> sugg = new ArrayList<>();
        for (Store.Task t : tasks) if (t.parent == null && t.done == 0 && t.dismissed == 0 && t.due == 0) sugg.add(t);
        if (sugg.isEmpty()) return Ui.spacer(this, 0);
        LinearLayout card = Ui.col(this);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Ui.bg(this, Ui.CARD, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), 0, dp(16), dp(12));
        card.setLayoutParams(lp);
        card.addView(Ui.tv(this, "💡 Рекомендуем сегодня", 14, Ui.TEXT, true));
        card.addView(Ui.spacer(this, dp(8)));
        int n = Math.min(3, sugg.size());
        for (int i = 0; i < n; i++) {
            final Store.Task t = sugg.get(i);
            LinearLayout row = Ui.row(this);
            row.setPadding(0, dp(6), 0, dp(6));
            TextView c = Ui.tv(this, "＋", 16, Ui.ACCENT, true);
            c.setPadding(0, 0, dp(8), 0);
            row.addView(c);
            row.addView(Ui.tv(this, t.title, 14, Ui.TEXT), Ui.weight(1));
            row.setOnClickListener(v -> { t.due = today; store.saveTask(t); reload(); tasksScreen(); toast("Добавлено на сегодня"); });
            card.addView(row);
        }
        return card;
    }

    void openSearch() {
        LinearLayout col = Ui.col(this);
        View hb = pushHeader("Поиск", this::pop);
        col.addView(hb);
        final EditText input = Ui.et(this, "Искать задачи…", 16);
        input.setBackground(Ui.bg(this, Ui.CARD, 12));
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout inputWrap = Ui.col(this);
        inputWrap.setPadding(dp(16), dp(12), dp(16), dp(8));
        inputWrap.addView(input);
        col.addView(inputWrap);
        final LinearLayout results = Ui.col(this);
        results.setPadding(dp(16), dp(8), dp(16), dp(20));
        ScrollView sv = Ui.scroll(this);
        sv.addView(results);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        push(col);

        input.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) { filterSearch(results, s.toString()); }
            public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            public void onTextChanged(CharSequence c, int a, int b, int d) { }
        });
        input.requestFocus();
    }

    void filterSearch(LinearLayout results, String q) {
        results.removeAllViews();
        String low = q.toLowerCase(Locale.ROOT).trim();
        if (low.length() == 0) {
            TextView hint = Ui.tv(this, "Введите запрос", 15, Ui.SUB);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(40), 0, 0);
            results.addView(hint);
            return;
        }
        int n = 0;
        for (Store.Task t : tasks) {
            if (t.parent != null) continue;
            if (t.title.toLowerCase(Locale.ROOT).contains(low) || t.notes.toLowerCase(Locale.ROOT).contains(low)) {
                results.addView(taskRow(t));
                n++;
            }
        }
        if (n == 0) {
            TextView e = Ui.tv(this, "Ничего не найдено", 15, Ui.SUB);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(40), 0, 0);
            results.addView(e);
        }
    }

    String viewTitle() {
        switch (view) {
            case "today": return "Сегодня";
            case "tomorrow": return "Завтра";
            case "next7": return "Следующие 7 дней";
            case "all": return "Все задачи";
            case "completed": return "Завершённые";
            case "skipped": return "Пропущенные";
            case "trash": return "Корзина";
            case "wish": return "Желания";
            case "inbox": return "Входящие";
            default:
                if (view.startsWith("list:")) {
                    long id = Long.parseLong(view.substring(5));
                    for (Store.TList l : lists) if (l.id == id) return l.name;
                }
                if (view.startsWith("tag:")) {
                    long id = Long.parseLong(view.substring(4));
                    for (Store.Tag g : tags) if (g.id == id) return "#" + g.name;
                }
                return "Задачи";
        }
    }

    ArrayList<Store.Task> filterTasks() {
        ArrayList<Store.Task> out = new ArrayList<>();
        long today = Store.todayStart();
        for (Store.Task t : tasks) {
            if (t.parent != null) continue; // top-level only
            if (t.deleted == 1) {
                if ("trash".equals(view)) out.add(t);
                continue;
            }
            switch (view) {
                case "today":
                    if (t.done == 0 && t.dismissed == 0 && (t.due == 0 || t.due <= today)) out.add(t);
                    break;
                case "tomorrow":
                    if (t.done == 0 && t.dismissed == 0 && t.due > 0 && Store.sameDay(t.due, Store.addDays(today, 1))) out.add(t);
                    break;
                case "next7":
                    if (t.done == 0 && t.dismissed == 0 && t.due > 0 && t.due >= today && t.due <= Store.addDays(today, 7)) out.add(t);
                    break;
                case "all":
                    if (t.done == 0 && t.dismissed == 0) out.add(t);
                    break;
                case "completed":
                    if (t.done == 1) out.add(t);
                    break;
                case "skipped":
                    if (t.dismissed == 1) out.add(t);
                    break;
                case "inbox":
                    if (t.done == 0 && t.dismissed == 0 && t.listId == 0) out.add(t);
                    break;
                default:
                    if (view.startsWith("list:")) {
                        long id = Long.parseLong(view.substring(5));
                        if (t.done == 0 && t.dismissed == 0 && t.listId == id) out.add(t);
                    } else if (view.startsWith("tag:")) {
                        long id = Long.parseLong(view.substring(4));
                        if (t.done == 0 && t.dismissed == 0 && t.tagIds.contains(id)) out.add(t);
                    }
            }
        }
        Collections.sort(out, (a, b) -> {
            if (a.pinned != b.pinned) return b.pinned - a.pinned;
            long da = a.due == 0 ? Long.MAX_VALUE : a.due;
            long db = b.due == 0 ? Long.MAX_VALUE : b.due;
            if (da != db) return Long.compare(da, db);
            return Long.compare(a.createdAt, b.createdAt);
        });
        return out;
    }

    void renderTaskGroups(LinearLayout list, ArrayList<Store.Task> shown) {
        long today = Store.todayStart();
        String current = null;
        boolean pinnedHeaderShown = false;
        for (Store.Task t : shown) {
            String header = null;
            if (t.pinned == 1) {
                if (!pinnedHeaderShown) { header = "📌 Закреплённые"; pinnedHeaderShown = true; }
            } else if ("skipped".equals(view)) {
                header = "Отложенные задачи";
            } else if ("today".equals(view)) {
                if (t.due > 0 && t.due < today) header = "Просроченные";
                else if (t.due > 0 && Store.sameDay(t.due, today)) header = "Сегодня";
                else if (t.due == 0) header = "Без даты";
                else header = "Предстоящие";
            } else if ("completed".equals(view)) {
                header = Store.shortDate(t.doneAt);
            } else if ("all".equals(view) || "inbox".equals(view)) {
                if (t.due == 0) header = "Без даты";
                else if (t.due < today) header = "Просроченные";
                else header = Store.shortDate(t.due);
            } else {
                if (t.due == 0) header = "Без даты";
                else header = Store.shortDate(t.due);
            }
            if (header != null && !header.equals(current)) {
                current = header;
                TextView hd = Ui.tv(this, header, 13, Ui.SUB, true);
                hd.setPadding(dp(24), dp(16), dp(24), dp(6));
                list.addView(hd);
            }
            list.addView(taskRow(t));
        }
        if (shown.isEmpty()) {
            TextView e = Ui.tv(this, emptyText(), 16, Ui.SUB);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(30), dp(80), dp(30), dp(40));
            list.addView(e);
        }
    }

    String emptyText() {
        if ("completed".equals(view)) return "Здесь появятся завершённые задачи 🎉";
        if ("today".equals(view)) return "День свободен! ☀️\nНажмите +, чтобы добавить задачу";
        return "Пока пусто 🍃\nНажмите +, чтобы добавить задачу";
    }

    View taskRow(final Store.Task t) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(14), dp(8), dp(12), dp(8));
        r.setBackground(Ui.bg(this, Ui.CARD, 14));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(dp(16), 0, dp(16), dp(8));
        r.setLayoutParams(rp);
        if (t.dismissed == 1) r.setAlpha(0.55f);

        TextView check = Ui.tv(this, t.done == 1 ? "✓" : "", 14, Color.WHITE, true);
        check.setGravity(Gravity.CENTER);
        int cs = dp(24);
        check.setLayoutParams(new LinearLayout.LayoutParams(cs, cs));
        check.setBackground(t.done == 1 ? Ui.oval(Ui.ACCENT) : Ui.ring(this, Ui.ACCENT, 2));
        check.setOnClickListener(v -> {
            completeTask(t, t.done != 1);
        });
        r.addView(check);

        LinearLayout info = Ui.col(this);
        info.setPadding(dp(12), 0, dp(8), 0);
        TextView name = Ui.tv(this, t.title, 16, t.done == 1 ? Ui.SUB : Ui.TEXT);
        if (t.done == 1 || t.dismissed == 1) name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        info.addView(name);
        if (t.subs.size() > 0) {
            int done = 0; for (Store.Task s : t.subs) if (s.done == 1) done++;
            info.addView(Ui.tv(this, "Подзадачи: " + done + "/" + t.subs.size(), 11, Ui.SUB));
        }
        // meta line
        String meta = metaText(t);
        if (meta.length() > 0) info.addView(Ui.tv(this, meta, 12, t.due > 0 && t.due < Store.todayStart() && t.done == 0 ? Ui.RED : Ui.SUB));
        r.addView(info, Ui.weight(1));

        // pin indicator
        if (t.pinned == 1) r.addView(Ui.tv(this, "📌", 14, Ui.SUB));

        // priority flag
        if (t.priority > 0) {
            String f = t.priority == 3 ? "!!!" : (t.priority == 2 ? "!!" : "!");
            int fc = t.priority == 3 ? Ui.RED : (t.priority == 2 ? Ui.ORANGE : Ui.BLUE);
            r.addView(Ui.tv(this, f, 14, fc, true));
        }

        r.setOnClickListener(v -> { if (t.deleted == 1) trashMenu(t); else openEditor(t); });
        r.setOnLongClickListener(v -> { if (t.deleted == 1) trashMenu(t); else taskMenu(t); return true; });
        return r;
    }

    void trashMenu(final Store.Task t) {
        java.util.List<Action> acts = new ArrayList<>();
        acts.add(new Action(R.drawable.ic_undo, "Восстановить", () -> { store.restoreTask(t); reload(); rebuildTasks(); toast("Восстановлено"); }));
        acts.add(new Action(R.drawable.ic_delete, "Удалить навсегда", () -> { store.hardDelete(t); reload(); rebuildTasks(); }));
        actionSheet(t.title, acts);
    }

    String metaText(Store.Task t) {
        StringBuilder s = new StringBuilder();
        if (t.due > 0) {
            long today = Store.todayStart();
            if (Store.sameDay(t.due, today)) s.append("Сегодня");
            else if (Store.sameDay(t.due, Store.addDays(today, 1))) s.append("Завтра");
            else s.append(Store.weekdayShort(t.due)).append(", ").append(Store.shortDate(t.due));
            if (t.hasTime == 1 && t.time.length() > 0) s.append(" ").append(fmtTime(t.time));
        }
        if (t.listId != 0) {
            for (Store.TList l : lists) if (l.id == t.listId) { if (s.length() > 0) s.append(" · "); s.append(l.name); break; }
        }
        if (!t.tagIds.isEmpty()) {
            for (long id : t.tagIds) for (Store.Tag g : tags) if (g.id == id) { s.append(" · #").append(g.name); break; }
        }
        if (t.repeat.length() > 0) { s.append(" · ⟳ ").append(repeatLabel(t.repeat)); }
        if (t.reminder > 0) s.append(" · ⏰");
        return s.toString();
    }

    String repeatLabel(String r) {
        if (r != null && r.startsWith("dow:")) return dowLabel(r);
        if (r != null && r.startsWith("dates:")) return datesLabel(r);
        switch (r) {
            case "daily": return "ежедневно";
            case "weekly": return "еженедельно";
            case "weekly2": return "раз в 2 недели";
            case "weekly3": return "раз в 3 недели";
            case "monthly": return "ежемесячно";
            case "yearly": return "ежегодно";
            default: return r;
        }
    }

    String dowLabel(String r) {
        String[] names = {"Вс","Пн","Вт","Ср","Чт","Пт","Сб"};
        StringBuilder s = new StringBuilder();
        for (String p : r.substring(4).split(",")) {
            try { int d = Integer.parseInt(p.trim()); if (d >= 1 && d <= 7) { if (s.length() > 0) s.append(", "); s.append(names[d]); } } catch (Exception ignored) { }
        }
        return s.length() > 0 ? s.toString() : "по дням недели";
    }

    String datesLabel(String r) {
        StringBuilder s = new StringBuilder();
        for (String p : r.substring(6).split(",")) {
            String[] md = p.split("-");
            if (md.length == 2) {
                if (s.length() > 0) s.append(", ");
                s.append(md[1]).append(".").append(md[0]);
            }
        }
        return s.length() > 0 ? s.toString() : "в выбранные даты";
    }

    String fmtTime(String hhmm) {
        if (hhmm == null || hhmm.length() < 5) return hhmm;
        try {
            boolean t12 = getSharedPreferences("planner", 0).getBoolean("time_12h", false);
            if (!t12) return hhmm;
            int h = Integer.parseInt(hhmm.substring(0, 2));
            String m = hhmm.substring(3);
            String suf = h >= 12 ? " PM" : " AM";
            h = h % 12; if (h == 0) h = 12;
            return h + ":" + m + suf;
        } catch (Exception e) { return hhmm; }
    }

    void completeTask(Store.Task t, boolean done) {
        if (!done) { store.setDone(t, false); reload(); rebuildTasks(); return; }
        store.setDone(t, true);
        if (t.repeat.length() > 0 && t.due > 0) {
            // create next occurrence
            Store.Task next = new Store.Task();
            next.title = t.title; next.notes = t.notes; next.time = t.time;
            next.hasTime = t.hasTime; next.priority = t.priority; next.repeat = t.repeat;
            next.listId = t.listId;
            next.due = nextDue(t.due, t.repeat);
            next.reminder = 0;
            for (Store.Task s : t.subs) { Store.Task ns = new Store.Task(); ns.title = s.title; ns.done = 0; next.subs.add(ns); }
            next.done = 0;
            // save next with subs
            Store.Task savedNext = storeSaveWithSubs(next);
        }
        reload();
        rebuildTasks();
        toast("Выполнено");
    }

    Store.Task storeSaveWithSubs(Store.Task t) {
        store.saveTask(t);
        for (Store.Task s : t.subs) { s.parent = t; store.saveTask(s); }
        return t;
    }

    long nextDue(long due, String repeat) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(due);
        if ("daily".equals(repeat)) c.add(Calendar.DAY_OF_MONTH, 1);
        else if ("weekly".equals(repeat)) c.add(Calendar.WEEK_OF_YEAR, 1);
        else if ("weekly2".equals(repeat)) c.add(Calendar.WEEK_OF_YEAR, 2);
        else if ("weekly3".equals(repeat)) c.add(Calendar.WEEK_OF_YEAR, 3);
        else if ("monthly".equals(repeat)) c.add(Calendar.MONTH, 1);
        else if ("yearly".equals(repeat)) c.add(Calendar.YEAR, 1);
        else if (repeat != null && repeat.startsWith("dow:")) {
            java.util.Set<Integer> days = new java.util.HashSet<>();
            for (String p : repeat.substring(4).split(",")) { try { days.add(Integer.parseInt(p.trim())); } catch (Exception ignored) { } }
            if (!days.isEmpty()) {
                for (int add = 1; add <= 7; add++) {
                    Calendar n = (Calendar) c.clone();
                    n.add(Calendar.DAY_OF_MONTH, add);
                    if (days.contains(n.get(Calendar.DAY_OF_WEEK))) return n.getTimeInMillis();
                }
            }
            c.add(Calendar.WEEK_OF_YEAR, 1);
        }
        else if (repeat != null && repeat.startsWith("dates:")) {
            java.util.Set<String> dates = new java.util.HashSet<>();
            for (String p : repeat.substring(6).split(",")) dates.add(p.trim());
            if (!dates.isEmpty()) {
                for (int add = 1; add <= 366; add++) {
                    Calendar n = (Calendar) c.clone();
                    n.add(Calendar.DAY_OF_MONTH, add);
                    String key = (n.get(Calendar.MONTH) + 1) + "-" + n.get(Calendar.DAY_OF_MONTH);
                    if (dates.contains(key)) return n.getTimeInMillis();
                }
            }
            c.add(Calendar.YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    void rebuildTasks() { if (tab == 0 && stack.isEmpty()) tasksScreen(); }

    // ================= WISHLIST (Желания) =================
    void wishScreen() {
        LinearLayout col = Ui.col(this);
        col.addView(topBar("Желания", true, R.drawable.ic_add, this::addWishDialog, null));
        ScrollView sv = Ui.scroll(this);
        LinearLayout list = Ui.col(this);
        list.setPadding(dp(16), dp(12), dp(16), dp(20));
        if (wishes.isEmpty()) {
            TextView e = Ui.tv(this, "Список желаний пуст 💝\nДобавьте название товара или ссылку\nна Ozon / Wildberries", 15, Ui.SUB);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(30), dp(80), dp(30), dp(40));
            list.addView(e);
        }
        for (Store.Wish w : wishes) list.addView(wishRow(w));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
        addFabWish();
    }

    void addFabWish() {
        ImageView fab = Ui.icon(this, R.drawable.ic_add, 28, Color.WHITE);
        fab.setBackground(Ui.oval(Ui.ACCENT));
        fab.setScaleType(ImageView.ScaleType.CENTER);
        fab.setOnClickListener(v -> addWishDialog());
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.RIGHT | Gravity.BOTTOM);
        fp.setMargins(0, 0, dp(18), dp(74));
        fabLayer.addView(fab, fp);
    }

    View wishRow(final Store.Wish w) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(14), dp(12), dp(12), dp(12));
        r.setBackground(Ui.bg(this, Ui.CARD, 14));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(0, 0, 0, dp(10));
        r.setLayoutParams(rp);

        ImageView ic = Ui.icon(this, R.drawable.ic_favorite, 22, Ui.ACCENT);
        ic.setPadding(0, 0, dp(10), 0);
        r.addView(ic);

        LinearLayout info = Ui.col(this);
        TextView name = Ui.tv(this, w.title, 16, Ui.TEXT);
        info.addView(name);
        if (w.price.length() > 0) {
            TextView price = Ui.tv(this, w.price, 14, Ui.ACCENT, true);
            price.setPadding(0, dp(2), 0, 0);
            info.addView(price);
        }
        r.addView(info, Ui.weight(1));

        if (w.url.length() > 0) {
            ImageView open = Ui.iconTouch(this, R.drawable.ic_open, 40, Ui.SUB);
            r.addView(open);
        }

        r.setOnClickListener(v -> {
            if (w.url.length() > 0) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(w.url))); }
                catch (Exception e) { toast("Не удалось открыть ссылку"); }
            }
        });
        r.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(w.title)
                .setItems(new String[]{"Открыть ссылку", "Удалить"}, (d, ww) -> {
                    if (ww == 0 && w.url.length() > 0) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(w.url))); } catch (Exception e) { } }
                    else if (ww == 1) { store.deleteWish(w.id); reload(); rebuildTasks(); }
                }).show();
            return true;
        });
        return r;
    }

    void addWishDialog() {
        final EditText input = Ui.et(this, "Название или ссылка на товар", 16);
        final EditText priceIn = Ui.et(this, "Цена (необязательно)", 15);
        LinearLayout box = Ui.col(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        box.addView(priceIn);
        new AlertDialog.Builder(this).setTitle("Новая вещь в «Желания»").setView(box)
            .setPositiveButton("Добавить", (d, w) -> {
                String raw = input.getText().toString().trim();
                if (raw.length() == 0) return;
                Store.Wish wish = parseWish(raw);
                String p = priceIn.getText().toString().trim();
                if (p.length() > 0) wish.price = p;
                store.saveWish(wish);
                reload();
                rebuildTasks();
            })
            .setNegativeButton("Отмена", null).show();
        input.requestFocus();
    }

    Store.Wish parseWish(String raw) {
        Store.Wish w = new Store.Wish();
        // price detection from text: "1 999 ₽", "999 руб", "12.50$"
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("(\\d[\\d\\s]*(?:\\.\\d+)?)\\s*(₽|руб|р\\.|рублей|RUB|рублей|\\$|usd)?").matcher(raw);
        String priceFound = "";
        if (pm.find()) {
            String num = pm.group(1).trim();
            String cur = pm.group(2) != null ? pm.group(2) : "";
            String sym = "₽";
            if (cur != null && (cur.equals("$") || cur.equalsIgnoreCase("usd"))) sym = "$";
            priceFound = num + " " + sym;
        }

        String lower = raw.toLowerCase(Locale.ROOT);
        if (raw.contains("://") || raw.startsWith("www.") || raw.contains("ozon.ru") || raw.contains("wildberries.ru") || raw.contains("wb.ru") || raw.contains("market.yandex")) {
            String url = raw;
            if (!url.startsWith("http")) url = "https://" + url;
            w.url = url;
            w.title = titleFromUrl(url);
        } else {
            w.title = raw;
        }
        if (priceFound.length() > 0 && w.price.length() == 0) w.price = priceFound;
        if (w.title.length() == 0) w.title = raw;
        return w;
    }

    String titleFromUrl(String url) {
        try {
            String path = url;
            int q = path.indexOf('?');
            if (q > 0) path = path.substring(0, q);
            String[] seg = path.split("/");
            // ozon: /product/{slug}-{id}
            for (int i = 0; i < seg.length; i++) {
                if ("product".equals(seg[i]) && i + 1 < seg.length) {
                    String slug = seg[i + 1];
                    slug = slug.replaceAll("-\\d+$", "");
                    return humanize(slug);
                }
            }
            // wildberries / others: last meaningful segment with letters
            for (int i = seg.length - 1; i >= 0; i--) {
                String s = seg[i];
                if (s.length() > 3 && s.matches(".*[а-яА-Яa-zA-Z].*") && !s.contains(".")) {
                    String slug = s.replaceAll("-\\d+$", "");
                    if (slug.replaceAll("[^а-яА-Яa-zA-Z]", "").length() >= 3) return humanize(slug);
                }
            }
        } catch (Exception ignored) { }
        return "Товар";
    }

    String humanize(String slug) {
        if (slug == null || slug.length() == 0) return "Товар";
        String s = slug.replace('-', ' ').replace('_', ' ').trim();
        if (s.length() > 48) s = s.substring(0, 48).trim() + "…";
        if (s.length() == 0) return "Товар";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    void taskMenu(final Store.Task t) {
        java.util.List<Action> acts = new ArrayList<>();
        acts.add(new Action(R.drawable.ic_edit, "Редактировать", () -> openEditor(t)));
        acts.add(new Action(R.drawable.ic_pin, t.pinned == 1 ? "Открепить" : "Закрепить", () -> { t.pinned = t.pinned == 1 ? 0 : 1; store.saveTask(t); reload(); rebuildTasks(); }));
        acts.add(new Action(R.drawable.ic_block, t.dismissed == 1 ? "Вернуть в список" : "Пропустить (Won't Do)", () -> { t.dismissed = t.dismissed == 1 ? 0 : 1; store.saveTask(t); reload(); rebuildTasks(); }));
        acts.add(new Action(R.drawable.ic_today, "Перенести на завтра", () -> { t.due = Store.todayStart() + 86400000L; if (t.hasTime == 1) t.due += timeMillis(t.time); store.saveTask(t); reload(); rebuildTasks(); }));
        acts.add(new Action(R.drawable.ic_date_range, "Перенести на неделю", () -> { t.due = Store.todayStart() + 7L * 86400000L; if (t.hasTime == 1) t.due += timeMillis(t.time); store.saveTask(t); reload(); rebuildTasks(); }));
        if (t.repeat.length() > 0) acts.add(new Action(R.drawable.ic_repeat, "Пропустить повторение", () -> { t.repeat = ""; t.done = 1; t.doneAt = System.currentTimeMillis(); store.saveTask(t); reload(); rebuildTasks(); }));
        acts.add(new Action(R.drawable.ic_info, "Подробнее", () -> pushDetail(t)));
        acts.add(new Action(R.drawable.ic_template, "Сохранить как шаблон", () -> saveAsTemplate(t)));
        acts.add(new Action(R.drawable.ic_delete, "Удалить", () -> confirmDelete(t)));
        actionSheet(t.title, acts);
    }

    static class Action {
        int icon;
        String label;
        Runnable run;
        Action(int i, String l, Runnable r) { icon = i; label = l; run = r; }
    }

    void actionSheet(String title, java.util.List<Action> acts) {
        LinearLayout list = Ui.col(this);
        list.setPadding(0, dp(8), 0, dp(8));
        final AlertDialog[] holder = new AlertDialog[1];
        for (final Action a : acts) {
            LinearLayout r = Ui.row(this);
            r.setPadding(dp(20), dp(13), dp(20), dp(13));
            ImageView ic = Ui.icon(this, a.icon, 22, Ui.ACCENT);
            ic.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
            r.addView(ic);
            TextView l = Ui.tv(this, a.label, 16, Ui.TEXT);
            l.setPadding(dp(16), 0, 0, 0);
            r.addView(l, Ui.weight(1));
            r.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); a.run.run(); });
            list.addView(r);
        }
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(title).setView(list).create();
        holder[0] = dlg;
        dlg.show();
    }

    void confirmDelete(final Store.Task t) {
        new AlertDialog.Builder(this).setMessage("Удалить задачу «" + t.title + "»?")
            .setPositiveButton("Удалить", (x, y) -> {
                store.deleteTask(t);
                reload();
                rebuildTasks();
                showUndoBar(t);
            })
            .setNegativeButton("Отмена", null).show();
    }

    void showUndoBar(final Store.Task t) {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(dp(20), dp(12), dp(20), dp(12));
        bar.setBackground(Ui.bg(this, Ui.CARD, 18));
        bar.addView(Ui.tv(this, "Задача удалена", 14, Ui.TEXT), Ui.weight(1));
        TextView undo = Ui.tv(this, "ОТМЕНА", 14, Ui.ACCENT, true);
        undo.setPadding(dp(12), dp(6), dp(2), dp(6));
        undo.setOnClickListener(v -> { restoreTask(t); });
        bar.addView(undo);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(16), 0, dp(16), dp(74));
        fabLayer.addView(bar, lp);
        h.postDelayed(() -> { fabLayer.removeView(bar); }, 5000);
    }

    void restoreTask(Store.Task t) {
        t.id = 0;
        t.done = 0;
        t.doneAt = 0;
        for (Store.Task s : t.subs) s.id = 0;
        storeSaveWithSubs(t);
        reload();
        rebuildTasks();
        toast("Восстановлено");
    }

    void pushDetail(Store.Task t) {
        LinearLayout col = Ui.col(this);
        col.addView(pushHeader("Задача", this::pop));
        ScrollView sv = Ui.scroll(this);
        LinearLayout body = Ui.col(this);
        body.setPadding(dp(20), dp(16), dp(20), dp(20));
        body.addView(Ui.tv(this, t.title, 22, Ui.TEXT, true));
        if (t.notes.length() > 0) { body.addView(Ui.spacer(this, dp(8))); body.addView(Ui.tv(this, t.notes, 15, Ui.SUB)); }
        body.addView(Ui.spacer(this, dp(12)));
        body.addView(detailRow("Список", t.listId == 0 ? "Входящие" : listName(t.listId)));
        if (t.due > 0) body.addView(detailRow("Дата", Store.fullDate(t.due) + (t.hasTime == 1 ? " " + t.time : "")));
        if (t.repeat.length() > 0) body.addView(detailRow("Повтор", repeatLabel(t.repeat)));
        if (t.priority > 0) body.addView(detailRow("Приоритет", t.priority == 3 ? "Высокий" : (t.priority == 2 ? "Средний" : "Низкий")));
        if (t.subs.size() > 0) {
            body.addView(Ui.spacer(this, dp(8)));
            body.addView(Ui.tv(this, "Подзадачи", 14, Ui.TEXT, true));
            for (Store.Task s : t.subs) {
                LinearLayout sr = Ui.row(this);
                sr.setPadding(0, dp(6), 0, dp(6));
                TextView ch = Ui.tv(this, s.done == 1 ? "✓" : "○", 18, s.done == 1 ? Ui.ACCENT : Ui.SUB);
                sr.addView(ch);
                TextView sn = Ui.tv(this, s.title, 15, s.done == 1 ? Ui.SUB : Ui.TEXT);
                sn.setPadding(dp(10), 0, 0, 0);
                sr.addView(sn, Ui.weight(1));
                body.addView(sr);
            }
        }
        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        push(col);
    }

    String listName(long id) { for (Store.TList l : lists) if (l.id == id) return l.name; return "Входящие"; }

    View detailRow(String k, String v) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, dp(6), 0, dp(6));
        r.addView(Ui.tv(this, k + ":", 14, Ui.SUB));
        TextView vt = Ui.tv(this, v, 14, Ui.TEXT);
        vt.setPadding(dp(10), 0, 0, 0);
        r.addView(vt, Ui.weight(1));
        return r;
    }

    // ================= FAB / QUICK ADD =================
    void addFab() {
        ImageView fab = Ui.icon(this, R.drawable.ic_add, 28, Color.WHITE);
        fab.setBackground(Ui.oval(Ui.ACCENT));
        fab.setScaleType(ImageView.ScaleType.CENTER);
        fab.setOnClickListener(v -> quickAdd());
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.RIGHT | Gravity.BOTTOM);
        fp.setMargins(0, 0, dp(18), dp(74));
        fabLayer.addView(fab, fp);
    }

    void quickAdd() {
        final EditText input = Ui.et(this, "Например: позвонить маме завтра в 18:00", 16);
        LinearLayout box = Ui.col(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        new AlertDialog.Builder(this).setTitle("Новая задача").setView(box)
            .setPositiveButton("Добавить", (d, w) -> {
                String s = input.getText().toString().trim();
                if (s.length() == 0) return;
                Store.Task t = parseQuick(s);
                store.saveTask(t);
                reload();
                rebuildTasks();
                if (t.reminder > 0) Reminders.schedule(this, t);
            })
            .setNegativeButton("Отмена", null).show();
        input.requestFocus();
    }

    Store.Task parseQuick(String raw) {
        Store.Task t = new Store.Task();
        String low = raw.toLowerCase(Locale.ROOT);
        long today = Store.todayStart();
        String title = raw;
        // time
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(raw);
        if (m.find()) {
            t.hasTime = 1;
            t.time = String.format(Locale.ROOT, "%02d:%02d", Integer.parseInt(m.group(1)) % 24, Integer.parseInt(m.group(2)));
            title = raw.replace(m.group(), "").trim();
        }
        // "через N дней/недель"
        long due = 0;
        java.util.regex.Matcher mn = java.util.regex.Pattern.compile("через\\s+(\\d+)\\s*(дн|недел|час|минут)").matcher(low);
        if (mn.find()) {
            int n = Integer.parseInt(mn.group(1));
            String unit = mn.group(2);
            if (unit.startsWith("дн")) due = Store.addDays(today, n);
            else if (unit.startsWith("недел")) due = Store.addDays(today, n * 7);
            else if (unit.startsWith("час")) { due = today; t.hasTime = 1; t.time = laterTime(n * 60); }
            else if (unit.startsWith("минут")) { due = today; t.hasTime = 1; t.time = laterTime(n); }
            title = title.replaceAll("(?i)через\\s+\\d+\\s*\\w+", "").trim();
        }
        // weekdays
        if (due == 0) {
            String[] days = {"воскресенье","понедельник","вторник","среда","четверг","пятница","суббота"};
            for (int k = 0; k < 7; k++) {
                if (low.contains(days[k])) {
                    Calendar c = Calendar.getInstance();
                    int target = k + 1;
                    int diff = (target - c.get(Calendar.DAY_OF_WEEK) + 7) % 7;
                    if (diff == 0) diff = 7;
                    due = Store.addDays(today, diff);
                    title = title.replaceAll("(?i)" + days[k], "").trim();
                    break;
                }
            }
        }
        if (due == 0) {
            if (low.contains("послезавтра")) { due = Store.addDays(today, 2); }
            else if (low.contains("завтра")) { due = Store.addDays(today, 1); }
            else if (low.contains("сегодня")) { due = today; }
            else if ("today".equals(view)) { due = today; }
            else if ("tomorrow".equals(view)) { due = Store.addDays(today, 1); }
        }
        if (view.startsWith("list:")) t.listId = Long.parseLong(view.substring(5));
        t.due = due;
        if (due > 0) {
            title = title.replaceAll("(?i)(завтра|сегодня|послезавтра)", "").trim();
        }
        t.title = title.length() == 0 ? raw.trim() : title;
        if (t.title.length() == 0) t.title = "Задача";
        if (due > 0 && t.hasTime == 1 && t.time != null) {
            t.due = due + timeMillis(t.time);
        }
        return t;
    }

    String laterTime(int plusMinutes) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MINUTE, plusMinutes);
        return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    long timeMillis(String hhmm) {
        String[] p = hhmm.split(":");
        return (Long.parseLong(p[0]) * 60L + Long.parseLong(p[1])) * 60000L;
    }

    // ================= CALENDAR =================
    Calendar calMonth = Calendar.getInstance();
    Calendar calSel = Calendar.getInstance();
    int calMode = 0; // 0 month, 1 week, 2 day
    {
        calMonth.set(Calendar.DAY_OF_MONTH, 1);
    }

    void calendarScreen() {
        content.removeAllViews();
        fabLayer.removeAllViews();
        LinearLayout col = Ui.col(this);

        LinearLayout tb = Ui.row(this);
        tb.setBackgroundColor(Ui.CARD);
        tb.setPadding(dp(8), dp(10), dp(14), dp(10));
        TextView prev = Ui.tv(this, "‹", 30, Ui.ACCENT);
        prev.setPadding(dp(12), 0, dp(4), 0);
        prev.setOnClickListener(v -> { shiftCal(-1); calendarScreen(); });
        tb.addView(prev);
        TextView titleTv = Ui.tv(this, calTitle(), 20, Ui.TEXT, true);
        titleTv.setGravity(Gravity.CENTER);
        tb.addView(titleTv, Ui.weight(1));
        TextView next = Ui.tv(this, "›", 30, Ui.ACCENT);
        next.setPadding(dp(4), 0, dp(12), 0);
        next.setOnClickListener(v -> { shiftCal(1); calendarScreen(); });
        tb.addView(next);
        TextView add = Ui.tv(this, "＋", 22, Ui.ACCENT);
        add.setPadding(dp(10), dp(4), dp(2), dp(4));
        add.setOnClickListener(v -> { openEditorNew(); });
        tb.addView(add);
        col.addView(tb);

        // mode switch
        LinearLayout modes = Ui.row(this);
        modes.setPadding(dp(16), dp(10), dp(16), dp(6));
        modes.addView(calModePill("Месяц", 0), Ui.weight(1));
        modes.addView(calModePill("Неделя", 1), Ui.weight(1));
        modes.addView(calModePill("День", 2), Ui.weight(1));
        col.addView(modes);

        ScrollView sv = Ui.scroll(this);
        LinearLayout body = Ui.col(this);
        if (calMode == 0) { body.addView(buildMonthGrid()); body.addView(buildDayAgenda()); }
        else if (calMode == 1) body.addView(buildWeekView());
        else body.addView(buildDayView());
        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
        addFab();
    }

    void shiftCal(int dir) {
        if (calMode == 0) calMonth.add(Calendar.MONTH, dir);
        else if (calMode == 1) calSel.add(Calendar.DAY_OF_MONTH, dir * 7);
        else calSel.add(Calendar.DAY_OF_MONTH, dir);
    }

    String calTitle() {
        String[] mo = {"Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
        if (calMode == 0) return mo[calMonth.get(Calendar.MONTH)] + " " + calMonth.get(Calendar.YEAR);
        if (calMode == 2) {
            String[] wd = {"Воскресенье","Понедельник","Вторник","Среда","Четверг","Пятница","Суббота"};
            return wd[calSel.get(Calendar.DAY_OF_WEEK)-1] + ", " + calSel.get(Calendar.DAY_OF_MONTH) + " " + mo[calSel.get(Calendar.MONTH)];
        }
        // week
        Calendar c = weekStart();
        Calendar e = (Calendar) c.clone();
        e.add(Calendar.DAY_OF_MONTH, 6);
        if (c.get(Calendar.MONTH) == e.get(Calendar.MONTH)) return c.get(Calendar.DAY_OF_MONTH) + " – " + e.get(Calendar.DAY_OF_MONTH) + " " + mo[c.get(Calendar.MONTH)];
        return c.get(Calendar.DAY_OF_MONTH) + " " + mo[c.get(Calendar.MONTH)] + " – " + e.get(Calendar.DAY_OF_MONTH) + " " + mo[e.get(Calendar.MONTH)];
    }

    Calendar weekStart() {
        Calendar c = (Calendar) calSel.clone();
        int offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        c.add(Calendar.DAY_OF_MONTH, -offset);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    TextView calModePill(String label, int id) {
        TextView t = Ui.tv(this, label, 13, calMode == id ? Color.WHITE : Ui.SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(9), dp(8), dp(9));
        t.setBackground(Ui.bg(this, calMode == id ? Ui.ACCENT : Ui.CARD2, 10));
        t.setOnClickListener(v -> { calMode = id; calendarScreen(); });
        return t;
    }

    View buildWeekView() {
        LinearLayout col = Ui.col(this);
        col.setPadding(dp(16), dp(4), dp(16), dp(20));
        Calendar start = weekStart();
        for (int i = 0; i < 7; i++) {
            long day = Store.addDays(start.getTimeInMillis(), i);
            col.addView(daySection(day, i == 0));
        }
        return col;
    }

    View buildDayView() {
        LinearLayout col = Ui.col(this);
        col.setPadding(dp(16), dp(4), dp(16), dp(20));
        col.addView(daySection(calSel.getTimeInMillis(), false));
        return col;
    }

    View daySection(long day, boolean first) {
        LinearLayout card = Ui.col(this);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(Ui.bg(this, Ui.CARD, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, first ? 0 : dp(10), 0, 0);
        card.setLayoutParams(lp);

        boolean isToday = Store.sameDay(day, Store.todayStart());
        boolean isSel = Store.sameDay(day, calSel.getTimeInMillis());
        LinearLayout head = Ui.row(this);
        TextView date = Ui.tv(this, Store.weekdayShort(day), 15, isSel ? Ui.ACCENT : (isToday ? Ui.ACCENT : Ui.TEXT), true);
        head.addView(date);
        TextView num = Ui.tv(this, Store.shortDate(day), 13, Ui.SUB);
        num.setPadding(dp(8), 0, 0, 0);
        head.addView(num, Ui.weight(1));
        card.addView(head);

        boolean any = false;
        for (Store.Task t : tasks) {
            if (t.parent != null || t.deleted == 1) continue;
            if (t.due > 0 && Store.sameDay(t.due, day)) { card.addView(taskRow(t)); any = true; }
        }
        if (!any) card.addView(Ui.tv(this, "Задач нет", 13, Ui.FAINT));
        return card;
    }

    String monthTitle() {
        String[] mo = {"Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
        return mo[calMonth.get(Calendar.MONTH)] + " " + calMonth.get(Calendar.YEAR);
    }

    View buildMonthGrid() {
        LinearLayout grid = Ui.col(this);
        grid.setPadding(dp(12), dp(8), dp(12), 0);

        SharedPreferences p = getSharedPreferences("planner", 0);
        boolean mondayFirst = p.getBoolean("monday_first", true);
        String[] wd = mondayFirst ? new String[]{"ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС"} : new String[]{"ВС","ПН","ВТ","СР","ЧТ","ПТ","СБ"};
        LinearLayout heads = Ui.row(this);
        for (String w : wd) {
            TextView x = Ui.tv(this, w, 11, Ui.SUB, true);
            x.setGravity(Gravity.CENTER);
            heads.addView(x, Ui.weight(1));
        }
        grid.addView(heads);
        grid.addView(Ui.spacer(this, dp(6)));

        Calendar c = (Calendar) calMonth.clone();
        int offset = mondayFirst ? (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 : c.get(Calendar.DAY_OF_WEEK) - 1;
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        int day = 1;
        long today = Store.todayStart();

        for (int week = 0; week < 6 && day <= max; week++) {
            LinearLayout line = Ui.row(this);
            for (int j = 0; j < 7; j++) {
                boolean inRange = !(week == 0 && j < offset) && day <= max;
                final int d = inRange ? day++ : 0;
                if (inRange) {
                    Calendar cell = (Calendar) calMonth.clone();
                    cell.set(Calendar.DAY_OF_MONTH, d);
                    line.addView(dayCell(d, cell.getTimeInMillis(), Store.sameDay(cell.getTimeInMillis(), today)), Ui.weight(1));
                } else {
                    TextView blank = Ui.tv(this, "", 16, Ui.TEXT);
                    line.addView(blank, Ui.weight(1));
                }
            }
            grid.addView(line);
        }
        return grid;
    }

    View dayCell(final int d, final long millis, boolean isToday) {
        LinearLayout cell = Ui.col(this);
        cell.setGravity(Gravity.CENTER);
        boolean sel = Store.sameDay(millis, calSel.getTimeInMillis());
        TextView num = Ui.tv(this, String.valueOf(d), 15, sel ? Color.WHITE : (isToday ? Ui.ACCENT : Ui.TEXT), sel || isToday);
        num.setGravity(Gravity.CENTER);
        int ns = dp(34);
        num.setLayoutParams(new LinearLayout.LayoutParams(ns, ns));
        if (sel) num.setBackground(Ui.oval(Ui.ACCENT));
        else if (isToday) num.setBackground(Ui.oval(Ui.ACCENT_SOFT));
        cell.addView(num);
        // dot indicator
        boolean hasTask = false, allDone = true;
        for (Store.Task t : tasks) if (t.due > 0 && Store.sameDay(t.due, millis)) { hasTask = true; if (t.done == 0) allDone = false; }
        View dot = new View(this);
        int ds = dp(4);
        dot.setLayoutParams(new LinearLayout.LayoutParams(ds, ds));
        dot.setBackground(Ui.oval(hasTask ? (allDone ? Ui.FAINT : Ui.ACCENT) : Color.TRANSPARENT));
        cell.addView(dot);
        cell.setOnClickListener(v -> { calSel = Calendar.getInstance(); calSel.setTimeInMillis(millis); calendarScreen(); });
        return cell;
    }

    View buildDayAgenda() {
        LinearLayout col = Ui.col(this);
        col.setPadding(dp(20), dp(8), dp(20), dp(20));
        TextView hd = Ui.tv(this, Store.weekdayShort(calSel.getTimeInMillis()) + ", " + Store.fullDate(calSel.getTimeInMillis()), 16, Ui.TEXT, true);
        hd.setPadding(0, dp(10), 0, dp(6));
        col.addView(hd);
        boolean any = false;
        for (Store.Task t : tasks) {
            if (t.parent != null) continue;
            if (t.due > 0 && Store.sameDay(t.due, calSel.getTimeInMillis())) {
                col.addView(taskRow(t));
                any = true;
            }
        }
        if (!any) col.addView(Ui.tv(this, "Нет задач на этот день", 15, Ui.SUB));
        return col;
    }

    // ================= FOCUS =================
    void focusScreen() {
        content.removeAllViews();
        fabLayer.removeAllViews();
        LinearLayout col = Ui.col(this);
        col.addView(topBar("Фокус", false, 0, null, null));
        ScrollView sv = Ui.scroll(this);
        LinearLayout body = Ui.col(this);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(20), dp(40), dp(20), dp(20));

        focusHintTv = Ui.tv(this, "Помодоро", 14, Ui.SUB);
        focusHintTv.setGravity(Gravity.CENTER);
        body.addView(focusHintTv);

        focusTimeTv = Ui.tv(this, fmt(focusRemain), 54, Ui.TEXT, true);
        focusTimeTv.setGravity(Gravity.CENTER);
        body.addView(Ui.spacer(this, dp(10)));
        body.addView(focusTimeTv);

        // mode pills
        LinearLayout modes = Ui.row(this);
        modes.setPadding(0, dp(24), 0, dp(8));
        modes.addView(modePill("Фокус", 25), Ui.weight(1));
        modes.addView(modePill("Перерыв", 5), Ui.weight(1));
        modes.addView(modePill("Длинный", 15), Ui.weight(1));
        body.addView(modes);

        LinearLayout btns = Ui.row(this);
        TextView start = pillBtn("Старт", Ui.ACCENT);
        start.setOnClickListener(v -> startFocus());
        btns.addView(start, Ui.weight(1));
        TextView pause = pillBtn("Пауза", Ui.CARD2);
        pause.setOnClickListener(v -> pauseFocus());
        btns.addView(pause, Ui.weight(1));
        TextView reset = pillBtn("Сброс", Ui.CARD2);
        reset.setOnClickListener(v -> resetFocus());
        btns.addView(reset, Ui.weight(1));
        body.addView(btns);

        body.addView(Ui.spacer(this, dp(30)));
        body.addView(Ui.tv(this, "Таймер Помодоро помогает сосредоточиться\nи не отвлекаться на посторонние дела.", 13, Ui.SUB));

        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (focusState == 1) h.removeCallbacks(focusTicker); h.post(focusTicker);
    }

    TextView modePill(String label, int minutes) {
        TextView t = Ui.tv(this, label, 13, focusMode == minutes ? Color.WHITE : Ui.SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(10), dp(8), dp(10));
        t.setBackground(Ui.bg(this, focusMode == minutes ? Ui.ACCENT : Ui.CARD2, 10));
        t.setOnClickListener(v -> { focusMode = minutes; focusState = 0; focusRemain = minutes * 60 * 1000L; h.removeCallbacks(focusTicker); focusScreen(); });
        return t;
    }

    TextView pillBtn(String label, int color) {
        TextView t = Ui.tv(this, label, 14, color == Ui.ACCENT ? Color.WHITE : Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(12), dp(8), dp(12));
        t.setBackground(Ui.bg(this, color, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        return t;
    }

    void startFocus() {
        if (focusState == 1) return;
        focusEnd = System.currentTimeMillis() + focusRemain;
        focusState = 1;
        focusHintTv.setText("Сосредоточьтесь…");
        h.removeCallbacks(focusTicker);
        h.post(focusTicker);
    }
    void pauseFocus() {
        if (focusState != 1) return;
        focusRemain = focusEnd - System.currentTimeMillis();
        focusState = 2;
        focusHintTv.setText("Пауза");
        h.removeCallbacks(focusTicker);
        if (focusTimeTv != null) focusTimeTv.setText(fmt(focusRemain));
    }
    void resetFocus() {
        focusState = 0;
        focusRemain = focusMode * 60 * 1000L;
        h.removeCallbacks(focusTicker);
        if (focusTimeTv != null) focusTimeTv.setText(fmt(focusRemain));
        if (focusHintTv != null) focusHintTv.setText("Помодоро");
    }
    void updateFocus() {
        if (focusState != 1) return;
        long left = focusEnd - System.currentTimeMillis();
        if (left <= 0) {
            left = 0; focusState = 0;
            h.removeCallbacks(focusTicker);
            focusHintTv.setText("Готово! Время вышло");
            store.addFocusLog(focusMode);
            toast("Фокус завершён");
            vibrate();
        }
        focusRemain = left;
        if (focusTimeTv != null) focusTimeTv.setText(fmt(left));
    }
    void vibrate() {
        try { ((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(800); } catch (Exception ignored) { }
    }
    String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    // ================= HABITS =================
    void habitsScreen() {
        content.removeAllViews();
        fabLayer.removeAllViews();
        LinearLayout col = Ui.col(this);
        col.addView(topBar("Привычки", false, R.drawable.ic_add, this::addHabit, null));
        ScrollView sv = Ui.scroll(this);
        LinearLayout list = Ui.col(this);
        list.setPadding(dp(16), dp(12), dp(16), dp(20));
        if (habits.isEmpty()) {
            TextView e = Ui.tv(this, "Создайте привычку\nнапример, читать или делать зарядку", 16, Ui.SUB);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(30), dp(80), dp(30), dp(40));
            list.addView(e);
        }
        for (Store.Habit h : habits) list.addView(habitRow(h));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    View habitRow(final Store.Habit h) {
        LinearLayout card = Ui.col(this);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(Ui.bg(this, Ui.CARD, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);

        LinearLayout top = Ui.row(this);
        TextView dot = Ui.tv(this, "●", 14, h.color);
        top.addView(dot);
        TextView name = Ui.tv(this, h.name, 16, Ui.TEXT, true);
        name.setPadding(dp(8), 0, 0, 0);
        top.addView(name, Ui.weight(1));
        int streak = habitStreak(h.id);
        if (streak > 0) {
            TextView st = Ui.tv(this, "🔥 " + streak, 13, Ui.ORANGE, true);
            st.setPadding(dp(8), 0, dp(4), 0);
            top.addView(st);
        }
        ImageView del = Ui.iconTouch(this, R.drawable.ic_delete, 36, Ui.FAINT);
        del.setOnClickListener(v -> {
            new AlertDialog.Builder(this).setMessage("Удалить привычку «" + h.name + "»?")
                .setPositiveButton("Удалить", (x, y) -> { store.deleteHabit(h.id); reload(); habitsScreen(); })
                .setNegativeButton("Отмена", null).show();
        });
        top.addView(del);
        card.addView(top);

        // last 7 days
        LinearLayout week = Ui.row(this);
        week.setPadding(0, dp(12), 0, 0);
        Calendar c = Calendar.getInstance();
        long today = Store.todayStart();
        for (int i = 6; i >= 0; i--) {
            final long day = Store.addDays(today, -i);
            final String ds = Store.dateStr(day);
            boolean checked = store.habitChecked(h.id, ds);
            LinearLayout cell = Ui.col(this);
            cell.setGravity(Gravity.CENTER);
            TextView cir = Ui.tv(this, checked ? "✓" : "", 11, Color.WHITE, true);
            cir.setGravity(Gravity.CENTER);
            int cs = dp(26);
            cir.setLayoutParams(new LinearLayout.LayoutParams(cs, cs));
            cir.setBackground(checked ? Ui.oval(h.color) : Ui.stroke(this, Ui.BORDER, 1, 20));
            cir.setOnClickListener(v -> {
                boolean nv = !store.habitChecked(h.id, ds);
                store.setHabitChecked(h.id, ds, nv);
                habitsScreen();
            });
            cell.addView(cir);
            TextView wd = Ui.tv(this, Store.weekdayShort(day), 9, Ui.SUB);
            wd.setGravity(Gravity.CENTER);
            cell.addView(wd);
            week.addView(cell, Ui.weight(1));
        }
        card.addView(week);
        return card;
    }

    int habitStreak(long habitId) {
        int streak = 0;
        long d = Store.todayStart();
        for (int i = 0; i < 3650; i++) {
            if (store.habitChecked(habitId, Store.dateStr(Store.addDays(d, -i)))) streak++;
            else break;
        }
        return streak;
    }

    LinearLayout colorPicker(final int[] chosen) {
        final LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(16), 0, 0);
        renderColorPicker(row, chosen);
        return row;
    }

    void renderColorPicker(final LinearLayout row, final int[] chosen) {
        row.removeAllViews();
        for (final int c : LIST_COLORS) {
            FrameLayout wrap = new FrameLayout(this);
            int outer = dp(46);
            wrap.setLayoutParams(new LinearLayout.LayoutParams(outer, outer));
            if (c == chosen[0]) {
                TextView ring = Ui.tv(this, "", 0, 0);
                ring.setBackground(Ui.ring(this, c, 3));
                wrap.addView(ring, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
            }
            TextView fill = Ui.tv(this, "", 0, 0);
            fill.setBackground(Ui.oval(c));
            wrap.addView(fill, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
            wrap.setOnClickListener(v -> { chosen[0] = c; renderColorPicker(row, chosen); });
            row.addView(wrap);
        }
    }

    void addHabit() {
        final EditText input = Ui.et(this, "Название привычки", 16);
        LinearLayout box = Ui.col(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        final int[] chosen = {Ui.ACCENT};
        box.addView(colorPicker(chosen));
        new AlertDialog.Builder(this).setTitle("Новая привычка").setView(box)
            .setPositiveButton("Добавить", (d, w) -> {
                String s = input.getText().toString().trim();
                if (s.length() == 0) return;
                Store.Habit hb = new Store.Habit();
                hb.name = s; hb.color = chosen[0];
                store.saveHabit(hb);
                reload();
                habitsScreen();
            })
            .setNegativeButton("Отмена", null).show();
    }

    // ================= MORE =================
    void moreScreen() {
        LinearLayout col = Ui.col(this);
        col.addView(topBar("Ещё", false, 0, null, null));
        ScrollView sv = Ui.scroll(this);
        LinearLayout body = Ui.col(this);
        body.setPadding(dp(16), dp(16), dp(16), dp(20));

        // profile card
        LinearLayout prof = Ui.row(this);
        prof.setPadding(dp(20), dp(20), dp(20), dp(20));
        prof.setBackground(Ui.bg(this, Ui.CARD, 18));
        TextView av = Ui.tv(this, "Р", 26, Color.WHITE, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Ui.oval(Ui.ACCENT));
        int as = dp(56);
        av.setLayoutParams(new LinearLayout.LayoutParams(as, as));
        prof.addView(av);
        LinearLayout pn = Ui.col(this);
        pn.setPadding(dp(14), 0, 0, 0);
        pn.addView(Ui.tv(this, "РыбинскLAB", 18, Ui.TEXT, true));
        pn.addView(Ui.tv(this, "rybinsklab.ru", 13, Ui.ACCENT));
        pn.addView(Ui.tv(this, "Продуктивность и задачи", 12, Ui.SUB));
        prof.addView(pn, Ui.weight(1));
        body.addView(prof);

        // statistics
        body.addView(Ui.spacer(this, dp(16)));
        TextView sh = Ui.tv(this, "Статистика", 14, Ui.SUB, true);
        sh.setPadding(dp(8), 0, dp(8), dp(8));
        body.addView(sh);
        LinearLayout stats = Ui.row(this);
        stats.addView(statCard("Всего задач", countAll()), Ui.weight(1));
        stats.addView(statCard("Выполнено", countDone()), Ui.weight(1));
        stats.addView(statCard("Сегодня", countToday()), Ui.weight(1));
        body.addView(stats);

        // menu
        body.addView(Ui.spacer(this, dp(16)));
        body.addView(menuCard());

        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    int countAll() { int n = 0; for (Store.Task t : tasks) if (t.parent == null) n++; return n; }
    int countDone() { int n = 0; for (Store.Task t : tasks) if (t.parent == null && t.done == 1) n++; return n; }
    int countToday() {
        int n = 0; long today = Store.todayStart();
        for (Store.Task t : tasks) if (t.due > 0 && Store.sameDay(t.due, today)) n++;
        return n;
    }

    View statCard(String label, int value) {
        LinearLayout c = Ui.col(this);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(8), dp(16), dp(8), dp(16));
        c.setBackground(Ui.bg(this, Ui.CARD, 18));
        TextView vv = Ui.tv(this, String.valueOf(value), 26, Ui.ACCENT, true);
        vv.setGravity(Gravity.CENTER);
        c.addView(vv);
        TextView ll = Ui.tv(this, label, 11, Ui.SUB);
        ll.setGravity(Gravity.CENTER);
        c.addView(ll);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        c.setLayoutParams(lp);
        return c;
    }

    View menuCard() {
        LinearLayout box = Ui.col(this);
        box.setPadding(dp(6), dp(4), dp(6), dp(4));
        box.setBackground(Ui.bg(this, Ui.CARD, 18));
        box.addView(menuItem(R.drawable.ic_chart, "Подробная статистика", "stats"));
        box.addView(menuItem(R.drawable.ic_settings, "Настройки", "s"));
        box.addView(menuItem(R.drawable.ic_theme, Ui.dark ? "Светлая тема" : "Тёмная тема", "theme"));
        box.addView(menuItem(R.drawable.ic_info, "О приложении", "a"));
        box.addView(menuItem(R.drawable.ic_language, "Сайт разработчика", "site"));
        box.addView(menuItem(R.drawable.ic_star, "Оценить приложение", "rate"));
        return box;
    }

    View menuItem(int resId, String label, final String action) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(14), dp(14), dp(14), dp(14));
        ImageView ic = Ui.icon(this, resId, 22, Ui.ACCENT);
        r.addView(ic);
        r.addView(Ui.tv(this, label, 16, Ui.TEXT), Ui.weight(1));
        r.addView(Ui.tv(this, "›", 22, Ui.FAINT));
        r.setOnClickListener(v -> onMenu(action));
        return r;
    }

    void onMenu(String a) {
        if ("s".equals(a)) pushSettings();
        else if ("stats".equals(a)) pushStats();
        else if ("theme".equals(a)) {
            SharedPreferences p = getSharedPreferences("planner", 0);
            p.edit().putBoolean("dark", !Ui.dark).apply();
            Ui.init(this);
            getWindow().setStatusBarColor(Ui.BG);
            recreate();
        } else if ("a".equals(a)) pushAbout();
        else if ("site".equals(a)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://rybinsklab.ru"))); } catch (Exception ignored) { }
        } else if ("rate".equals(a)) {
            toast("Спасибо! Поставьте оценку в магазине");
        }
    }

    // ================= STATISTICS =================
    void pushStats() {
        LinearLayout col = Ui.col(this);
        col.addView(pushHeader("Статистика", this::pop));
        ScrollView sv = Ui.scroll(this);
        LinearLayout body = Ui.col(this);
        body.setPadding(dp(16), dp(16), dp(16), dp(20));

        // totals
        LinearLayout stats = Ui.row(this);
        stats.addView(statCard("Всего", countAll()), Ui.weight(1));
        stats.addView(statCard("Выполнено", countDone()), Ui.weight(1));
        stats.addView(statCard("Активно", countAll() - countDone()), Ui.weight(1));
        body.addView(stats);

        body.addView(Ui.spacer(this, dp(20)));
        LinearLayout chartCard = Ui.col(this);
        chartCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        chartCard.setBackground(Ui.bg(this, Ui.CARD, 18));
        chartCard.addView(Ui.tv(this, "Выполнено за последние 7 дней", 14, Ui.TEXT, true));
        chartCard.addView(Ui.spacer(this, dp(14)));

        int[] counts = new int[7];
        int max = 1;
        long today = Store.todayStart();
        for (int i = 0; i < 7; i++) {
            final long day = Store.addDays(today, -i);
            for (Store.Task t : tasks) if (t.done == 1 && t.doneAt > 0 && Store.sameDay(t.doneAt, day)) counts[i]++;
            if (counts[i] > max) max = counts[i];
        }

        LinearLayout bars = Ui.row(this);
        for (int i = 6; i >= 0; i--) {
            final long day = Store.addDays(today, -i);
            LinearLayout cell = Ui.col(this);
            cell.setGravity(Gravity.CENTER);
            int hgt = max == 0 ? 0 : (int)(dp(100) * counts[i] / (float) max);
            FrameLayout barWrap = new FrameLayout(this);
            LinearLayout.LayoutParams bw = new LinearLayout.LayoutParams(dp(26), dp(100));
            barWrap.setLayoutParams(bw);
            View fill = new View(this);
            fill.setBackgroundColor(counts[i] == 0 ? Ui.ACCENT_SOFT : Ui.ACCENT);
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(26), hgt, Gravity.BOTTOM);
            fill.setBackground(Ui.bg(this, counts[i] == 0 ? Ui.ACCENT_SOFT : Ui.ACCENT, 6));
            barWrap.addView(fill, fp);
            cell.addView(barWrap);
            TextView num = Ui.tv(this, String.valueOf(counts[i]), 10, Ui.SUB);
            num.setGravity(Gravity.CENTER);
            cell.addView(num);
            TextView wd = Ui.tv(this, Store.weekdayShort(day).substring(0, 2), 10, Ui.SUB);
            wd.setGravity(Gravity.CENTER);
            cell.addView(wd);
            bars.addView(cell, Ui.weight(1));
        }
        chartCard.addView(bars);
        body.addView(chartCard);

        // habits done today
        body.addView(Ui.spacer(this, dp(16)));
        int habitsDone = 0;
        String todayStr = Store.dateStr(today);
        for (Store.Habit hb : habits) if (store.habitChecked(hb.id, todayStr)) habitsDone++;
        LinearLayout habitStat = Ui.row(this);
        habitStat.setPadding(dp(20), dp(16), dp(20), dp(16));
        habitStat.setBackground(Ui.bg(this, Ui.CARD, 18));
        habitStat.addView(Ui.tv(this, "Привычки сегодня", 15, Ui.TEXT), Ui.weight(1));
        habitStat.addView(Ui.tv(this, habitsDone + " из " + habits.size(), 15, Ui.ACCENT, true));
        body.addView(habitStat);

        // focus total
        body.addView(Ui.spacer(this, dp(16)));
        int focusMin = store.totalFocusMinutes();
        LinearLayout focusStat = Ui.row(this);
        focusStat.setPadding(dp(20), dp(16), dp(20), dp(16));
        focusStat.setBackground(Ui.bg(this, Ui.CARD, 18));
        focusStat.addView(Ui.tv(this, "Время в фокусе", 15, Ui.TEXT), Ui.weight(1));
        focusStat.addView(Ui.tv(this, fmt(focusMin * 60000L), 15, Ui.ACCENT, true));
        body.addView(focusStat);

        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        push(col);
    }

    // ================= SETTINGS =================
    void pushSettings() {
        LinearLayout col = Ui.col(this);
        col.addView(pushHeader("Настройки", this::pop));
        ScrollView sv = Ui.scroll(this);
        LinearLayout body = Ui.col(this);
        body.setPadding(dp(16), dp(16), dp(16), dp(20));

        final SharedPreferences p = getSharedPreferences("planner", 0);

        // theme switch
        body.addView(switchRow("Тёмная тема", Ui.dark, checked -> {
            p.edit().putBoolean("dark", checked).apply();
            Ui.init(this);
            getWindow().setStatusBarColor(Ui.BG);
            recreate();
        }));

        // first day of week
        final boolean mondayFirst = p.getBoolean("monday_first", true);
        LinearLayout dow = row("Первый день недели", mondayFirst ? "Понедельник" : "Воскресенье");
        dow.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Первый день недели")
            .setItems(new String[]{"Понедельник", "Воскресенье"}, (d, w) -> {
                p.edit().putBoolean("monday_first", w == 0).apply();
                pop(); pushSettings();
            }).show());
        body.addView(dow);

        // time format
        final boolean time12 = p.getBoolean("time_12h", false);
        LinearLayout tf = row("Формат времени", time12 ? "12 часов" : "24 часа");
        tf.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Формат времени")
            .setItems(new String[]{"24 часа", "12 часов"}, (d, w) -> {
                p.edit().putBoolean("time_12h", w == 1).apply();
                pop(); pushSettings();
            }).show());
        body.addView(tf);

        // default list
        final long defList = p.getLong("default_list", 0);
        LinearLayout dl = row("Список по умолчанию", listName(defList));
        dl.setOnClickListener(v -> {
            ArrayList<String> names = new ArrayList<>();
            final ArrayList<Long> ids = new ArrayList<>();
            names.add("Входящие"); ids.add(0L);
            for (Store.TList l : lists) { names.add(l.name); ids.add(l.id); }
            new AlertDialog.Builder(this).setTitle("Список по умолчанию")
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    p.edit().putLong("default_list", ids.get(w)).apply();
                    pop(); pushSettings();
                }).show();
        });
        body.addView(dl);

        body.addView(accentRow(p));

        body.addView(row("Уведомления", "Включены"));

        // daily review
        final boolean dailyOn = p.getBoolean("daily_review", false);
        body.addView(switchRow("Ежедневный обзор", dailyOn, checked -> {
            p.edit().putBoolean("daily_review", checked).apply();
            if (checked) Reminders.scheduleDailyReview(this, 8, 0);
            else Reminders.cancelDailyReview(this);
        }));

        // backup
        LinearLayout exp = row("Экспорт данных (JSON)", "");
        TextView exb = Ui.tv(this, "Экспорт", 14, Ui.ACCENT);
        exp.addView(exb);
        exp.setOnClickListener(v -> exportBackup());
        body.addView(exp);
        LinearLayout imp = row("Импорт данных (JSON)", "");
        TextView imb = Ui.tv(this, "Импорт", 14, Ui.ACCENT);
        imp.addView(imb);
        imp.setOnClickListener(v -> importBackup());
        body.addView(imp);

        LinearLayout clear = row("Очистить все данные", "");
        TextView cl = Ui.tv(this, "Очистить", 14, Ui.RED);
        clear.addView(cl);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("Удалить все задачи, списки и привычки?")
            .setPositiveButton("Удалить", (x, y) -> { store.db.delete("tasks", null, null); store.db.delete("lists", null, null); store.db.delete("habits", null, null); store.db.delete("habit_log", null, null); reload(); toast("Данные очищены"); pop(); })
            .setNegativeButton("Отмена", null).show());
        body.addView(clear);

        body.addView(row("Версия", "2.0"));
        body.addView(Ui.spacer(this, dp(20)));
        body.addView(Ui.tv(this, "© РыбинскLAB · rybinsklab.ru", 12, Ui.SUB));

        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        push(col);
    }

    View switchRow(String label, boolean value, final Callback callback) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(20), dp(6), dp(20), dp(6));
        r.setBackground(Ui.bg(this, Ui.CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        r.setLayoutParams(lp);
        r.addView(Ui.tv(this, label, 16, Ui.TEXT), Ui.weight(1));
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((b, c) -> callback.run(c));
        r.addView(sw);
        return r;
    }

    interface Callback { void run(boolean v); }

    View accentRow(final SharedPreferences p) {
        LinearLayout r = Ui.col(this);
        r.setPadding(dp(20), dp(14), dp(20), dp(14));
        r.setBackground(Ui.bg(this, Ui.CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        r.setLayoutParams(lp);
        r.addView(Ui.tv(this, "Цвет темы", 16, Ui.TEXT));
        r.addView(Ui.spacer(this, dp(10)));
        LinearLayout dots = Ui.row(this);
        final int[] ACCENTS = {0xFF4772FA, 0xFF7C4DFF, 0xFFE91E63, 0xFF43A047, 0xFFFF7043, 0xFF00ACC1, 0xFF8E24AA, 0xFF546E7A};
        for (final int c : ACCENTS) {
            FrameLayout wrap = new FrameLayout(this);
            int outer = dp(44);
            wrap.setLayoutParams(new LinearLayout.LayoutParams(outer, outer));
            if (c == Ui.ACCENT) {
                TextView ring = Ui.tv(this, "", 0, 0);
                ring.setBackground(Ui.ring(this, c, 3));
                wrap.addView(ring, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            }
            TextView fill = Ui.tv(this, "", 0, 0);
            fill.setBackground(Ui.oval(c));
            wrap.addView(fill, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            wrap.setOnClickListener(v -> {
                p.edit().putInt("accent", c).apply();
                Ui.init(this);
                getWindow().setStatusBarColor(Ui.BG);
                recreate();
            });
            dots.addView(wrap);
        }
        r.addView(dots);
        return r;
    }

    LinearLayout row(String label, String value) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(20), dp(16), dp(20), dp(16));
        r.setBackground(Ui.bg(this, Ui.CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        r.setLayoutParams(lp);
        r.addView(Ui.tv(this, label, 16, Ui.TEXT), Ui.weight(1));
        if (value.length() > 0) r.addView(Ui.tv(this, value, 14, Ui.SUB));
        return r;
    }

    // ================= ABOUT =================
    void pushAbout() {
        LinearLayout col = Ui.col(this);
        col.addView(pushHeader("О приложении", this::pop));
        ScrollView sv = Ui.scroll(this);
        LinearLayout body = Ui.col(this);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(30), dp(40), dp(30), dp(30));

        ImageView icon = Ui.icon(this, R.drawable.ic_tasks, 44, Color.WHITE);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setBackground(Ui.oval(Ui.ACCENT));
        int is = dp(88);
        icon.setLayoutParams(new LinearLayout.LayoutParams(is, is));
        body.addView(icon);

        body.addView(Ui.spacer(this, dp(16)));
        TextView nm = Ui.tv(this, "Планировщик", 24, Ui.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        body.addView(nm);
        body.addView(Ui.spacer(this, dp(4)));
        TextView ver = Ui.tv(this, "Версия 2.0", 13, Ui.SUB);
        ver.setGravity(Gravity.CENTER);
        body.addView(ver);

        body.addView(Ui.spacer(this, dp(24)));
        TextView desc = Ui.tv(this, "Бесплатный менеджер задач и календарь.\n\nЗадачи, подзадачи, напоминания, повторения,\nкалендарь, привычки и таймер Помодоро —\nбез рекламы и подписок.", 14, Ui.SUB);
        desc.setGravity(Gravity.CENTER);
        body.addView(desc);

        body.addView(Ui.spacer(this, dp(28)));
        TextView dev = Ui.tv(this, "Разработано", 12, Ui.SUB);
        dev.setGravity(Gravity.CENTER);
        body.addView(dev);
        body.addView(Ui.spacer(this, dp(2)));
        TextView lab = Ui.tv(this, "РыбинскLAB", 18, Ui.TEXT, true);
        lab.setGravity(Gravity.CENTER);
        body.addView(lab);
        TextView site = Ui.tv(this, "rybinsklab.ru", 14, Ui.ACCENT);
        site.setPadding(dp(8), dp(8), dp(8), dp(8));
        site.setGravity(Gravity.CENTER);
        site.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://rybinsklab.ru"))); } catch (Exception ignored) { } });
        body.addView(site);

        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        push(col);
    }

    // ================= EDITOR =================
    Store.Task ed;
    ArrayList<Integer> edRemOffsets = new ArrayList<>();
    EditText edTitleInput, edNotesInput;

    void openEditorNew() {
        ed = new Store.Task();
        edRemOffsets = new ArrayList<>();
        SharedPreferences p = getSharedPreferences("planner", 0);
        ed.listId = p.getLong("default_list", 0);
        if ("today".equals(view)) ed.due = Store.todayStart();
        else if ("tomorrow".equals(view)) ed.due = Store.addDays(Store.todayStart(), 1);
        if (view.startsWith("list:")) ed.listId = Long.parseLong(view.substring(5));
        buildEditor();
    }

    void openEditor(Store.Task t) {
        ed = t;
        edRemOffsets = new ArrayList<>(t.remOffsets);
        buildEditor();
    }

    void buildEditor() {
        LinearLayout col = Ui.col(this);
        LinearLayout hb = Ui.row(this);
        hb.setBackgroundColor(Ui.CARD);
        hb.setPadding(dp(8), dp(8), dp(8), dp(8));
        ImageView cancel = Ui.iconTouch(this, R.drawable.ic_close, 40, Ui.SUB);
        cancel.setOnClickListener(v -> pop());
        hb.addView(cancel);
        TextView save = Ui.tv(this, "Сохранить", 15, Color.WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(16), dp(10), dp(16), dp(10));
        save.setBackground(Ui.bg(this, Ui.ACCENT, 10));
        save.setOnClickListener(v -> saveEditor());
        hb.addView(save);
        col.addView(hb);

        ScrollView sv = Ui.scroll(this);
        LinearLayout body = Ui.col(this);
        body.setPadding(dp(20), dp(8), dp(20), dp(30));

        edTitleInput = Ui.et(this, "Название задачи", 20);
        edTitleInput.setText(ed.title);
        edTitleInput.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(edTitleInput);

        // date row
        body.addView(editorRow(R.drawable.ic_event, "Дата", ed.due > 0 ? dateLabel() : "Сегодня / Завтра / Выбрать", () -> pickDate()));
        if (ed.hasTime == 1) body.addView(editorRow(R.drawable.ic_schedule, "Время", ed.time, this::pickTime));
        body.addView(editorRow(R.drawable.ic_notifications, "Напоминание", remLabel(), this::pickReminder));
        body.addView(editorRow(R.drawable.ic_repeat, "Повтор", repeatLabel(ed.repeat), this::pickRepeat));
        body.addView(editorRow(R.drawable.ic_flag, "Приоритет", prioLabel(), this::pickPriority));
        body.addView(editorRow(R.drawable.ic_list, "Список", listName(ed.listId), this::pickList));
        body.addView(editorRow(R.drawable.ic_tag, "Теги", tagLabel(), this::pickTags));
        body.addView(editorRow(R.drawable.ic_template, "Шаблон", "Применить шаблон", this::pickTemplate));

        // notes
        body.addView(Ui.spacer(this, dp(10)));
        edNotesInput = Ui.et(this, "Заметки", 15);
        edNotesInput.setText(ed.notes);
        edNotesInput.setSingleLine(false);
        edNotesInput.setMinLines(2);
        body.addView(edNotesInput);

        // subtasks
        body.addView(Ui.spacer(this, dp(16)));
        body.addView(Ui.tv(this, "Подзадачи", 14, Ui.TEXT, true));
        for (final Store.Task s : ed.subs) {
            LinearLayout sr = Ui.row(this);
            TextView ch = Ui.tv(this, "○", 20, Ui.SUB);
            ch.setOnClickListener(v -> { s.done = s.done == 1 ? 0 : 1; buildEditor(); });
            sr.addView(ch);
            TextView sn = Ui.tv(this, s.title, 15, Ui.TEXT);
            sn.setPadding(dp(10), 0, 0, 0);
            sr.addView(sn, Ui.weight(1));
            ImageView rm = Ui.iconTouch(this, R.drawable.ic_close, 36, Ui.FAINT);
            rm.setOnClickListener(v -> { ed.subs.remove(s); buildEditor(); });
            sr.addView(rm);
            body.addView(sr);
        }
        TextView addSub = Ui.tv(this, "+  Добавить подзадачу", 14, Ui.ACCENT);
        addSub.setPadding(0, dp(6), 0, dp(6));
        addSub.setOnClickListener(v -> {
            final EditText inp = Ui.et(this, "Подзадача", 15);
            LinearLayout box = Ui.col(this);
            box.setPadding(dp(24), dp(8), dp(24), 0);
            box.addView(inp);
            new AlertDialog.Builder(this).setTitle("Подзадача").setView(box)
                .setPositiveButton("Добавить", (d, w) -> {
                    String s = inp.getText().toString().trim();
                    if (s.length() > 0) { Store.Task sub = new Store.Task(); sub.title = s; ed.subs.add(sub); buildEditor(); }
                }).setNegativeButton("Отмена", null).show();
        });
        body.addView(addSub);

        if (ed.id != 0) {
            TextView del = Ui.tv(this, "Удалить задачу", 15, Ui.RED, true);
            del.setGravity(Gravity.CENTER);
            del.setPadding(0, dp(16), 0, dp(16));
            del.setOnClickListener(v -> {
                store.deleteTask(ed);
                reload();
                while (!stack.isEmpty()) pop();
                showTab(tab);
            });
            body.addView(del);
        }

        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        push(col);
    }

    View editorRow(int resId, String label, String value, Runnable onClick) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(0), dp(12), dp(0), dp(12));
        ImageView ic = Ui.icon(this, resId, 20, Ui.ACCENT);
        LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(36), dp(36));
        ic.setLayoutParams(iclp);
        ic.setScaleType(ImageView.ScaleType.CENTER);
        r.addView(ic);
        r.addView(Ui.tv(this, label, 15, Ui.TEXT), Ui.weight(1));
        TextView val = Ui.tv(this, value, 14, Ui.SUB);
        r.addView(val);
        r.setOnClickListener(v -> onClick.run());
        return r;
    }

    String dateLabel() {
        long today = Store.todayStart();
        if (Store.sameDay(ed.due, today)) return "Сегодня";
        if (Store.sameDay(ed.due, Store.addDays(today, 1))) return "Завтра";
        return Store.fullDate(ed.due);
    }
    String remLabel() {
        if (ed.due == 0) return "Выберите дату";
        if (edRemOffsets.isEmpty()) return "Не напоминать";
        ArrayList<String> labels = new ArrayList<>();
        for (int v : edRemOffsets) {
            switch (v) {
                case 0: labels.add("в момент"); break;
                case 5: labels.add("за 5 мин"); break;
                case 15: labels.add("за 15 мин"); break;
                case 30: labels.add("за 30 мин"); break;
                case 60: labels.add("за 1 ч"); break;
                case 1440: labels.add("за 1 д"); break;
                default: labels.add(String.valueOf(v));
            }
        }
        return String.join(", ", labels);
    }
    String prioLabel() {
        switch (ed.priority) {
            case 1: return "Низкий";
            case 2: return "Средний";
            case 3: return "Высокий";
            default: return "Без приоритета";
        }
    }

    void pickDate() {
        Calendar c = Calendar.getInstance();
        if (ed.due > 0) c.setTimeInMillis(ed.due);
        new DatePickerDialog(this, (v, y, m, d) -> {
            Calendar nc = Calendar.getInstance();
            nc.set(y, m, d, 0, 0, 0);
            nc.set(Calendar.MILLISECOND, 0);
            ed.due = nc.getTimeInMillis();
            buildEditor();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    void pickTime() {
        int hh = 9, mm = 0;
        if (ed.time.length() > 0) {
            String[] p = ed.time.split(":");
            hh = Integer.parseInt(p[0]); mm = Integer.parseInt(p[1]);
        }
        new TimePickerDialog(this, (v, h_, m_) -> {
            ed.time = String.format(Locale.ROOT, "%02d:%02d", h_, m_);
            ed.hasTime = 1;
            buildEditor();
        }, hh, mm, true).show();
    }

    void pickReminder() {
        if (ed.due == 0) { toast("Сначала выберите дату"); return; }
        final String[] labels = {"В момент", "За 5 минут", "За 15 минут", "За 30 минут", "За 1 час", "За 1 день"};
        final int[] vals = {0, 5, 15, 30, 60, 1440};
        boolean[] checked = new boolean[labels.length];
        for (int i = 0; i < labels.length; i++) checked[i] = edRemOffsets.contains(vals[i]);
        new AlertDialog.Builder(this).setTitle("Напоминания (до 5)")
            .setMultiChoiceItems(labels, checked, (d, w, isChecked) -> {
                if (isChecked) { if (edRemOffsets.size() < 5 && !edRemOffsets.contains(vals[w])) edRemOffsets.add(vals[w]); }
                else edRemOffsets.remove(Integer.valueOf(vals[w]));
            })
            .setPositiveButton("Готово", (d, w) -> buildEditor())
            .setNegativeButton("Отмена", null).show();
    }

    void pickRepeat() {
        final AlertDialog[] holder = new AlertDialog[1];
        LinearLayout list = Ui.col(this);
        list.setPadding(0, dp(8), 0, dp(8));
        addRepeatRow(list, holder, "Не повторять", () -> { ed.repeat = ""; buildEditor(); });
        addRepeatRow(list, holder, "Ежедневно", () -> { ed.repeat = "daily"; buildEditor(); });
        // еженедельно: тап = weekly, долгое нажатие = интервал
        LinearLayout wr = Ui.row(this);
        wr.setPadding(dp(20), dp(14), dp(20), dp(14));
        wr.addView(Ui.tv(this, "Еженедельно", 16, Ui.TEXT), Ui.weight(1));
        TextView hint = Ui.tv(this, "⋯", 18, Ui.SUB);
        wr.addView(hint);
        wr.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); ed.repeat = "weekly"; buildEditor(); });
        wr.setOnLongClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); pickWeeklyInterval(); return true; });
        list.addView(wr);
        addRepeatRow(list, holder, "По дням недели…", () -> pickDays());
        addRepeatRow(list, holder, "Выбрать даты…", () -> pickDates());
        addRepeatRow(list, holder, "Ежемесячно", () -> { ed.repeat = "monthly"; buildEditor(); });
        addRepeatRow(list, holder, "Ежегодно", () -> { ed.repeat = "yearly"; buildEditor(); });
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Повтор").setView(list).create();
        holder[0] = dlg;
        dlg.show();
    }

    void addRepeatRow(LinearLayout list, final AlertDialog[] holder, String label, final Runnable action) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(20), dp(14), dp(20), dp(14));
        r.addView(Ui.tv(this, label, 16, Ui.TEXT), Ui.weight(1));
        r.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); action.run(); });
        list.addView(r);
    }

    void pickWeeklyInterval() {
        new AlertDialog.Builder(this).setTitle("Интервал недели")
            .setItems(new String[]{"Еженедельно", "Раз в 2 недели", "Раз в 3 недели"}, (d, w) -> {
                ed.repeat = w == 0 ? "weekly" : (w == 1 ? "weekly2" : "weekly3");
                buildEditor();
            }).show();
    }

    void pickDates() {
        final java.util.Set<String> sel = new java.util.HashSet<>();
        if (ed.repeat != null && ed.repeat.startsWith("dates:")) {
            for (String p : ed.repeat.substring(6).split(",")) sel.add(p.trim());
        }
        final int[] ym = {Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH)};
        final LinearLayout box = Ui.col(this);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        final LinearLayout grid = Ui.col(this);
        final TextView title = Ui.tv(this, "", 16, Ui.TEXT, true);

        LinearLayout hdr = Ui.row(this);
        TextView prev = Ui.tv(this, "‹", 30, Ui.ACCENT);
        prev.setPadding(dp(14), 0, dp(4), 0);
        prev.setOnClickListener(v -> { if (ym[1] == 0) { ym[1] = 11; ym[0]--; } else ym[1]--; renderDatePickerGrid(grid, title, sel, ym); });
        hdr.addView(prev);
        title.setGravity(Gravity.CENTER);
        hdr.addView(title, Ui.weight(1));
        TextView next = Ui.tv(this, "›", 30, Ui.ACCENT);
        next.setPadding(dp(4), 0, dp(14), 0);
        next.setOnClickListener(v -> { if (ym[1] == 11) { ym[1] = 0; ym[0]++; } else ym[1]++; renderDatePickerGrid(grid, title, sel, ym); });
        hdr.addView(next);
        box.addView(hdr);

        box.addView(grid);
        renderDatePickerGrid(grid, title, sel, ym);

        new AlertDialog.Builder(this).setTitle("Повтор в выбранные даты").setView(box)
            .setPositiveButton("Готово", (d, w) -> {
                if (sel.isEmpty()) { ed.repeat = "weekly"; }
                else {
                    java.util.List<String> sorted = new java.util.ArrayList<>(sel);
                    java.util.Collections.sort(sorted, (a, b) -> compareMd(a, b));
                    StringBuilder sb = new StringBuilder("dates:");
                    boolean first = true;
                    for (String s : sorted) { if (!first) sb.append(','); sb.append(s); first = false; }
                    ed.repeat = sb.toString();
                }
                buildEditor();
            })
            .setNegativeButton("Отмена", null).show();
    }

    int compareMd(String a, String b) {
        String[] pa = a.split("-"), pb = b.split("-");
        int ma = Integer.parseInt(pa[0]), mb = Integer.parseInt(pb[0]);
        if (ma != mb) return ma - mb;
        return Integer.parseInt(pa[1]) - Integer.parseInt(pb[1]);
    }

    void renderDatePickerGrid(LinearLayout grid, TextView title, java.util.Set<String> sel, int[] ym) {
        grid.removeAllViews();
        String[] mo = {"Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
        title.setText(mo[ym[1]] + " " + ym[0]);

        String[] wd = {"ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС"};
        LinearLayout heads = Ui.row(this);
        for (String w : wd) { TextView x = Ui.tv(this, w, 11, Ui.SUB, true); x.setGravity(Gravity.CENTER); heads.addView(x, Ui.weight(1)); }
        grid.addView(heads);
        grid.addView(Ui.spacer(this, dp(4)));

        Calendar c = Calendar.getInstance();
        c.set(ym[0], ym[1], 1);
        int offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        int day = 1;
        for (int week = 0; week < 6 && day <= max; week++) {
            LinearLayout line = Ui.row(this);
            for (int j = 0; j < 7; j++) {
                boolean inRange = !(week == 0 && j < offset) && day <= max;
                if (inRange) {
                    final int d = day;
                    final String key = (ym[1] + 1) + "-" + d;
                    boolean selected = sel.contains(key);
                    TextView cell = Ui.tv(this, String.valueOf(d), 14, selected ? Color.WHITE : Ui.TEXT, selected);
                    cell.setGravity(Gravity.CENTER);
                    int s = dp(38);
                    cell.setLayoutParams(new LinearLayout.LayoutParams(s, s));
                    cell.setBackground(selected ? Ui.oval(Ui.ACCENT) : Ui.stroke(this, Color.TRANSPARENT, 1, 20));
                    cell.setOnClickListener(v -> {
                        if (sel.contains(key)) sel.remove(key); else sel.add(key);
                        renderDatePickerGrid(grid, title, sel, ym);
                    });
                    line.addView(cell, Ui.weight(1));
                    day++;
                } else {
                    TextView blank = Ui.tv(this, "", 14, Ui.TEXT);
                    line.addView(blank, Ui.weight(1));
                }
            }
            grid.addView(line);
        }
    }

    void pickDays() {
        String[] names = {"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
        int[] calDays = {2,3,4,5,6,7,1};
        final boolean[] sel = new boolean[7];
        java.util.Set<Integer> cur = new java.util.HashSet<>();
        if (ed.repeat != null && ed.repeat.startsWith("dow:")) {
            for (String p : ed.repeat.substring(4).split(",")) { try { cur.add(Integer.parseInt(p.trim())); } catch (Exception ignored) { } }
        }
        for (int i = 0; i < 7; i++) sel[i] = cur.contains(calDays[i]);
        final LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(8), 0, dp(8));
        renderDayPicker(row, names, sel);
        LinearLayout box = Ui.col(this);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        box.addView(row);
        new AlertDialog.Builder(this).setTitle("Повторять в дни недели").setView(box)
            .setPositiveButton("Готово", (d, w) -> {
                StringBuilder sb = new StringBuilder("dow:");
                boolean any = false;
                for (int i = 0; i < 7; i++) if (sel[i]) { if (any) sb.append(','); sb.append(calDays[i]); any = true; }
                ed.repeat = any ? sb.toString() : "weekly";
                buildEditor();
            })
            .setNegativeButton("Отмена", null).show();
    }

    void renderDayPicker(LinearLayout row, String[] names, boolean[] sel) {
        row.removeAllViews();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout cell = Ui.col(this);
            cell.setGravity(Gravity.CENTER);
            TextView c = Ui.tv(this, names[i], 12, sel[i] ? Color.WHITE : Ui.TEXT, sel[i]);
            c.setGravity(Gravity.CENTER);
            int s = dp(38);
            c.setLayoutParams(new LinearLayout.LayoutParams(s, s));
            c.setBackground(sel[i] ? Ui.oval(Ui.ACCENT) : Ui.stroke(this, Ui.BORDER, 1, 20));
            c.setOnClickListener(v -> { sel[idx] = !sel[idx]; renderDayPicker(row, names, sel); });
            cell.addView(c);
            row.addView(cell, Ui.weight(1));
        }
    }

    void pickPriority() {
        final String[] labels = {"Без приоритета", "Низкий", "Средний", "Высокий"};
        final int[] vals = {0, 1, 2, 3};
        new AlertDialog.Builder(this).setTitle("Приоритет").setItems(labels, (d, w) -> {
            ed.priority = vals[w];
            buildEditor();
        }).show();
    }

    void pickList() {
        ArrayList<String> names = new ArrayList<>();
        final ArrayList<Long> ids = new ArrayList<>();
        names.add("Входящие"); ids.add(0L);
        for (Store.TList l : lists) { names.add(l.name); ids.add(l.id); }
        names.add("+  Новый список");
        new AlertDialog.Builder(this).setTitle("Список").setItems(names.toArray(new String[0]), (d, w) -> {
            if (w == names.size() - 1) { addListDialog(); return; }
            ed.listId = ids.get(w);
            buildEditor();
        }).show();
    }

    void addListDialog() {
        final EditText input = Ui.et(this, "Название списка", 16);
        LinearLayout box = Ui.col(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        final int[] chosen = {Ui.ACCENT};
        box.addView(colorPicker(chosen));
        new AlertDialog.Builder(this).setTitle("Новый список").setView(box)
            .setPositiveButton("Создать", (d, w) -> {
                String s = input.getText().toString().trim();
                if (s.length() == 0) return;
                Store.TList l = new Store.TList();
                l.name = s; l.color = chosen[0];
                store.saveList(l);
                reload();
                if (ed != null) ed.listId = l.id;
                buildEditor();
            })
            .setNegativeButton("Отмена", null).show();
    }

    String tagLabel() {
        if (ed.tagIds.isEmpty()) return "Нет";
        StringBuilder s = new StringBuilder();
        for (long id : ed.tagIds) for (Store.Tag g : tags) if (g.id == id) { if (s.length() > 0) s.append(", "); s.append(g.name); }
        return s.toString();
    }

    void pickTags() {
        if (tags.isEmpty()) { addTagDialog(); return; }
        final String[] names = new String[tags.size()];
        boolean[] checked = new boolean[tags.size()];
        for (int i = 0; i < tags.size(); i++) { names[i] = tags.get(i).name; checked[i] = ed.tagIds.contains(tags.get(i).id); }
        new AlertDialog.Builder(this).setTitle("Теги")
            .setMultiChoiceItems(names, checked, (d, w, c) -> {
                long id = tags.get(w).id;
                if (c) { if (!ed.tagIds.contains(id)) ed.tagIds.add(id); } else ed.tagIds.remove(Long.valueOf(id));
            })
            .setPositiveButton("Готово", (d, w) -> buildEditor())
            .setNeutralButton("Новый тег", (d, w) -> addTagDialog())
            .setNegativeButton("Отмена", null).show();
    }

    void addTagDialog() {
        final EditText input = Ui.et(this, "Название тега", 16);
        LinearLayout box = Ui.col(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        final int[] chosen = {Ui.ACCENT};
        box.addView(colorPicker(chosen));
        new AlertDialog.Builder(this).setTitle("Новый тег").setView(box)
            .setPositiveButton("Создать", (d, w) -> {
                String s = input.getText().toString().trim();
                if (s.length() == 0) return;
                Store.Tag t = new Store.Tag(); t.name = s; t.color = chosen[0];
                store.saveTag(t);
                reload();
                if (ed != null) { ed.tagIds.add(t.id); buildEditor(); }
            })
            .setNegativeButton("Отмена", null).show();
    }

    void pickTemplate() {
        ArrayList<Store.Template> temps = store.loadTemplates();
        if (temps.isEmpty()) { toast("Нет шаблонов. Сохраните задачу как шаблон."); return; }
        String[] names = new String[temps.size()];
        for (int i = 0; i < temps.size(); i++) names[i] = temps.get(i).name;
        new AlertDialog.Builder(this).setTitle("Шаблоны").setItems(names, (d, w) -> applyTemplate(temps.get(w))).show();
    }

    void applyTemplate(Store.Template tpl) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(tpl.json);
            ed.title = o.optString("title", ed.title);
            ed.notes = o.optString("notes", ed.notes);
            ed.priority = o.optInt("priority", ed.priority);
            ed.subs.clear();
            org.json.JSONArray subs = o.optJSONArray("subs");
            if (subs != null) for (int i = 0; i < subs.length(); i++) {
                Store.Task s = new Store.Task();
                s.title = subs.getJSONObject(i).optString("title", "");
                ed.subs.add(s);
            }
            buildEditor();
        } catch (Exception e) { toast("Ошибка шаблона"); }
    }

    void saveAsTemplate(Store.Task t) {
        final EditText input = Ui.et(this, "Название шаблона", 16);
        LinearLayout box = Ui.col(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        new AlertDialog.Builder(this).setTitle("Сохранить как шаблон").setView(box)
            .setPositiveButton("Сохранить", (d, w) -> {
                String n = input.getText().toString().trim();
                if (n.length() == 0) return;
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("title", t.title);
                    o.put("notes", t.notes);
                    o.put("priority", t.priority);
                    org.json.JSONArray arr = new org.json.JSONArray();
                    for (Store.Task s : t.subs) { org.json.JSONObject so = new org.json.JSONObject(); so.put("title", s.title); arr.put(so); }
                    o.put("subs", arr);
                    Store.Template tp = new Store.Template();
                    tp.name = n; tp.json = o.toString();
                    store.saveTemplate(tp);
                    toast("Шаблон сохранён");
                } catch (Exception e) { toast("Ошибка"); }
            })
            .setNegativeButton("Отмена", null).show();
    }

    void saveEditor() {
        String title = edTitleInput.getText().toString().trim();
        if (title.length() == 0) { toast("Введите название"); return; }
        ed.title = title;
        ed.notes = edNotesInput.getText().toString().trim();
        // compute reminders
        ed.remOffsets = new ArrayList<>(edRemOffsets);
        ed.reminder = 0;
        if (!ed.remOffsets.isEmpty() && ed.due > 0) {
            long base = ed.due;
            if (ed.hasTime == 1 && ed.time.length() > 0) base = base + timeMillis(ed.time);
            long earliest = Long.MAX_VALUE;
            for (int off : ed.remOffsets) { long w = base - off * 60000L; if (w < earliest) earliest = w; }
            ed.reminder = earliest;
        }

        store.saveTask(ed);
        for (Store.Task s : ed.subs) { s.parent = ed; store.saveTask(s); }
        if (!ed.remOffsets.isEmpty() && ed.done == 0 && ed.deleted == 0) Reminders.schedule(this, ed); else Reminders.cancel(this, ed.id);
        reload();
        while (!stack.isEmpty()) pop();
        showTab(tab);
        toast("Сохранено");
    }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    // ================= BACKUP =================
    static final int REQ_EXPORT = 9001, REQ_IMPORT = 9002;

    void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "planner-backup.json");
        try { startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { toast("Нет файлового менеджера"); }
    }

    void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        try { startActivityForResult(i, REQ_IMPORT); } catch (Exception e) { toast("Нет файлового менеджера"); }
    }

    String buildBackupJson() throws Exception {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("app", "planner");
        o.put("version", 4);
        o.put("tasks", tableToJson("tasks"));
        o.put("lists", tableToJson("lists"));
        o.put("tags", tableToJson("tags"));
        o.put("habits", tableToJson("habits"));
        o.put("habit_log", tableToJson("habit_log"));
        o.put("focus_log", tableToJson("focus_log"));
        o.put("templates", tableToJson("templates"));
        return o.toString(2);
    }

    org.json.JSONArray tableToJson(String table) throws Exception {
        org.json.JSONArray arr = new org.json.JSONArray();
        android.database.Cursor c = store.db.rawQuery("SELECT * FROM " + table, null);
        String[] cols = c.getColumnNames();
        while (c.moveToNext()) {
            org.json.JSONObject row = new org.json.JSONObject();
            for (int i = 0; i < cols.length; i++) {
                switch (c.getType(i)) {
                    case android.database.Cursor.FIELD_TYPE_INTEGER: row.put(cols[i], c.getLong(i)); break;
                    case android.database.Cursor.FIELD_TYPE_FLOAT: row.put(cols[i], c.getDouble(i)); break;
                    case android.database.Cursor.FIELD_TYPE_NULL: row.put(cols[i], org.json.JSONObject.NULL); break;
                    default: row.put(cols[i], c.getString(i));
                }
            }
            arr.put(row);
        }
        c.close();
        return arr;
    }

    void applyBackupJson(String json) throws Exception {
        org.json.JSONObject o = new org.json.JSONObject(json);
        String[] tables = {"tasks", "lists", "tags", "habits", "habit_log", "focus_log", "templates"};
        for (String table : tables) {
            org.json.JSONArray arr = o.optJSONArray(table);
            if (arr == null) continue;
            store.db.delete(table, null, null);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject row = arr.getJSONObject(i);
                android.content.ContentValues v = new android.content.ContentValues();
                org.json.JSONArray keys = row.names();
                for (int k = 0; k < keys.length(); k++) {
                    String key = keys.getString(k);
                    Object val = row.get(key);
                    if (val instanceof Integer) v.put(key, (Integer) val);
                    else if (val instanceof Long) v.put(key, (Long) val);
                    else if (val instanceof Boolean) v.put(key, ((Boolean) val) ? 1 : 0);
                    else if (val == org.json.JSONObject.NULL) v.putNull(key);
                    else v.put(key, String.valueOf(val));
                }
                store.db.insert(table, null, v);
            }
        }
        reload();
        while (!stack.isEmpty()) pop();
        showTab(tab);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (req == REQ_EXPORT) {
                String json = buildBackupJson();
                java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                if (os != null) { os.write(json.getBytes("UTF-8")); os.close(); toast("Резервная копия сохранена"); }
            } else if (req == REQ_IMPORT) {
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                if (is != null) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    is.close();
                    applyBackupJson(new String(bos.toByteArray(), "UTF-8"));
                    toast("Данные импортированы");
                }
            }
        } catch (Exception e) { toast("Ошибка: " + e.getMessage()); }
    }
}
