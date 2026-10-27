package com.pocketflasher.android

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var imageUri: Uri? = null
    private var imageName = ""
    private var imageSize = -1L
    private var imageSelectionVersion = 0
    private var destinationTreeUri: Uri? = null
    private var pendingUsbReceiver: BroadcastReceiver? = null
    private lateinit var status: TextView
    private lateinit var imageInfo: TextView
    private lateinit var expectedHashInput: EditText
    private lateinit var deviceSpinner: Spinner

    private val profiles = listOf(
        "Anbernic R36H — prioridade", "Anbernic R36S",
        "Anbernic RG351 / RK3326", "Outro console Linux portátil",
        "PC / mini PC", "Raspberry Pi", "Outro dispositivo (genérico)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(0xFF10141C.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        scroll.addView(content)
        page.addView(scroll, ViewGroup.LayoutParams(-1, -1))

        content.addView(TextView(this).apply {
            text = "POCKET FLASHER"
            textSize = 25f
            setTextColor(0xFFFFC857.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Preparação e verificação de imagens de sistemas"
            textSize = 14f
            setTextColor(0xFFD2D8E2.toInt())
            setPadding(0, dp(4), 0, dp(18))
        })

        section(content, "1 · Dispositivo de destino")
        deviceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, profiles)
        }
        content.addView(deviceSpinner, matchWrap())
        content.addView(makeButton("Pesquisar tipo de tela na internet").apply {
            setOnClickListener {
                val selected = deviceSpinner.selectedItem?.toString() ?: "Anbernic R36H"
                val query = if (selected.contains("R36H")) {
                    "Anbernic R36H screen panel resolution display compatibility"
                } else if (selected.contains("R36S")) {
                    "Anbernic R36S screen panel resolution display compatibility"
                } else {
                    selected + " screen panel resolution display compatibility"
                }
                openWebSearch(query)
            }
        }, matchWrap())

        section(content, "2 · Imagem do sistema")
        content.addView(makeButton("Selecionar arquivo ISO / IMG / ZIP").apply {
            setOnClickListener { pickImage() }
        }, matchWrap())
        imageInfo = TextView(this).apply {
            text = "Nenhuma imagem selecionada."
            textSize = 14f
            setTextColor(0xFFD2D8E2.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF1B2230.toInt())
        }
        content.addView(imageInfo, matchWrap())
        expectedHashInput = EditText(this).apply {
            hint = "SHA-256 oficial (opcional)"
            setSingleLine(true)
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFADB7C7.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0xFF1B2230.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        content.addView(expectedHashInput, matchWrap())
        content.addView(makeButton("Calcular SHA-256 e comparar").apply {
            setOnClickListener { calculateSha256() }
        }, matchWrap())

        section(content, "3 · Método de conexão")
        val connectionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Detectar USB OTG conectado", "Slot microSD interno do celular", "Dispositivo com root (planejado)")
            )
        }
        content.addView(connectionSpinner, matchWrap())
        content.addView(makeButton("Verificar dispositivos USB conectados").apply {
            setOnClickListener { inspectUsbDevices() }
        }, matchWrap())
        content.addView(makeButton("Solicitar autorização USB para o leitor").apply {
            setOnClickListener { requestUsbPermission() }
        }, matchWrap())

        section(content, "4 · Desenvolvedor / root (Moto G52)")
        content.addView(makeButton("Como habilitar opções do desenvolvedor").apply {
            setOnClickListener { showDeveloperOptionsGuide() }
        }, matchWrap())
        content.addView(makeButton("Abrir opções do desenvolvedor").apply {
            setOnClickListener { openDeveloperSettings() }
        }, matchWrap())
        content.addView(makeButton("Informações oficiais sobre desbloqueio do bootloader").apply {
            setOnClickListener {
                openUrl("https://pt-br.support.motorola.com/app/answers/detail/a_id/89960/")
            }
        }, matchWrap())
        content.addView(makeButton("Diagnosticar acesso ao microSD interno (sem gravar)").apply {
            setOnClickListener { diagnoseInternalSdAccess() }
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "Importante: o Pocket Flasher não consegue ativar root automaticamente. " +
                "Ativar opções do desenvolvedor ou depuração USB não libera a escrita física no microSD. " +
                "Desbloquear o bootloader pode apagar os dados e reduzir a segurança; confirme a elegibilidade " +
                "do modelo e siga somente instruções oficiais. A gravação raw ainda não está implementada."
            textSize = 12f
            setTextColor(0xFFFFC857.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setBackgroundColor(0xFF1B2230.toInt())
        }, matchWrap())

        section(content, "4 · Cartão SD / USB")
        content.addView(makeButton("Selecionar pasta/cartão (acesso Android)").apply {
            setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE) }
        }, matchWrap())
        content.addView(makeButton("Copiar imagem para o microSD (não inicializável)").apply {
            setOnClickListener { confirmCopyToSdCard() }
        }, matchWrap())

        section(content, "Estado")
        status = TextView(this).apply {
            text = "Selecione uma imagem e uma pasta do microSD. A cópia de arquivo não torna o cartão inicializável."
            textSize = 13f
            setTextColor(0xFFFFC857.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF1B2230.toInt())
        }
        content.addView(status, matchWrap())
        expectedHashInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                status.text = "Hash oficial alterado. Recalcule o SHA-256 para comparar com o novo valor."
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        content.addView(TextView(this).apply {
            text = "Limitação do Android: pelo slot interno, APIs públicas comuns não permitem escrever setores físicos do microSD. " +
                "A função disponível copia a imagem como arquivo e NÃO cria um cartão inicializável."
            textSize = 12f
            setTextColor(0xFFADB7C7.toInt())
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(page)
    }

    /**
     * Diagnóstico somente de leitura. Nunca escreve nem altera partições.
     * O acesso ao comando "su" depende de root já instalado e autorizado pelo usuário.
     */
    /**
     * Read-only diagnosis of root and MMC block-device metadata.
     * This method intentionally never opens a block device for writing.
     */
    private fun diagnoseInternalSdAccess() {
        status.text = "Verificando root e identificadores do microSD. Diagnóstico somente de leitura…"
        worker.execute {
            var process: Process? = null
            val result = try {
                val diagnosticCommand = """
                    id
                    echo __SYS_BLOCK_MMC__
                    for p in /sys/block/mmcblk*; do
                      [ -e "$p" ] || continue
                      n=${p##*/}
                      echo "DEVICE=$n"
                      echo -n "REMOVABLE="; cat "$p/removable" 2>/dev/null
                      echo -n "SECTORS_512B="; cat "$p/size" 2>/dev/null
                      echo -n "SYSFS_PATH="; readlink -f "$p/device" 2>/dev/null
                      echo -n "PARTITIONS="
                      ls "$p" 2>/dev/null | grep -E "^${n}p?[0-9]+$" | tr '\\n' ' '
                      echo
                    done
                    echo __BLOCK_NODES__
                    ls -l /dev/block/mmcblk* 2>/dev/null
                    echo __MOUNTS__
                    cat /proc/mounts | grep -E '/dev/block/mmcblk' 2>/dev/null
                """.trimIndent()
                process = ProcessBuilder("su", "-c", diagnosticCommand)
                    .redirectErrorStream(true).start()
                val finished = process.waitFor(60, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    "Tempo esgotado esperando a autorização root. Se apareceu uma solicitação do Magisk/Superuser, autorize e tente novamente. Nenhuma gravação foi tentada."
                } else {
                    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                    when {
                        process.exitValue() != 0 ->
                            "O diagnóstico terminou com código " + process.exitValue() +
                                ". Saída: " + output.ifBlank { "(vazia)" } +
                                "\\nNenhuma gravação foi tentada."
                        !output.contains("uid=0") ->
                            "Root não confirmado (uid=0 ausente).\\n" + output +
                                "\\nNenhuma gravação foi tentada."
                        !output.contains("REMOVABLE=1") ->
                            "Root confirmado, mas o diagnóstico não encontrou um dispositivo MMC marcado como removível. " +
                                "Não é seguro escolher um alvo automaticamente. Confira os metadados abaixo.\\n\\n" + output
                        else ->
                            "Root confirmado. Há pelo menos um dispositivo MMC marcado como removível, mas isso ainda não basta para autorizar uma gravação. " +
                                "Precisamos confirmar o modelo, capacidade, partições e montagens antes de implementar escrita.\\n\\n" + output
                    }
                }
            } catch (error: Exception) {
                "Não foi possível executar o diagnóstico root: " +
                    (error.message ?: error.javaClass.simpleName) +
                    "\\nConfirme se há root instalado e se o gerenciador de root permite o acesso. " +
                    "Opções do desenvolvedor/depuração USB não substituem root. Nenhuma gravação foi tentada."
            } finally {
                try { process?.destroy() } catch (_: Exception) { }
            }
            runOnUiThread { status.text = result }
        }
    }

    private fun showDeveloperOptionsGuide() {
        AlertDialog.Builder(this)
            .setTitle("Moto G52 — opções do desenvolvedor")
            .setMessage(
                "1. Abra Configurações > Sobre o telefone.\n" +
                "2. Localize Número da versão (em algumas versões, fica em Detalhes do dispositivo/Versão do Android).\n" +
                "3. Toque em Número da versão 7 vezes e confirme o PIN se solicitado.\n" +
                "4. Volte para Configurações > Sistema > Opções do desenvolvedor.\n" +
                "5. Para depuração, ative Depuração USB e autorize apenas computadores confiáveis.\n\n" +
                "Isso não ativa root nem permite gravar setores do microSD pelo slot interno."
            )
            .setPositiveButton("Abrir configurações") { _, _ -> openDeveloperSettings() }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun openDeveloperSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Configurações não abertas")
                .setMessage("Abra manualmente Configurações > Sistema > Opções do desenvolvedor. Se o menu estiver oculto, toque 7 vezes em Número da versão em Sobre o telefone.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            status.text = "Não foi possível abrir o navegador. Consulte o suporte oficial da Motorola."
        }
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(18), 0, dp(8))
        })
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(0xFF10141C.toInt())
        setBackgroundColor(0xFFFFC857.toInt())
        gravity = Gravity.CENTER
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(8)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream", "application/x-iso9660-image",
                "application/zip", "application/x-7z-compressed"
            ))
        }
        startActivityForResult(intent, REQ_IMAGE)
    }

    @Deprecated("Deprecated Android API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == REQ_IMAGE) {
            imageSelectionVersion++
            imageUri = uri
            try {
                val readFlag = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (readFlag != 0) contentResolver.takePersistableUriPermission(uri, readFlag)
            } catch (_: SecurityException) {
                // Alguns provedores não oferecem permissão persistente.
            } catch (_: IllegalArgumentException) {
                // O provedor não suporta persistência para este URI.
            }
            imageName = queryName(uri)
            imageSize = querySize(uri)
            imageInfo.text = "Arquivo: " + imageName + "\nTamanho: " + formatSize(imageSize) +
                "\nSHA-256: ainda não calculado"
            status.text = "Imagem selecionada. Confira a compatibilidade com o dispositivo escolhido."
        } else if (requestCode == REQ_TREE) {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try {
                if (flags != 0) contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Alguns provedores não permitem persistir a permissão.
            } catch (_: IllegalArgumentException) {
                // O provedor não permite persistir este URI.
            }
            destinationTreeUri = uri
            status.text = "Destino selecionado. Confirme que esta pasta pertence ao microSD: " +
                "a próxima operação copiará a imagem como arquivo, sem gravar setores de inicialização."
        }
    }

    private fun confirmCopyToSdCard() {
        val source = imageUri
        val tree = destinationTreeUri
        if (source == null) {
            status.text = "Selecione primeiro a imagem ISO/IMG."
            return
        }
        if (tree == null) {
            status.text = "Selecione primeiro a pasta do microSD usando o botão de acesso Android."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Copiar imagem como arquivo?")
            .setMessage(
                "Destino: pasta selecionada no Android. O arquivo será copiado para o microSD, " +
                "mas isso NÃO grava setores de inicialização e NÃO torna o cartão inicializável. " +
                "Confirme que a pasta selecionada está no microSD e que há espaço suficiente."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Copiar arquivo") { _, _ -> copyImageToSelectedFolder(source, tree) }
            .show()
    }

    private fun copyImageToSelectedFolder(source: Uri, tree: Uri) {
        val selectionVersion = imageSelectionVersion
        val sourceName = imageName.ifBlank { "imagem.img" }
        status.text = "Preparando cópia para o microSD…"
        worker.execute {
            var destination: Uri? = null
            try {
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree)
                )
                val mime = contentResolver.getType(source) ?: "application/octet-stream"
                destination = DocumentsContract.createDocument(contentResolver, parent, mime, sourceName)
                    ?: error("O provedor Android não conseguiu criar o arquivo de destino.")
                val sourceDigest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                val input = contentResolver.openInputStream(source)
                    ?: error("Não foi possível abrir a imagem de origem.")
                val output = contentResolver.openOutputStream(destination, "w")
                    ?: error("Não foi possível abrir o arquivo de destino para escrita.")
                input.use { inStream ->
                    output.use { outStream ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = inStream.read(buffer)
                            if (count < 0) break
                            outStream.write(buffer, 0, count)
                            sourceDigest.update(buffer, 0, count)
                            copied += count
                            if (copied % (16L * 1024 * 1024) < count) {
                                val progress = copied
                                runOnUiThread {
                                    if (selectionVersion == imageSelectionVersion) {
                                        status.text = "Copiando: " + formatSize(progress) + " transferidos…"
                                    }
                                }
                            }
                        }
                        outStream.flush()
                    }
                }
                val expectedDigest = sourceDigest.digest()
                val verifyDigest = MessageDigest.getInstance("SHA-256")
                val verifyInput = contentResolver.openInputStream(destination)
                    ?: error("Arquivo copiado, mas o Android não permitiu reabri-lo para verificação.")
                var verifiedBytes = 0L
                verifyInput.use { stream ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        verifyDigest.update(buffer, 0, count)
                        verifiedBytes += count
                    }
                }
                if (verifiedBytes != copied || !expectedDigest.contentEquals(verifyDigest.digest())) {
                    error("A verificação após a cópia falhou: tamanho ou SHA-256 diferente.")
                }
                runOnUiThread {
                    if (selectionVersion != imageSelectionVersion) return@runOnUiThread
                    status.text = "Cópia concluída e SHA-256 verificado: " + formatSize(copied) +
                        ". Atenção: o arquivo está no microSD, mas o cartão NÃO foi gravado como inicializável."
                    AlertDialog.Builder(this)
                        .setTitle("Cópia verificada")
                        .setMessage(
                            "Arquivo: " + sourceName + "\nTamanho copiado: " + formatSize(copied) +
                            "\nSHA-256 de origem e destino: iguais.\n\n" +
                            "Isto é somente uma cópia de arquivo. Não é uma gravação raw e não torna o microSD inicializável."
                        )
                        .setPositiveButton("OK", null).show()
                }
            } catch (error: Exception) {
                destination?.let {
                    try { DocumentsContract.deleteDocument(contentResolver, it) } catch (_: Exception) { }
                }
                runOnUiThread {
                    if (selectionVersion != imageSelectionVersion) return@runOnUiThread
                    status.text = "Falha na cópia/verificação: " + (error.message ?: "erro desconhecido") +
                        ". Se houver arquivo parcial, confira a pasta do microSD."
                }
            }
        }
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "imagem sem nome"
        }
        return uri.lastPathSegment ?: "imagem sem nome"
    }

    private fun querySize(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return -1L
    }

    private fun formatSize(size: Long): String {
        if (size < 0) return "desconhecido"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = size.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return String.format(Locale.US, "%.2f %s", value, units[index])
    }

    private fun calculateSha256() {
        val uri = imageUri
        if (uri == null) {
            status.text = "Selecione uma imagem primeiro."
            return
        }
        val selectionVersion = imageSelectionVersion
        val selectedName = imageName
        val selectedSize = imageSize
        status.text = "Calculando SHA-256… arquivos grandes podem levar algum tempo."
        worker.execute {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val input = contentResolver.openInputStream(uri) ?: error("Não foi possível abrir o arquivo.")
                input.use { stream ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val result = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                runOnUiThread {
                    if (selectionVersion != imageSelectionVersion) return@runOnUiThread
                    imageInfo.text = "Arquivo: " + selectedName + "\nTamanho: " + formatSize(selectedSize) +
                        "\nSHA-256: " + result
                    val expected = expectedHashInput.text.toString()
                    status.text = when {
                        Sha256Verifier.normalize(expected).isEmpty() ->
                            "SHA-256 calculado. Se tiver o hash oficial, informe-o para comparar."
                        Sha256Verifier.matches(result, expected) == null ->
                            "Hash calculado, mas o valor informado não tem formato SHA-256 válido (64 caracteres hexadecimais)."
                        Sha256Verifier.matches(result, expected) == true ->
                            "VERIFICADO: o SHA-256 corresponde exatamente ao valor informado."
                        else ->
                            "ATENÇÃO: o SHA-256 NÃO corresponde ao valor informado. Não use esta imagem até confirmar a origem."
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (selectionVersion != imageSelectionVersion) return@runOnUiThread
                    status.text = "Falha ao calcular SHA-256: " + (error.message ?: "erro desconhecido")
                }
            }
        }
    }

    private fun requestUsbPermission() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values.toList()
        if (devices.isEmpty()) {
            status.text = "Nenhum USB encontrado. Conecte o leitor por OTG e tente novamente."
            return
        }

        val target = devices.firstOrNull { device ->
            (0 until device.interfaceCount).any { index ->
                device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
            }
        } ?: devices.first()

        if (manager.hasPermission(target)) {
            status.text = "O Android já autorizou o acesso USB ao dispositivo " + target.deviceName +
                ". A escrita raw ainda não está implementada."
            return
        }

        pendingUsbReceiver?.let {
            try { unregisterReceiver(it) } catch (_: IllegalArgumentException) { }
            pendingUsbReceiver = null
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                try { unregisterReceiver(this) } catch (_: IllegalArgumentException) { }
                if (pendingUsbReceiver === this) pendingUsbReceiver = null

                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
                status.text = if (granted) {
                    "Autorização USB concedida para " + (device?.deviceName ?: target.deviceName) +
                        ". Isso permite comunicação USB autorizada, mas a gravação raw ainda não está implementada."
                } else {
                    "Autorização USB negada. Nenhuma gravação foi realizada."
                }
            }
        }
        pendingUsbReceiver = receiver
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }

        val permissionIntent = PendingIntent.getBroadcast(
            this,
            target.deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        manager.requestPermission(target, permissionIntent)
        status.text = "Aguardando a confirmação de permissão USB do Android…"
    }

    private fun inspectUsbDevices() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val connected = manager.deviceList.values.toList()
        if (connected.isEmpty()) {
            status.text = "Nenhum dispositivo USB detectado. Conecte o leitor por USB OTG e tente novamente."
            return
        }
        val details = connected.joinToString("\n\n") { device ->
            val storageInterfaces = (0 until device.interfaceCount).count { index ->
                device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
            }
            "Nome: " + device.deviceName + "\nFabricante ID: " + device.vendorId +
                " · Produto ID: " + device.productId + "\nClasse do dispositivo: " + device.deviceClass +
                " · Interfaces: " + device.interfaceCount +
                "\nInterfaces Mass Storage: " + storageInterfaces +
                (if (storageInterfaces > 0) " (compatível com armazenamento USB)" else " (armazenamento não identificado)") +
                "\nAcesso autorizado pelo Android: " + manager.hasPermission(device)
        }
        AlertDialog.Builder(this)
            .setTitle("Dispositivos USB detectados")
            .setMessage(details + "\n\nDetectar um leitor não significa que a gravação raw esteja disponível. Esta versão ainda não grava.")
            .setPositiveButton("OK", null).show()
        status.text = "USB detectado: " + connected.size +
            " dispositivo(s). A escrita raw continua desabilitada."
    }

    private fun openWebSearch(query: String) {
        val url = "https://www.google.com/search?q=" + Uri.encode(query)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onDestroy() {
        pendingUsbReceiver?.let {
            try { unregisterReceiver(it) } catch (_: IllegalArgumentException) { }
            pendingUsbReceiver = null
        }
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_IMAGE = 1001
        private const val REQ_TREE = 1002
        private const val ACTION_USB_PERMISSION = "com.pocketflasher.android.USB_PERMISSION"
    }
}
