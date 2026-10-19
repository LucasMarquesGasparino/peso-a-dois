import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/** Testes do núcleo puro. Rodar com: sh tests/run.sh */
public final class CoreTests {
    private static int checks;
    private static int failures;
    private static String section = "";

    /** Bloco que pode lançar exceção checada. */
    interface Action {
        void run() throws Exception;
    }

    private CoreTests() { }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        Locale.setDefault(new Locale("pt", "BR"));

        section("ambiente");
        check(TimeZone.getDefault().getID().equals("America/Sao_Paulo"), "fuso de teste");
        eq("68,5", String.format(Locale.getDefault(), "%.1f", 68.5).replace('.', ','), "locale pt-BR");

        System.out.println(checks + " verificações, " + failures + " falhas.");
        if (failures > 0) System.exit(1);
    }

    static void section(String name) {
        section = name;
        System.out.println("== " + name);
    }

    static void check(boolean condition, String name) {
        checks++;
        if (!condition) fail(name, "condição falsa");
    }

    static void eq(Object expected, Object actual, String name) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            fail(name, "esperado <" + expected + "> mas veio <" + actual + ">");
        }
    }

    /** Compara doubles com tolerância; null esperado/recebido só casa com null. */
    static void near(Double expected, Double actual, String name) {
        checks++;
        if (expected == null || actual == null) {
            if (expected != actual) fail(name, "esperado <" + expected + "> mas veio <" + actual + ">");
            return;
        }
        if (Math.abs(expected - actual) > 1e-9) {
            fail(name, "esperado <" + expected + "> mas veio <" + actual + ">");
        }
    }

    static void throwsError(Class<? extends Throwable> type, Action action, String name) {
        checks++;
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) return;
            fail(name, "exceção inesperada " + thrown);
            return;
        }
        fail(name, "nenhuma exceção (esperada " + type.getSimpleName() + ")");
    }

    private static void fail(String name, String detail) {
        failures++;
        System.out.println("FALHA [" + section + "] " + name + ": " + detail);
    }
}
