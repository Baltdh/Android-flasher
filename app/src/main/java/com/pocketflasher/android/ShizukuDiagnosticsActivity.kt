package com.pocketflasher.android

import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

/**
 * Diagnóstico de conexão e autorização Shizuku. Não executa comandos shell,
 * não procura dispositivos de bloco e não escreve em armazenamento.
 */
class ShizukuDiagnosticsActivity : Activity() {
    private lateinit var details: TextView

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus("Binder Shizuku recebido.")
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        details.text = "Shizuku desconectado. O serviço pode ter parado ou sido reiniciado. Nenhuma operação foi executada."
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_SHIZUKU_PERMISSION) {
            refreshStatus(
                if (grantResult == PackageManager.PERMISSION_GRANTED)
                    "Permissão Shizuku concedida pelo usuário."
                else
                    "Permissão Shizuku negada. O aplicativo continuará sem privilégios Shizuku."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(0xFF10141C.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        page.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        content.addView(TextView(this).apply {
            text = "DIAGNÓSTICO SHIZUKU"
            textSize = 22f
            setTextColor(0xFFFFC857.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Este teste verifica apenas a conexão Binder e a permissão do aplicativo. Não grava dados."
            textSize = 14f
            setTextColor(0xFFD2D8E2.toInt())
            setPadding(0, dp(8), 0, dp(12))
        })
        details = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF1B2230.toInt())
        }
        content.addView(details, params())
        content.addView(makeButton("Atualizar diagnóstico").apply {
            setOnClickListener { refreshStatus("Diagnóstico atualizado manualmente.") }
        }, params())
        content.addView(makeButton("Solicitar permissão Shizuku").apply {
            setOnClickListener { requestShizukuPermission() }
        }, params())
        content.addView(makeButton("Voltar").apply { setOnClickListener { finish() } }, params())

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        setContentView(page)
        refreshStatus("Diagnóstico iniciado.")
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            refreshStatus("Não há Binder ativo. Abra Shizuku, inicie o serviço e tente novamente.")
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshStatus("O Pocket Flasher já possui permissão Shizuku.")
            } else {
                Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION)
            }
        } catch (error: RuntimeException) {
            refreshStatus("Não foi possível solicitar permissão: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun refreshStatus(prefix: String) {
        if (!::details.isInitialized) return
        if (!Shizuku.pingBinder()) {
            details.text = prefix + "\n\nEstado: Shizuku não conectado / serviço indisponível.\n" +
                "Ação: abra Shizuku e confirme que o serviço está em execução.\n" +
                "Nenhum comando de sistema foi executado."
            return
        }
        try {
            val serverUid = Shizuku.getUid()
            val permission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val identity = when (serverUid) {
                0 -> "root (UID 0) — identidade do servidor"
                2000 -> "shell/ADB (UID 2000) — não equivale a root"
                else -> "UID do servidor: ${serverUid}"
            }
            val permissionText = if (permission) "concedida" else "não concedida"
            details.text = prefix + "\n\nEstado: Binder conectado\nIdentidade: " + identity +
                "\nPermissão do Pocket Flasher: " + permissionText +
                "\n\nLimite: conexão e permissão não provam acesso ao dispositivo microSD nem autorização para gravar setores. " +
                "Este teste não tenta acesso raw."
        } catch (error: RuntimeException) {
            details.text = "Binder detectado, mas a consulta falhou: ${error.message ?: error.javaClass.simpleName}\n" +
                "Nenhuma gravação foi tentada."
        }
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        gravity = Gravity.CENTER
        setTextColor(0xFF10141C.toInt())
        setBackgroundColor(0xFFFFC857.toInt())
    }

    private fun params() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(8)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_SHIZUKU_PERMISSION = 5107
    }
}
