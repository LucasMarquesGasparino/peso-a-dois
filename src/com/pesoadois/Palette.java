package com.pesoadois;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import com.pesoadois.core.Diary;

/** Cores da interface. Claro = cores da versão 1.0; escuro = paleta própria. */
final class Palette {
    final boolean dark;
    final int background;
    final int surface;
    final int border;
    final int ink;
    final int muted;
    final int accentElisa;
    final int tintElisa;
    final int accentLucas;
    final int tintLucas;
    final int gold;
    final int primary;
    final int onPrimary;
    final int field;
    final int error;

    private Palette(boolean dark) {
        this.dark = dark;
        if (dark) {
            background = Color.parseColor("#121614");
            surface = Color.parseColor("#1B221F");
            border = Color.parseColor("#2C3833");
            ink = Color.parseColor("#E7EEEA");
            muted = Color.parseColor("#9DB0A5");
            accentElisa = Color.parseColor("#F08C78");
            tintElisa = Color.parseColor("#3A2622");
            accentLucas = Color.parseColor("#62C290");
            tintLucas = Color.parseColor("#1E3329");
            gold = Color.parseColor("#D9A65E");
            primary = Color.parseColor("#3E9A6E");
            onPrimary = Color.parseColor("#0B1410");
            field = Color.parseColor("#232B27");
            error = Color.parseColor("#FF8A80");
        } else {
            background = Color.parseColor("#F7F6F1");
            surface = Color.WHITE;
            border = Color.parseColor("#DDE7E0");
            ink = Color.parseColor("#1C2D24");
            muted = Color.parseColor("#6C7D73");
            accentElisa = Color.parseColor("#E46F5B");
            tintElisa = Color.parseColor("#FDE8E2");
            accentLucas = Color.parseColor("#2E7D5A");
            tintLucas = Color.parseColor("#DCEFE3");
            gold = Color.parseColor("#B57E39");
            primary = Color.parseColor("#2E7D5A");
            onPrimary = Color.WHITE;
            field = Color.parseColor("#F6F9F6");
            error = Color.parseColor("#B3261E");
        }
    }

    static Palette of(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return new Palette(mode == Configuration.UI_MODE_NIGHT_YES);
    }

    int accent(String person) {
        return Diary.ELISA.equals(person) ? accentElisa : accentLucas;
    }

    int tint(String person) {
        return Diary.ELISA.equals(person) ? tintElisa : tintLucas;
    }
}
