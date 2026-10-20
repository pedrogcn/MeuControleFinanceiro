name: Compilar APK

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-24.04
    defaults:
      run:
        working-directory: MeuControleFinanceiro
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Preparar assinatura fixa
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          if [ -z "$KEYSTORE_BASE64" ]; then
            echo "::error::Segredo KEYSTORE_BASE64 não configurado em Settings > Secrets and variables > Actions"
            exit 1
          fi
          echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/meucontrole.jks"

      - name: Compilar APK
        env:
          KEYSTORE_FILE: ${{ runner.temp }}/meucontrole.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
        run: |
          echo "sdk.dir=$ANDROID_HOME" > local.properties
          chmod +x gradlew
          ./gradlew --no-daemon assembleRelease
          mv app/build/outputs/apk/release/app-release.apk "MeuControleFinanceiro-1.0.${{ github.run_number }}.apk"

      - name: Publicar versão (Release)
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v1.0.${{ github.run_number }}
          name: Versão 1.0.${{ github.run_number }}
          body: ${{ github.event.head_commit.message }}
          files: MeuControleFinanceiro/MeuControleFinanceiro-1.0.${{ github.run_number }}.apk
