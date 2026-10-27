# Pocket Flasher — Android alpha

Aplicativo Android em Kotlin para preparar e verificar imagens de sistemas, com prioridade inicial para Anbernic R36H/R36S.

## Estado atual

- Seleção de imagem/arquivo local pelo seletor nativo do Android.
- Exibição de nome e tamanho do arquivo.
- Cálculo de SHA-256 em fluxo, sem carregar a imagem inteira na memória.
- Perfis de dispositivos e pesquisa na internet sobre painel/tela.
- Enumeração de dispositivos USB conectados via Android USB Host e solicitação de permissão.
- Cópia de uma imagem como arquivo para uma pasta escolhida no Android, com verificação SHA-256 de origem/destino.
- Workflow GitHub Actions para compilar APK de depuração e publicar o APK como artefato.

## Limitação importante

**Esta versão NÃO grava imagens inicializáveis e não é equivalente funcional ao Rufus.** A função disponível copia a imagem como arquivo para a pasta selecionada e compara o SHA-256 após a cópia; isso NÃO grava setores de inicialização. Pelo slot microSD interno, as APIs públicas comuns do Android não expõem acesso raw ao dispositivo físico.

### Caminhos de hardware planejados

- **USB OTG:** identificar leitores USB Mass Storage e desenvolver uma implementação USB/SCSI com autorização explícita, capacidade do cartão, escrita em blocos, sincronização, leitura de verificação e cancelamento seguro.
- **Slot microSD interno do celular:** é possível copiar arquivos para a pasta do cartão se o Android/fabricante disponibilizar acesso via seletor de documentos. Isso não equivale a gravar a imagem no cartão. APIs públicas comuns não garantem acesso raw; root não é presumido.
- **Dispositivo com root:** se for desenvolvido, será um backend separado com validação rigorosa do destino.

Não habilitar escrita destrutiva antes de testar com cartões descartáveis, validar capacidade e verificar os dados gravados. Nunca apresentar a cópia de um arquivo ISO/IMG para uma pasta como gravação inicializável.

## Compilação

O workflow GitHub Actions em `.github/workflows/android-build.yml` é executado quando há alterações na branch `main` e também conforme os gatilhos definidos no próprio arquivo. Ele compila o APK de depuração e publica `pocket-flasher-debug-apk` como artefato da execução.

Para compilar localmente, use Android Studio, JDK 17, Android SDK Platform 35 e Gradle 8.9. O projeto usa Android Gradle Plugin 8.7.3 e Kotlin 2.0.21.

## Instalar o APK de teste

1. Abra a aba Actions do repositório.
2. Selecione a execução Build Android APK.
3. Quando terminar com sucesso, abra a execução e baixe o artefato pocket-flasher-debug-apk.
4. Extraia o ZIP do artefato e instale app-debug.apk no Android.

APK de depuração é para testes; ainda não é uma versão de produção assinada.
