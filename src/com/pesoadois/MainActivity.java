package com.pesoadois;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pesoadois.core.ChartMath;
import com.pesoadois.core.DateText;
import com.pesoadois.core.Diary;
import com.pesoadois.core.Entry;
import com.pesoadois.core.PersonInfo;
import com.pesoadois.core.Stats;
import com.pesoadois.core.WeightFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Diário local de pesos para duas pessoas. Nenhum dado sai do aparelho. */
public final class MainActivity extends Activity {
    private static final String UI_PREFS = "ui";
    private static final String KEY_CHART_PERCENT = "chart_percent";
    private static final String KEY_CHART_RANGE = "chart_range";
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final long DAY_MS = 86_400_000L;
    private static final int MAX_NAME_LENGTH = 30;
    private static final int HISTORY_FIRST_DAYS = 14;
    private static final int HISTORY_STEP_DAYS = 30;
    /** Variação acima deste percentual pede confirmação antes de salvar. */
    private static final double BIG_CHANGE = 0.08;
    private static final int[] RANGE_DAYS = {30, 90, 365, 0};
    private static final String[] RANGE_LABELS = {"30 dias", "90 dias", "1 ano", "Tudo"};
    private static final String WEIGHT_ERROR = "Digite um peso entre 20 e 400 kgf (ex.: 68,5)";
    private static final String STATE_ELISA = "elisa_text";
    private static final String STATE_LUCAS = "lucas_text";
    private static final String STATE_SELECTED = "selected_at";
    private static final String STATE_MANUAL = "manual_date";
    private static final String STATE_HISTORY = "history_days";

    private static final class PersonCard {
        TextView name;
        TextView current;
        TextView ago;
        TextView change;
        TextView base;
        TextView goalText;
        LinearLayout goalBar;
    }

    private static final class Pending {
        final String person;
        final double kg;

        Pending(String person, double kg) {
            this.person = person;
            this.kg = kg;
        }
    }

    private DiaryStore store;
    private SharedPreferences ui;
    private Palette palette;
    private Diary diary = new Diary();
    private String notice;
    private long selectedAt;
    private boolean manualDate;
    private int historyDays = HISTORY_FIRST_DAYS;
    private boolean chartPercent;
    private int chartRange = 90;
    private String restoredElisa;
    private String restoredLucas;

    private LinearLayout root;
    private TextView subtitle;
    private LinearLayout noticeCard;
    private TextView noticeText;
    private LinearLayout emptyCard;
    private TextView dateText;
    private Button todayButton;
    private EditText elisaInput;
    private EditText lucasInput;
    private TextView elisaName;
    private TextView lucasName;
    private TextView elisaLast;
    private TextView lucasLast;
    private TextView saveStatus;
    private final Map<String, PersonCard> cards = new HashMap<>();
    private LinearLayout togetherCard;
    private TextView togetherSince;
    private TextView togetherPercent;
    private TextView togetherRecent;
    private TextView legendElisa;
    private TextView legendLucas;
    private WeightChart chart;
    private TextView[] modeChips;
    private TextView[] rangeChips;
    private LinearLayout historyContainer;
    private Button showMore;
    private TextView footer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = Palette.of(this);
        ui = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        chartPercent = ui.getBoolean(KEY_CHART_PERCENT, false);
        chartRange = ui.getInt(KEY_CHART_RANGE, 90);
        store = new DiaryStore(this);
        DiaryStore.LoadResult loaded = store.load();
        diary = loaded.diary;
        notice = loaded.notice;
        selectedAt = System.currentTimeMillis();
        if (state != null) restoreState(state);
        applySystemBars();
        buildScreen();
        refreshUi(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (root == null) return;
        if (!manualDate) selectedAt = System.currentTimeMillis();
        refreshUi(false);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(STATE_ELISA, elisaInput == null ? "" : elisaInput.getText().toString());
        state.putString(STATE_LUCAS, lucasInput == null ? "" : lucasInput.getText().toString());
        state.putLong(STATE_SELECTED, selectedAt);
        state.putBoolean(STATE_MANUAL, manualDate);
        state.putInt(STATE_HISTORY, historyDays);
    }

    private void restoreState(Bundle state) {
        restoredElisa = state.getString(STATE_ELISA, "");
        restoredLucas = state.getString(STATE_LUCAS, "");
        selectedAt = state.getLong(STATE_SELECTED, selectedAt);
        manualDate = state.getBoolean(STATE_MANUAL, false);
        historyDays = state.getInt(STATE_HISTORY, HISTORY_FIRST_DAYS);
    }

    /** Cores da barra de status e de navegação; a API 30+ descontinuou estas chamadas. */
    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(palette.background);
        window.setNavigationBarColor(palette.background);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (palette.dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    // ------------------------------------------------------------------ tela

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(buildHeader(), lp(-1, -2, 0));
        noticeCard = card();
        noticeText = label("", 14, palette.ink, false);
        noticeCard.addView(noticeText);
        Button ok = button("OK", palette.field, palette.ink, true);
        ok.setOnClickListener(v -> {
            notice = null;
            refreshUi(false);
        });
        noticeCard.addView(ok, lp(-2, dp(48), 8));
        root.addView(noticeCard, lp(-1, -2, 16));

        emptyCard = card();
        TextView eyebrow = label("DIÁRIO DO CASAL", 11, palette.gold, true);
        eyebrow.setLetterSpacing(.12f);
        emptyCard.addView(eyebrow);
        emptyCard.addView(label("Como vocês estão evoluindo?", 20, palette.ink, true), lp(-1, -2, 5));
        emptyCard.addView(label("Registre os dois pesos juntos e acompanhe a história em um só lugar.\n"
                + "Tudo fica salvo apenas neste celular.", 14, palette.muted, false), lp(-1, -2, 5));
        root.addView(emptyCard, lp(-1, -2, 16));

        root.addView(sectionTitle("Nova marcação"), lp(-1, -2, 22));
        root.addView(buildEntryCard(), lp(-1, -2, 8));

        root.addView(sectionTitle("Resumo"), lp(-1, -2, 22));
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(buildPersonCard(Diary.ELISA), new LinearLayout.LayoutParams(0, -2, 1f));
        summary.addView(space(10, 1));
        summary.addView(buildPersonCard(Diary.LUCAS), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(summary, lp(-1, -2, 8));
        togetherCard = buildTogetherCard();
        root.addView(togetherCard, lp(-1, -2, 10));

        root.addView(sectionTitle("Evolução"), lp(-1, -2, 22));
        root.addView(buildChartCard(), lp(-1, -2, 8));

        root.addView(sectionTitle("Histórico"), lp(-1, -2, 22));
        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyContainer, lp(-1, -2, 8));
        showMore = button("Mostrar mais", palette.field, palette.ink, false);
        showMore.setOnClickListener(v -> {
            historyDays += HISTORY_STEP_DAYS;
            refreshUi(false);
        });
        root.addView(showMore, lp(-1, dp(48), 10));

        footer = label("", 12, palette.muted, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, lp(-1, -2, 24));

        setContentView(scroll);
        if (restoredElisa != null) elisaInput.setText(restoredElisa);
        if (restoredLucas != null) lucasInput.setText(restoredLucas);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = label("↗", 24, palette.onPrimary, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(palette.primary, 16));
        header.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        titleBox.addView(label("Peso a dois", 24, palette.ink, true));
        subtitle = label("", 13, palette.muted, false);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView menu = label("⋮", 24, palette.muted, true);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Mais opções");
        menu.setOnClickListener(this::showMenu);
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Sobre");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showAbout();
            return true;
        });
        popup.show();
    }

    private View sectionTitle(String text) {
        TextView title = label(text, 19, palette.ink, true);
        return title;
    }

    private View buildEntryCard() {
        LinearLayout card = card();
        card.addView(label("Pesos da pesagem", 17, palette.ink, true));

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateText = label("", 14, palette.ink, false);
        dateText.setGravity(Gravity.CENTER_VERTICAL);
        dateText.setPadding(dp(14), 0, dp(14), 0);
        dateText.setBackground(round(palette.field, 12));
        dateText.setContentDescription("Escolher data da marcação");
        dateText.setOnClickListener(v -> openDatePicker());
        dateRow.addView(dateText, new LinearLayout.LayoutParams(0, dp(48), 1f));
        todayButton = button("Hoje", palette.field, palette.ink, true);
        todayButton.setContentDescription("Voltar para hoje");
        todayButton.setOnClickListener(v -> {
            manualDate = false;
            selectedAt = System.currentTimeMillis();
            refreshUi(false);
        });
        dateRow.addView(todayButton, lp(-2, dp(48), 0));
        card.addView(dateRow, lp(-1, -2, 12));

        LinearLayout fields = new LinearLayout(this);
        fields.setGravity(Gravity.TOP);
        elisaName = new TextView(this);
        elisaInput = new EditText(this);
        elisaLast = new TextView(this);
        fields.addView(personInput(Diary.ELISA, elisaName, elisaInput, elisaLast),
                new LinearLayout.LayoutParams(0, -2, 1f));
        fields.addView(space(10, 1));
        lucasName = new TextView(this);
        lucasInput = new EditText(this);
        lucasLast = new TextView(this);
        fields.addView(personInput(Diary.LUCAS, lucasName, lucasInput, lucasLast),
                new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(fields, lp(-1, -2, 14));

        Button register = button("Registrar", palette.primary, palette.onPrimary, true);
        register.setOnClickListener(v -> onRegister());
        card.addView(register, lp(-1, dp(52), 4));

        saveStatus = label("A data fica automaticamente como hoje.", 12, palette.muted, false);
        card.addView(saveStatus, lp(-1, -2, 8));
        return card;
    }

    private LinearLayout personInput(String person, TextView nameView, EditText input, TextView lastView) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        nameView.setTextSize(12);
        nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nameView.setTextColor(palette.accent(person));
        nameView.setLetterSpacing(.10f);
        box.addView(nameView);

        input.setSingleLine(true);
        input.setTextSize(18);
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.muted);
        input.setHint("ex. 68,5");
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));
        input.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setPadding(dp(13), 0, dp(10), 0);
        input.setBackground(stroked(palette.field, palette.border, 12, 1));
        input.setContentDescription("Peso de " + diary.person(person).name + " em kgf");
        if (Diary.ELISA.equals(person)) {
            input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        } else {
            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_DONE) return false;
                onRegister();
                return true;
            });
        }
        box.addView(input, lp(-1, dp(54), 6));

        box.addView(label("kgf", 12, palette.muted, false));
        lastView.setTextSize(11);
        lastView.setTextColor(palette.muted);
        box.addView(lastView, lp(-1, -2, 4));
        return box;
    }

    private LinearLayout buildPersonCard(String person) {
        LinearLayout box = card();
        box.setClickable(true);
        box.setOnClickListener(v -> showPersonDialog(person));
        PersonCard holder = new PersonCard();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        holder.name = label("", 12, palette.accent(person), true);
        holder.name.setLetterSpacing(.10f);
        top.addView(holder.name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gear = label("⚙", 18, palette.muted, false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Configurar " + diary.person(person).name);
        gear.setOnClickListener(v -> showPersonDialog(person));
        top.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        box.addView(top);

        holder.current = label("—", 24, palette.ink, true);
        box.addView(holder.current, lp(-1, -2, 4));
        holder.ago = label("Sem marcações", 13, palette.muted, false);
        box.addView(holder.ago, lp(-1, -2, 2));
        holder.change = label("", 13, palette.ink, true);
        box.addView(holder.change, lp(-1, -2, 8));
        holder.base = label("", 11, palette.muted, false);
        box.addView(holder.base, lp(-1, -2, 4));
        holder.goalText = label("", 12, palette.muted, false);
        box.addView(holder.goalText, lp(-1, -2, 8));
        holder.goalBar = new LinearLayout(this);
        holder.goalBar.setOrientation(LinearLayout.HORIZONTAL);
        holder.goalBar.setWeightSum(1f);
        holder.goalBar.setBackground(round(palette.field, 3));
        box.addView(holder.goalBar, lp(-1, dp(6), 6));

        cards.put(person, holder);
        return box;
    }

    private LinearLayout buildTogetherCard() {
        LinearLayout box = card();
        togetherSince = label("", 14, palette.ink, true);
        box.addView(togetherSince);
        togetherPercent = label("", 13, palette.muted, false);
        box.addView(togetherPercent, lp(-1, -2, 6));
        togetherRecent = label("", 13, palette.muted, false);
        box.addView(togetherRecent, lp(-1, -2, 4));
        return box;
    }

    private LinearLayout buildChartCard() {
        LinearLayout box = card();
        LinearLayout legend = new LinearLayout(this);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.addView(dot(palette.accentElisa), new LinearLayout.LayoutParams(dp(9), dp(9)));
        legendElisa = label("", 12, palette.muted, false);
        legend.addView(legendElisa, lp(-2, -2, 4));
        legend.addView(space(16, 1));
        legend.addView(dot(palette.accentLucas), new LinearLayout.LayoutParams(dp(9), dp(9)));
        legendLucas = label("", 12, palette.muted, false);
        legend.addView(legendLucas, lp(-2, -2, 4));
        box.addView(legend);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeChips = new TextView[2];
        modeChips[0] = chip("kgf", "Mostrar em quilos");
        modeChips[1] = chip("%", "Mostrar variação em porcentagem desde a base");
        modeChips[0].setOnClickListener(v -> setChartMode(false));
        modeChips[1].setOnClickListener(v -> setChartMode(true));
        modeRow.addView(modeChips[0], lp(-2, dp(48), 0));
        modeRow.addView(modeChips[1], lp(-2, dp(48), 0));
        box.addView(modeRow, lp(-1, -2, 10));

        LinearLayout rangeRow = new LinearLayout(this);
        rangeRow.setOrientation(LinearLayout.HORIZONTAL);
        rangeChips = new TextView[RANGE_DAYS.length];
        for (int i = 0; i < RANGE_DAYS.length; i++) {
            final int days = RANGE_DAYS[i];
            rangeChips[i] = chip(RANGE_LABELS[i], "Período: " + RANGE_LABELS[i]);
            rangeChips[i].setOnClickListener(v -> setChartRange(days));
            rangeRow.addView(rangeChips[i], lp(-2, dp(48), 0));
        }
        box.addView(rangeRow, lp(-1, -2, 2));

        chart = new WeightChart(this, palette);
        box.addView(chart, lp(-1, dp(250), 8));
        return box;
    }

    private TextView chip(String text, String description) {
        TextView chip = label(text, 13, palette.muted, true);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(48));
        chip.setMinWidth(dp(48));
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setContentDescription(description);
        return chip;
    }

    private void setChartMode(boolean percent) {
        chartPercent = percent;
        ui.edit().putBoolean(KEY_CHART_PERCENT, percent).apply();
        refreshUi(true);
    }

    private void setChartRange(int days) {
        chartRange = days;
        ui.edit().putInt(KEY_CHART_RANGE, days).apply();
        refreshUi(true);
    }

    // ------------------------------------------------------------------ atualização

    private void refreshUi(boolean withChart) {
        if (root == null) return;
        long now = System.currentTimeMillis();

        subtitle.setText(subtitleText());
        noticeCard.setVisibility(notice != null ? View.VISIBLE : View.GONE);
        if (notice != null) noticeText.setText(notice);
        emptyCard.setVisibility(diary.isEmpty() ? View.VISIBLE : View.GONE);
        dateText.setText(manualDate ? "📅  " + DateText.fullDate(selectedAt)
                : "📅  Hoje · " + DateText.fullDate(now));
        todayButton.setVisibility(manualDate ? View.VISIBLE : View.GONE);

        elisaName.setText(diary.person(Diary.ELISA).name.toUpperCase(PT_BR));
        lucasName.setText(diary.person(Diary.LUCAS).name.toUpperCase(PT_BR));
        elisaLast.setText(lastHint(Diary.ELISA, now));
        lucasLast.setText(lastHint(Diary.LUCAS, now));
        legendElisa.setText(diary.person(Diary.ELISA).name);
        legendLucas.setText(diary.person(Diary.LUCAS).name);

        for (String person : Diary.PEOPLE) refreshPersonCard(person, now);
        refreshTogether(now);
        if (withChart) chart.setData(diary, chartRange, chartPercent, now);
        refreshChips();
        refreshHistory(now);
        footer.setText("Dados salvos só neste aparelho · versão " + versionName());
    }

    private String subtitleText() {
        if (diary.isEmpty()) return "um registro de cada vez";
        long first = Long.MAX_VALUE;
        for (Entry entry : diary.all()) first = Math.min(first, entry.at);
        int count = diary.size();
        return count + (count == 1 ? " marcação desde " : " marcações desde ") + DateText.monthYear(first);
    }

    private String lastHint(String person, long now) {
        Entry latest = diary.latest(person);
        if (latest == null) return "primeira marcação";
        return "último: " + WeightFormat.format(latest.kg) + " · " + DateText.relative(latest.at, now);
    }

    private void refreshPersonCard(String person, long now) {
        PersonCard card = cards.get(person);
        if (card == null) return;
        PersonInfo info = diary.person(person);
        card.name.setText(info.name.toUpperCase(PT_BR));
        Entry latest = diary.latest(person);
        if (latest == null) {
            card.current.setText("—");
            card.ago.setText("Sem marcações");
            card.change.setText("");
            card.base.setText("");
            card.goalText.setVisibility(View.GONE);
            card.goalBar.setVisibility(View.GONE);
            return;
        }
        card.current.setText(WeightFormat.format(latest.kg) + " kgf");
        card.ago.setText(DateText.relative(latest.at, now));
        Double change = Stats.changeSinceBase(diary, person);
        Double percent = Stats.percentSinceBase(diary, person);
        StringBuilder changeText = new StringBuilder();
        if (change != null) changeText.append(WeightFormat.formatSigned(change)).append(" kgf");
        if (percent != null) changeText.append(" · ").append(WeightFormat.formatPercent(percent));
        card.change.setText(changeText.toString());

        Diary.Base base = diary.base(person);
        if (base.custom) {
            card.base.setText("Base " + WeightFormat.format(base.kg) + " kgf · " + DateText.shortDate(base.at));
        } else {
            card.base.setText("Base: primeira marcação · " + DateText.shortDate(base.at));
        }

        if (info.hasGoal()) {
            Stats.GoalStatus goal = Stats.goal(base.kg, latest.kg, info.goalKg);
            card.goalText.setVisibility(View.VISIBLE);
            card.goalBar.setVisibility(View.VISIBLE);
            card.goalText.setText(goal.reached
                    ? "Meta alcançada 🎉"
                    : "Meta " + WeightFormat.format(info.goalKg) + " · faltam "
                    + WeightFormat.format(goal.remainingKg) + " kgf");
            card.goalBar.removeAllViews();
            float done = (float) goal.progress;
            if (done > 0) {
                View fill = new View(this);
                fill.setBackground(round(palette.accent(person), 3));
                card.goalBar.addView(fill, new LinearLayout.LayoutParams(0, -1, done));
            }
            if (done < 1f) {
                card.goalBar.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f - done));
            }
        } else {
            card.goalText.setVisibility(View.GONE);
            card.goalBar.setVisibility(View.GONE);
        }
    }

    private void refreshTogether(long now) {
        boolean both = diary.latest(Diary.ELISA) != null && diary.latest(Diary.LUCAS) != null;
        togetherCard.setVisibility(both ? View.VISIBLE : View.GONE);
        if (!both) return;
        Double elisaChange = Stats.changeSinceBase(diary, Diary.ELISA);
        Double lucasChange = Stats.changeSinceBase(diary, Diary.LUCAS);
        Double elisaPercent = Stats.percentSinceBase(diary, Diary.ELISA);
        Double lucasPercent = Stats.percentSinceBase(diary, Diary.LUCAS);
        togetherSince.setText("Juntos desde as bases: "
                + WeightFormat.formatSigned(elisaChange + lucasChange) + " kgf");
        togetherPercent.setText("Em %: " + diary.person(Diary.ELISA).name + " "
                + (elisaPercent == null ? "—" : WeightFormat.formatPercent(elisaPercent))
                + " · " + diary.person(Diary.LUCAS).name + " "
                + (lucasPercent == null ? "—" : WeightFormat.formatPercent(lucasPercent)));
        togetherRecent.setText("Últimos 30 dias: " + diary.person(Diary.ELISA).name + " "
                + recentChange(Diary.ELISA) + " · " + diary.person(Diary.LUCAS).name + " "
                + recentChange(Diary.LUCAS) + " kgf");
    }

    private String recentChange(String person) {
        Double change = Stats.changeOverDays(diary.of(person), 30);
        return change == null ? "—" : WeightFormat.formatSigned(change);
    }

    private void refreshChips() {
        for (int i = 0; i < modeChips.length; i++) styleChip(modeChips[i], (i == 1) == chartPercent);
        for (int i = 0; i < rangeChips.length; i++) styleChip(rangeChips[i], RANGE_DAYS[i] == chartRange);
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(selected ? palette.onPrimary : palette.muted);
        GradientDrawable pill = round(selected ? palette.primary : palette.field, 16);
        Drawable inset = new InsetDrawable(pill, 0, dp(12), 0, dp(12));
        chip.setBackground(inset);
    }

    // ------------------------------------------------------------------ histórico

    private void refreshHistory(long now) {
        historyContainer.removeAllViews();
        if (diary.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(label("Ainda não há marcações. O primeiro registro aparece aqui.", 13,
                    palette.muted, false));
            historyContainer.addView(empty, lp(-1, -2, 0));
            showMore.setVisibility(View.GONE);
            return;
        }
        Map<String, List<Entry>> byPerson = new HashMap<>();
        for (String person : Diary.PEOPLE) byPerson.put(person, diary.of(person));

        List<Entry> all = diary.all();
        ArrayList<LocalDate> days = new ArrayList<>();
        ArrayList<Long> sampleAt = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            LocalDate day = DateText.day(all.get(i).at);
            if (days.isEmpty() || !days.get(days.size() - 1).equals(day)) {
                days.add(day);
                sampleAt.add(all.get(i).at);
            }
        }
        LocalDate windowStart = DateText.day(now).minusDays(historyDays - 1);
        boolean olderHidden = false;
        int shown = 0;
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).isBefore(windowStart)) {
                olderHidden = true;
                break;
            }
            historyContainer.addView(buildDayRow(days.get(i), sampleAt.get(i), now, byPerson),
                    lp(-1, -2, shown == 0 ? 0 : 6));
            shown++;
        }
        showMore.setVisibility(olderHidden ? View.VISIBLE : View.GONE);
    }

    private View buildDayRow(LocalDate day, long sample, long now, Map<String, List<Entry>> byPerson) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(stroked(palette.surface, palette.border, 12, 1));
        TextView label = label(DateText.dayLabel(sample, now), 13, palette.ink, true);
        row.addView(label, new LinearLayout.LayoutParams(dp(92), -2));
        for (String person : Diary.PEOPLE) {
            row.addView(buildCell(person, day, byPerson.get(person), now),
                    new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return row;
    }

    private View buildCell(String person, LocalDate day, List<Entry> entries, long now) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setMinimumHeight(dp(48));
        cell.setPadding(dp(6), dp(4), dp(6), dp(4));
        cell.setClickable(true);
        String name = diary.person(person).name;

        Entry last = null;
        int lastIndex = -1;
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (DateText.day(entries.get(i).at).equals(day)) {
                count++;
                last = entries.get(i);
                lastIndex = i;
            }
        }
        if (last == null) {
            cell.addView(label("—", 15, palette.muted, false));
            cell.setContentDescription(name + ", sem marcação neste dia.");
            return cell;
        }
        LinearLayout valueLine = new LinearLayout(this);
        valueLine.setGravity(Gravity.CENTER_VERTICAL);
        valueLine.addView(label(WeightFormat.format(last.kg), 16, palette.accent(person), true));
        if (count > 1) {
            TextView extra = label(" +" + (count - 1), 11, palette.muted, true);
            valueLine.addView(extra);
        }
        cell.addView(valueLine);
        if (lastIndex > 0) {
            double delta = last.kg - entries.get(lastIndex - 1).kg;
            cell.addView(label(WeightFormat.formatSigned(delta), 11, palette.muted, false));
        }
        cell.setContentDescription(name + ", " + WeightFormat.format(last.kg) + " kgf, "
                + DateText.fullDate(last.at) + ". Toque para editar.");
        cell.setOnClickListener(v -> onHistoryCell(person, day));
        return cell;
    }

    private void onHistoryCell(String person, LocalDate day) {
        final List<Entry> same = new ArrayList<>();
        for (Entry entry : diary.of(person)) {
            if (DateText.day(entry.at).equals(day)) same.add(entry);
        }
        if (same.isEmpty()) return;
        if (same.size() == 1) {
            showEditDialog(same.get(0));
            return;
        }
        String[] labels = new String[same.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = DateText.time(same.get(i).at) + " · " + WeightFormat.format(same.get(i).kg) + " kgf";
        }
        new AlertDialog.Builder(this)
                .setTitle(diary.person(person).name + " · " + DateText.shortDate(same.get(0).at))
                .setItems(labels, (dialog, which) -> showEditDialog(same.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ registro

    private void onRegister() {
        String elisaRaw = elisaInput.getText().toString();
        String lucasRaw = lucasInput.getText().toString();
        Double elisaKg = null;
        Double lucasKg = null;
        if (!WeightFormat.isBlank(elisaRaw)) {
            elisaKg = WeightFormat.parse(elisaRaw);
            if (elisaKg == null) {
                elisaInput.setError(WEIGHT_ERROR);
                elisaInput.requestFocus();
                return;
            }
        }
        if (!WeightFormat.isBlank(lucasRaw)) {
            lucasKg = WeightFormat.parse(lucasRaw);
            if (lucasKg == null) {
                lucasInput.setError(WEIGHT_ERROR);
                lucasInput.requestFocus();
                return;
            }
        }
        if (elisaKg == null && lucasKg == null) {
            elisaInput.setError("Preencha pelo menos um dos pesos.");
            elisaInput.requestFocus();
            return;
        }
        List<Pending> pending = new ArrayList<>();
        if (elisaKg != null) pending.add(new Pending(Diary.ELISA, elisaKg));
        if (lucasKg != null) pending.add(new Pending(Diary.LUCAS, lucasKg));
        long stamp = registrationTime();
        confirmBigChanges(pending, stamp);
    }

    /** Data escolhida (se manual) com a hora atual. */
    private long registrationTime() {
        long now = System.currentTimeMillis();
        return manualDate ? DateText.combine(selectedAt, now) : now;
    }

    private void confirmBigChanges(List<Pending> pending, long stamp) {
        StringBuilder message = new StringBuilder();
        for (Pending item : pending) {
            Entry last = diary.latest(item.person);
            if (last != null && Math.abs(item.kg - last.kg) > BIG_CHANGE * last.kg) {
                if (message.length() > 0) message.append(' ');
                message.append(diary.person(item.person).name).append(' ')
                        .append(WeightFormat.format(item.kg)).append(" kgf (último ")
                        .append(WeightFormat.format(last.kg)).append(" kgf).");
            }
        }
        if (message.length() == 0) {
            confirmSameDay(pending, stamp);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Confira o valor")
                .setMessage("Confira o valor: " + message)
                .setPositiveButton("Salvar mesmo assim", (dialog, which) -> confirmSameDay(pending, stamp))
                .setNegativeButton("Corrigir", (dialog, which) -> focusPerson(pending.get(0).person))
                .show();
    }

    private void confirmSameDay(List<Pending> pending, long stamp) {
        StringBuilder message = new StringBuilder();
        boolean today = DateText.sameDay(stamp, System.currentTimeMillis());
        for (Pending item : pending) {
            List<Entry> same = diary.sameDay(item.person, stamp);
            if (same.isEmpty()) continue;
            Entry last = same.get(same.size() - 1);
            if (message.length() > 0) message.append('\n');
            message.append(diary.person(item.person).name).append(" já tem ")
                    .append(WeightFormat.format(last.kg)).append(" kgf ")
                    .append(today ? "hoje" : "em " + DateText.shortDate(stamp))
                    .append(" (").append(DateText.time(last.at)).append(").");
        }
        if (message.length() == 0) {
            saveMeasurements(pending, stamp, false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Já há marcação")
                .setMessage(message)
                .setPositiveButton("Substituir", (dialog, which) -> saveMeasurements(pending, stamp, true))
                .setNeutralButton("Manter as duas", (dialog, which) -> saveMeasurements(pending, stamp, false))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveMeasurements(List<Pending> pending, long stamp, boolean replace) {
        Diary next = diary.copy();
        for (Pending item : pending) {
            if (replace) next.removeSameDay(item.person, stamp);
            next.add(Entry.create(item.person, stamp, item.kg));
        }
        if (!commit(next)) return;
        elisaInput.setError(null);
        lucasInput.setError(null);
        elisaInput.setText("");
        lucasInput.setText("");
        hideKeyboard();
        long now = System.currentTimeMillis();
        if (!manualDate) selectedAt = now;
        String when = DateText.sameDay(stamp, now) ? "" : " em " + DateText.shortDate(stamp);
        saveStatus.setText("Salvo" + when + " às " + DateText.time(stamp) + " ✓");
        saveStatus.setTextColor(palette.primary);
        root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        refreshUi(true);
    }

    /** Grava o diário novo; só troca o da memória se a gravação deu certo. */
    private boolean commit(Diary next) {
        if (store.save(next)) {
            diary = next;
            return true;
        }
        Toast.makeText(this, "Não foi possível salvar. Verifique o espaço livre.", Toast.LENGTH_LONG).show();
        return false;
    }

    private void focusPerson(String person) {
        EditText target = Diary.ELISA.equals(person) ? elisaInput : lucasInput;
        target.requestFocus();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && root != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    private void openDatePicker() {
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(selectedAt);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedAt = DateText.withDate(selectedAt, year, month, day);
            manualDate = true;
            refreshUi(false);
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    // ------------------------------------------------------------------ diálogos

    private void showEditDialog(Entry entry) {
        String name = diary.person(entry.person).name;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(4), dp(22), 0);

        EditText weight = new EditText(this);
        weight.setSingleLine(true);
        weight.setTextSize(17);
        weight.setTextColor(palette.ink);
        weight.setHint("ex. 68,5");
        weight.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));
        weight.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weight.setText(WeightFormat.format(entry.kg));
        weight.setPadding(dp(12), 0, dp(10), 0);
        weight.setBackground(stroked(palette.field, palette.border, 10, 1));
        weight.setContentDescription("Peso de " + name + " em kgf");
        body.addView(weight, lp(-1, dp(50), 6));

        final long[] chosen = {entry.at};
        Button date = button("📅  " + DateText.fullDate(chosen[0]), palette.field, palette.ink, false);
        body.addView(date, lp(-1, dp(48), 6));
        date.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(chosen[0]);
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
                chosen[0] = DateText.withDate(chosen[0], year, month, day);
                date.setText("📅  " + DateText.fullDate(chosen[0]));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar marcação de " + name)
                .setView(body)
                .setPositiveButton("Salvar", null)
                .setNeutralButton("Excluir", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Double kg = WeightFormat.parse(weight.getText().toString());
                if (kg == null) {
                    weight.setError(WEIGHT_ERROR);
                    return;
                }
                Diary next = diary.copy();
                if (next.update(entry.id, chosen[0], kg) && commit(next)) {
                    dialog.dismiss();
                    refreshUi(true);
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> confirmDelete(entry, dialog));
        });
        dialog.show();
    }

    private void confirmDelete(Entry entry, AlertDialog editDialog) {
        String name = diary.person(entry.person).name;
        new AlertDialog.Builder(this)
                .setMessage("Excluir a marcação de " + name + " de " + DateText.shortDate(entry.at)
                        + " (" + WeightFormat.format(entry.kg) + " kgf)?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    Diary next = diary.copy();
                    if (next.remove(entry.id) && commit(next)) {
                        editDialog.dismiss();
                        refreshUi(true);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showPersonDialog(String person) {
        PersonInfo info = diary.person(person);
        Entry latest = diary.latest(person);
        Diary.Base current = diary.base(person);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(4), dp(22), 0);

        body.addView(label("Nome", 12, palette.muted, false));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setTextSize(16);
        name.setTextColor(palette.ink);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_NAME_LENGTH)});
        name.setText(info.name);
        name.setBackground(stroked(palette.field, palette.border, 10, 1));
        name.setPadding(dp(12), 0, dp(10), 0);
        body.addView(name, lp(-1, dp(48), 4));

        body.addView(label("Meta de peso (opcional)", 12, palette.muted, false), lp(-1, -2, 12));
        EditText goal = new EditText(this);
        goal.setSingleLine(true);
        goal.setTextSize(16);
        goal.setTextColor(palette.ink);
        goal.setHint("vazio = sem meta");
        goal.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));
        goal.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (info.hasGoal()) goal.setText(WeightFormat.format(info.goalKg));
        goal.setBackground(stroked(palette.field, palette.border, 10, 1));
        goal.setPadding(dp(12), 0, dp(10), 0);
        body.addView(goal, lp(-1, dp(48), 4));

        CheckBox automatic = new CheckBox(this);
        automatic.setText("Usar a primeira marcação como base");
        automatic.setTextColor(palette.ink);
        automatic.setTextSize(14);
        automatic.setChecked(!info.baseCustom);
        body.addView(automatic, lp(-1, -2, 14));

        final EditText baseWeight = new EditText(this);
        baseWeight.setSingleLine(true);
        baseWeight.setTextSize(16);
        baseWeight.setTextColor(palette.ink);
        baseWeight.setHint("Peso de referência em kgf");
        baseWeight.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));
        baseWeight.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        baseWeight.setBackground(stroked(palette.field, palette.border, 10, 1));
        baseWeight.setPadding(dp(12), 0, dp(10), 0);
        if (current != null) baseWeight.setText(WeightFormat.format(current.kg));
        body.addView(baseWeight, lp(-1, dp(48), 4));

        final long[] baseAt = {current != null ? current.at : (latest != null ? latest.at : System.currentTimeMillis())};
        final Button baseDate = button("📅  " + DateText.fullDate(baseAt[0]), palette.field, palette.ink, false);
        body.addView(baseDate, lp(-1, dp(48), 4));
        baseDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(baseAt[0]);
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
                baseAt[0] = DateText.withDate(baseAt[0], year, month, day);
                baseDate.setText("📅  " + DateText.fullDate(baseAt[0]));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        automatic.setOnCheckedChangeListener((view, checked) -> {
            baseWeight.setEnabled(!checked);
            baseDate.setEnabled(!checked);
            baseWeight.setAlpha(checked ? .45f : 1f);
            baseDate.setAlpha(checked ? .45f : 1f);
        });
        boolean custom = !automatic.isChecked();
        baseWeight.setEnabled(custom);
        baseDate.setEnabled(custom);
        baseWeight.setAlpha(custom ? 1f : .45f);
        baseDate.setAlpha(custom ? 1f : .45f);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Configurar " + info.name)
                .setView(body)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newName = name.getText().toString().trim();
            if (newName.isEmpty() || newName.length() > MAX_NAME_LENGTH) {
                name.setError("Use de 1 a 30 caracteres.");
                return;
            }
            double goalKg = Double.NaN;
            if (!WeightFormat.isBlank(goal.getText().toString())) {
                Double parsed = WeightFormat.parse(goal.getText().toString());
                if (parsed == null) {
                    goal.setError(WEIGHT_ERROR);
                    return;
                }
                goalKg = parsed;
            }
            boolean auto = automatic.isChecked();
            Double baseKg = null;
            if (!auto) {
                baseKg = WeightFormat.parse(baseWeight.getText().toString());
                if (baseKg == null) {
                    baseWeight.setError(WEIGHT_ERROR);
                    return;
                }
            }
            Diary next = diary.copy();
            PersonInfo edited = next.person(person);
            edited.name = newName;
            edited.goalKg = goalKg;
            edited.baseCustom = !auto;
            if (!auto) {
                edited.baseKg = baseKg;
                edited.baseAt = baseAt[0];
            }
            if (commit(next)) {
                dialog.dismiss();
                refreshUi(true);
            }
        }));
        dialog.show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Peso a dois")
                .setMessage("Versão " + versionName() + "\n\nSeus dados ficam apenas neste aparelho.")
                .setPositiveButton("Entendi", null)
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "?";
        }
    }

    // ------------------------------------------------------------------ helpers de view

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private Button button(String text, int background, int textColor, boolean bold) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackground(round(background, 12));
        return button;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackground(stroked(palette.surface, palette.border, 16, 1));
        return box;
    }

    private View dot(int color) {
        View view = new View(this);
        view.setBackground(round(color, 8));
        return view;
    }

    private View space(int width, int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(width), dp(height)));
        return view;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(topMargin);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable stroked(int color, int strokeColor, int radiusDp, int strokeDp) {
        GradientDrawable drawable = round(color, radiusDp);
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }
}
