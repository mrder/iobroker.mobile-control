package com.mobilecontrol.app.ui.dashboards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecontrol.app.domain.model.UrlEmbed
import com.mobilecontrol.app.domain.repository.UrlEmbedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Feeds [AddWidgetDialog]'s "URL-Einbettungen" tab - a one-shot fetch is enough since the
 *  allowlist is admin-managed and rarely changes while a dashboard is being edited. */
@HiltViewModel
class UrlEmbedPickerViewModel @Inject constructor(
    private val urlEmbedRepository: UrlEmbedRepository,
) : ViewModel() {
    private val _embeds = MutableStateFlow<List<UrlEmbed>>(emptyList())
    val embeds: StateFlow<List<UrlEmbed>> = _embeds

    init {
        viewModelScope.launch {
            urlEmbedRepository.listEmbeds().onSuccess { _embeds.value = it }
        }
    }
}
