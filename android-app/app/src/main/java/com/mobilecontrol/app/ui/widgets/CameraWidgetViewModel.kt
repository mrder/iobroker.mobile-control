package com.mobilecontrol.app.ui.widgets

import androidx.lifecycle.ViewModel
import com.mobilecontrol.app.domain.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin DI shim, same reasoning as [HistoryWidgetViewModel] - see its doc comment. */
@HiltViewModel
class CameraWidgetViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
) : ViewModel() {
    suspend fun loadSnapshot(objectId: String): Result<ByteArray> = cameraRepository.fetchSnapshot(objectId)
}
