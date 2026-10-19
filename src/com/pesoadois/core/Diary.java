package com.pesoadois.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo em memória do diário: marcações ordenadas por data e dados das duas pessoas.
 * Não é thread-safe: a UI copia ({@link #copy()}), altera a cópia e só então grava.
 */
public final class Diary {
    public static final String ELISA = "elisa";
    public static final String LUCAS = "lucas";
    public static final List<String> PEOPLE =
            Collections.unmodifiableList(Arrays.asList(ELISA, LUCAS));

    /** Duas marcações do mesmo momento (até 60 s) com o mesmo peso são a mesma pesagem. */
    private static final long DUPLICATE_WINDOW_MS = 60_000L;
    private static final double DUPLICATE_KG = 0.005;

    private static final Comparator<Entry> ORDER = (a, b) -> {
        if (a.at != b.at) return Long.compare(a.at, b.at);
        return a.id.compareTo(b.id);
    };

    /** Base de comparação efetiva de uma pessoa. */
    public static final class Base {
        public final double kg;
        public final long at;
        public final boolean custom;

        public Base(double kg, long at, boolean custom) {
            this.kg = kg;
            this.at = at;
            this.custom = custom;
        }
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private final Map<String, PersonInfo> people = new LinkedHashMap<>();
    /** Quando o usuário exportou o backup pela última vez (0 = nunca). */
    public long lastBackupAt;

    public Diary() {
        people.put(ELISA, new PersonInfo(ELISA, "Elisa"));
        people.put(LUCAS, new PersonInfo(LUCAS, "Lucas"));
    }

    public PersonInfo person(String id) {
        PersonInfo info = people.get(id);
        if (info == null) throw new IllegalArgumentException("Pessoa desconhecida: " + id);
        return info;
    }

    /** Todas as marcações, em ordem crescente de data. Lista somente leitura. */
    public List<Entry> all() {
        return Collections.unmodifiableList(entries);
    }

    /** Marcações de uma pessoa, em ordem crescente (cópia). */
    public List<Entry> of(String person) {
        ArrayList<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.person.equals(person)) result.add(entry);
        }
        return result;
    }

    /** Marcação mais recente de uma pessoa, ou null. */
    public Entry latest(String person) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).person.equals(person)) return entries.get(i);
        }
        return null;
    }

    public Entry find(String id) {
        int index = indexOf(id);
        return index < 0 ? null : entries.get(index);
    }

    /** Insere uma marcação mantendo a ordem. A pessoa precisa ser conhecida. */
    public void add(Entry entry) {
        person(entry.person);
        insertSorted(entry);
    }

    /** Troca data e peso de uma marcação (mesmo id). Retorna false se o id não existe. */
    public boolean update(String id, long at, double kg) {
        int index = indexOf(id);
        if (index < 0) return false;
        Entry old = entries.remove(index);
        insertSorted(old.with(at, kg));
        return true;
    }

    public boolean remove(String id) {
        int index = indexOf(id);
        if (index < 0) return false;
        entries.remove(index);
        return true;
    }

    /** Marcações da pessoa no mesmo dia (calendário local) de {@code at}. */
    public List<Entry> sameDay(String person, long at) {
        ArrayList<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.person.equals(person) && DateText.sameDay(entry.at, at)) result.add(entry);
        }
        return result;
    }

    /** Remove as marcações da pessoa no mesmo dia de {@code at}. Retorna quantas saíram. */
    public int removeSameDay(String person, long at) {
        int removed = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.person.equals(person) && DateText.sameDay(entry.at, at)) {
                entries.remove(i);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Base de comparação: a personalizada, se houver (com peso válido); senão a primeira
     * marcação da pessoa. Null se não há nada para comparar.
     */
    public Base base(String person) {
        PersonInfo info = person(person);
        if (info.baseCustom && !Double.isNaN(info.baseKg)) {
            return new Base(info.baseKg, info.baseAt, true);
        }
        for (Entry entry : entries) {
            if (entry.person.equals(person)) return new Base(entry.kg, entry.at, false);
        }
        return null;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Cópia profunda: alterar a cópia não altera este diário. */
    public Diary copy() {
        Diary other = new Diary();
        other.entries.addAll(entries);
        for (Map.Entry<String, PersonInfo> item : people.entrySet()) {
            other.people.put(item.getKey(), item.getValue().copy());
        }
        other.lastBackupAt = lastBackupAt;
        return other;
    }

    /**
     * Acrescenta as marcações de {@code other} que ainda não existem aqui: id novo e não é
     * duplicata (mesma pessoa, até 60 s de diferença e peso igual). Não altera pessoas nem metas.
     * Retorna quantas entraram.
     */
    public int merge(Diary other) {
        int added = 0;
        for (Entry candidate : other.entries) {
            if (indexOf(candidate.id) >= 0 || isDuplicate(candidate)) continue;
            insertSorted(candidate);
            added++;
        }
        return added;
    }

    private boolean isDuplicate(Entry candidate) {
        for (Entry entry : entries) {
            if (entry.person.equals(candidate.person)
                    && Math.abs(entry.at - candidate.at) <= DUPLICATE_WINDOW_MS
                    && Math.abs(entry.kg - candidate.kg) < DUPLICATE_KG) {
                return true;
            }
        }
        return false;
    }

    private int indexOf(String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private void insertSorted(Entry entry) {
        int index = entries.size();
        while (index > 0 && ORDER.compare(entries.get(index - 1), entry) > 0) index--;
        entries.add(index, entry);
    }
}
