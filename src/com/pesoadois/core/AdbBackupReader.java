package com.pesoadois.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;

/**
 * Lê um arquivo gerado por {@code adb backup} (formato "ANDROID BACKUP", versões 1 a 5 e
 * afins): cabeçalho de 4 linhas, payload zlib opcional e um tar ustar dentro.
 * Extrai só o arquivo pedido, sem gravar nada em disco.
 */
public final class AdbBackupReader {
    private static final String MAGIC = "ANDROID BACKUP";
    private static final byte[] MAGIC_LINE = (MAGIC + "\n").getBytes(StandardCharsets.US_ASCII);
    private static final int BLOCK = 512;
    private static final int MAX_HEADER_LINE = 32;
    /** Limite do total descompactado, para não ler um arquivo malicioso sem fim. */
    private static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    private static final String NOT_ADB = "Este arquivo não é um backup do adb válido.";
    private static final String TRUNCATED = "O backup do adb está incompleto ou corrompido.";
    private static final String ENCRYPTED =
            "Backup do adb com senha não é suportado. Gere de novo deixando a senha em branco.";

    private AdbBackupReader() { }

    /**
     * Retorna o conteúdo do primeiro arquivo cujo nome termina com {@code nameSuffix}, ou null
     * se o backup não contém esse arquivo.
     *
     * @throws BackupException se o cabeçalho for inválido, o backup tiver senha, o arquivo
     *         passar de {@code maxBytes} ou o backup estiver truncado
     */
    public static byte[] extract(InputStream in, String nameSuffix, int maxBytes) throws BackupException {
        try {
            if (!MAGIC.equals(readHeaderLine(in))) throw new BackupException(NOT_ADB);
            int version = parseSmallInt(readHeaderLine(in));
            if (version < 1 || version > 10) {
                throw new BackupException("Versão de backup do adb não suportada.");
            }
            String compression = readHeaderLine(in);
            if (!"0".equals(compression) && !"1".equals(compression)) {
                throw new BackupException(NOT_ADB);
            }
            String encryption = readHeaderLine(in);
            if (!"none".equals(encryption)) throw new BackupException(ENCRYPTED);

            InputStream data = "1".equals(compression) ? new InflaterInputStream(in) : in;
            return findEntry(data, nameSuffix, maxBytes);
        } catch (IOException error) {
            throw new BackupException(TRUNCATED);
        }
    }

    /** Indica se o início dos bytes é o cabeçalho de um backup do adb. */
    public static boolean looksLikeAdbBackup(byte[] head) {
        if (head == null || head.length < MAGIC_LINE.length) return false;
        for (int i = 0; i < MAGIC_LINE.length; i++) {
            if (head[i] != MAGIC_LINE[i]) return false;
        }
        return true;
    }

    /** Lê uma linha do cabeçalho byte a byte, sem buffer (não pode consumir o payload). */
    private static String readHeaderLine(InputStream in) throws IOException, BackupException {
        StringBuilder line = new StringBuilder();
        while (true) {
            int b = in.read();
            if (b < 0) throw new BackupException(NOT_ADB);
            if (b == '\n') return line.toString();
            if (line.length() >= MAX_HEADER_LINE) throw new BackupException(NOT_ADB);
            line.append((char) (b & 0xff));
        }
    }

    private static int parseSmallInt(String text) throws BackupException {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException error) {
            throw new BackupException(NOT_ADB);
        }
    }

    private static byte[] findEntry(InputStream data, String nameSuffix, int maxBytes)
            throws IOException, BackupException {
        byte[] header = new byte[BLOCK];
        long total = 0;
        while (true) {
            int got = readFully(data, header, BLOCK);
            if (got == 0) return null;
            if (got < BLOCK) throw new BackupException(TRUNCATED);
            total += BLOCK;
            if (total > MAX_TOTAL_BYTES) throw new BackupException("Backup do adb grande demais.");
            if (isZeroBlock(header)) return null;

            String name = cString(header, 0, 100);
            if (isUstar(header)) {
                String prefix = cString(header, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
            }
            long size = octal(header, 124, 12);
            if (size < 0) throw new BackupException(TRUNCATED);
            byte type = header[156];
            boolean regularFile = type == '0' || type == 0;
            if (regularFile && name.endsWith(nameSuffix)) {
                if (size > maxBytes) throw new BackupException("Arquivo de dados grande demais.");
                byte[] content = new byte[(int) size];
                if (readFully(data, content, content.length) < content.length) {
                    throw new BackupException(TRUNCATED);
                }
                return content;
            }
            long padded = (size + BLOCK - 1) / BLOCK * BLOCK;
            total += padded;
            if (total > MAX_TOTAL_BYTES) throw new BackupException("Backup do adb grande demais.");
            skipFully(data, padded);
        }
    }

    /** Lê até {@code length} bytes, em laço (InputStream pode devolver menos). Retorna o total. */
    private static int readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int done = 0;
        while (done < length) {
            int count = in.read(buffer, done, length - done);
            if (count < 0) break;
            done += count;
        }
        return done;
    }

    private static void skipFully(InputStream in, long count) throws IOException, BackupException {
        byte[] scratch = new byte[BLOCK * 8];
        long remaining = count;
        while (remaining > 0) {
            int chunk = (int) Math.min(scratch.length, remaining);
            if (readFully(in, scratch, chunk) < chunk) throw new BackupException(TRUNCATED);
            remaining -= chunk;
        }
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) return false;
        }
        return true;
    }

    private static boolean isUstar(byte[] header) {
        return header[257] == 'u' && header[258] == 's' && header[259] == 't'
                && header[260] == 'a' && header[261] == 'r';
    }

    /** Texto terminado em NUL dentro de um campo de tamanho fixo. */
    private static String cString(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) end++;
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    /** Número octal de um campo do tar, ignorando espaços e NUL. */
    private static long octal(byte[] header, int offset, int length) {
        int i = offset;
        int end = offset + length;
        while (i < end && (header[i] == ' ' || header[i] == 0)) i++;
        long value = 0;
        while (i < end && header[i] >= '0' && header[i] <= '7') {
            value = value * 8 + (header[i] - '0');
            i++;
        }
        return value;
    }
}
