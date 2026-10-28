# Pocket Flasher — notas de campo e decisão técnica (2026-10-27)

## Dispositivo-alvo e requisitos

- Telefone: Motorola Moto G52 XT2221-1, Android 13 original.
- Sem root; não desbloquear bootloader como parte deste experimento.
- Preferência por usar o slot microSD interno; sem depender de computador.
- Objetivo futuro: gravar uma imagem de disco em setores e verificar a mídia resultante, se existir uma interface suportada.
- Não declarar o gravador como funcional sem testes reais e verificação setorial.

## Evidências observadas no aparelho

### Diagnóstico do shell comum / app

- O diagnóstico do app não encontrou dispositivos de bloco visíveis nem entradas MMC na execução compartilhada pelo usuário.
- A chamada `dumpsys package com.pocketflasher.android` retornou `Permission Denial` por falta de `android.permission.DUMP`. Isso é esperado para um processo de app comum e não significa que o pacote esteja ausente.
- A saída de montagens mostrou partições privadas de apps, mas não demonstrou acesso raw ao cartão removível.
- A saída disponível não prova que o microSD esteja ausente; o cartão pode não estar inserido, não ter sido enumerado pelo comando ou estar oculto pela política de acesso.

### Preparação do Shizuku/rish no Termux

- O script `rish` e o arquivo `rish_shizuku.dex` existem em `~/storage/shared/Documents/`.
- O script `rish` contém o identificador genérico `RISH_APPLICATION_ID="PKG"`.
- Ambos os arquivos foram copiados com sucesso para `$HOME/rish` e `$HOME/rish_shizuku.dex`, dentro do diretório privado do Termux.
- O teste `RISH_APPLICATION_ID=com.termux sh "$HOME/rish" -c 'id'` retornou: `Request timeout. The connection between the current app (com.termux) and Shizuku app may be blocked by your system. Please disable all battery optimization features for both current app (com.termux) and Shizuku app.`
- Portanto, **a sessão Shizuku/rish ainda não foi validada**. Não inferir UID `shell` nem privilégios a partir da presença dos arquivos.
- Próximo passo de diagnóstico: confirmar na interface Shizuku que o serviço está iniciado; desativar otimização de bateria para Termux e Shizuku; verificar a configuração oficial do rish para Termux e repetir apenas o comando não destrutivo `id`. Se continuar expirando, coletar o estado do Shizuku e investigar a comunicação sem tentar gravação raw.

## Decisão de segurança

Não executar `dd`, `mkfs`, `wipefs`, `partprobe`, comandos de particionamento, montagem/desmontagem, nem abrir dispositivos de bloco para escrita. Não usar exploits para elevar privilégios. Não afirmar que Shizuku, depuração sem fio, SAF ou copiar um arquivo ISO/IMG concedem acesso raw.

## Próximos passos do Pocket Flasher

1. Preservar seleção de imagem e cálculo SHA-256 em fluxo.
2. Melhorar a tela de diagnóstico para separar claramente:
   - volumes lógicos expostos por APIs públicas;
   - acesso a arquivos por SAF;
   - diagnóstico opcional via shell Shizuku (somente leitura);
   - escrita raw, atualmente indisponível/não comprovada.
3. Tornar explícito que `android.permission.DUMP` não deve ser solicitado por um app comum para contornar o bloqueio; usar APIs públicas para o inventário do app.
4. Adicionar exportação/compartilhamento do relatório de diagnóstico sem coletar dados pessoais desnecessários.
5. Manter qualquer função de cópia com o rótulo “cópia de arquivo — não inicializável”.
6. Só avaliar backend de gravação quando houver uma interface de hardware oficialmente acessível; considerar USB OTG Mass Storage como projeto separado caso o requisito do slot interno seja flexibilizado.

## Critério para declarar gravação real

A gravação precisa escrever a imagem nos setores do dispositivo-alvo, validar que o alvo é o cartão inteiro e não o armazenamento UFS interno, sincronizar os dados e verificar a mídia lendo os setores de volta. Copiar a imagem como arquivo e comparar o hash do arquivo não satisfaz esse critério.

## Status

- Viabilidade do slot microSD interno sem root: não demonstrada; as APIs públicas comuns não oferecem acesso raw documentado.
- Shizuku/rish: não validado; o último teste expirou.
- Gravador raw no app: não implementado.
- Diagnósticos realizados até aqui: não destrutivos.
