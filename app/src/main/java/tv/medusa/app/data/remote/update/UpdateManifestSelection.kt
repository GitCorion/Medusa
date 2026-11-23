package tv.medusa.app.data.remote.update

/**
 * Reglas de selección de manifiesto OTA para los canales estable y beta.
 *
 * - El canal estable solo lee `version.json`.
 * - El canal beta lee ambos y se queda con el `versionCode` más alto: así una estable posterior
 *   gradúa al equipo sin reinstalar y un manifiesto beta caído no bloquea la app.
 * - `killSwitch`, `minSupportedVersionCode` y `signatures` son globales y mandan desde el
 *   manifiesto estable.
 */
internal object UpdateManifestSelection {

    const val STABLE_PATH = "api/version.json"
    const val BETA_PATH = "api/version-beta.json"

    fun isBeta(channel: String): Boolean = channel.equals("beta", ignoreCase = true)

    fun merge(stable: UpdateManifestDto?, beta: UpdateManifestDto?): UpdateManifestDto? {
        val control = stable ?: beta ?: return null
        val newest = listOfNotNull(stable, beta).maxByOrNull { it.versionCode } ?: control
        return newest.copy(
            minSupportedVersionCode = maxOf(
                stable?.minSupportedVersionCode ?: 0L,
                beta?.minSupportedVersionCode ?: 0L
            ),
            forceUpdate = stable?.forceUpdate == true || beta?.forceUpdate == true,
            killSwitch = stable?.killSwitch == true || beta?.killSwitch == true,
            signatures = control.signatures
        )
    }
}
