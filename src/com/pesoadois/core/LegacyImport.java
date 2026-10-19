package com.pesoadois.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Importa os dados da versão 1.0 (SharedPreferences "peso_a_dois_dados"). */
public final class LegacyImport {
    private static final int MAX_COUNT = 100_000;

    /**
     * Um elemento do XML de preferências: tipado (grupos 1-3) ou string (grupos 4-5).
     * Strings vazias aparecem como {@code <string name="x" />}.
     */
    private static final Pattern ITEM = Pattern.compile(
            "<(int|long|float|boolean)\\s+name=\"([^\"]*)\"\\s+value=\"([^\"]*)\"\\s*/>"
                    + "|<string\\s+name=\"([^\"]*)\"\\s*(?:/>|>(.*?)</string>)",
            Pattern.DOTALL);

    private LegacyImport() { }

    /** Lê o XML de SharedPreferences do Android. Itens malformados são ignorados. */
    public static Map<String, Object> parsePrefsXml(String xml) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (xml == null) return map;
        Matcher matcher = ITEM.matcher(xml);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                String name = unescape(matcher.group(2));
                String value = unescape(matcher.group(3));
                Object typed = typedValue(matcher.group(1), value);
                if (typed != null) map.put(name, typed);
            } else {
                String name = unescape(matcher.group(4));
                String text = matcher.group(5) == null ? "" : unescape(matcher.group(5));
                map.put(name, text);
            }
        }
        return map;
    }

    /** Monta o Diary a partir do mapa de SharedPreferences da versão 1.0. Pode ficar vazio. */
    public static Diary fromPrefsMap(Map<String, ?> map) {
        Diary diary = new Diary();
        for (String person : Diary.PEOPLE) {
            long count = longValue(map.get("count_" + person));
            if (count > MAX_COUNT) count = MAX_COUNT;
            for (int i = 0; i < count; i++) {
                Double kg = importWeight(map.get("weight_" + person + "_" + i));
                long at = longValue(map.get("date_" + person + "_" + i));
                if (kg == null || at <= 0) continue;
                String id = "v1-" + person + "-" + i + "-" + at;
                diary.add(new Entry(id, person, at, kg));
            }

            PersonInfo info = diary.person(person);
            Double baseKg = importWeight(map.get("base_weight_" + person));
            long baseAt = longValue(map.get("base_date_" + person));
            boolean custom = Boolean.TRUE.equals(map.get("base_custom_" + person));
            if (baseKg != null) info.baseKg = baseKg;
            info.baseAt = baseAt;
            info.baseCustom = custom && baseKg != null && baseAt > 0;
        }
        return diary;
    }

    /** Indica se o texto parece um XML de SharedPreferences (e não um JSON). */
    public static boolean looksLikePrefsXml(String text) {
        return text != null && text.contains("<map");
    }

    private static Object typedValue(String type, String value) {
        try {
            if ("int".equals(type)) return Integer.valueOf(value.trim());
            if ("long".equals(type)) return Long.valueOf(value.trim());
            if ("float".equals(type)) return Float.valueOf(value.trim());
            if ("boolean".equals(type)) return Boolean.valueOf(value.trim());
        } catch (NumberFormatException error) {
            return null;
        }
        return null;
    }

    /** Peso antigo: String com ponto ou vírgula, ou número. Null se fora da faixa de importação. */
    private static Double importWeight(Object value) {
        double kg;
        if (value instanceof Number) {
            kg = ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                kg = Double.parseDouble(((String) value).trim().replace(',', '.'));
            } catch (NumberFormatException error) {
                return null;
            }
        } else {
            return null;
        }
        if (Double.isNaN(kg) || Double.isInfinite(kg)) return null;
        if (kg < WeightFormat.IMPORT_MIN_KG || kg > WeightFormat.IMPORT_MAX_KG) return null;
        return WeightFormat.round2(kg);
    }

    private static long longValue(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException error) {
                return 0;
            }
        }
        return 0;
    }

    /** Desfaz as entidades XML que o Android grava nas preferências. */
    static String unescape(String text) {
        if (text.indexOf('&') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int semicolon = c == '&' ? text.indexOf(';', i) : -1;
            if (semicolon < 0 || semicolon - i > 10) {
                out.append(c);
                i++;
                continue;
            }
            String entity = text.substring(i + 1, semicolon);
            String decoded = decodeEntity(entity);
            if (decoded == null) {
                out.append(c);
                i++;
            } else {
                out.append(decoded);
                i = semicolon + 1;
            }
        }
        return out.toString();
    }

    private static String decodeEntity(String entity) {
        switch (entity) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            default:
                if (entity.length() > 1 && entity.charAt(0) == '#') {
                    try {
                        int code = entity.charAt(1) == 'x' || entity.charAt(1) == 'X'
                                ? Integer.parseInt(entity.substring(2), 16)
                                : Integer.parseInt(entity.substring(1));
                        return new String(Character.toChars(code));
                    } catch (IllegalArgumentException error) {
                        return null;
                    }
                }
                return null;
        }
    }
}
