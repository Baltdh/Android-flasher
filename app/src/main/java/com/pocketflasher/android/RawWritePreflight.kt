package com.pocketflasher.android

/**
 * Safety gate for a future raw-image backend.
 *
 * This class does not open devices, invoke a shell, request root, or write bytes.
 * A backend must establish these facts independently before a writer can be enabled.
 */
data class RawWriteTarget(
    val stableId: String,
    val capacityBytes: Long,
    val removable: Boolean,
    val writableBySelectedBackend: Boolean,
    val backend: Backend
) {
    enum class Backend { USB_MASS_STORAGE, INTERNAL_MMC }
}

data class RawWriteSource(
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val officialSha256Matched: Boolean
)

data class RawWriteAssessment(
    val allowedForFutureBackend: Boolean,
    val blockers: List<String>
)

object RawWritePreflight {
    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")

    /**
     * Evaluates prerequisites only. A positive result is NOT permission to write:
     * the concrete backend must still revalidate the target immediately before I/O.
     */
    fun assess(source: RawWriteSource, target: RawWriteTarget?): RawWriteAssessment {
        val blockers = mutableListOf<String>()

        if (source.displayName.isBlank()) blockers += "Nome da imagem ausente."
        if (source.sizeBytes <= 0L) blockers += "Tamanho da imagem inválido ou desconhecido."
        if (!sha256Pattern.matches(source.sha256)) blockers += "SHA-256 da imagem ausente ou inválido."
        if (!source.officialSha256Matched) blockers += "Hash oficial não foi confirmado."
        if (target == null) {
            blockers += "Nenhum dispositivo de destino identificado."
        } else {
            if (target.stableId.isBlank()) blockers += "Identificador estável do destino ausente."
            if (target.capacityBytes <= 0L) blockers += "Capacidade do destino inválida ou desconhecida."
            if (!target.removable) blockers += "Destino não foi confirmado como mídia removível."
            if (!target.writableBySelectedBackend) blockers += "Backend não comprovou acesso de escrita ao destino."
            if (source.sizeBytes > 0L && target.capacityBytes > 0L &&
                source.sizeBytes > target.capacityBytes) {
                blockers += "A imagem é maior que a capacidade do destino."
            }
        }

        return RawWriteAssessment(
            allowedForFutureBackend = blockers.isEmpty(),
            blockers = blockers
        )
    }
}
