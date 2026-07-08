package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AgentChatMessage
import com.example.data.AgentMemory
import com.example.data.AgentSubTask
import com.example.data.AgentTask
import com.example.ui.AgentViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AgentViewModel = viewModel()) {
    var currentTab by remember { mutableStateOf(0) } // 0 = Chat, 1 = Tasks, 2 = Memories
    val scope = rememberCoroutineScope()

    val tasks by viewModel.allTasks.collectAsState()
    val memories by viewModel.allMemories.collectAsState()
    val generalMessages by viewModel.generalChatMessages.collectAsState()
    
    val selectedTaskId by viewModel.selectedTaskId.collectAsState()
    val selectedTask by viewModel.selectedTask.collectAsState()
    val selectedSubtasks by viewModel.selectedSubtasks.collectAsState()
    val selectedTaskChat by viewModel.selectedTaskChat.collectAsState()
    val executingTaskIds by viewModel.executingTaskIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (viewModel.isApiKeyConfigured) TerminalGreen else AmberWarm)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Joe joe (SecOps)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                text = if (viewModel.isApiKeyConfigured) "Uplink Secure" else "Emulated Local Deck",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (currentTab == 1) viewModel.clearAllTasks()
                            else if (currentTab == 2) viewModel.clearAllMemories()
                        },
                        modifier = Modifier.testTag("clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Current Tab Items",
                            tint = ErrorRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SlateCard,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Face, contentDescription = "Chat with Joe joe") },
                    label = { Text("Chat") },
                    modifier = Modifier.testTag("tab_chat")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Autonomous Tasks") },
                    label = { Text("Tasks") },
                    modifier = Modifier.testTag("tab_tasks")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Memory Bank") },
                    label = { Text("Memories") },
                    modifier = Modifier.testTag("tab_memories")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SlateDark)
        ) {
            when (currentTab) {
                0 -> CompanionChatView(generalMessages, viewModel)
                1 -> TasksView(
                    tasks = tasks,
                    selectedTaskId = selectedTaskId,
                    selectedTask = selectedTask,
                    subtasks = selectedSubtasks,
                    taskChat = selectedTaskChat,
                    executingTaskIds = executingTaskIds,
                    viewModel = viewModel
                )
                2 -> MemoriesView(memories, viewModel)
            }
        }
    }
}

// ==========================================
// 1. Companion Chat Screen
// ==========================================
@Composable
fun CompanionChatView(messages: List<AgentChatMessage>, viewModel: AgentViewModel) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Welcome Header Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AmberWarm),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = SlateDark
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Joe joe (Operations Console)",
                        fontWeight = FontWeight.Bold,
                        color = AmberWarm,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "SecOps diagnostic terminal & controller. Core ready for pentests, firmware recovery, and automation scripts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SteelGrey
                    )
                }
            }
        }

        if (!viewModel.isApiKeyConfigured) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                color = AmberWarm.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AmberWarm.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarm)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Joe joe: \"SecOps uplink is offline (no API Key). Running in local diagnostic emulation mode—full pentests, recoveries, and command controls can still be processed.\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperCream
                    )
                }
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.sender == "USER"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .background(if (isUser) SlateBorder else SlateCard)
                            .border(
                                1.dp,
                                if (isUser) SlateBorder else SlateBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isUser) "You" else "Joe joe",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) SteelGrey else AmberWarm,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PaperCream
                                )
                            )
                        }
                    }
                }
            }
        }

        // Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Speak with Joe joe...", color = SteelGrey) },
                modifier = Modifier
                    .weight(1.0f)
                    .testTag("chat_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberWarm,
                    unfocusedBorderColor = SlateBorder,
                    focusedTextColor = PaperCream,
                    unfocusedTextColor = PaperCream,
                    focusedContainerColor = SlateCard,
                    unfocusedContainerColor = SlateCard
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendGeneralMessage(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(AmberWarm, CircleShape)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Message",
                    tint = SlateDark
                )
            }
        }
    }
}

// ==========================================
// 2. Tackle Tasks Screen
// ==========================================
@Composable
fun TasksView(
    tasks: List<AgentTask>,
    selectedTaskId: Int?,
    selectedTask: AgentTask?,
    subtasks: List<AgentSubTask>,
    taskChat: List<AgentChatMessage>,
    executingTaskIds: Set<Int>,
    viewModel: AgentViewModel
) {
    if (selectedTaskId == null || selectedTask == null) {
        // Show Task List & Create Input
        var newTaskTitle by remember { mutableStateOf("") }
        val presets = listOf(
            "Perform CVE-2026 exploit audit & security hardening scan",
            "Unbrick Android device stuck in fastboot bootloop",
            "Deploy active system daemon to automate process monitoring & firewall control"
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "SecOps Operations Deck",
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberWarm,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Define a security audit, system repair protocol, or automation script target. Joe joe will plan and execute the necessary subprocesses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SteelGrey
                )
            }

            // Input Form
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "New Goal Input",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = PaperCream
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            placeholder = { Text("What are we achieving today?", color = SteelGrey) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("task_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberWarm,
                                unfocusedBorderColor = SlateBorder,
                                focusedTextColor = PaperCream,
                                unfocusedTextColor = PaperCream,
                                focusedContainerColor = SlateDark,
                                unfocusedContainerColor = SlateDark
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    viewModel.createTaskAndStartLoop(newTaskTitle)
                                    newTaskTitle = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarm, contentColor = SlateDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_task_button")
                        ) {
                            Text("Launch Autonomous Loop", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Suggested goals to test:",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberGlow
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        presets.forEach { preset ->
                            Text(
                                text = "💡 $preset",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = SteelGrey,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { newTaskTitle = preset }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "SecOps Pipeline History",
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberWarm,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (tasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No operations executed yet. Submit a goal above to spin up the SecOps core!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SteelGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(tasks) { task ->
                    val isRunning = executingTaskIds.contains(task.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectTask(task.id) },
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.Bold,
                                    color = PaperCream,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusColor = when (task.status) {
                                        "COMPLETED" -> TerminalGreen
                                        "FAILED" -> ErrorRed
                                        "PENDING" -> SteelGrey
                                        else -> AmberWarm // Planning/Executing
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = task.status + if (isRunning) " (Running)" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Task", tint = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Detailed Task execution Screen
        val isRunning = executingTaskIds.contains(selectedTask.id)

        Column(modifier = Modifier.fillMaxSize()) {
            // Task Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Goal Dashboard",
                            color = AmberWarm,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Back to List",
                            color = SteelGrey,
                            modifier = Modifier
                                .clickable { viewModel.selectTask(null) }
                                .padding(4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedTask.title,
                        fontWeight = FontWeight.Bold,
                        color = PaperCream,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = when (selectedTask.status) {
                            "COMPLETED" -> TerminalGreen
                            "FAILED" -> ErrorRed
                            "PENDING" -> SteelGrey
                            else -> AmberWarm
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Status: ${selectedTask.status} ${if (isRunning) "• Autonomous Loop Active" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedTask.status == "FAILED" && !isRunning) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Retry",
                                color = AmberWarm,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.retryTask(selectedTask.id) }
                                    .padding(4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Tabs for Task details: 0 = Subtasks & Terminal, 1 = Final Report
            var taskTab by remember { mutableStateOf(0) }
            TabRow(
                selectedTabIndex = taskTab,
                containerColor = SlateDark,
                contentColor = AmberWarm
            ) {
                Tab(
                    selected = taskTab == 0,
                    onClick = { taskTab = 0 },
                    text = { Text("Task Pipeline") }
                )
                Tab(
                    selected = taskTab == 1,
                    onClick = { taskTab = 1 },
                    text = { Text("Final Synthesis Report") }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                if (taskTab == 0) {
                    // Task Pipeline vertical progress
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Step-By-Step Executions",
                                style = MaterialTheme.typography.bodySmall,
                                color = SteelGrey,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (subtasks.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Planning stages of Joe joe starting soon...",
                                            color = SteelGrey,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        } else {
                            items(subtasks) { step ->
                                val statusIcon = when (step.status) {
                                    "COMPLETED" -> Icons.Default.Check
                                    "IN_PROGRESS" -> Icons.Default.Refresh
                                    "FAILED" -> Icons.Default.Close
                                    else -> Icons.Default.Lock
                                }
                                val statusColor = when (step.status) {
                                    "COMPLETED" -> TerminalGreen
                                    "IN_PROGRESS" -> AmberWarm
                                    "FAILED" -> ErrorRed
                                    else -> SteelGrey
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = statusIcon,
                                                    contentDescription = null,
                                                    tint = statusColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = step.title,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PaperCream,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Surface(
                                                color = statusColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = step.toolUsed,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                    color = statusColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Brain Thought: ${step.thoughtProcess}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SteelGrey
                                        )
                                        if (step.toolInput.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tool Argument: ${step.toolInput}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = AmberGlow
                                            )
                                        }
                                        if (step.toolOutput.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = SlateDark,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = step.toolOutput,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                    color = TerminalGreen,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Internal Autonomous Logs",
                                style = MaterialTheme.typography.bodySmall,
                                color = SteelGrey,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        items(taskChat) { chat ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "[${chat.sender}] ${chat.message}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = when (chat.sender) {
                                        "AGENT" -> AmberWarm
                                        "SYSTEM" -> SteelGrey
                                        else -> PaperCream
                                    },
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Render synthesized report Markdown
                    if (selectedTask.finalSummary.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No synthesis report available yet.\nThe report compiles automatically once all subtasks complete.",
                                color = SteelGrey,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                RenderMarkdown(selectedTask.finalSummary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Simple Markdown block printer to look super native
@Composable
fun RenderMarkdown(markdown: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val lines = markdown.split("\n")
            lines.forEach { line ->
                when {
                    line.startsWith("# ") -> {
                        Text(
                            text = line.removePrefix("# "),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AmberWarm,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    line.startsWith("## ") -> {
                        Text(
                            text = line.removePrefix("## "),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AmberGlow,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    line.startsWith("### ") -> {
                        Text(
                            text = line.removePrefix("### "),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = PaperCream,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    line.startsWith("- ") || line.startsWith("* ") -> {
                        Text(
                            text = "• " + line.substring(2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PaperCream,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                    line.startsWith("|") -> {
                        // Table representation
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = SteelGrey,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    line.isBlank() -> {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    else -> {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PaperCream,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. Memories Screen
// ==========================================
@Composable
fun MemoriesView(memories: List<AgentMemory>, viewModel: AgentViewModel) {
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "SecOps Target & Device Profiles",
                style = MaterialTheme.typography.titleMedium,
                color = AmberWarm,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Joe joe extracts network profiles, hardware symptoms, and remote credentials automatically when you chat with him (e.g. target host IP addresses, phone partition maps, serial numbers). You can also add or delete profiles manually below.",
                style = MaterialTheme.typography.bodySmall,
                color = SteelGrey
            )
        }

        // Add Preference form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Add Telemetry / Target Fact Manually",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PaperCream
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        placeholder = { Text("Parameter Target (e.g. Host IP)", color = SteelGrey) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_key_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberWarm,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = PaperCream,
                            unfocusedTextColor = PaperCream,
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        placeholder = { Text("Parameter Value (e.g. 192.168.1.104)", color = SteelGrey) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_value_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberWarm,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = PaperCream,
                            unfocusedTextColor = PaperCream,
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newKey.isNotBlank() && newValue.isNotBlank()) {
                                viewModel.addMemory(newKey, newValue)
                                newKey = ""
                                newValue = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarm, contentColor = SlateDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_memory_button")
                    ) {
                        Text("Commit Telemetry Fact", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "Telemetry & Hardware Profiles (${memories.size})",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PaperCream,
                fontFamily = FontFamily.Monospace
            )
        }

        if (memories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No diagnostic profiles saved yet. Try saying 'target subnet ip is 10.0.0.45' or 'Samsung Galaxy fastboot error' in chat!",
                        color = SteelGrey,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(memories) { memo ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = memo.keyName,
                                fontWeight = FontWeight.Bold,
                                color = AmberWarm,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = memo.factValue,
                                color = PaperCream,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { viewModel.deleteMemory(memo.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Memory",
                                tint = ErrorRed
                            )
                        }
                    }
                }
            }
        }
    }
}
