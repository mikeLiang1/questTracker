package com.mikeliang.questtracker.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikeliang.questtracker.core.onboarding.StartingClass
import com.mikeliang.questtracker.core.onboarding.questLoadout
import com.mikeliang.questtracker.ui.theme.QuestTrackerTheme
import java.time.Instant

/**
 * Stateful entry point. Owns the Health Connect permission launcher (an Activity
 * contract, so it cannot live in the ViewModel); grant and deny both proceed —
 * the flow never blocks on the answer.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(),
    onFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.onEvent(OnboardingEvent.HealthPermissionResult) }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    OnboardingContent(
        state = state,
        onEvent = viewModel::onEvent,
        onConnectHealth = { healthLauncher.launch(state.healthPermissions) },
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    onConnectHealth: () -> Unit,
) {
    when (state.step) {
        OnboardingStep.ChooseClass -> ChooseClassStep(
            applying = state.applying,
            onEvent = onEvent,
        )
        OnboardingStep.HealthConnect -> HealthConnectStep(
            onConnect = onConnectHealth,
            onSkip = { onEvent(OnboardingEvent.SkipHealth) },
        )
    }
}

/**
 * Step 1: pick a starting class. One tap to a working board — no account, no name,
 * no taxonomy. The cards show only the quests you'd start with.
 */
@Composable
private fun ChooseClassStep(
    applying: Boolean,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Choose your starting class",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "A ready-made set of quests. Everything can be changed later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        StartingClass.entries.forEach { startingClass ->
            StartingClassCard(
                startingClass = startingClass,
                enabled = !applying,
                onChoose = { onEvent(OnboardingEvent.ClassChosen(startingClass)) },
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { onEvent(OnboardingEvent.SkipPresets) },
            enabled = !applying,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Skip — start with an empty list")
        }
    }
}

@Composable
private fun StartingClassCard(
    startingClass: StartingClass,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    // Only the titles are shown, so a fixed instant is fine here; the real loadout is
    // stamped with Clock.now() by the ViewModel at choice time.
    val questTitles = remember(startingClass) {
        startingClass.questLoadout(Instant.EPOCH).map { it.title }
    }
    Card(
        onClick = onChoose,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = startingClass.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = startingClass.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            questTitles.forEach { title ->
                Text(
                    text = "• $title",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Identity flavor only — the mechanics live entirely in the loadout. */
private val StartingClass.tagline: String
    get() = when (this) {
        StartingClass.Warrior -> "Train the body first."
        StartingClass.Sage -> "Sharpen the mind first."
        StartingClass.Adventurer -> "A bit of everything."
    }

/**
 * Step 2: the optional Health Connect ask, shown only when there is something to
 * grant. Manual mode is framed as fully functional, not a fallback of last resort.
 */
@Composable
private fun HealthConnectStep(
    onConnect: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Count steps automatically?",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Quest Tracker can count steps and sleep for you through Health " +
                "Connect. Prefer to tick quests off yourself? Manual mode always works.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Connect Health Connect")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Skip for now")
        }
    }
}

// --- Previews -------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun ChooseClassPreview() {
    QuestTrackerTheme {
        OnboardingContent(state = OnboardingUiState(), onEvent = {}, onConnectHealth = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HealthConnectPreview() {
    QuestTrackerTheme {
        OnboardingContent(
            state = OnboardingUiState(step = OnboardingStep.HealthConnect),
            onEvent = {},
            onConnectHealth = {},
        )
    }
}
