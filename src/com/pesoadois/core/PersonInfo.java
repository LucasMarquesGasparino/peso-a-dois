package com.pesoadois.core;

/** Dados de uma pessoa: nome, meta e base de comparação. NaN em goalKg/baseKg = não definido. */
public final class PersonInfo {
    public final String id;
    public String name;
    public double goalKg = Double.NaN;
    public boolean baseCustom;
    public double baseKg = Double.NaN;
    public long baseAt;

    public PersonInfo(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public PersonInfo copy() {
        PersonInfo other = new PersonInfo(id, name);
        other.goalKg = goalKg;
        other.baseCustom = baseCustom;
        other.baseKg = baseKg;
        other.baseAt = baseAt;
        return other;
    }

    public boolean hasGoal() {
        return !Double.isNaN(goalKg);
    }
}
