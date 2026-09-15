# Peso a Dois

Diário do casal: Elisa e Lucas registram o peso lado a lado, comparam com uma
base e acompanham a evolução no tempo — tudo no aparelho, sem conta e sem
internet.

- Pacote: `com.pesoadois`
- Dados locais (SharedPreferences/SQLite): nada sai do celular.

## Recursos

- **Pesos de hoje**: um registro de Elisa + um de Lucas por dia.
- **Nova marcação**: teclado numérico em kgf (1–500), data = hoje por padrão
  (toque para alterar).
- **Bases de comparação**: primeira marcação ou base personalizada por pessoa;
  variação desde a base em kgf e %.
- **Evolução no tempo**: lista/estatísticas das últimas marcações.

## Compilar

```sh
cd ~/projects/peso-a-dois
bash build.sh
```

APK `Peso-a-dois.apk` em `build/`, copiado para `Documents/`.

## Estrutura

```
peso-a-dois/
├── src/com/pesoadois/MainActivity.java  # diário + bases + evolução
└── res/
```
