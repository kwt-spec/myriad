package com.cauldron.myriad

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cauldron.myriad.content.EmberCellar
import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.FeedKind
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.appendFeed
import com.cauldron.myriad.engine.persist.SaveCodec
import com.cauldron.myriad.engine.persist.SaveFile
import com.cauldron.myriad.engine.persist.SaveStore
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface UiState {
        data object Loading : UiState
        data class Title(val hasSave: Boolean) : UiState
        data class Playing(
            val game: GameState,
            val chips: List<Chip>,
            /** Events from the step that produced this state — drives popups/shake/haptics. */
            val lastEvents: List<Event> = emptyList(),
        ) : UiState
    }

    data class Chip(val action: Action, val label: String)

    val content = EmberCellar.pack
    private val engine = Engine(content)
    private val store = SaveStore(app.filesDir.toPath().resolve("saves"))

    private val _ui = MutableStateFlow<UiState>(UiState.Loading)
    val ui: StateFlow<UiState> = _ui

    // Latest-wins autosave pipeline: StateFlow conflates to the newest snapshot,
    // so a burst of turns can never persist out of order (MASTER_PLAN §8).
    private val pendingSave = MutableStateFlow<SaveFile?>(null)
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = UiState.Title(hasSave = store.hasLoadableSave())
        }
        viewModelScope.launch(Dispatchers.IO) {
            pendingSave.filterNotNull().collect { save ->
                saveMutex.withLock { runCatching { store.write(save) } }
            }
        }
        PanicSaver.hook = {
            pendingSave.value?.let { save -> store.write(save) }
        }
    }

    fun continueGame() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = store.load()
            if (loaded == null) {
                _ui.value = UiState.Title(hasSave = false)
                return@launch
            }
            var game = loaded.save.state
            if (loaded.usedBackupIndex > 0) {
                game = game.appendFeed(
                    listOf(FeedKind.SYSTEM to "The primary save was damaged; restored from backup ${loaded.usedBackupIndex}.")
                )
            }
            present(game)
        }
    }

    fun newRun() {
        present(engine.newGame(SecureRandom().nextLong(), "Wanderer"))
    }

    /** Game logic runs on the main thread only — a single-threaded actor by construction. */
    fun act(action: Action) {
        val current = (_ui.value as? UiState.Playing)?.game ?: return
        val result = engine.step(current, action)
        present(result.state, result.events)
    }

    private fun present(game: GameState, events: List<Event> = emptyList()) {
        _ui.value = UiState.Playing(game, chipsFor(game), events)
        pendingSave.value = SaveCodec.fresh(game)
    }

    /** OXO OS kills backgrounded apps eagerly; onStop is our last reliable moment. */
    fun flushSaveBlocking() {
        val save = pendingSave.value ?: return
        runBlocking { saveMutex.withLock { runCatching { store.write(save) } } }
    }

    private fun chipsFor(game: GameState): List<Chip> =
        engine.legalActions(game).map { action ->
            Chip(
                action = action,
                label = when (action) {
                    Action.Look -> "Look around"
                    Action.Search -> "Search"
                    Action.QuickStrike -> "Quick strike"
                    Action.HeavyStrike -> "Heavy strike"
                    Action.Brace -> "Brace"
                    Action.Flee -> "Flee"
                    is Action.Move ->
                        content.rooms.getValue(game.currentRoom)
                            .exits.firstOrNull { it.to == action.to }?.label ?: "Go"
                    is Action.Take -> "Take ${content.items.getValue(action.item).name}"
                    is Action.Equip -> "Wield ${content.items.getValue(action.item).name}"
                },
            )
        }

    override fun onCleared() {
        PanicSaver.hook = null
        super.onCleared()
    }
}
