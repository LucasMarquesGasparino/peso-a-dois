package com.pesoadois.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON mínimo (RFC 8259) para o backup. Objetos viram LinkedHashMap, arrays ArrayList,
 * inteiros Long, demais números Double, null vira null.
 */
public final class Json {
    private static final int MAX_DEPTH = 64;

    private Json() { }

    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("Texto nulo");
        Parser parser = new Parser(text);
        if (text.length() > 0 && text.charAt(0) == '﻿') parser.pos = 1;
        parser.skipWhitespace();
        Object value = parser.value(0);
        parser.skipWhitespace();
        if (parser.pos != text.length()) throw parser.error("texto sobrando depois do valor");
        return value;
    }

    /** Escreve com indentação de 2 espaços. Aceita Map, List, String, Boolean, Number e null. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value, int level) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(((Boolean) value).booleanValue() ? "true" : "false");
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new IllegalArgumentException("Número não finito não existe em JSON");
            }
            out.append(Double.toString(number));
        } else if (value instanceof Number) {
            out.append(((Number) value).longValue());
        } else if (value instanceof Map) {
            writeObject(out, (Map<?, ?>) value, level);
        } else if (value instanceof List) {
            writeArray(out, (List<?>) value, level);
        } else {
            throw new IllegalArgumentException("Tipo não suportado: " + value.getClass().getName());
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map, int level) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) out.append(",\n");
            first = false;
            indent(out, level + 1);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(": ");
            writeValue(out, entry.getValue(), level + 1);
        }
        out.append('\n');
        indent(out, level);
        out.append('}');
    }

    private static void writeArray(StringBuilder out, List<?> list, int level) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        boolean first = true;
        for (Object item : list) {
            if (!first) out.append(",\n");
            first = false;
            indent(out, level + 1);
            writeValue(out, item, level + 1);
        }
        out.append('\n');
        indent(out, level);
        out.append(']');
    }

    private static void indent(StringBuilder out, int level) {
        for (int i = 0; i < level; i++) out.append("  ");
    }

    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        int pos;

        Parser(String text) {
            this.text = text;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("JSON inválido na posição " + pos + ": " + message);
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object value(int depth) {
            if (pos >= text.length()) throw error("fim inesperado");
            char c = text.charAt(pos);
            switch (c) {
                case '{': return object(depth + 1);
                case '[': return array(depth + 1);
                case '"': return string();
                case 't': return literal("true", Boolean.TRUE);
                case 'f': return literal("false", Boolean.FALSE);
                case 'n': return literal("null", null);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw error("caractere inesperado '" + c + "'");
            }
        }

        private Map<String, Object> object(int depth) {
            if (depth > MAX_DEPTH) throw error("aninhamento muito profundo");
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != '"') throw error("nome de campo esperado");
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, value(depth));
                skipWhitespace();
                if (pos >= text.length()) throw error("objeto não terminado");
                char c = text.charAt(pos++);
                if (c == '}') return map;
                if (c != ',') throw error("',' ou '}' esperado");
            }
        }

        private List<Object> array(int depth) {
            if (depth > MAX_DEPTH) throw error("aninhamento muito profundo");
            pos++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(value(depth));
                skipWhitespace();
                if (pos >= text.length()) throw error("array não terminado");
                char c = text.charAt(pos++);
                if (c == ']') return list;
                if (c != ',') throw error("',' ou ']' esperado");
            }
        }

        private void expect(char expected) {
            if (pos >= text.length() || text.charAt(pos) != expected) {
                throw error("'" + expected + "' esperado");
            }
            pos++;
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) throw error("literal inválido");
            pos += word.length();
            return value;
        }

        private Object number() {
            int start = pos;
            if (text.charAt(pos) == '-') pos++;
            if (pos >= text.length()) throw error("número incompleto");
            char first = text.charAt(pos);
            if (first == '0') {
                pos++;
            } else if (first >= '1' && first <= '9') {
                while (pos < text.length() && isDigit(text.charAt(pos))) pos++;
            } else {
                throw error("número inválido");
            }
            boolean integral = true;
            if (pos < text.length() && text.charAt(pos) == '.') {
                integral = false;
                pos++;
                if (pos >= text.length() || !isDigit(text.charAt(pos))) throw error("dígito após ponto esperado");
                while (pos < text.length() && isDigit(text.charAt(pos))) pos++;
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos++;
                if (pos >= text.length() || !isDigit(text.charAt(pos))) throw error("expoente inválido");
                while (pos < text.length() && isDigit(text.charAt(pos))) pos++;
            }
            String token = text.substring(start, pos);
            if (integral) {
                try {
                    return Long.parseLong(token);
                } catch (NumberFormatException tooBig) {
                    // Inteiro fora do alcance de long: cai para double abaixo.
                }
            }
            double value = Double.parseDouble(token);
            if (Double.isInfinite(value) || Double.isNaN(value)) throw error("número fora do intervalo");
            return value;
        }

        private String string() {
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= text.length()) throw error("texto não terminado");
                char c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw error("caractere de controle dentro de texto");
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) throw error("escape incompleto");
                char escape = text.charAt(pos++);
                switch (escape) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (pos + 4 > text.length()) throw error("escape \\u incompleto");
                        String hex = text.substring(pos, pos + 4);
                        for (int i = 0; i < 4; i++) {
                            if (Character.digit(hex.charAt(i), 16) < 0) throw error("escape \\u inválido");
                        }
                        out.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default:
                        throw error("escape inválido");
                }
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
