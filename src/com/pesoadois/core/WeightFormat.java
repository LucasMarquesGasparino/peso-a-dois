package com.pesoadois.core;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

/** Leitura e escrita de pesos em pt-BR (vírgula decimal). */
public final class WeightFormat {
    /** Faixa aceita na digitação. */
    public static final double MIN_KG = 20;
    public static final double MAX_KG = 400;
    /** Faixa aceita em dados importados (mais ampla, para não descartar histórico antigo). */
    public static final double IMPORT_MIN_KG = 1;
    public static final double IMPORT_MAX_KG = 500;

    private static final Pattern NUMBER = Pattern.compile("^[0-9]{1,3}([.][0-9]{1,3})?$");
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final char MINUS = '−';

    private WeightFormat() { }

    /**
     * Lê um peso digitado. Aceita "68,5", "68.5", " 68,50 kgf", "68,5 kg". Retorna null se
     * vazio, inválido ou fora de [MIN_KG, MAX_KG]. O resultado tem no máximo 2 casas.
     */
    public static Double parse(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (endsWithIgnoreCase(text, "kgf")) {
            text = text.substring(0, text.length() - 3);
        } else if (endsWithIgnoreCase(text, "kg")) {
            text = text.substring(0, text.length() - 2);
        }
        text = text.replaceAll("\\s+", "").replace(',', '.');
        if (text.isEmpty() || !NUMBER.matcher(text).matches()) return null;
        double value = Double.parseDouble(text);
        if (value < MIN_KG || value > MAX_KG) return null;
        return round2(value);
    }

    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /** "68,5", "68,55", "70,0". */
    public static String format(double kg) {
        return new DecimalFormat("0.0#", symbols()).format(normalizeZero(round2(kg)));
    }

    /** Variação com sinal e 1 casa: "+1,2", "−0,8" (sinal de menos U+2212), "0,0". */
    public static String formatSigned(double change) {
        long tenths = Math.round(Math.abs(change) * 10);
        if (tenths == 0) return "0,0";
        String sign = change < 0 ? String.valueOf(MINUS) : "+";
        return sign + (tenths / 10) + "," + (tenths % 10);
    }

    /** Variação percentual: "+1,5%", "−4,2%". */
    public static String formatPercent(double percent) {
        return formatSigned(percent) + "%";
    }

    /** Número para JSON/arquivos: "68.5", Locale.ROOT, até 2 casas. */
    public static String toMachine(double kg) {
        if (Double.isNaN(kg) || Double.isInfinite(kg)) {
            throw new IllegalArgumentException("Valor não finito");
        }
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(normalizeZero(round2(kg)));
    }

    /** Arredonda para 2 casas decimais. */
    public static double round2(double value) {
        return roundTo(value, 100);
    }

    private static double roundTo(double value, int scale) {
        return Math.round(value * scale) / (double) scale;
    }

    private static double normalizeZero(double value) {
        return value == 0 ? 0.0 : value;
    }

    private static DecimalFormatSymbols symbols() {
        return new DecimalFormatSymbols(PT_BR);
    }

    private static boolean endsWithIgnoreCase(String text, String suffix) {
        int start = text.length() - suffix.length();
        return start >= 0 && text.regionMatches(true, start, suffix, 0, suffix.length());
    }
}
