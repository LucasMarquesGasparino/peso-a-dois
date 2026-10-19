# Peso a Dois

Diário do casal: Elisa e Lucas registram o peso lado a lado, acompanham a
evolução e comparam com uma base — tudo no aparelho, sem conta e sem internet.

- Pacote: `com.pesoadois` · versão atual 1.1.0 (versionCode 2)
- Dados: `getFilesDir()/diario.json` no celular. Nada sai do aparelho.

## Recursos (1.1.0)

- **Nova marcação**: data (padrão hoje, pode ser passada), peso de cada pessoa
  em kgf, aceita vírgula. Confirma valores muito diferentes do último (>8%) e
  pergunta o que fazer se a pessoa já tem marcação no mesmo dia.
- **Resumo**: peso atual, variação desde a base em kgf e %, meta com barra de
  progresso. Cartão "Vocês dois" com a comparação do casal.
- **Evolução**: gráfico em kgf ou variação %, períodos de 30 dias, 90 dias,
  1 ano ou tudo. Toque no gráfico para ver a data e os valores.
- **Histórico** por dia, com edição e exclusão de qualquer marcação.
- **Configurar pessoa** (⚙ ou toque no cartão): nome, meta (opcional) e base
  (primeira marcação ou peso e data personalizados).
- **Tema claro e escuro** (segue o sistema).
- **Backup, importação e planilha** pelo menu ⋮ (ver abaixo).

## Backup e importação

Os dados ficam só no aparelho: exporte um backup de vez em quando. O app avisa
depois de 30 dias sem backup (com 5 marcações ou mais).

- **Exportar backup (.json)**: arquivo com todas as marcações, metas e bases.
  O app relê o arquivo gravado e confere a contagem antes de confirmar.
- **Importar backup**: aceita o `.json` deste app, o `.ab` de `adb backup` da
  versão 1.0 e o XML de preferências da versão 1.0. Antes de aplicar, o diário
  atual é guardado em `antes-da-importacao.json` (pasta interna do app).
  Escolha entre **Substituir tudo** e **Juntar** (adiciona só o que não existe).
- **Exportar planilha (.csv)**: `data;hora;pessoa;peso_kgf`, com BOM UTF-8 e
  vírgula decimal, para abrir no Excel ou Planilhas.

Formato do backup (formato 1):

```json
{
  "app": "peso-a-dois",
  "format": 1,
  "savedAt": 1760800000000,
  "lastBackupAt": 0,
  "people": [
    {"id": "elisa", "name": "Elisa", "goalKg": null, "baseCustom": false, "baseKg": null, "baseAt": 0},
    {"id": "lucas", "name": "Lucas", "goalKg": 80.0, "baseCustom": true, "baseKg": 85.0, "baseAt": 1750000000000}
  ],
  "entries": [
    {"id": "uuid", "person": "elisa", "at": 1760800000000, "kg": 68.5}
  ]
}
```

Na leitura, entradas inválidas (pessoa desconhecida, data fora de 2000 até
dois dias no futuro, peso fora de 1 a 500 kg) são ignoradas e contadas.

## Resgatar os dados da 1.0 (antes de desinstalar)

A versão 1.0 foi assinada com uma chave que se perdeu, então a 1.1 só instala
por cima se você desinstalar a 1.0 antes. Os dados da 1.0 se perdem com a
desinstalação, por isso gere um `.ab` antes:

1. `pkg install android-tools`. Em Opções do desenvolvedor, ative
   Depuração por Wi-Fi e use "Parear com código".
2. `adb pair 127.0.0.1:<porta-de-pareamento> <código>` e depois
   `adb connect 127.0.0.1:<porta>`.
3. `adb backup -f /sdcard/Download/peso-a-dois-v1.ab com.pesoadois` e confirme
   na tela **sem senha**.
4. Confira que o arquivo tem mais que alguns KB. Desinstale a 1.0 e instale a 1.1.
5. No app: ⋮ > Importar backup > escolha `peso-a-dois-v1.ab`.

Se o `.ab` tiver senha, o app recusa e pede para gerar de novo deixando a senha
em branco.

## Testes

```sh
sh tests/run.sh
```

Compila e roda na JVM, sem Android, o núcleo em `src/com/pesoadois/core/`
(formatos, datas, diário, backup, importação, `.ab` e CSV). Não depende de
Gradle nem de bibliotecas. O `build.sh` roda estes testes antes de gerar o APK.

## Compilar

```sh
cd ~/projects/peso-a-dois
sh build.sh
```

Toolchain manual (aapt2, javac `--release 8`, d8, apksigner), sem Gradle. A versão
vem de `AndroidManifest.xml`. O APK assinado sai em `build/Peso-a-dois.apk` e é
copiado para `/storage/emulated/0/Documents/APK/Peso-a-dois.apk`.

O keystore de assinatura fica em `~/.android-keys/peso-a-dois.keystore`, fora do
repositório (nunca commitar). O `build.sh` cria o keystore se ele não existir, com
alias `pesadois`. Faça backup dele: sem ele não há atualização por cima do APK.

## Estrutura

```
peso-a-dois/
├── AndroidManifest.xml
├── build.sh                         # build manual e assinatura
├── res/                             # temas claro (values/) e escuro (values-night/)
├── src/com/pesoadois/
│   ├── MainActivity.java            # tela, diálogos, backup e importação
│   ├── WeightChart.java             # gráfico (View)
│   ├── DiaryStore.java              # diario.json (escrita atômica) e migração da 1.0
│   ├── Palette.java                 # cores claras e escuras
│   └── core/                        # Java puro, testado na JVM
├── tests/                           # run.sh e CoreTests.java
└── tools/StripMethodParameters.java # passo do build (ver comentário em build.sh)
```
