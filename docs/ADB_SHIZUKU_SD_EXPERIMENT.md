# Experimento sem computador: diagnóstico ADB/Shizuku do microSD

## Objetivo

Coletar evidências no Moto G52 XT2221-1 com Android 13, sem root, sem desbloquear o bootloader e sem escrever no microSD. O experimento usa o shell ADB disponibilizado por Shizuku/aShell iniciado por Depuração sem fio, recurso documentado para Android 11+ sem computador.

Referências:
- Shizuku — início por Depuração sem fio: https://shizuku.rikka.app/guide/setup/
- API StorageVolume — representa um volume lógico para o usuário, não um dispositivo raw: https://developer.android.com/reference/android/os/storage/StorageVolume
- AOSP SELinux — o acesso a dispositivos de bloco é controlado por política de segurança: https://source.android.com/docs/security/features/selinux

## O que o experimento testa

O script `tools/android_shell_sd_probe.sh` recolhe:
- identidade e versão do Android;
- estado SELinux;
- discos conhecidos pelo serviço de armazenamento (`sm list-disks`);
- parte da saída de `dumpsys disk`;
- metadados de nós MMC e sysfs quando visíveis ao shell;
- aliases e montagens relevantes.

O script é somente de leitura. Não abre dispositivos de bloco para escrita, não usa `su`, não executa `dd`, não formata nem desmonta volumes.

## Como executar no telefone

1. Instale Shizuku pela fonte oficial e siga a documentação oficial para iniciar por Depuração sem fio no próprio Android 11+.
2. Use um cliente de shell que execute comandos pela sessão Shizuku/ADB. Não execute o script no terminal Android comum esperando obter os mesmos privilégios: ele precisa rodar na sessão shell.
3. Disponibilize o script como arquivo de texto no telefone e execute-o na sessão shell, se o cliente permitir executar arquivos. Caso contrário, execute as seções do script manualmente, copiando os comandos de leitura.
4. Salve ou copie a saída completa. Remova identificadores pessoais antes de compartilhá-la.

## Interpretação e limites

- Se `sm list-disks` listar o cartão, o Android reconhece um disco gerenciado por vold. Isso **não** prova permissão para escrita raw.
- Se os nós `/dev/block/mmcblk*` não forem visíveis, isso não prova que o cartão não exista; a política de acesso pode ocultá-los.
- `ls -l`, `test -w` ou permissões Unix isoladas não bastam para provar que uma escrita funcionaria: SELinux e o driver também podem negar o acesso.
- Se o shell não puder abrir o dispositivo de bloco para escrita, não existe correção legítima dentro do app comum que simplesmente altere o UID. Seria necessário um serviço privilegiado suportado, um firmware/recovery modificado, root ou outra interface de hardware.
- Mesmo se o shell conseguir listar o disco, não vamos testar gravação com bytes reais nesta etapa. Uma tentativa de escrita errada pode corromper o cartão ou o armazenamento interno.

## Decisão técnica após o teste

1. Comparar a lista de discos e capacidades com o microSD fisicamente inserido/removido, sem alterar partições.
2. Confirmar se o disco interno UFS e o microSD podem ser distinguidos por múltiplas evidências.
3. Só investigar acesso de escrita se houver uma API ou permissão documentada que o conceda. Não usar exploits ou comandos destrutivos como teste.
4. Se o shell ADB não tiver acesso raw (resultado esperado em aparelho de produção), registrar o bloqueio e avaliar uma via de instalação baseada em arquivos somente se o formato de destino realmente suportar isso.

## Status

Pesquisa/diagnóstico, não gravador. Ainda não foi executado no Moto G52 real. Nenhum resultado deste experimento deve ser descrito como prova de gravação até uma verificação setorial segura em mídia de teste.
