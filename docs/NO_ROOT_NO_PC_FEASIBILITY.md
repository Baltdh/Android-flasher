# Pesquisa de viabilidade: gravador de microSD no próprio Android, sem root e sem computador

## Requisitos do projeto

- Telefone de referência: Motorola Moto G52 XT2221-1 (codinome rhode), Android 13 de fábrica.
- Não fazer root.
- Não depender de computador para desbloquear o aparelho.
- Preferência: usar o slot microSD interno, sem leitor OTG.
- Objetivo: gravar uma imagem de disco (IMG/ISO adequada) em nível de setores, para produzir mídia inicializável em outro equipamento.
- Prioridade: não apagar dados do telefone nem danificar o microSD por seleção incorreta.

## Conclusão executiva

**Não há, com as APIs públicas normais do Android 13, um caminho confirmado para um app comum gravar setores arbitrários no microSD do slot interno.** O Storage Access Framework concede acesso a documentos/arquivos por URIs; ele não concede ao app o dispositivo de bloco físico. A FAQ do EtchDroid também informa explicitamente que o slot SD interno não é suportado porque o Android não permite a escrita necessária para imagens de disco.

O requisito "sem root, sem computador para desbloquear e usando o slot interno" deixa o projeto sem um backend raw-writing suportado conhecido. Não devemos fingir que uma cópia de IMG para uma pasta, uma permissão SAF, Shizuku, depuração USB ou um servidor local transforma essa cópia em mídia inicializável.

## Matriz de opções

| Opção | Sem root | Sem computador | Slot interno | Resultado |
|---|---:|---:|---:|---|
| SAF / DocumentFile | Sim | Sim | Sim, para arquivos expostos pelo Android | Copia arquivos; não grava a imagem em setores |
| Termux / shell comum | Sim | Sim | Sem acesso raw garantido | Não atende à gravação bruta |
| Shizuku / depuração sem root | Sim | Pode ser configurado no telefone | Não concede automaticamente acesso ao bloco | Não é solução confirmada |
| App com leitor USB OTG e protocolo USB Mass Storage | Sim | Sim | Não; usa dispositivo USB externo | Possível backend separado, mas viola a preferência pelo slot interno |
| Recovery com privilégios, após desbloqueio | Não exige root no Android instalado, mas altera o estado do dispositivo | Normalmente exige ferramentas/host no processo suportado | Potencialmente | Rota técnica possível, incompatível com os requisitos atuais até provar o contrário |
| Exploit de elevação de privilégios | Não é um caminho realmente "sem root" do ponto de vista de privilégios | Variável | Talvez | Rejeitado como base do produto: não confiável, arriscado e dependente de vulnerabilidade |
| Gravação por firmware/bootloader fechado | Sim, em tese | Depende do fabricante | Não confirmado para esse objetivo | Sem interface documentada no Moto G52 para gravar uma imagem arbitrária no microSD |

## Por que o desbloqueio não é apenas uma opção do menu

A opção "Desbloqueio OEM" em Opções do desenvolvedor habilita a elegibilidade quando suportada; ela não executa sozinha o desbloqueio. O procedimento AOSP usa comandos fastboot no modo bootloader e exige confirmação física. A transição de bloqueado para desbloqueado apaga os dados do dispositivo por proteção de privacidade. Portanto, não existe base para prometer desbloqueio autônomo e sem computador no Moto G52 usando apenas o aplicativo Android comum.

Referências:
- AOSP, bloqueio/desbloqueio do bootloader: https://source.android.com/docs/core/architecture/bootloader/locking_unlocking
- Android Developers, Storage Access Framework: https://developer.android.com/guide/topics/providers/document-provider
- EtchDroid, dispositivos suportados e limitação do slot SD interno: https://github.com/EtchDroid/EtchDroid

## Caminho de desenvolvimento próprio que respeita os requisitos

### Fase A — implementar sem risco no Pocket Flasher
1. Manter seleção de imagem, leitura em fluxo e SHA-256.
2. Detectar tamanho, extensão, compressão e espaço disponível; não presumir que toda ISO/IMG é apropriada para qualquer dispositivo.
3. Preparar um manifesto local com tamanho, SHA-256, origem e perfil do dispositivo de destino.
4. Copiar o arquivo para o microSD por SAF apenas quando o Android expuser a pasta, identificando claramente a operação como **cópia de arquivo, não gravação inicializável**.
5. Criar um relatório de capacidades do dispositivo: volumes visíveis pelo Android, URIs concedidas e resultado de operações de arquivo não destrutivas. Não tentar abrir ou escrever em `/dev/block/*`.
6. Manter o backend de escrita desativado enquanto não houver uma API suportada que forneça acesso real ao dispositivo de bloco.

### Fase B — experimentos de pesquisa, sem escrita destrutiva
1. Testar no Moto G52 se o volume removível aparece via StorageManager e SAF, quais operações de arquivo são concedidas e se a permissão persiste após reinicialização.
2. Confirmar, em código de diagnóstico, que o acesso concedido é a um URI/documento, não a um dispositivo de bloco.
3. Comparar os resultados em versões Android e fabricantes distintos; não generalizar a partir de outro telefone.
4. Se um firmware futuro expuser uma interface oficial de escrita raw, implementar um backend específico e testá-lo com cartões descartáveis.
5. Se o requisito de slot interno permanecer obrigatório e não existir interface oficial, registrar o bloqueio como limitação da plataforma, sem usar exploits como dependência do aplicativo.

### Critérios para declarar sucesso

- A gravação deve alterar setores da mídia, incluindo tabela de partições e dados de sistema de arquivos quando presentes na imagem.
- A verificação deve ler os setores escritos e comparar o conteúdo esperado, não apenas recalcular o hash do arquivo copiado.
- A aplicação deve provar que o alvo é o microSD inteiro e nunca o armazenamento UFS interno.
- Um teste de cópia via SAF, mesmo com hash idêntico, **não** satisfaz estes critérios.

## Estado atual do repositório

O Pocket Flasher ainda é um preparador/verificador de arquivos. Não há gravador raw implementado nem prova de que o Moto G52 permita esse acesso sem privilégios especiais. A próxima implementação segura é o relatório de capacidades SAF/StorageManager e os testes de imagem/manifesto. A escrita destrutiva não deve ser adicionada até existir uma interface de escrita suportada e verificável.
