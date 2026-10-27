package com.pocketflasher.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var imageUri: Uri? = null
    private var imageName = ""
    private var imageSize = -1L
    private lateinit var status: TextView
    private lateinit var imageInfo: TextView
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
        content.addView(makeButton("Calcular SHA-256 da imagem").apply {
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

        section(content, "4 · Cartão SD / USB")
        content.addView(makeButton("Selecionar pasta/cartão (acesso Android)").apply {
            setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE) }
        }, matchWrap())
        content.addView(makeButton("Gravar imagem inicializável").apply {
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Gravação raw ainda não habilitada")
                    .setMessage(
                        "Esta versão seleciona imagens, calcula SHA-256 e pesquisa informações de tela. " +
                        "Ela ainda NÃO grava setores de inicialização do cartão. O seletor de pastas do Android " +
                        "não fornece, por si só, acesso bruto ao dispositivo. Não use esta versão como gravador Rufus."
                    )
                    .setPositiveButton("Entendi", null).show()
            }
        }, matchWrap())

        section(content, "Estado")
        status = TextView(this).apply {
            text = "Pronto. A gravação raw requer um backend específico para o método de conexão do cartão."
            textSize = 13f
            setTextColor(0xFFFFC857.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF1B2230.toInt())
        }
        content.addView(status, matchWrap())
        content.addView(TextView(this).apply {
            text = "Aviso: gravar uma imagem no dispositivo errado pode apagar todos os dados. " +
                "Antes de habilitar a gravação, o app deverá confirmar capacidade, destino e checksum."
            textSize = 12f
            setTextColor(0xFFADB7C7.toInt())
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(page)
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
            status.text = "Pasta selecionada: " + uri +
                ". Isso não significa acesso raw ao cartão nem que ele será inicializável."
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
                val result = digest.digest().joinToString("") { "%02x".format(it) }
                runOnUiThread {
                    imageInfo.text = "Arquivo: " + imageName + "\nTamanho: " + formatSize(imageSize) +
                        "\nSHA-256: " + result
                    status.text = "SHA-256 calculado. Compare com o valor publicado pelo fornecedor da imagem."
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "Falha ao calcular SHA-256: " + (error.message ?: "erro desconhecido")
                }
            }
        }
    }

    private fun inspectUsbDevices() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val connected = manager.deviceList.values.toList()
        if (connected.isEmpty()) {
            status.text = "Nenhum dispositivo USB detectado. Conecte o leitor por USB OTG e tente novamente."
            return
        }
        val details = connected.joinToString("\n\n") { device ->
            "Nome: " + device.deviceName + "\nFabricante ID: " + device.vendorId +
                " · Produto ID: " + device.productId + "\nClasse: " + device.deviceClass +
                " · Interfaces: " + device.interfaceCount + "\nAcesso autorizado: " + manager.hasPermission(device)
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
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_IMAGE = 1001
        private const val REQ_TREE = 1002
    }
}
