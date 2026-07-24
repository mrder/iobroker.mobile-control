package com.mobilecontrol.app.ui.widgets

import androidx.lifecycle.ViewModel
import com.mobilecontrol.app.domain.repository.UrlEmbedRepository
import com.mobilecontrol.app.tunnel.TunnelSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin DI shim, same reasoning as [CameraWidgetViewModel] - see its doc comment. */
@HiltViewModel
class UrlEmbedWidgetViewModel @Inject constructor(
    private val urlEmbedRepository: UrlEmbedRepository,
    private val tunnelSessionManager: TunnelSessionManager,
) : ViewModel() {
    suspend fun loadContent(id: String): Result<ByteArray> = urlEmbedRepository.fetchContent(id)
    suspend fun resolveUrl(id: String): Result<String> = urlEmbedRepository.resolveUrl(id)

    /** See TunnelSessionManager.start - no-ops (returns false) if unsupported/not http://. */
    suspend fun startTunnel(id: String, targetUrl: String): Boolean = tunnelSessionManager.start(id, targetUrl)
    suspend fun stopTunnel() = tunnelSessionManager.stop()
}
