package com.pesoadois.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Datas em pt-BR, no fuso do aparelho. Instantes são milissegundos desde a época. */
public final class DateText {
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final int MAX_RELATIVE_DAYS = 60;
    private static final int DAYS_PER_MONTH = 30;

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM", PT_BR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", PT_BR);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR);
    private static final DateTimeFormatter WEEKDAY_DAY_MONTH = DateTimeFormatter.ofPattern("EEE, dd/MM", PT_BR);
    private static final DateTimeFormatter WEEKDAY_FULL_DATE = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM/yy", PT_BR);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private DateText() { }

    /** "18/10/2026". */
    public static String shortDate(long at) {
        return SHORT_DATE.format(zoned(at));
    }

    /** "18/10". */
    public static String dayMonth(long at) {
        return DAY_MONTH.format(zoned(at));
    }

    /** "07:42". */
    public static String time(long at) {
        return TIME.format(zoned(at));
    }

    /** "Sábado, 18 de outubro" (primeira letra maiúscula). */
    public static String fullDate(long at) {
        return capitalize(FULL_DATE.format(zoned(at)));
    }

    /**
     * Rótulo de dia para o histórico: "Hoje", "Ontem", ou "Sáb., 11/10" (com ano, "Sáb., 11/10/2025",
     * quando o ano é diferente do atual).
     */
    public static String dayLabel(long at, long now) {
        LocalDate day = day(at);
        LocalDate today = day(now);
        if (day.equals(today)) return "Hoje";
        if (day.equals(today.minusDays(1))) return "Ontem";
        DateTimeFormatter format = day.getYear() == today.getYear() ? WEEKDAY_DAY_MONTH : WEEKDAY_FULL_DATE;
        return capitalize(format.format(day));
    }

    /** "hoje", "ontem", "há 5 dias" (até 60), "há 3 meses". Datas futuras contam como "hoje". */
    public static String relative(long at, long now) {
        long days = ChronoUnit.DAYS.between(day(at), day(now));
        if (days <= 0) return "hoje";
        if (days == 1) return "ontem";
        if (days <= MAX_RELATIVE_DAYS) return "há " + days + " dias";
        long months = days / DAYS_PER_MONTH;
        return "há " + months + " meses";
    }

    /** "mar/26" (sem ponto da abreviação). */
    public static String monthYear(long at) {
        return MONTH_YEAR.format(zoned(at)).replace(".", "");
    }

    /** "2026-10-18", usado em nomes de arquivo. */
    public static String isoDate(long at) {
        return ISO_DATE.format(zoned(at));
    }

    public static LocalDate day(long at) {
        return zoned(at).toLocalDate();
    }

    public static boolean sameDay(long a, long b) {
        return day(a).equals(day(b));
    }

    /**
     * Troca a data (dia/mês/ano locais) de {@code at} mantendo a hora local.
     * {@code month0} é 0-based, como no DatePicker e no Calendar.
     */
    public static long withDate(long at, int year, int month0, int dayOfMonth) {
        LocalTime time = zoned(at).toLocalTime();
        ZonedDateTime moved = LocalDate.of(year, month0 + 1, dayOfMonth).atTime(time).atZone(zone());
        return moved.toInstant().toEpochMilli();
    }

    /** Data de {@code dateSource} com a hora de {@code timeSource} (ambos no fuso local). */
    public static long combine(long dateSource, long timeSource) {
        LocalDate date = day(dateSource);
        LocalTime time = zoned(timeSource).toLocalTime();
        return date.atTime(time).atZone(zone()).toInstant().toEpochMilli();
    }

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private static ZonedDateTime zoned(long at) {
        return Instant.ofEpochMilli(at).atZone(zone());
    }

    private static String capitalize(String text) {
        if (text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(PT_BR) + text.substring(1);
    }
}
