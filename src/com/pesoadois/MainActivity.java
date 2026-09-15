package com.pesoadois;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Diário local de pesos para duas pessoas. Nenhum dado sai do aparelho. */
public final class MainActivity extends Activity {
    private static final String PREFS = "peso_a_dois_dados";
    private static final String KEY_BASE_CUSTOM = "base_custom_";
    private static final String KEY_BASE_WEIGHT = "base_weight_";
    private static final String KEY_BASE_DATE = "base_date_";
    private static final Locale PT_BR = new Locale("pt", "BR");

    private static final int INK = Color.rgb(28, 45, 36);
    private static final int MUTED = Color.rgb(108, 125, 115);
    private static final int BACKGROUND = Color.rgb(247, 246, 241);
    private static final int SURFACE = Color.WHITE;
    private static final int BORDER = Color.rgb(221, 231, 224);
    private static final int GREEN = Color.rgb(46, 125, 90);
    private static final int GREEN_LIGHT = Color.rgb(220, 239, 227);
    private static final int CORAL = Color.rgb(228, 111, 91);
    private static final int CORAL_LIGHT = Color.rgb(253, 232, 226);
    private static final int GOLD = Color.rgb(181, 126, 57);

    private EditText elisaInput;
    private EditText lucasInput;
    private TextView chosenDate;
    private TextView saveStatus;
    private TextView elisaCurrent;
    private TextView lucasCurrent;
    private TextView elisaDelta;
    private TextView lucasDelta;
    private TextView elisaPercent;
    private TextView lucasPercent;
    private TextView elisaBase;
    private TextView lucasBase;
    private LinearLayout historyContainer;
    private WeightChart chart;
    private long selectedAt;
    private boolean manuallySelectedDate;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        selectedAt = System.currentTimeMillis();
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!manuallySelectedDate) {
            selectedAt = System.currentTimeMillis();
            if (chosenDate != null) chosenDate.setText("📅  " + fullDate(selectedAt));
        }
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(buildHeader(), lp(-1, -2, 0));
        content.addView(buildIntroCard(), lp(-1, -2, 18));
        content.addView(label("NOVA MARCAÇÃO", 12, GREEN, true), lp(-1, -2, 24));
        content.addView(buildEntryCard(), lp(-1, -2, 24));

        LinearLayout statsHeader = new LinearLayout(this);
        statsHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView statsTitle = label("Variação desde a base", 21, INK, true);
        statsHeader.addView(statsTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView statsHint = label("kgf", 13, MUTED, false);
        statsHeader.addView(statsHint);
        content.addView(statsHeader, lp(-1, -2, 12));

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.TOP);
        statsRow.addView(buildStatsCard("ELISA", CORAL, true), new LinearLayout.LayoutParams(0, -2, 1f));
        statsRow.addView(space(10, 1));
        statsRow.addView(buildStatsCard("LUCAS", GREEN, false), new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(statsRow, lp(-1, -2, 24));

        TextView chartTitle = label("Evolução no tempo", 21, INK, true);
        content.addView(chartTitle, lp(-1, -2, 12));
        content.addView(buildChartCard(), lp(-1, dp(326), 24));

        TextView historyTitle = label("Últimas marcações", 21, INK, true);
        content.addView(historyTitle, lp(-1, -2, 12));
        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(historyContainer, lp(-1, -2, 0));

        setContentView(scroll);
        refreshUi();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = label("↗", 26, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(GREEN, 18));
        header.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        TextView title = label("Peso a dois", 25, INK, true);
        TextView subtitle = label("um registro de cada vez", 13, MUTED, false);
        titleBox.addView(title);
        titleBox.addView(subtitle, lp(-1, -2, 1));
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView gear = label("⚙", 25, MUTED, false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Configurar bases de comparação");
        gear.setOnClickListener(v -> openAboutBases());
        header.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private View buildIntroCard() {
        LinearLayout card = card();
        TextView eyebrow = label("DIÁRIO DO CASAL", 11, GOLD, true);
        eyebrow.setLetterSpacing(.12f);
        card.addView(eyebrow);
        TextView title = label("Como vocês estão evoluindo?", 20, INK, true);
        card.addView(title, lp(-1, -2, 5));
        TextView body = label("Registre os dois pesos juntos e acompanhe a história em um só lugar.\nTudo fica salvo apenas neste celular.", 14, MUTED, false);
        body.setLineSpacing(dp(2), 1f);
        card.addView(body, lp(-1, -2, 5));
        return card;
    }

    private View buildEntryCard() {
        LinearLayout card = card();
        TextView prompt = label("Pesos de hoje", 17, INK, true);
        card.addView(prompt);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setGravity(Gravity.TOP);
        fields.addView(personField("ELISA", "peso dela", CORAL, CORAL_LIGHT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        fields.addView(space(10, 1));
        fields.addView(personField("LUCAS", "seu peso", GREEN, GREEN_LIGHT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(fields, lp(-1, -2, 17));

        chosenDate = label("📅  " + fullDate(selectedAt), 14, INK, false);
        chosenDate.setGravity(Gravity.CENTER_VERTICAL);
        chosenDate.setPadding(dp(14), 0, dp(14), 0);
        chosenDate.setBackground(round(Color.rgb(246, 249, 246), 12));
        chosenDate.setOnClickListener(v -> openDatePicker());
        chosenDate.setContentDescription("Escolher data da marcação");
        card.addView(chosenDate, lp(-1, dp(48), 0));

        Button save = button("Registrar pesagens", GREEN, Color.WHITE, true);
        save.setOnClickListener(v -> saveMeasurements());
        card.addView(save, lp(-1, dp(52), 12));
        saveStatus = label("A data fica automaticamente como hoje.", 12, MUTED, false);
        card.addView(saveStatus, lp(-1, -2, 0));
        return card;
    }

    private View personField(String name, String hint, int accent, int tint, boolean elisa) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = label(name, 12, accent, true);
        nameView.setLetterSpacing(.10f);
        box.addView(nameView);
        TextView hintView = label(hint, 11, MUTED, false);
        box.addView(hintView, lp(-1, -2, 3));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(18);
        input.setTextColor(INK);
        input.setHintTextColor(Color.rgb(167, 181, 171));
        input.setHint("ex. 68,5");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setPadding(dp(13), 0, dp(10), 0);
        input.setBackground(stroked(tint, BORDER, 12, 1));
        input.setContentDescription("Peso de " + (elisa ? "Elisa" : "Lucas") + " em kgf");
        if (elisa) elisaInput = input; else lucasInput = input;
        box.addView(input, lp(-1, dp(54), 7));

        TextView unit = label("kgf", 12, MUTED, false);
        unit.setGravity(Gravity.RIGHT);
        box.addView(unit);
        return box;
    }

    private View buildStatsCard(String person, int accent, boolean elisa) {
        LinearLayout box = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(person, 12, accent, true);
        name.setLetterSpacing(.10f);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gear = label("⚙", 18, MUTED, false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Configurar base de " + person);
        final boolean personIsElisa = elisa;
        gear.setOnClickListener(v -> openBaseDialog(personIsElisa));
        top.addView(gear, new LinearLayout.LayoutParams(dp(32), dp(30)));
        box.addView(top);

        TextView current = label("—", 23, INK, true);
        box.addView(current, lp(-1, -2, 9));
        TextView delta = label("Sem marcações", 13, MUTED, true);
        box.addView(delta, lp(-1, -2, 4));
        TextView percent = label("—", 13, MUTED, false);
        box.addView(percent, lp(-1, -2, 2));
        TextView base = label("Base: primeira marcação", 10, MUTED, false);
        box.addView(base, lp(-1, -2, 8));

        if (elisa) {
            elisaCurrent = current;
            elisaDelta = delta;
            elisaPercent = percent;
            elisaBase = base;
        } else {
            lucasCurrent = current;
            lucasDelta = delta;
            lucasPercent = percent;
            lucasBase = base;
        }
        return box;
    }

    private View buildChartCard() {
        LinearLayout box = card();
        LinearLayout legend = new LinearLayout(this);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.addView(dot(CORAL), new LinearLayout.LayoutParams(dp(9), dp(9)));
        legend.addView(label(" Elisa", 12, MUTED, false), lp(-2, -2, 4));
        legend.addView(space(16, 1));
        legend.addView(dot(GREEN), new LinearLayout.LayoutParams(dp(9), dp(9)));
        legend.addView(label(" Lucas", 12, MUTED, false), lp(-2, -2, 4));
        box.addView(legend);
        chart = new WeightChart(this);
        box.addView(chart, lp(-1, dp(250), 8));
        return box;
    }

    private void refreshUi() {
        ArrayList<WeightPoint> elisa = readPoints("elisa");
        ArrayList<WeightPoint> lucas = readPoints("lucas");
        updateStats(elisa, true);
        updateStats(lucas, false);
        chart.setData(elisa, lucas);
        updateHistory(elisa, lucas);
        if (chosenDate != null) chosenDate.setText("📅  " + fullDate(selectedAt));
    }

    private void updateStats(ArrayList<WeightPoint> points, boolean isElisa) {
        TextView current = isElisa ? elisaCurrent : lucasCurrent;
        TextView delta = isElisa ? elisaDelta : lucasDelta;
        TextView percent = isElisa ? elisaPercent : lucasPercent;
        TextView baseText = isElisa ? elisaBase : lucasBase;
        if (points.isEmpty()) {
            current.setText("—");
            delta.setText("Sem marcações");
            percent.setText("—");
            baseText.setText("Base: primeira marcação");
            return;
        }
        WeightPoint latest = points.get(points.size() - 1);
        WeightPoint base = comparisonBase(isElisa, points);
        current.setText(formatWeight(latest.value) + " kgf");
        double change = latest.value - base.value;
        String direction = change >= 0 ? "Ganho " : "Perda ";
        delta.setText(direction + formatWeight(Math.abs(change)) + " kgf");
        double pct = base.value == 0 ? 0 : (change / base.value) * 100.0;
        percent.setText((pct >= 0 ? "+" : "−") + formatWeight(Math.abs(pct)) + "%");
        boolean custom = prefs().getBoolean(KEY_BASE_CUSTOM + (isElisa ? "elisa" : "lucas"), false);
        baseText.setText(custom ? "Base: " + formatWeight(base.value) + " kgf · " + shortDate(base.at)
                : "Base: primeira marcação · " + shortDate(base.at));
    }

    private void updateHistory(ArrayList<WeightPoint> elisa, ArrayList<WeightPoint> lucas) {
        if (historyContainer == null) return;
        historyContainer.removeAllViews();
        ArrayList<HistoryRow> rows = new ArrayList<>();
        for (WeightPoint point : elisa) rows.add(new HistoryRow(point.at, point.value, Double.NaN));
        for (WeightPoint point : lucas) rows.add(new HistoryRow(point.at, Double.NaN, point.value));
        Collections.sort(rows, (a, b) -> Long.compare(b.at, a.at));
        if (rows.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(label("Ainda não há marcações. O primeiro registro aparece aqui.", 13, MUTED, false));
            historyContainer.addView(empty, lp(-1, -2, 0));
            return;
        }
        int limit = Math.min(12, rows.size());
        for (int i = 0; i < limit; i++) {
            HistoryRow row = rows.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(10), dp(14), dp(10));
            item.setBackground(round(SURFACE, 12));
            TextView date = label(shortDateTime(row.at), 12, MUTED, false);
            item.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));
            if (!Double.isNaN(row.elisa)) {
                TextView value = label("Elisa  " + formatWeight(row.elisa), 13, CORAL, true);
                item.addView(value, lp(-2, -2, 8));
            }
            if (!Double.isNaN(row.lucas)) {
                TextView value = label("Lucas  " + formatWeight(row.lucas), 13, GREEN, true);
                item.addView(value, lp(-2, -2, 8));
            }
            historyContainer.addView(item, lp(-1, -2, i == limit - 1 ? 0 : 6));
        }
    }

    private void saveMeasurements() {
        Double elisa = parseWeight(elisaInput);
        Double lucas = parseWeight(lucasInput);
        if (elisa == null && hasText(elisaInput) || lucas == null && hasText(lucasInput)) {
            Toast.makeText(this, "Digite um peso válido entre 1 e 500 kgf.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (elisa == null && lucas == null) {
            Toast.makeText(this, "Preencha pelo menos um dos pesos.", Toast.LENGTH_SHORT).show();
            return;
        }
        long stamp = registrationTimestamp();
        if (elisa != null) appendPoint("elisa", new WeightPoint(stamp, elisa));
        if (lucas != null) appendPoint("lucas", new WeightPoint(stamp, lucas));
        elisaInput.setText("");
        lucasInput.setText("");
        selectedAt = System.currentTimeMillis();
        manuallySelectedDate = false;
        saveStatus.setText("Salvo em " + shortDateTime(stamp) + ". Dados guardados neste celular.");
        saveStatus.setTextColor(GREEN);
        refreshUi();
        Toast.makeText(this, "Pesagem registrada", Toast.LENGTH_SHORT).show();
    }

    private void openDatePicker() {
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(selectedAt);
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.setTimeInMillis(selectedAt);
                    chosen.set(Calendar.YEAR, year);
                    chosen.set(Calendar.MONTH, month);
                    chosen.set(Calendar.DAY_OF_MONTH, day);
                    selectedAt = chosen.getTimeInMillis();
                    manuallySelectedDate = true;
                    chosenDate.setText("📅  " + fullDate(selectedAt));
                    saveStatus.setText("Data escolhida manualmente; toque para alterar.");
                    saveStatus.setTextColor(MUTED);
                }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void openAboutBases() {
        new AlertDialog.Builder(this)
                .setTitle("Bases de comparação")
                .setMessage("Por padrão, cada variação usa a primeira marcação de Elisa e de Lucas. Toque na engrenagem do cartão de cada pessoa para escolher outro peso e outra data.")
                .setPositiveButton("Entendi", null)
                .show();
    }

    private void openBaseDialog(final boolean isElisa) {
        final String key = isElisa ? "elisa" : "lucas";
        final String person = isElisa ? "Elisa" : "Lucas";
        final ArrayList<WeightPoint> points = readPoints(key);
        final boolean custom = prefs().getBoolean(KEY_BASE_CUSTOM + key, false);
        WeightPoint first = points.isEmpty() ? new WeightPoint(System.currentTimeMillis(), 0) : points.get(0);
        final double initialWeight = custom ? prefs().getFloat(KEY_BASE_WEIGHT + key, (float) first.value) : first.value;
        final long initialDate = custom ? prefs().getLong(KEY_BASE_DATE + key, first.at) : first.at;

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(4), dp(22), 0);
        TextView description = label("Escolha de onde a variação de " + person + " deve começar.", 13, MUTED, false);
        body.addView(description, lp(-1, -2, 6));
        CheckBox automatic = new CheckBox(this);
        automatic.setText("Usar a primeira marcação automaticamente");
        automatic.setTextColor(INK);
        automatic.setTextSize(14);
        automatic.setChecked(!custom);
        body.addView(automatic, lp(-1, -2, 4));

        final EditText weight = new EditText(this);
        weight.setSingleLine(true);
        weight.setTextSize(17);
        weight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weight.setHint("Peso de referência em kgf");
        if (initialWeight > 0) weight.setText(formatWeight(initialWeight));
        weight.setPadding(dp(12), 0, dp(10), 0);
        weight.setBackground(stroked(Color.WHITE, BORDER, 10, 1));
        body.addView(weight, lp(-1, dp(50), 4));

        final Button date = button("📅  " + fullDate(initialDate), Color.rgb(246, 249, 246), INK, false);
        body.addView(date, lp(-1, dp(46), 4));
        automatic.setOnCheckedChangeListener((buttonView, checked) -> {
            weight.setEnabled(!checked);
            date.setEnabled(!checked);
            weight.setAlpha(checked ? .45f : 1f);
            date.setAlpha(checked ? .45f : 1f);
        });
        weight.setEnabled(custom);
        date.setEnabled(custom);
        weight.setAlpha(custom ? 1f : .45f);
        date.setAlpha(custom ? 1f : .45f);

        final long[] chosenDateValue = new long[]{initialDate};
        date.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(chosenDateValue[0]);
            new DatePickerDialog(this, (view, year, month, day) -> {
                Calendar picked = Calendar.getInstance();
                picked.setTimeInMillis(chosenDateValue[0]);
                picked.set(Calendar.YEAR, year);
                picked.set(Calendar.MONTH, month);
                picked.set(Calendar.DAY_OF_MONTH, day);
                chosenDateValue[0] = picked.getTimeInMillis();
                date.setText("📅  " + fullDate(chosenDateValue[0]));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Base de " + person)
                .setView(body)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar base", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (automatic.isChecked()) {
                if (points.isEmpty()) {
                    Toast.makeText(this, "Ainda não há uma primeira marcação para usar.", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs().edit().putBoolean(KEY_BASE_CUSTOM + key, false).apply();
            } else {
                Double value = parseWeight(weight);
                if (value == null) {
                    weight.setError("Digite um peso válido");
                    return;
                }
                prefs().edit()
                        .putBoolean(KEY_BASE_CUSTOM + key, true)
                        .putFloat(KEY_BASE_WEIGHT + key, value.floatValue())
                        .putLong(KEY_BASE_DATE + key, chosenDateValue[0])
                        .apply();
            }
            dialog.dismiss();
            refreshUi();
        }));
        dialog.show();
    }

    private WeightPoint comparisonBase(boolean isElisa, ArrayList<WeightPoint> points) {
        String key = isElisa ? "elisa" : "lucas";
        if (prefs().getBoolean(KEY_BASE_CUSTOM + key, false)) {
            return new WeightPoint(prefs().getLong(KEY_BASE_DATE + key, points.get(0).at),
                    prefs().getFloat(KEY_BASE_WEIGHT + key, (float) points.get(0).value));
        }
        return points.get(0);
    }

    private ArrayList<WeightPoint> readPoints(String person) {
        SharedPreferences data = prefs();
        int count = data.getInt("count_" + person, 0);
        ArrayList<WeightPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String weight = data.getString("weight_" + person + "_" + i, "");
            long at = data.getLong("date_" + person + "_" + i, 0L);
            try {
                if (!weight.isEmpty() && at > 0) points.add(new WeightPoint(at, Double.parseDouble(weight)));
            } catch (NumberFormatException ignored) { }
        }
        Collections.sort(points, (a, b) -> Long.compare(a.at, b.at));
        return points;
    }

    private void appendPoint(String person, WeightPoint point) {
        SharedPreferences data = prefs();
        int count = data.getInt("count_" + person, 0);
        data.edit()
                .putInt("count_" + person, count + 1)
                .putString("weight_" + person + "_" + count, Double.toString(point.value))
                .putLong("date_" + person + "_" + count, point.at)
                .apply();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private long registrationTimestamp() {
        if (!manuallySelectedDate) return System.currentTimeMillis();
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(selectedAt);
        Calendar now = Calendar.getInstance();
        selected.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
        selected.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
        selected.set(Calendar.SECOND, now.get(Calendar.SECOND));
        selected.set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND));
        return selected.getTimeInMillis();
    }

    private static boolean hasText(EditText edit) {
        return edit != null && edit.getText() != null && edit.getText().toString().trim().length() > 0;
    }

    private static Double parseWeight(EditText edit) {
        if (!hasText(edit)) return null;
        try {
            String raw = edit.getText().toString().trim().replace(',', '.');
            double value = Double.parseDouble(raw);
            return value >= 1.0 && value <= 500.0 ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static String formatWeight(double value) {
        return String.format(PT_BR, "%.1f", value);
    }

    private static String shortDate(long at) {
        return new SimpleDateFormat("dd/MM/yyyy", PT_BR).format(new Date(at));
    }

    private static String shortDateTime(long at) {
        return new SimpleDateFormat("dd/MM/yyyy · HH:mm", PT_BR).format(new Date(at));
    }

    private static String fullDate(long at) {
        String value = new SimpleDateFormat("EEEE, d 'de' MMMM", PT_BR).format(new Date(at));
        return value.substring(0, 1).toUpperCase(PT_BR) + value.substring(1);
    }

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
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setBackground(round(background, 12));
        return button;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackground(stroked(SURFACE, BORDER, 16, 1));
        return box;
    }

    private TextView dot(int color) {
        TextView view = label("", 1, color, false);
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

    private int dp(int value) {
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

    private static final class WeightPoint {
        final long at;
        final double value;

        WeightPoint(long at, double value) {
            this.at = at;
            this.value = value;
        }
    }

    private static final class HistoryRow {
        final long at;
        final double elisa;
        final double lucas;

        HistoryRow(long at, double elisa, double lucas) {
            this.at = at;
            this.elisa = elisa;
            this.lucas = lucas;
        }
    }

    private final class WeightChart extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ArrayList<WeightPoint> elisa = new ArrayList<>();
        private ArrayList<WeightPoint> lucas = new ArrayList<>();

        WeightChart(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setData(ArrayList<WeightPoint> first, ArrayList<WeightPoint> second) {
            elisa = first;
            lucas = second;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float left = 44 * density;
            float right = getWidth() - 13 * density;
            float top = 16 * density;
            float bottom = getHeight() - 34 * density;
            ArrayList<WeightPoint> all = new ArrayList<>();
            all.addAll(elisa);
            all.addAll(lucas);
            if (all.isEmpty()) {
                paint.setColor(MUTED);
                paint.setTextSize(13 * density);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Registre uma pesagem para ver o gráfico", getWidth() / 2f,
                        getHeight() / 2f, paint);
                return;
            }
            long minAt = all.get(0).at;
            long maxAt = all.get(0).at;
            double minWeight = all.get(0).value;
            double maxWeight = all.get(0).value;
            for (WeightPoint point : all) {
                minAt = Math.min(minAt, point.at);
                maxAt = Math.max(maxAt, point.at);
                minWeight = Math.min(minWeight, point.value);
                maxWeight = Math.max(maxWeight, point.value);
            }
            if (minAt == maxAt) {
                minAt -= 12 * 60 * 60 * 1000L;
                maxAt += 12 * 60 * 60 * 1000L;
            }
            double range = maxWeight - minWeight;
            if (range < .5) range = 2.0;
            minWeight -= range * .18;
            maxWeight += range * .18;
            paint.setStrokeWidth(1 * density);
            paint.setTextSize(10 * density);
            paint.setTextAlign(Paint.Align.RIGHT);
            for (int i = 0; i <= 4; i++) {
                float y = bottom - ((bottom - top) * i / 4f);
                paint.setColor(BORDER);
                canvas.drawLine(left, y, right, y, paint);
                paint.setColor(MUTED);
                double value = minWeight + (maxWeight - minWeight) * i / 4.0;
                canvas.drawText(formatWeight(value), left - 7 * density, y + 4 * density, paint);
            }
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(MUTED);
            canvas.drawText(shortDate(minAt), left, bottom + 23 * density, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(shortDate(maxAt), right, bottom + 23 * density, paint);
            drawLine(canvas, elisa, CORAL, minAt, maxAt, minWeight, maxWeight, left, right, top, bottom, density);
            drawLine(canvas, lucas, GREEN, minAt, maxAt, minWeight, maxWeight, left, right, top, bottom, density);
        }

        private void drawLine(Canvas canvas, List<WeightPoint> points, int color, long minAt,
                              long maxAt, double minWeight, double maxWeight, float left,
                              float right, float top, float bottom, float density) {
            if (points.isEmpty()) return;
            Path path = new Path();
            for (int i = 0; i < points.size(); i++) {
                WeightPoint point = points.get(i);
                float x = left + (float) ((point.at - minAt) / (double) (maxAt - minAt)) * (right - left);
                float y = bottom - (float) ((point.value - minWeight) / (maxWeight - minWeight)) * (bottom - top);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            for (WeightPoint point : points) {
                float x = left + (float) ((point.at - minAt) / (double) (maxAt - minAt)) * (right - left);
                float y = bottom - (float) ((point.value - minWeight) / (maxWeight - minWeight)) * (bottom - top);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, 4.3f * density, paint);
                paint.setColor(color);
                canvas.drawCircle(x, y, 2.8f * density, paint);
            }
        }
    }
}
