package com.pesoadois.core;

/** Matemática do gráfico: marcas "bonitas" para o eixo e transformação em percentual. */
public final class ChartMath {
    private static final double[] NICE = {1, 2, 2.5, 5, 10};

    private ChartMath() { }

    /** Passo "bonito" (1, 2, 2,5 ou 5 vezes 10^k) que não fica abaixo de {@code rawStep}. */
    public static double niceStep(double rawStep) {
        if (!(rawStep > 0) || Double.isInfinite(rawStep)) return 1;
        double exponent = Math.floor(Math.log10(rawStep));
        double scale = Math.pow(10, exponent);
        double fraction = rawStep / scale;
        for (double nice : NICE) {
            if (fraction <= nice + 1e-9) return nice * scale;
        }
        return 10 * scale;
    }

    /**
     * Marcas do eixo cobrindo [min, max], em passos bonitos, com cerca de {@code target}
     * intervalos. Ex.: (66,3; 71,8; 4) gera {66, 68, 70, 72}.
     */
    public static double[] niceTicks(double min, double max, int target) {
        double low = min;
        double high = max;
        if (high - low < 1e-9) {
            low -= 1;
            high += 1;
        }
        int intervals = Math.max(1, target);
        double step = niceStep((high - low) / intervals);
        long first = (long) Math.floor(low / step + 1e-9);
        long last = (long) Math.ceil(high / step - 1e-9);
        if (last <= first) last = first + 1;
        double[] ticks = new double[(int) (last - first + 1)];
        for (int i = 0; i < ticks.length; i++) {
            double value = (first + i) * step;
            ticks[i] = Math.round(value * 1e6) / 1e6;
        }
        return ticks;
    }

    /** Variação percentual de {@code kg} em relação a {@code baseKg}. */
    public static double toPercent(double kg, double baseKg) {
        return (kg - baseKg) / baseKg * 100.0;
    }
}
