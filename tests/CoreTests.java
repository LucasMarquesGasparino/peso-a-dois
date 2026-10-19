import com.pesoadois.core.AdbBackupReader;
import com.pesoadois.core.BackupCodec;
import com.pesoadois.core.BackupException;
import com.pesoadois.core.ChartMath;
import com.pesoadois.core.CsvExport;
import com.pesoadois.core.DateText;
import com.pesoadois.core.Diary;
import com.pesoadois.core.Entry;
import com.pesoadois.core.Json;
import com.pesoadois.core.LegacyImport;
import com.pesoadois.core.PersonInfo;
import com.pesoadois.core.Stats;
import com.pesoadois.core.WeightFormat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.zip.DeflaterOutputStream;

/** Testes do núcleo puro (sem Android). Rodar com: sh tests/run.sh */
public final class CoreTests {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final long DAY = 86_400_000L;
    private static final long NOW = at(2026, 10, 18, 20, 0);

    private static int checks;
    private static int failures;
    private static String section = "";

    /** Bloco que pode lançar exceção checada. */
    interface Action {
        void run() throws Exception;
    }

    private CoreTests() { }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        Locale.setDefault(new Locale("pt", "BR"));

        testParse();
        testFormat();
        testDates();
        testDiary();
        testStats();
        testChartMath();
        testJson();
        testBackupCodec();
        testLegacyImport();
        testAdbBackup();
        testCsv();

        System.out.println(checks + " verificações, " + failures + " falhas.");
        if (failures > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- pesos

    private static void testParse() {
        section("WeightFormat.parse");
        near(68.5, WeightFormat.parse("68,5"), "vírgula");
        near(68.5, WeightFormat.parse("68.5"), "ponto");
        near(68.5, WeightFormat.parse(" 68,50 kgf "), "kgf com espaços");
        near(68.5, WeightFormat.parse("68,5 kg"), "kg");
        near(68.5, WeightFormat.parse("68,5 KGF"), "KGF maiúsculo");
        eq(null, WeightFormat.parse("68,5,3"), "dois separadores");
        eq(null, WeightFormat.parse("abc"), "letras");
        eq(null, WeightFormat.parse("NaN"), "NaN");
        eq(null, WeightFormat.parse("Infinity"), "Infinity");
        eq(null, WeightFormat.parse("1e2"), "notação científica");
        eq(null, WeightFormat.parse("19,9"), "abaixo do mínimo");
        near(20.0, WeightFormat.parse("20"), "mínimo exato");
        near(400.0, WeightFormat.parse("400"), "máximo exato");
        eq(null, WeightFormat.parse("400,1"), "acima do máximo");
        eq(null, WeightFormat.parse("1234"), "quatro dígitos inteiros");
        eq(null, WeightFormat.parse(""), "vazio");
        eq(null, WeightFormat.parse("   "), "só espaços");
        eq(null, WeightFormat.parse(null), "nulo");
        eq(null, WeightFormat.parse("-5"), "negativo");
        eq(null, WeightFormat.parse(".5"), "sem dígito inteiro");
        eq(true, WeightFormat.isBlank("  "), "isBlank espaços");
        eq(false, WeightFormat.isBlank("1"), "isBlank com valor");
    }

    private static void testFormat() {
        section("WeightFormat.format");
        eq("68,5", WeightFormat.format(68.5), "format 1 casa");
        eq("68,55", WeightFormat.format(68.55), "format 2 casas");
        eq("70,0", WeightFormat.format(70.0), "format inteiro");
        eq("0,0", WeightFormat.format(-0.0), "format zero negativo");
        eq("+1,2", WeightFormat.formatSigned(1.23), "signed positivo");
        eq("\u22120,8", WeightFormat.formatSigned(-0.8), "signed negativo (U+2212)");
        eq("0,0", WeightFormat.formatSigned(0.04), "signed quase zero");
        eq("0,0", WeightFormat.formatSigned(0.0), "signed zero");
        eq("+1,5%", WeightFormat.formatPercent(1.5), "percent positivo");
        eq("\u22124,2%", WeightFormat.formatPercent(-4.2), "percent negativo");
        eq("68.5", WeightFormat.toMachine(68.5), "toMachine");
        eq("70", WeightFormat.toMachine(70.0), "toMachine inteiro");
        eq("68.55", WeightFormat.toMachine(68.55), "toMachine 2 casas");
    }

    // ---------------------------------------------------------------- datas

    private static void testDates() {
        section("DateText");
        long morning = at(2026, 10, 18, 7, 42);
        eq("18/10/2026", DateText.shortDate(morning), "shortDate");
        eq("07:42", DateText.time(morning), "time");
        eq("Domingo, 18 de outubro", DateText.fullDate(morning), "fullDate capitalizado");
        eq("18/10", DateText.dayMonth(morning), "dayMonth");
        eq("2026-10-18", DateText.isoDate(morning), "isoDate");
        eq("mar/26", DateText.monthYear(at(2026, 3, 5, 12, 0)), "monthYear sem ponto");
        eq("Hoje", DateText.dayLabel(morning, NOW), "dayLabel hoje");
        eq("Ontem", DateText.dayLabel(at(2026, 10, 17, 9, 0), NOW), "dayLabel ontem");
        eq("Dom., 11/10", DateText.dayLabel(at(2026, 10, 11, 9, 0), NOW), "dayLabel mesmo ano");
        eq("Sáb., 11/10/2025", DateText.dayLabel(at(2025, 10, 11, 9, 0), NOW), "dayLabel outro ano");
        eq("hoje", DateText.relative(morning, NOW), "relative hoje");
        eq("ontem", DateText.relative(at(2026, 10, 17, 9, 0), NOW), "relative ontem");
        eq("há 5 dias", DateText.relative(at(2026, 10, 13, 9, 0), NOW), "relative dias");
        eq("há 60 dias", DateText.relative(at(2026, 8, 19, 9, 0), NOW), "relative 60 dias");
        eq("há 4 meses", DateText.relative(at(2026, 6, 1, 9, 0), NOW), "relative meses");
        eq("hoje", DateText.relative(at(2026, 10, 20, 9, 0), NOW), "relative futuro");
        eq(true, DateText.sameDay(at(2026, 10, 18, 0, 5), at(2026, 10, 18, 23, 30)), "mesmo dia");
        eq(false, DateText.sameDay(at(2026, 10, 18, 23, 59), at(2026, 10, 19, 0, 1)), "virada do dia");
        eq("25/09/2026 07:42", DateText.shortDate(DateText.withDate(morning, 2026, 8, 25)) + " "
                + DateText.time(DateText.withDate(morning, 2026, 8, 25)), "withDate mantém hora");
        eq("11/10/2026 07:42", DateText.shortDate(DateText.combine(at(2026, 10, 11, 0, 10), morning)) + " "
                + DateText.time(DateText.combine(at(2026, 10, 11, 0, 10), morning)), "combine");
    }

    // ---------------------------------------------------------------- diário

    private static void testDiary() {
        section("Diary");
        Diary diary = new Diary();
        eq("Elisa", diary.person(Diary.ELISA).name, "nome padrão Elisa");
        eq("Lucas", diary.person(Diary.LUCAS).name, "nome padrão Lucas");
        throwsError(IllegalArgumentException.class, () -> diary.person("maria"), "pessoa desconhecida");
        eq(true, diary.isEmpty(), "diário novo vazio");
        eq(null, diary.base(Diary.ELISA), "sem base sem dados");
        eq(null, diary.latest(Diary.LUCAS), "sem última sem dados");

        long t0 = at(2026, 10, 10, 7, 0);
        Entry late = Entry.create(Diary.ELISA, t0 + 2 * DAY, 67.9);
        Entry early = Entry.create(Diary.ELISA, t0, 70.0);
        Entry lucas = Entry.create(Diary.LUCAS, t0 + DAY, 81.2);
        diary.add(late);
        diary.add(early);
        diary.add(lucas);
        eq(3, diary.size(), "add três");
        eq(early.id, diary.all().get(0).id, "ordenado: primeira é a mais antiga");
        eq(late.id, diary.all().get(2).id, "ordenado: última é a mais recente");
        eq(2, diary.of(Diary.ELISA).size(), "of elisa");
        eq(late.id, diary.latest(Diary.ELISA).id, "latest elisa");
        eq(lucas.id, diary.find(lucas.id).id, "find");
        eq(null, diary.find("x"), "find desconhecido");
        throwsError(UnsupportedOperationException.class, () -> diary.all().clear(), "all é somente leitura");

        eq(true, diary.update(early.id, t0 + 5 * DAY, 69.4), "update existente");
        eq(false, diary.update("nao-existe", t0, 70), "update inexistente");
        eq(69.4, diary.find(early.id).kg, "update trocou peso");
        eq(early.id, diary.latest(Diary.ELISA).id, "update reordenou (última agora é a de t0+5)");
        eq(true, diary.remove(lucas.id), "remove existente");
        eq(false, diary.remove(lucas.id), "remove repetido");
        eq(0, diary.of(Diary.LUCAS).size(), "lucas sem marcações após remover");

        // Estado aqui: Elisa tem late (12/10, 67,9) e early (15/10, 69,4); Lucas nenhuma.
        Entry sameDayA = Entry.create(Diary.ELISA, at(2026, 10, 13, 7, 10), 69.0);
        Entry sameDayB = Entry.create(Diary.ELISA, at(2026, 10, 13, 21, 30), 69.2);
        diary.add(sameDayA);
        diary.add(sameDayB);
        eq(2, diary.sameDay(Diary.ELISA, at(2026, 10, 13, 12, 0)).size(), "sameDay encontra duas");
        eq(0, diary.sameDay(Diary.LUCAS, at(2026, 10, 13, 12, 0)).size(), "sameDay de outra pessoa");
        eq(2, diary.removeSameDay(Diary.ELISA, at(2026, 10, 13, 12, 0)), "removeSameDay conta");
        eq(0, diary.removeSameDay(Diary.ELISA, at(2026, 10, 13, 12, 0)), "removeSameDay vazio");
        eq(2, diary.size(), "após removeSameDay restam as outras");

        Diary.Base first = diary.base(Diary.ELISA);
        near(67.9, first.kg, "base automática = primeira marcação");
        eq(false, first.custom, "base automática não é custom");
        eq(late.at, Long.valueOf(first.at), "base automática data = primeira");

        PersonInfo info = diary.person(Diary.ELISA);
        info.baseCustom = true;
        info.baseKg = 72.0;
        info.baseAt = t0 - DAY;
        Diary.Base custom = diary.base(Diary.ELISA);
        near(72.0, custom.kg, "base custom usa peso definido");
        eq(true, custom.custom, "base custom marcada");
        info.baseKg = Double.NaN;
        near(67.9, diary.base(Diary.ELISA).kg, "base custom sem peso volta à primeira");

        Diary copy = diary.copy();
        copy.add(Entry.create(Diary.LUCAS, t0, 80.0));
        copy.person(Diary.ELISA).name = "Ely";
        copy.lastBackupAt = 123L;
        eq(2, diary.size(), "cópia não altera o original (marcações)");
        eq("Elisa", diary.person(Diary.ELISA).name, "cópia não altera o original (nome)");
        eq(0L, Long.valueOf(diary.lastBackupAt), "cópia não altera o original (backup)");
        eq(3, copy.size(), "cópia recebeu a inclusão");

        testMerge(t0);
    }

    private static void testMerge(long t0) {
        section("Diary.merge");
        Diary current = new Diary();
        Entry e1 = Entry.create(Diary.ELISA, t0, 68.5);
        Entry l1 = Entry.create(Diary.LUCAS, t0, 80.0);
        current.add(e1);
        current.add(l1);

        Diary other = new Diary();
        other.person(Diary.ELISA).name = "Outro nome";
        other.add(e1);                                                  // mesmo id: ignora
        other.add(new Entry("novo-1", Diary.ELISA, t0 + 30_000L, 68.5)); // duplicata (30 s, mesmo peso)
        other.add(new Entry("novo-2", Diary.ELISA, t0 + 90_000L, 68.5)); // 90 s: entra
        other.add(new Entry("novo-3", Diary.LUCAS, t0, 80.004));        // diferença < 5 g: duplicata
        other.add(new Entry("novo-4", Diary.LUCAS, t0, 80.01));         // diferença de 10 g: entra
        eq(2, current.merge(other), "merge conta só as novas");
        eq(4, current.size(), "merge soma");
        eq("Elisa", current.person(Diary.ELISA).name, "merge não altera nomes");
        eq(0, current.merge(other), "merge repetido não adiciona nada");
    }

    // ---------------------------------------------------------------- estatísticas

    private static void testStats() {
        section("Stats");
        Diary diary = new Diary();
        long t0 = at(2026, 8, 1, 7, 0);
        diary.add(new Entry("a", Diary.ELISA, t0, 70.0));
        diary.add(new Entry("b", Diary.ELISA, t0 + 10 * DAY, 68.5));
        near(-1.5, Stats.changeSinceBase(diary, Diary.ELISA), "variação desde a base");
        near((68.5 - 70.0) / 70.0 * 100.0, Stats.percentSinceBase(diary, Diary.ELISA), "variação em %");
        eq(null, Stats.changeSinceBase(diary, Diary.LUCAS), "sem dados não tem variação");
        eq(null, Stats.percentSinceBase(diary, Diary.LUCAS), "sem dados não tem % ");

        List<Entry> series = new ArrayList<>();
        series.add(new Entry("s0", Diary.ELISA, t0, 80.0));
        series.add(new Entry("s1", Diary.ELISA, t0 + 10 * DAY, 79.0));
        series.add(new Entry("s2", Diary.ELISA, t0 + 30 * DAY, 78.0));
        series.add(new Entry("s3", Diary.ELISA, t0 + 60 * DAY, 75.0));
        near(-3.0, Stats.changeOverDays(series, 30), "30 dias: referência exata em t+30");
        near(-4.0, Stats.changeOverDays(series, 50), "50 dias: referência em t+10");
        near(-5.0, Stats.changeOverDays(series, 60), "60 dias: referência em t0");
        eq(null, Stats.changeOverDays(series, 90), "90 dias sem referência");
        eq(null, Stats.changeOverDays(new ArrayList<Entry>(), 30), "lista vazia");

        Stats.GoalStatus losing = Stats.goal(70, 68, 65);
        near(3.0, losing.remainingKg, "perder: falta");
        eq(false, losing.reached, "perder: não alcançada");
        near(0.4, losing.progress, "perder: progresso");

        Stats.GoalStatus done = Stats.goal(70, 64.9, 65);
        eq(true, done.reached, "perder: alcançada");
        near(1.0, done.progress, "perder alcançada: progresso 1");
        eq(true, Stats.goal(70, 65, 65).reached, "perder: exatamente na meta");

        Stats.GoalStatus gaining = Stats.goal(60, 62, 65);
        near(0.4, gaining.progress, "ganhar: progresso");
        eq(false, gaining.reached, "ganhar: não alcançada");
        eq(true, Stats.goal(60, 66, 65).reached, "ganhar: alcançada");

        eq(true, Stats.goal(65, 65.02, 65).reached, "base igual à meta, dentro da tolerância");
        near(0.0, Stats.goal(65, 66, 65).progress, "base igual à meta, fora: progresso 0");
        near(0.0, Stats.goal(70, 75, 65).progress, "progresso limitado a zero");
        eq(false, Stats.goal(70, 75, 65).reached, "piorou: não alcançada");
    }

    // ---------------------------------------------------------------- gráfico

    private static void testChartMath() {
        section("ChartMath");
        near(2.0, ChartMath.niceStep(1.375), "passo 2");
        near(1.0, ChartMath.niceStep(0.7), "passo 1");
        near(5.0, ChartMath.niceStep(3.0), "passo 5");
        near(2.5, ChartMath.niceStep(2.2), "passo 2,5");
        near(0.2, ChartMath.niceStep(0.12), "passo fracionário");
        near(100.0, ChartMath.niceStep(80.0), "passo 100");
        double[] ticks = ChartMath.niceTicks(66.3, 71.8, 4);
        eq(4, ticks.length, "ticks: quantidade");
        near(66.0, ticks[0], "ticks: primeiro");
        near(72.0, ticks[ticks.length - 1], "ticks: último");
        near(68.0, ticks[1], "ticks: segundo");
        double[] flat = ChartMath.niceTicks(70, 70, 4);
        near(69.0, flat[0], "faixa zero: expande para baixo");
        near(71.0, flat[flat.length - 1], "faixa zero: expande para cima");
        near(-2.142857142857143, ChartMath.toPercent(68.5, 70.0), "toPercent");
    }

    // ---------------------------------------------------------------- JSON

    private static void testJson() {
        section("Json");
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("nome", "Ela\u00e7\u00e3o \u2713 \"aspas\" \\barra\\ /\n\ttab");
        original.put("ts", 1760800000000L);
        original.put("kg", 68.5);
        original.put("nil", null);
        original.put("flag", Boolean.TRUE);
        List<Object> list = new ArrayList<>();
        list.add(1L);
        list.add("x");
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("a", new ArrayList<Object>());
        list.add(inner);
        original.put("lista", list);
        original.put("vazio", new LinkedHashMap<String, Object>());
        String text = Json.write(original);
        eq(original, Json.parse(text), "round trip completo");
        eq(true, text.contains("\n  \"nome\": "), "indentação de 2 espaços");
        eq(Long.valueOf(1760800000000L), Json.parse("1760800000000"), "inteiro grande vira Long");
        eq(-0.5, Json.parse("-0.5"), "decimal negativo");
        eq("\u00e9\n/", Json.parse("\"\\u00e9\\n\\/\""), "escapes \\u \\n \\/");
        eq("\"\\u0001\"", Json.write("\u0001"), "controle escapado");
        eq(Json.write("\u00e9"), "\"\u00e9\"", "acento sem escape");
        eq(new LinkedHashMap<String, Object>(), Json.parse("\uFEFF{}"), "BOM inicial aceito");
        eq("\"\"", Json.write(""), "string vazia");

        throwsError(IllegalArgumentException.class, () -> Json.parse("{\"a\":1"), "objeto truncado");
        throwsError(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} x"), "lixo no fim");
        throwsError(IllegalArgumentException.class, () -> Json.parse("[1,2,]"), "vírgula sobrando em array");
        throwsError(IllegalArgumentException.class, () -> Json.parse("{\"a\":1,}"), "vírgula sobrando em objeto");
        throwsError(IllegalArgumentException.class, () -> Json.parse(""), "texto vazio");
        throwsError(IllegalArgumentException.class, () -> Json.parse("01"), "zero à esquerda");
        throwsError(IllegalArgumentException.class, () -> Json.parse("1."), "ponto sem dígito");
        throwsError(IllegalArgumentException.class, () -> Json.parse("\"abc"), "texto não terminado");
        throwsError(IllegalArgumentException.class, () -> Json.parse("\"a\u0001\""), "controle cru");
        throwsError(IllegalArgumentException.class, () -> Json.write(Double.NaN), "NaN não é JSON");
        throwsError(IllegalArgumentException.class, () -> Json.write(Double.POSITIVE_INFINITY), "infinito não é JSON");
        throwsError(IllegalArgumentException.class, () -> Json.parse("1e999"), "número fora do intervalo");
        eq(64, nestedArrays(64), "aninhamento 64 aceito");
        throwsError(IllegalArgumentException.class, () -> Json.parse(nestedText(65)), "aninhamento 65 recusado");
    }

    private static int nestedArrays(int depth) {
        Object value = Json.parse(nestedText(depth));
        int count = 0;
        while (value instanceof List) {
            count++;
            value = ((List<?>) value).isEmpty() ? null : ((List<?>) value).get(0);
        }
        return count;
    }

    private static String nestedText(int depth) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < depth; i++) text.append('[');
        for (int i = 0; i < depth; i++) text.append(']');
        return text.toString();
    }

    // ---------------------------------------------------------------- backup

    private static void testBackupCodec() {
        section("BackupCodec");
        long t0 = at(2026, 10, 1, 7, 0);
        Diary diary = new Diary();
        diary.person(Diary.ELISA).name = "Ely";
        diary.person(Diary.ELISA).goalKg = 62.0;
        diary.person(Diary.ELISA).baseCustom = true;
        diary.person(Diary.ELISA).baseKg = 70.5;
        diary.person(Diary.ELISA).baseAt = t0 - DAY;
        diary.person(Diary.LUCAS).goalKg = 80.0;
        Entry e1 = Entry.create(Diary.ELISA, t0, 68.5);
        Entry e2 = Entry.create(Diary.LUCAS, t0 + DAY, 81.25);
        Entry e3 = Entry.create(Diary.ELISA, t0 + 2 * DAY, 68.0);
        diary.add(e1);
        diary.add(e2);
        diary.add(e3);
        diary.lastBackupAt = t0 + 3 * DAY;

        String text = BackupCodec.encode(diary, NOW);
        eq(true, text.contains("\"app\": \"peso-a-dois\""), "encode traz o app");
        eq(true, text.contains("\"format\": 1"), "encode traz o formato");
        BackupCodec.Result result = decodeOk(text, NOW);
        eq(0, result.skipped, "round trip sem entradas ignoradas");
        Diary back = result.diary;
        eq(3, back.size(), "round trip: marcações");
        eq("Ely", back.person(Diary.ELISA).name, "round trip: nome");
        near(62.0, back.person(Diary.ELISA).goalKg, "round trip: meta");
        eq(true, back.person(Diary.ELISA).baseCustom, "round trip: base custom");
        near(70.5, back.person(Diary.ELISA).baseKg, "round trip: peso da base");
        eq(t0 - DAY, Long.valueOf(back.person(Diary.ELISA).baseAt), "round trip: data da base");
        eq(true, back.person(Diary.LUCAS).hasGoal(), "round trip: meta de Lucas");
        near(80.0, back.person(Diary.LUCAS).goalKg, "round trip: meta de Lucas valor");
        eq(false, back.person(Diary.LUCAS).baseCustom, "round trip: Lucas sem base custom");
        eq(true, Double.isNaN(back.person(Diary.LUCAS).baseKg), "round trip: sem base = NaN");
        eq(t0 + 3 * DAY, Long.valueOf(back.lastBackupAt), "round trip: lastBackupAt");
        eq(e2.id, back.find(e2.id).id, "round trip: ids preservados");
        near(81.25, back.find(e2.id).kg, "round trip: peso preservado");
        eq(e3.id, back.latest(Diary.ELISA).id, "round trip: ordem preservada");

        eq("Este arquivo não é um backup do Peso a dois.",
                message(() -> BackupCodec.decode("{\"app\":\"outro\",\"entries\":[]}", NOW)), "app errado");
        eq("Este backup foi criado por uma versão mais nova do app.",
                message(() -> BackupCodec.decode("{\"app\":\"peso-a-dois\",\"format\":2,\"entries\":[]}", NOW)),
                "formato 2");
        eq("O arquivo está corrompido ou não é um backup válido.",
                message(() -> BackupCodec.decode("{\"entries\":3}", NOW)), "entries não é lista");
        eq("Este arquivo não é um backup do Peso a dois.",
                message(() -> BackupCodec.decode("[1,2]", NOW)), "raiz não é objeto");
        eq("O arquivo está corrompido ou não é um backup válido.",
                message(() -> BackupCodec.decode("{\"entries\": [", NOW)), "JSON truncado");

        testBackupValidation(t0);
    }

    private static void testBackupValidation(long t0) {
        String bad = "{\"app\":\"peso-a-dois\",\"format\":1,\"entries\":["
                + "{\"id\":\"ok\",\"person\":\"elisa\",\"at\":" + t0 + ",\"kg\":68.5},"
                + "{\"id\":\"p\",\"person\":\"maria\",\"at\":" + t0 + ",\"kg\":60},"
                + "{\"id\":\"y\",\"person\":\"lucas\",\"at\":" + at(1999, 12, 31, 12, 0) + ",\"kg\":80},"
                + "{\"id\":\"f\",\"person\":\"lucas\",\"at\":" + (NOW + 3 * DAY) + ",\"kg\":80},"
                + "{\"id\":\"b1\",\"person\":\"lucas\",\"at\":" + t0 + ",\"kg\":0.5},"
                + "{\"id\":\"b2\",\"person\":\"lucas\",\"at\":" + t0 + ",\"kg\":600},"
                + "{\"id\":\"b3\",\"person\":\"lucas\",\"at\":" + t0 + ",\"kg\":\"abc\"},"
                + "\"nao sou objeto\","
                + "{\"id\":\"ok\",\"person\":\"lucas\",\"at\":" + t0 + ",\"kg\":70},"
                + "{\"person\":\"lucas\",\"at\":" + (t0 + DAY) + ",\"kg\":79.9}"
                + "],\"people\":["
                + "{\"id\":\"elisa\",\"name\":\"   \",\"goalKg\":\"abc\"},"
                + "{\"id\":\"lucas\",\"name\":\"" + repeatText("N", 31) + "\",\"goalKg\":9999,"
                + "\"baseCustom\":true,\"baseKg\":null,\"baseAt\":" + t0 + "}"
                + "],\"lastBackupAt\":" + (NOW + 100 * DAY) + "}";
        BackupCodec.Result result = decodeOk(bad, NOW);
        eq(8, result.skipped, "entradas inválidas e repetidas contadas (ok repetido conta)");
        eq(2, result.diary.size(), "válidas: a original e a sem id");
        eq("Elisa", result.diary.person(Diary.ELISA).name, "nome vazio cai no padrão");
        eq("Lucas", result.diary.person(Diary.LUCAS).name, "nome longo cai no padrão");
        eq(true, Double.isNaN(result.diary.person(Diary.ELISA).goalKg), "meta inválida = sem meta");
        eq(true, Double.isNaN(result.diary.person(Diary.LUCAS).goalKg), "meta fora da faixa = sem meta");
        eq(false, result.diary.person(Diary.LUCAS).baseCustom, "base custom sem peso não vale");
        eq(0L, Long.valueOf(result.diary.lastBackupAt), "lastBackupAt no futuro é descartado");

        String noMeta = "{\"entries\":[{\"person\":\"elisa\",\"at\":" + t0 + ",\"kg\":\"68\"}]}";
        eq(1, decodeOk(noMeta, NOW).skipped, "kg como texto é inválido");
        eq(0, decodeOk("{\"entries\":[]}", NOW).diary.size(), "sem app e sem format é aceito");
    }

    private static BackupCodec.Result decodeOk(String text, long now) {
        try {
            return BackupCodec.decode(text, now);
        } catch (BackupException error) {
            fail("decode", "exceção inesperada: " + error.getMessage());
            return new BackupCodec.Result(new Diary(), 0);
        }
    }

    // ---------------------------------------------------------------- versão 1.0

    private static void testLegacyImport() {
        section("LegacyImport");
        String xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
                + "    <string name=\"weight_elisa_0\">68.5</string>\n"
                + "    <long name=\"date_elisa_0\" value=\"1724351234567\" />\n"
                + "    <int name=\"count_elisa\" value=\"1\" />\n"
                + "    <boolean name=\"base_custom_elisa\" value=\"false\" />\n"
                + "    <float name=\"base_weight_elisa\" value=\"70.0\" />\n"
                + "    <string name=\"nota\">A &amp; B &lt;c&gt; &quot;q&quot; &#233; &#x41; &apos;</string>\n"
                + "    <string name=\"vazio\" />\n"
                + "</map>\n";
        Map<String, Object> map = LegacyImport.parsePrefsXml(xml);
        eq("68.5", map.get("weight_elisa_0"), "xml: string");
        eq(Long.valueOf(1724351234567L), map.get("date_elisa_0"), "xml: long");
        eq(Integer.valueOf(1), map.get("count_elisa"), "xml: int");
        eq(Boolean.FALSE, map.get("base_custom_elisa"), "xml: boolean");
        eq(Float.valueOf(70.0f), map.get("base_weight_elisa"), "xml: float");
        eq("A & B <c> \"q\" \u00e9 A '", map.get("nota"), "xml: entidades");
        eq("", map.get("vazio"), "xml: string vazia autofechada");
        eq(7, map.size(), "xml: quantidade de itens");
        eq(true, LegacyImport.looksLikePrefsXml(xml), "looksLikePrefsXml true");
        eq(false, LegacyImport.looksLikePrefsXml("{\"app\":1}"), "looksLikePrefsXml false");

        Map<String, Object> prefs = new LinkedHashMap<>();
        long t1 = at(2026, 9, 1, 8, 0);
        long t2 = at(2026, 9, 2, 8, 0);
        long t3 = at(2026, 9, 3, 8, 0);
        prefs.put("count_elisa", 3);
        prefs.put("weight_elisa_0", "68.5");
        prefs.put("date_elisa_0", t1);
        prefs.put("weight_elisa_1", "68,7");
        prefs.put("date_elisa_1", Long.valueOf(t2));
        prefs.put("weight_elisa_2", "600");
        prefs.put("date_elisa_2", Long.valueOf(t3));
        prefs.put("count_lucas", 1);
        prefs.put("weight_lucas_0", Double.valueOf(80.0));
        prefs.put("date_lucas_0", Long.valueOf(t3));
        prefs.put("base_custom_lucas", Boolean.TRUE);
        prefs.put("base_weight_lucas", Float.valueOf(85.0f));
        prefs.put("base_date_lucas", Long.valueOf(t1));
        Diary diary = LegacyImport.fromPrefsMap(prefs);
        eq(2, diary.of(Diary.ELISA).size(), "migração: elisa (600 descartado)");
        eq(1, diary.of(Diary.LUCAS).size(), "migração: lucas");
        eq("v1-elisa-0-" + t1, diary.of(Diary.ELISA).get(0).id, "migração: id determinístico");
        near(68.7, diary.of(Diary.ELISA).get(1).kg, "migração: vírgula decimal");
        eq(true, diary.person(Diary.LUCAS).baseCustom, "migração: base custom");
        near(85.0, diary.person(Diary.LUCAS).baseKg, "migração: peso da base");
        eq(t1, Long.valueOf(diary.person(Diary.LUCAS).baseAt), "migração: data da base");
        eq(false, diary.person(Diary.ELISA).baseCustom, "migração: elisa sem base custom");
        eq(true, LegacyImport.fromPrefsMap(new LinkedHashMap<String, Object>()).isEmpty(), "migração vazia");

        Diary again = LegacyImport.fromPrefsMap(prefs);
        Diary merged = diary.copy();
        eq(0, merged.merge(again), "migrar duas vezes não duplica");
    }

    // ---------------------------------------------------------------- adb backup

    private static void testAdbBackup() {
        section("AdbBackupReader");
        byte[] xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>"
                + repeatText("<string name=\"weight_elisa_0\">68.5</string>\n", 40) + "</map>")
                .getBytes(StandardCharsets.UTF_8);
        byte[] tar = tarArchive(
                tarFile("apps/com.pesoadois/", null, 5),
                tarFile("apps/com.pesoadois/", "sp/outro.txt", new byte[]{1, 2, 3}),
                tarFile("apps/com.pesoadois/sp", "peso_a_dois_dados.xml", xml));

        byte[] compressed = adbFile("5", "1", "none", deflate(tar));
        eq(true, Arrays.equals(xml, extractOk(compressed, "/sp/peso_a_dois_dados.xml", 1 << 20)),
                "extrai do .ab comprimido (prefixo ustar + padding)");
        byte[] plain = adbFile("3", "0", "none", tar);
        eq(true, Arrays.equals(xml, extractOk(plain, "/sp/peso_a_dois_dados.xml", 1 << 20)),
                "extrai do .ab sem compressão");
        eq(null, extractOk(adbFile("5", "1", "none", deflate(tarArchive(tarFile("x/", "y.txt", new byte[]{1})))),
                "/sp/peso_a_dois_dados.xml", 1024), "arquivo inexistente devolve null");
        eq("Backup do adb com senha não é suportado. Gere de novo deixando a senha em branco.",
                message(() -> AdbBackupReader.extract(new ByteArrayInputStream(adbFile("5", "1", "AES-256", tar)),
                        "/sp/peso_a_dois_dados.xml", 1024)), "senha AES-256 recusada");
        eq("Arquivo de dados grande demais.",
                message(() -> AdbBackupReader.extract(new ByteArrayInputStream(compressed),
                        "/sp/peso_a_dois_dados.xml", 100)), "limite de tamanho");
        eq("O backup do adb está incompleto ou corrompido.",
                message(() -> AdbBackupReader.extract(new ByteArrayInputStream(
                        Arrays.copyOf(compressed, compressed.length / 2)), "/sp/peso_a_dois_dados.xml", 1 << 20)),
                "backup truncado");
        eq("Este arquivo não é um backup do adb válido.",
                message(() -> AdbBackupReader.extract(new ByteArrayInputStream("texto qualquer".getBytes(StandardCharsets.US_ASCII)),
                        "/sp/peso_a_dois_dados.xml", 1024)), "cabeçalho inválido");
        eq(true, AdbBackupReader.looksLikeAdbBackup(compressed), "looksLikeAdbBackup true");
        eq(false, AdbBackupReader.looksLikeAdbBackup("{\"app\"".getBytes(StandardCharsets.US_ASCII)),
                "looksLikeAdbBackup false");
    }

    private static byte[] extractOk(byte[] file, String suffix, int maxBytes) {
        try {
            return AdbBackupReader.extract(new ByteArrayInputStream(file), suffix, maxBytes);
        } catch (BackupException error) {
            fail("extract", "exceção inesperada: " + error.getMessage());
            return null;
        }
    }

    private static byte[] adbFile(String version, String compression, String encryption, byte[] payload) {
        byte[] header = ("ANDROID BACKUP\n" + version + "\n" + compression + "\n" + encryption + "\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[header.length + payload.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(payload, 0, out, header.length, payload.length);
        return out;
    }

    private static byte[] deflate(byte[] data) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DeflaterOutputStream out = new DeflaterOutputStream(bytes);
            out.write(data);
            out.close();
            return bytes.toByteArray();
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Entrada de tar ustar. type 5 = diretório (sem conteúdo). */
    private static byte[] tarFile(String prefix, String name, Object content) {
        byte[] data;
        byte type;
        if (content instanceof Integer) {
            data = new byte[0];
            type = '5';
        } else {
            data = (byte[]) content;
            type = '0';
        }
        byte[] header = new byte[512];
        String fileName = name == null ? prefix : name;
        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(100, nameBytes.length));
        byte[] size = String.format("%011o", data.length).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(size, 0, header, 124, size.length);
        header[156] = type;
        byte[] magic = "ustar\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        byte[] prefixBytes = (name == null ? new byte[0] : prefix.getBytes(StandardCharsets.UTF_8));
        System.arraycopy(prefixBytes, 0, header, 345, Math.min(155, prefixBytes.length));
        int padded = (data.length + 511) / 512 * 512;
        byte[] block = new byte[512 + padded];
        System.arraycopy(header, 0, block, 0, 512);
        System.arraycopy(data, 0, block, 512, data.length);
        return block;
    }

    private static byte[] tarArchive(byte[]... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] entry : entries) out.write(entry, 0, entry.length);
        out.write(new byte[1024], 0, 1024);
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- CSV

    private static void testCsv() {
        section("CsvExport");
        Diary diary = new Diary();
        diary.person(Diary.ELISA).name = "Eli;sa \"A\"";
        diary.add(new Entry("c1", Diary.ELISA, at(2026, 10, 18, 7, 42), 68.5));
        diary.add(new Entry("c2", Diary.LUCAS, at(2026, 10, 18, 7, 50), 80.0));
        String csv = CsvExport.toCsv(diary);
        eq('\uFEFF', csv.charAt(0), "CSV começa com BOM");
        eq("data;hora;pessoa;peso_kgf\r\n", csv.substring(1, 1 + "data;hora;pessoa;peso_kgf\r\n".length()),
                "CSV cabeçalho");
        eq(true, csv.contains("18/10/2026;07:42;\"Eli;sa \"\"A\"\"\";68,5\r\n"),
                "CSV aspas e ponto e vírgula no nome");
        eq(true, csv.contains("18/10/2026;07:50;Lucas;80,0\r\n"), "CSV vírgula decimal e ordem");
        eq(true, csv.indexOf("07:42") < csv.indexOf("07:50"), "CSV em ordem cronológica");
        eq("data;hora;pessoa;peso_kgf\r\n", CsvExport.toCsv(new Diary()).substring(1),
                "CSV sem marcações tem só cabeçalho");
    }

    // ---------------------------------------------------------------- utilitários de teste

    private static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, SAO_PAULO).toInstant().toEpochMilli();
    }

    private static String repeatText(String text, int times) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < times; i++) out.append(text);
        return out.toString();
    }

    static void section(String name) {
        section = name;
        System.out.println("== " + name);
    }

    static void check(boolean condition, String name) {
        checks++;
        if (!condition) fail(name, "condição falsa");
    }

    static void eq(Object expected, Object actual, String name) {
        checks++;
        if (expected instanceof byte[] && actual instanceof byte[]) {
            if (!Arrays.equals((byte[]) expected, (byte[]) actual)) fail(name, "bytes diferentes");
            return;
        }
        if (!Objects.equals(expected, actual)) {
            fail(name, "esperado <" + expected + "> mas veio <" + actual + ">");
        }
    }

    /** Compara doubles com tolerância; null só casa com null. */
    static void near(Double expected, Double actual, String name) {
        checks++;
        if (expected == null || actual == null) {
            if (expected != actual) fail(name, "esperado <" + expected + "> mas veio <" + actual + ">");
            return;
        }
        if (Math.abs(expected - actual) > 1e-9) {
            fail(name, "esperado <" + expected + "> mas veio <" + actual + ">");
        }
    }

    static void throwsError(Class<? extends Throwable> type, Action action, String name) {
        checks++;
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) return;
            fail(name, "exceção inesperada " + thrown);
            return;
        }
        fail(name, "nenhuma exceção (esperada " + type.getSimpleName() + ")");
    }

    /** Mensagem da exceção lançada pela ação, ou null se não lançou. */
    static String message(Action action) {
        try {
            action.run();
            return null;
        } catch (Exception error) {
            return error.getMessage();
        }
    }

    private static void fail(String name, String detail) {
        failures++;
        System.out.println("FALHA [" + section + "] " + name + ": " + detail);
    }
}
