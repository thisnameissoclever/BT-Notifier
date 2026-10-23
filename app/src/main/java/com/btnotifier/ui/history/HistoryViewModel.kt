package com.btnotifier.ui.history

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btnotifier.data.history.DetectionObservation
import com.btnotifier.data.history.HistoryCsv
import com.btnotifier.data.history.ObservationRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    data class State(
        val rows: List<DetectionObservation> = emptyList(),
        val loading: Boolean = true,
        val busy: Boolean = false,
        val loadFailed: Boolean = false,
        val message: Message? = null
    )
    enum class Message { LOAD_FAILED, CLEAR_FAILED, CLEARED, EXPORT_FAILED, EXPORTED }

    private val repository = ObservationRepository.get(application)
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState
    private var observationJob: Job? = null

    init { refresh() }

    fun refresh() {
        observationJob?.cancel()
        mutableState.update { it.copy(loading = true, loadFailed = false, message = null) }
        observationJob = viewModelScope.launch {
            try {
                repository.prune()
                repository.observeVisible().collect { rows ->
                    mutableState.update { it.copy(rows = rows, loading = false, loadFailed = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("HistoryViewModel", "Loading detection history failed", error)
                mutableState.update { it.copy(loading = false, loadFailed = true, message = Message.LOAD_FAILED) }
            }
        }
    }

    fun clearHistory() = perform(Message.CLEAR_FAILED, Message.CLEARED) { repository.clearHistory() }

    fun export(uri: Uri) = perform(Message.EXPORT_FAILED, Message.EXPORTED) {
        val snapshot = repository.visibleSnapshot().map { it.toSample() }
        withContext(Dispatchers.IO) {
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("The selected destination could not be opened")
            stream.bufferedWriter(Charsets.UTF_8).use { HistoryCsv.write(snapshot, it) }
        }
    }

    private fun perform(failure: Message, success: Message, operation: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                operation()
                mutableState.update { it.copy(message = success) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("HistoryViewModel", "History action failed: $failure", error)
                mutableState.update { it.copy(message = failure) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
