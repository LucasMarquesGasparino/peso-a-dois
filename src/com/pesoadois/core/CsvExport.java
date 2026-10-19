package com.pesoadois.core;

/** Planilha CSV (separador ponto e vírgula, decimal com vírgula) para abrir no Excel/Planilhas. */
public final class CsvExport {
    /** BOM UTF-8 para o Excel reconhecer os acentos. */
    private static final char BOM = '﻿';

    private CsvExport() { }

    public static String toCsv(Diary diary) {
        StringBuilder out = new StringBuilder();
        out.append(BOM).append("data;hora;pessoa;peso_kgf\r\n");
        for (Entry entry : diary.all()) {
            out.append(field(DateText.shortDate(entry.at))).append(';')
                    .append(field(DateText.time(entry.at))).append(';')
                    .append(field(diary.person(entry.person).name)).append(';')
                    .append(field(WeightFormat.format(entry.kg)))
                    .append("\r\n");
        }
        return out.toString();
    }

    /** Campo entre aspas quando tem ;, aspas ou quebra de linha; aspas são dobradas. */
    static String field(String value) {
        boolean quote = value.indexOf(';') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!quote) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
