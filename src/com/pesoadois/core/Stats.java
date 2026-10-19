package com.pesoadois.core;

import java.util.List;

/** Variações, metas e comparações. Métodos que podem não ter dado retornam null. */
public final class Stats {
    private static final long DAY_MS = 86_400_000L;
    /** Diferença abaixo disto conta como "meta alcançada" quando base == meta. */
    private static final double GOAL_TOLERANCE_KG = 0.05;

    /** Situação de uma meta. progress vai de 0 a 1. */
    public static final class GoalStatus {
        public final double remainingKg;
        public final boolean reached;
        public final double progress;

        public GoalStatus(double remainingKg, boolean reached, double progress) {
            this.remainingKg = remainingKg;
            this.reached = reached;
            this.progress = progress;
        }
    }

    private Stats() { }

    /** Última marcação menos a base (kg). Null sem marcações ou sem base. */
    public static Double changeSinceBase(Diary diary, String person) {
        Entry latest = diary.latest(person);
        Diary.Base base = diary.base(person);
        if (latest == null || base == null) return null;
        return latest.kg - base.kg;
    }

    /** Variação percentual desde a base. Null sem dados ou se a base for zero. */
    public static Double percentSinceBase(Diary diary, String person) {
        Entry latest = diary.latest(person);
        Diary.Base base = diary.base(person);
        if (latest == null || base == null || base.kg == 0) return null;
        return (latest.kg - base.kg) / base.kg * 100.0;
    }

    /**
     * Variação da última marcação em relação à última marcação com pelo menos {@code days}
     * dias de antecedência. {@code asc} deve estar em ordem crescente. Null se não houver.
     */
    public static Double changeOverDays(List<Entry> asc, int days) {
        if (asc.isEmpty()) return null;
        Entry last = asc.get(asc.size() - 1);
        long cutoff = last.at - days * DAY_MS;
        Entry reference = null;
        for (Entry entry : asc) {
            if (entry.at <= cutoff) reference = entry;
        }
        if (reference == null) return null;
        return last.kg - reference.kg;
    }

    /**
     * Situação da meta. Perder peso: base &gt; meta; ganhar: base &lt; meta.
     * Alcançada quando o peso atual cruza a meta (ou, com base == meta, fica a menos de 50 g).
     */
    public static GoalStatus goal(double baseKg, double currentKg, double goalKg) {
        double remaining = Math.abs(currentKg - goalKg);
        boolean reached = (goalKg < baseKg && currentKg <= goalKg)
                || (goalKg > baseKg && currentKg >= goalKg)
                || (goalKg == baseKg && remaining < GOAL_TOLERANCE_KG);
        double progress;
        if (reached) {
            progress = 1.0;
        } else if (baseKg == goalKg) {
            progress = 0.0;
        } else {
            progress = (baseKg - currentKg) / (baseKg - goalKg);
            progress = Math.max(0.0, Math.min(1.0, progress));
        }
        return new GoalStatus(remaining, reached, progress);
    }
}
