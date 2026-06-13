package com.cauldron.myriad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cauldron.myriad.ui.GameScreen
import com.cauldron.myriad.ui.MyriadTheme
import com.cauldron.myriad.ui.SkillsScreen
import com.cauldron.myriad.ui.TitleScreen

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyriadTheme {
                MyriadRoot(viewModel)
            }
        }
    }

    override fun onStop() {
        // OXO OS may kill the process the moment we leave the foreground;
        // never rely on onDestroy (MASTER_PLAN §2, §8).
        viewModel.flushSaveBlocking()
        super.onStop()
    }
}

@Composable
private fun MyriadRoot(viewModel: GameViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    when (val state = ui) {
        GameViewModel.UiState.Loading ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        is GameViewModel.UiState.Title ->
            TitleScreen(
                hasSave = state.hasSave,
                onContinue = viewModel::continueGame,
                onNewRun = viewModel::newRun,
            )

        is GameViewModel.UiState.Playing ->
            if (state.showSkills) {
                SkillsScreen(
                    playing = state,
                    onUnlock = viewModel::unlockNode,
                    onRespec = viewModel::respec,
                    onClose = viewModel::closeSkills,
                )
            } else {
                GameScreen(
                    playing = state,
                    content = viewModel.content,
                    onAct = viewModel::act,
                    onNewRun = viewModel::newRun,
                    onOpenSkills = viewModel::openSkills,
                )
            }
    }
}
