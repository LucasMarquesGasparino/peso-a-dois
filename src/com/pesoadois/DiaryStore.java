package com.pesoadois;

import android.content.Context;
import android.util.AtomicFile;

import com.pesoadois.core.BackupCodec;
import com.pesoadois.core.BackupException;
import com.pesoadois.core.Diary;
import com.pesoadois.core.LegacyImport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Arquivo do diário (diario.json, escrita atômica) e migração da versão 1.0.
 * Escritas são síncronas e retornam boolean: a UI só troca o diário em memória se gravou.
 */
final class DiaryStore {
    static final String FILE_NAME = "diario.json";
    static final String LEGACY_PREFS = "peso_a_dois_dados";

    /** Resultado do carregamento: diário e aviso opcional para o usuário. */
    static final class LoadResult {
        final Diary diary;
        final String notice;

        LoadResult(Diary diary, String notice) {
            this.diary = diary;
            this.notice = notice;
        }
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final Context context;

    DiaryStore(Context context) {
        this.context = context.getApplicationContext();
    }

    LoadResult load() {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
        if (file.getBaseFile().exists()) {
            byte[] bytes;
            try {
                bytes = file.readFully();
            } catch (IOException error) {
                bytes = null;
            }
            if (bytes != null) {
                try {
                    BackupCodec.Result result = BackupCodec.decode(
                            new String(bytes, StandardCharsets.UTF_8), System.currentTimeMillis());
                    return new LoadResult(result.diary, null);
                } catch (BackupException error) {
                    // cai na preservação abaixo
                }
            }
            return preserveCorrupt(bytes);
        }
        return migrateLegacy();
    }

    boolean save(Diary diary) {
        return write(new AtomicFile(new File(context.getFilesDir(), FILE_NAME)), diary);
    }

    /** Cópia de segurança em getFilesDir()/fileName (usada antes de importar). */
    boolean saveCopy(Diary diary, String fileName) {
        return write(new AtomicFile(new File(context.getFilesDir(), fileName)), diary);
    }

    private static boolean write(AtomicFile file, Diary diary) {
        FileOutputStream out = null;
        try {
            byte[] bytes = BackupCodec.encode(diary, System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
            out = file.startWrite();
            out.write(bytes);
            file.finishWrite(out);
            return true;
        } catch (IOException | RuntimeException error) {
            if (out != null) file.failWrite(out);
            return false;
        }
    }

    /** Copia o arquivo ilegível para um nome novo (nunca apaga) e devolve diário vazio aviso. */
    private LoadResult preserveCorrupt(byte[] bytes) {
        String fallback = "Não consegui ler os dados salvos. Importe um backup.";
        if (bytes == null) return new LoadResult(new Diary(), fallback);
        String name = "diario-corrompido-" + LocalDateTime.now().format(STAMP) + ".json";
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), name))) {
            out.write(bytes);
            out.getFD().sync();
        } catch (IOException error) {
            return new LoadResult(new Diary(), fallback);
        }
        return new LoadResult(new Diary(),
                "Não consegui ler os dados salvos. O arquivo foi preservado como " + name + ". Importe um backup.");
    }

    /** Primeira execução da 1.1: importa as SharedPreferences da 1.0 (não as apaga). */
    private LoadResult migrateLegacy() {
        Map<String, ?> all = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getAll();
        if (all == null || all.isEmpty()) return new LoadResult(new Diary(), null);
        Diary migrated = LegacyImport.fromPrefsMap(all);
        if (migrated.isEmpty()) return new LoadResult(migrated, null);
        int count = migrated.size();
        if (save(migrated)) {
            return new LoadResult(migrated, "Dados da versão anterior migrados (" + count + " marcações).");
        }
        return new LoadResult(migrated, "Não consegui salvar os dados da versão anterior. Verifique o espaço livre.");
    }
}
