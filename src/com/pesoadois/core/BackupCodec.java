package com.pesoadois.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Formato 1 do backup (JSON). O mesmo formato vale para o arquivo interno do app e para o
 * backup exportado. Na leitura, entradas inválidas são ignoradas e contadas.
 */
public final class BackupCodec {
    public static final String APP = "peso-a-dois";
    public static final int FORMAT = 1;

    /** 2000-01-01T00:00:00 UTC: data mais antiga aceita. */
    private static final long MIN_AT = 946_684_800_000L;
    /** Tolerância para relógio adiantado. */
    private static final long FUTURE_SLACK_MS = 2 * 86_400_000L;
    private static final int MAX_NAME_LENGTH = 30;

    private static final String CORRUPT = "O arquivo está corrompido ou não é um backup válido.";
    private static final String NOT_BACKUP = "Este arquivo não é um backup do Peso a dois.";
    private static final String NEWER = "Este backup foi criado por uma versão mais nova do app.";

    /** Diário lido e quantas entradas foram ignoradas. */
    public static final class Result {
        public final Diary diary;
        public final int skipped;

        public Result(Diary diary, int skipped) {
            this.diary = diary;
            this.skipped = skipped;
        }
    }

    private BackupCodec() { }

    public static String encode(Diary diary, long savedAt) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("app", APP);
        root.put("format", (long) FORMAT);
        root.put("savedAt", savedAt);
        root.put("lastBackupAt", diary.lastBackupAt);

        List<Object> people = new ArrayList<>();
        for (String id : Diary.PEOPLE) {
            PersonInfo info = diary.person(id);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", info.id);
            item.put("name", info.name);
            item.put("goalKg", nullIfNaN(info.goalKg));
            item.put("baseCustom", info.baseCustom);
            item.put("baseKg", nullIfNaN(info.baseKg));
            item.put("baseAt", info.baseAt);
            people.add(item);
        }
        root.put("people", people);

        List<Object> entries = new ArrayList<>();
        for (Entry entry : diary.all()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.id);
            item.put("person", entry.person);
            item.put("at", entry.at);
            item.put("kg", entry.kg);
            entries.add(item);
        }
        root.put("entries", entries);
        return Json.write(root);
    }

    /**
     * Lê um backup. {@code now} define o limite de datas futuras aceitas.
     *
     * @throws BackupException com mensagem para o usuário se o arquivo não puder ser usado
     */
    public static Result decode(String text, long now) throws BackupException {
        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (IllegalArgumentException error) {
            throw new BackupException(CORRUPT);
        }
        if (!(parsed instanceof Map)) throw new BackupException(NOT_BACKUP);
        Map<?, ?> root = (Map<?, ?>) parsed;

        Object app = root.get("app");
        if (app != null && !APP.equals(app)) throw new BackupException(NOT_BACKUP);

        Object format = root.get("format");
        if (format != null) {
            if (!(format instanceof Number)) throw new BackupException(CORRUPT);
            if (((Number) format).doubleValue() > FORMAT) throw new BackupException(NEWER);
        }

        Object entriesValue = root.get("entries");
        if (!(entriesValue instanceof List)) throw new BackupException(CORRUPT);

        Diary diary = new Diary();
        readPeople(root.get("people"), diary, now);
        diary.lastBackupAt = readLastBackup(root.get("lastBackupAt"), now);

        Set<String> ids = new HashSet<>();
        int skipped = 0;
        for (Object item : (List<?>) entriesValue) {
            Entry entry = readEntry(item, now, ids);
            if (entry == null) skipped++;
            else diary.add(entry);
        }
        return new Result(diary, skipped);
    }

    private static void readPeople(Object value, Diary diary, long now) {
        if (!(value instanceof List)) return;
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) item;
            Object id = map.get("id");
            if (!(id instanceof String) || !Diary.PEOPLE.contains(id)) continue;
            PersonInfo info = diary.person((String) id);

            Object name = map.get("name");
            if (name instanceof String) {
                String trimmed = ((String) name).trim();
                if (!trimmed.isEmpty() && trimmed.length() <= MAX_NAME_LENGTH) info.name = trimmed;
            }
            Double goal = importWeight(map.get("goalKg"));
            info.goalKg = goal == null ? Double.NaN : goal;

            Double baseKg = importWeight(map.get("baseKg"));
            long baseAt = readTimestamp(map.get("baseAt"), now);
            boolean custom = Boolean.TRUE.equals(map.get("baseCustom"));
            info.baseKg = baseKg == null ? Double.NaN : baseKg;
            info.baseAt = baseAt;
            // Base personalizada só vale com peso e data válidos; senão volta à primeira marcação.
            info.baseCustom = custom && baseKg != null && baseAt > 0;
        }
    }

    private static long readLastBackup(Object value, long now) {
        if (!(value instanceof Number)) return 0;
        long at = ((Number) value).longValue();
        return at >= 0 && at <= now + FUTURE_SLACK_MS ? at : 0;
    }

    /** Retorna a marcação, ou null se a entrada for inválida ou repetida. */
    private static Entry readEntry(Object item, long now, Set<String> ids) {
        if (!(item instanceof Map)) return null;
        Map<?, ?> map = (Map<?, ?>) item;
        Object person = map.get("person");
        if (!(person instanceof String) || !Diary.PEOPLE.contains(person)) return null;
        long at = readTimestamp(map.get("at"), now);
        if (at < MIN_AT || at > now + FUTURE_SLACK_MS) return null;
        Double kg = importWeight(map.get("kg"));
        if (kg == null) return null;
        Object idValue = map.get("id");
        String id = idValue instanceof String && !((String) idValue).isEmpty()
                ? (String) idValue
                : UUID.randomUUID().toString();
        if (!ids.add(id)) return null;
        return new Entry(id, (String) person, at, kg);
    }

    /** Timestamp em ms ou 0 se ausente/inválido. */
    private static long readTimestamp(Object value, long now) {
        if (!(value instanceof Number)) return 0;
        long at = ((Number) value).longValue();
        return at >= MIN_AT && at <= now + FUTURE_SLACK_MS ? at : 0;
    }

    /** Peso de dados importados (faixa ampla), arredondado; null se inválido. */
    private static Double importWeight(Object value) {
        if (!(value instanceof Number)) return null;
        double kg = ((Number) value).doubleValue();
        if (Double.isNaN(kg) || Double.isInfinite(kg)) return null;
        if (kg < WeightFormat.IMPORT_MIN_KG || kg > WeightFormat.IMPORT_MAX_KG) return null;
        return WeightFormat.round2(kg);
    }

    private static Double nullIfNaN(double value) {
        return Double.isNaN(value) ? null : value;
    }
}
