# Pesquisa: gravação raw de imagens no Pocket Flasher

Data: 2026-10-28  
Escopo prioritário: Moto G52 (XT2221-1), Android 13, sem root, cartão microSD de destino.

## Conclusão técnica

O caminho mais promissor sem root é **USB OTG + leitor USB de cartão + backend de armazenamento em blocos em espaço de usuário**. Não depende de o Android montar o cartão como volume de arquivos: o app pede autorização USB e fala com a interface USB Mass Storage do leitor. Isso é tecnicamente diferente de copiar um arquivo para uma pasta do cartão.

Isto **não resolve automaticamente o slot microSD interno do Moto G52**. O acesso ao dispositivo MMC interno é controlado pelo kernel/permissões/SELinux e APIs públicas de armazenamento não concedem escrita arbitrária de setores. Sem root, privilégio de sistema ou suporte específico do fabricante, não há base para prometer gravação raw pelo slot interno.

## Fontes consultadas

- Android USB Host: https://developer.android.com/develop/connectivity/usb/host
  - O app pode enumerar dispositivos, obter autorização, abrir uma interface e usar endpoints USB por transferências bulk.
- Android `UsbDeviceConnection`: https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection
  - Documenta transferências USB, não uma API de escrita de setores de cartão.
- Android scoped storage: https://source.android.com/docs/core/storage/scoped
  - O acesso a arquivos concedido pelo sistema é distinto de acesso a dispositivo de bloco.
- libaums: https://github.com/magnusja/libaums
  - Biblioteca Android para USB Mass Storage/SCSI e operações de sistema de arquivos, mas sua documentação indica suporte a FAT32 e acesso centrado em partições/arquivos. Não presumir que a API de arquivos existente seja suficiente para escrever uma imagem de disco completa setor por setor.

## Arquitetura proposta

1. **Detecção sem escrita:** listar somente interfaces USB Mass Storage e mostrar fabricante, produto, VID:PID, endpoints e autorização. Não selecionar silenciosamente dispositivos não relacionados.
2. **Sonda SCSI somente leitura:** implementar ou adaptar o protocolo USB Mass Storage Bulk-Only Transport (BOT) para comandos de consulta, por exemplo INQUIRY e READ CAPACITY(10), validar assinatura/respostas e descobrir tamanho de bloco/capacidade. Não enviar WRITE nesta fase.
3. **Dispositivo lógico de blocos:** encapsular o acesso em uma interface interna com capacidade, tamanho de bloco, identidade do USB, leitura de setores e, somente depois de testes, escrita de setores. Não usar APIs de FAT32 para gravar a imagem como arquivo.
4. **Simulação (dry-run):** abrir a imagem via Storage Access Framework, verificar tamanho e SHA-256, confirmar que cabe no destino, exibir o identificador completo do leitor/cartão e calcular o plano de blocos sem transmitir dados de escrita.
5. **Escrita experimental separada e desativada por padrão:** somente após leitura de capacidade e testes automatizados. Implementar comandos de escrita em blocos com transferência fragmentada, timeout, tratamento de desconexão, checagem de bytes transferidos, flush/sync quando suportado e leitura de retorno. Nunca escrever em disco de teste que contenha dados importantes.
6. **Validação pós-escrita:** ler de volta amostras e, quando viável, a imagem completa para comparar hash. A imagem gravada pode não ser inicializável se a imagem for incompatível com o hardware, mesmo quando os bytes coincidem.
7. **Slot interno:** manter como pesquisa independente. O diagnóstico `su` atual é somente leitura e não comprova que root exista nem que o app consiga escrever. Shizuku/ADB não equivale a root.

## Riscos e barreiras

- Um comando de escrita raw sobrescreve partições, tabela de partições e dados existentes do cartão escolhido.
- VID:PID identifica o leitor/modelo USB, não garante sozinho a identidade do cartão inserido. Revalidar capacidade e presença antes da operação.
- Alguns leitores implementam USB Mass Storage com variantes/problemas de protocolo; alguns dispositivos podem ser reivindicados pelo sistema. Falhas devem abortar, não tentar outro dispositivo automaticamente.
- O consumo de energia OTG pode ser insuficiente para alguns leitores/cartões.
- Imagens ZIP não são imagens raw diretamente: devem ser extraídas e verificadas antes de qualquer plano de gravação.
- Hash calculado localmente comprova integridade apenas se comparado a uma referência confiável. Um hash oficial não disponível não deve ser marcado como verificado.
- Interrupção de energia/desconexão durante a escrita pode deixar o cartão inutilizável.

## Critérios para habilitar qualquer escrita

- Testes unitários de parsing SCSI/BOT com respostas sintéticas e casos truncados/malformados.
- Teste de leitura em pelo menos um leitor OTG e cartão descartável, confirmando capacidade e leitura estável.
- Teste de escrita primeiro em dispositivo de teste dedicado, com confirmação explícita e destino mostrado integralmente.
- Revalidação imediata do destino; bloqueio se imagem exceder capacidade, identidade/capacidade mudar ou hash exigido falhar.
- Não habilitar gravação pelo slot interno apenas porque o inventário Android mostra um volume removível.
- A função deve permanecer desativada até revisão e teste físico documentado.

## Estado atual do repositório

A branch de trabalho tem inventário USB e solicitação de permissão, pré-validação da imagem e documentação de segurança. Ainda **não** tem sonda SCSI/BOT validada nem escrita raw. A próxima implementação de código deve ser uma sonda estritamente de leitura com testes de protocolo, nunca a escrita completa diretamente.
