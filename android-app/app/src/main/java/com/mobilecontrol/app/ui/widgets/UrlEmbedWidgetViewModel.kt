package com.mobilecontrol.app.ui.widgets

import androidx.lifecycle.ViewModel
import com.mobilecontrol.app.domain.repository.UrlEmbedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin DI shim, same reasoning as [CameraWidgetViewModel] - see its doc comment. */
@HiltViewModel
class UrlEmbedWidgetViewModel @Inject constructor(
    private val urlEmbedRepository: UrlEmbedRepository,
) : ViewModel() {
    suspend fun loadContent(id: String): Result<ByteArray> = urlEmbedRepository.fetchContent(id)
    suspend fun resolveUrl(id: String): Result<String> = urlEmbedRepository.resolveUrl(id)
}
