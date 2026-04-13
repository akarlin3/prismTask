package com.averycorp.prismtask.ui.screens.onboarding

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.averycorp.prismtask.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOTAL_PAGES = 7

private val accentColors = listOf(
    "#2563EB", "#7C3AED", "#DB2777", "#DC2626", "#EA580C", "#D97706",
    "#65A30D", "#059669", "#0891B2", "#6366F1", "#8B5CF6", "#EC4899"
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> SmartTasksPage()
                2 -> NaturalLanguagePage()
                3 -> HabitsPage()
                4 -> ViewsPage()
                5 -> BrainModePage(viewModel = viewModel)
                6 -> SetupPage(
                    viewModel = viewModel,
                    onComplete = {
                        viewModel.completeOnboarding()
                        onComplete()
                    }
                )
            }
        }

        // Skip button (pages 0-4)
        if (pagerState.currentPage < 6) {
            TextButton(
                onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(6) }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
            ) {
                Text("Skip")
            }
        }

        // Bottom controls
        if (pagerState.currentPage < 6) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(TOTAL_PAGES) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateFloatAsState(
                            targetValue = if (isSelected) 24f else 8f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Navigation buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        ) {
                            Text("Back")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    ) {
                        Text(if (pagerState.currentPage == 0) "Get Started" else "Next")
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val scale = remember { Animatable(0.5f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = "\uD83D\uDD73",
                fontSize = 80.sp,
                modifier = Modifier.scale(scale.value)
            )
            Spacer(modifier = Modifier.height(24.dp))
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 30 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Welcome to PrismTask",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your smart, adaptive productivity companion",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartTasksPage() {
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animStarted = true }

    OnboardingPageLayout(
        emoji = "\u2705",
        headline = "Organize Everything",
        body = "Projects, tags, subtasks, and priorities. Drag to reorder, bulk edit, and quick-reschedule with a tap."
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            listOf("Buy groceries", "Finish report", "Call dentist").forEachIndexed { index, task ->
                AnimatedVisibility(
                    visible = animStarted,
                    enter = fadeIn(tween(300, delayMillis = index * 150)) +
                            slideInVertically(tween(300, delayMillis = index * 150)) { it }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(task, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NaturalLanguagePage() {
    var showChips by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }
    val fullText = "Buy groceries tomorrow !high #errands"

    LaunchedEffect(Unit) {
        for (i in fullText.indices) {
            typedText = fullText.substring(0, i + 1)
            delay(40)
        }
        delay(300)
        showChips = true
    }

    OnboardingPageLayout(
        emoji = "\u2328\uFE0F",
        headline = "Type Naturally",
        body = "Just type 'Buy groceries tomorrow !high #errands' and PrismTask understands instantly."
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = typedText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedVisibility(
                visible = showChips,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { 20 }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChipLabel("Tomorrow", MaterialTheme.colorScheme.primary)
                    ChipLabel("High", Color(0xFFE86F3C))
                    ChipLabel("#errands", MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun ChipLabel(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun HabitsPage() {
    var streakCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 1..14) {
            streakCount = i
            delay(40)
        }
    }

    OnboardingPageLayout(
        emoji = "\uD83D\uDD25",
        headline = "Build Habits, Stay Focused",
        body = "Track daily habits with streaks and analytics. Use AI-powered focus sessions to get more done."
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("\uD83D\uDD25", fontSize = 36.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$streakCount days this week",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                repeat(7) { day ->
                    val filled = day < 5
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (filled) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ViewsPage() {
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animStarted = true }

    OnboardingPageLayout(
        emoji = "\uD83D\uDC41\uFE0F",
        headline = "See Your Way",
        body = "Today focus, week planner, calendar, timeline, and Eisenhower matrix. Your tasks, your view."
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            listOf("Today" to "\u2600\uFE0F", "Week" to "\uD83D\uDCC5", "Month" to "\uD83D\uDDD3\uFE0F").forEachIndexed { index, (label, icon) ->
                AnimatedVisibility(
                    visible = animStarted,
                    enter = fadeIn(tween(400, delayMillis = index * 200)) +
                            slideInVertically(tween(400, delayMillis = index * 200)) { 40 }
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(icon, fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrainModePage(viewModel: OnboardingViewModel) {
    var adhdSelected by remember { mutableStateOf(false) }
    var calmSelected by remember { mutableStateOf(false) }
    var focusReleaseSelected by remember { mutableStateOf(false) }
    var expandedCard by remember { mutableIntStateOf(-1) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 60.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 30 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\uD83E\uDDE0",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "How Does Your Brain Work?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select any that apply \u2014 or skip if none fit. You can always change these in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Card 1: ADHD Mode
        BrainModeCard(
            emoji = "\u26A1",
            title = "I Get Distracted Easily",
            subtitle = "Hard to start tasks, lose track of time, need momentum to keep going",
            expandedDescription = "This turns on: task decomposition to break big tasks into small wins, " +
                    "focus guard timers, body doubling check-ins, completion celebrations, " +
                    "progress bars, and forgiveness streaks.",
            isSelected = adhdSelected,
            isExpanded = expandedCard == 0,
            onToggle = {
                adhdSelected = !adhdSelected
                viewModel.setAdhdMode(!adhdSelected.not()) // toggle already flipped
                if (expandedCard != 0) expandedCard = 0 else expandedCard = -1
            },
            onExpandToggle = { expandedCard = if (expandedCard == 0) -1 else 0 },
            index = 0
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Calm Mode
        BrainModeCard(
            emoji = "\uD83C\uDF3F",
            title = "I Get Overstimulated Easily",
            subtitle = "Animations, bright colors, and sounds can be too much",
            expandedDescription = "This turns on: reduced animations, muted color palette, " +
                    "quiet mode (no sounds), reduced haptics, and soft contrast throughout the app.",
            isSelected = calmSelected,
            isExpanded = expandedCard == 1,
            onToggle = {
                calmSelected = !calmSelected
                viewModel.setCalmMode(!calmSelected.not())
                if (expandedCard != 1) expandedCard = 1 else expandedCard = -1
            },
            onExpandToggle = { expandedCard = if (expandedCard == 1) -1 else 1 },
            index = 1
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: Focus & Release Mode
        BrainModeCard(
            emoji = "\uD83E\uDD32",
            title = "I Have Trouble Letting Go of Tasks",
            subtitle = "Spend too long polishing, re-check finished work, struggle to call things done",
            expandedDescription = "This turns on: \u2018good enough\u2019 timers that gently nudge you to finish, " +
                    "guards against endlessly re-editing completed work, celebrations for " +
                    "shipping (not perfecting), and help when you\u2019re stuck choosing.",
            isSelected = focusReleaseSelected,
            isExpanded = expandedCard == 2,
            onToggle = {
                focusReleaseSelected = !focusReleaseSelected
                viewModel.setFocusReleaseMode(!focusReleaseSelected.not())
                if (expandedCard != 2) expandedCard = 2 else expandedCard = -1
            },
            onExpandToggle = { expandedCard = if (expandedCard == 2) -1 else 2 },
            index = 2
        )
    }
}

@Composable
private fun BrainModeCard(
    emoji: String,
    title: String,
    subtitle: String,
    expandedDescription: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onExpandToggle: () -> Unit,
    index: Int
) {
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 100L)
        animStarted = true
    }

    AnimatedVisibility(
        visible = animStarted,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { 40 }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (isSelected)
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else
                null
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(emoji, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "$title selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Expandable preview
                AnimatedVisibility(visible = isExpanded || isSelected) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = expandedDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupPage(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val signInState by viewModel.signInState
        .let { flow ->
            val state = remember { mutableStateOf<SignInState>(SignInState.NotSignedIn) }
            LaunchedEffect(Unit) { flow.collect { state.value = it } }
            state
        }
    val themeMode by viewModel.themeMode
        .let { flow ->
            val state = remember { mutableStateOf("system") }
            LaunchedEffect(Unit) { flow.collect { state.value = it } }
            state
        }
    val accentColor by viewModel.accentColor
        .let { flow ->
            val state = remember { mutableStateOf("#2563EB") }
            LaunchedEffect(Unit) { flow.collect { state.value = it } }
            state
        }
    var taskText by remember { mutableStateOf("") }
    var taskCreated by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Let's Get You Started",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set up your preferences (all optional)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 1. Sign In Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sign In with Google", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Sync your tasks across devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                when (signInState) {
                    is SignInState.SignedIn -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text((signInState as? SignInState.SignedIn)?.email ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is SignInState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val option = GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build()
                                            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                                            val result = CredentialManager.create(context).getCredential(context as Activity, request)
                                            val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                                            viewModel.onGoogleSignIn(idToken)
                                        } catch (_: GetCredentialCancellationException) {
                                            // User cancelled
                                        } catch (_: Exception) {
                                            // Handle error silently for onboarding
                                        }
                                    }
                                }
                            ) {
                                Text("Sign In")
                            }
                            TextButton(onClick = { /* skip — do nothing */ }) {
                                Text("Set Up Later")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Theme Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pick Your Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (value, label) ->
                        FilterChip(
                            selected = themeMode == value,
                            onClick = { viewModel.setThemeMode(value) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    accentColors.forEach { hex ->
                        val color = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (_: Exception) {
                            Color.Gray
                        }
                        val isSelected = accentColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { viewModel.setAccentColor(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Quick Task Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Create Your First Task", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))
                if (taskCreated) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Task created!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    OutlinedTextField(
                        value = taskText,
                        onValueChange = { taskText = it },
                        placeholder = { Text("e.g., Buy groceries tomorrow") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (taskText.isNotBlank()) {
                                    viewModel.createQuickTask(taskText)
                                    taskCreated = true
                                }
                            }
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Complete button
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Using PrismTask")
        }
    }
}

@Composable
private fun OnboardingPageLayout(
    emoji: String,
    headline: String,
    body: String,
    illustration: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 30 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 40.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        illustration()
    }
}
