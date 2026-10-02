package dev.better.app.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.better.app.data.*
import dev.better.app.domain.SetupInput
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppState(
    val loading: Boolean = true,
    val settings: AppSettings? = null,
    val standards: List<CareerStandard> = emptyList(),
    val theme: String = "System",
    val saving: Boolean = false,
    val error: String? = null,
)

class AppViewModel(private val repository: LocalRepository, private val preferences: UiPreferences) : ViewModel() {
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    val state = combine(repository.settings, repository.standards, preferences.theme, saving, error) {
        settings, standards, theme, busy, failure ->
        AppState(false, settings, standards, theme, busy, failure)
    }.catch { emit(AppState(loading = false, error = "Local storage could not be opened. Close and reopen BETTER; your data has not been reset.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    fun onboard(input: SetupInput) {
        if (saving.value) return
        error.value = input.validate()
        if (error.value != null) return
        saving.value = true
        viewModelScope.launch {
            try { repository.onboard(input) }
            catch (_: Exception) { error.value = "Could not save your setup. Your existing data has not been replaced. Try again." }
            finally { saving.value = false }
        }
    }
    fun setTheme(theme: String) { viewModelScope.launch {
        try { preferences.setTheme(theme) }
        catch (_: Exception) { error.value = "Could not save appearance. Try again." }
    } }
}
