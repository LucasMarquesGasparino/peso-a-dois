package com.pesoadois.core;

import java.util.UUID;

/** Uma marcação de peso. Imutável: alterações criam outra instância com o mesmo id. */
public final class Entry {
    public final String id;
    public final String person;
    public final long at;
    public final double kg;

    public Entry(String id, String person, long at, double kg) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id vazio");
        if (person == null) throw new IllegalArgumentException("pessoa nula");
        this.id = id;
        this.person = person;
        this.at = at;
        this.kg = kg;
    }

    public static Entry create(String person, long at, double kg) {
        return new Entry(UUID.randomUUID().toString(), person, at, kg);
    }

    public Entry with(long newAt, double newKg) {
        return new Entry(id, person, newAt, newKg);
    }

    @Override public String toString() {
        return person + " " + at + " " + kg;
    }
}
