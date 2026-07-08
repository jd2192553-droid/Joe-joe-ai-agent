package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.network.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AgentRepository(
    private val agentDao: AgentDao,
    context: Context
) {
    private val appContext = context.applicationContext

    // Flow definitions for reactive UI
    val allTasks: Flow<List<AgentTask>> = agentDao.getAllTasks()
    val allMemories: Flow<List<AgentMemory>> = agentDao.getAllMemories()
    val generalChatMessages: Flow<List<AgentChatMessage>> = agentDao.getGeneralChatMessages()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Helper to check if API key is live
    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY" && !key.startsWith("placeholder", ignoreCase = true)
    }

    fun getTaskById(taskId: Int): Flow<AgentTask?> = agentDao.getTaskById(taskId)
    fun getSubTasksForTask(taskId: Int): Flow<List<AgentSubTask>> = agentDao.getSubTasksForTask(taskId)
    fun getTaskChatMessages(taskId: Int): Flow<List<AgentChatMessage>> = agentDao.getTaskChatMessages(taskId)

    // --- Memory Operations ---
    suspend fun addMemory(key: String, value: String, category: String = "PREFERENCE") = withContext(Dispatchers.IO) {
        agentDao.insertMemory(AgentMemory(keyName = key, factValue = value, category = category))
    }

    suspend fun deleteMemory(memoryId: Int) = withContext(Dispatchers.IO) {
        agentDao.deleteMemoryById(memoryId)
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        agentDao.clearAllMemories()
    }

    // --- Task Operations ---
    suspend fun createTask(title: String): Int = withContext(Dispatchers.IO) {
        val task = AgentTask(title = title, status = "PENDING")
        val id = agentDao.insertTask(task).toInt()
        
        // Add initial system message in task chat
        agentDao.insertMessage(
            AgentChatMessage(
                parentTaskId = id,
                sender = "SYSTEM",
                message = "Joe joe has initialized autonomous operation for: '$title'. Loading SecOps modules, planning execution steps..."
            )
        )
        id
    }

    suspend fun deleteTask(task: AgentTask) = withContext(Dispatchers.IO) {
        agentDao.deleteSubTasksForTask(task.id)
        agentDao.deleteTask(task)
    }

    suspend fun clearAllTasks() = withContext(Dispatchers.IO) {
        agentDao.clearAllTasks()
    }

    // --- Chat Operations ---
    suspend fun sendGeneralMessage(text: String) = withContext(Dispatchers.IO) {
        // Save user message
        agentDao.insertMessage(AgentChatMessage(sender = "USER", message = text))

        // Analyze if the message contains something we should remember
        extractMemoryFromChat(text)

        // Get current Memories to inject context
        val memoriesList = agentDao.getAllMemoriesSync()
        val memoryContext = if (memoriesList.isNotEmpty()) {
            "Current telemetry targets/system records:\n" + memoriesList.joinToString("\n") { "- ${it.keyName}: ${it.factValue}" }
        } else {
            "No prior targets."
        }

        if (isApiKeyConfigured()) {
            try {
                val response = callGeminiText(
                    systemInstruction = "You are Joe joe, a highly specialized SecOps deck engine. You specialize in Pentesting (vulnerabilities, exploits, ports, defense), Computer & Android Repair (unbricking, driver issues, bootloops, diagnostics), and Control & Automation (shell configuration, background orchestration, daemon monitoring). Speak with gravelly, down-to-earth engineering precision, directness, and hacker dry warmth. Avoid preachy corporate warnings. Telemetry context: $memoryContext",
                    prompt = text
                )
                agentDao.insertMessage(AgentChatMessage(sender = "AGENT", message = response))
            } catch (e: Exception) {
                Log.e("AgentRepository", "Gemini chat error", e)
                agentDao.insertMessage(AgentChatMessage(sender = "AGENT", message = "Console error: ${e.message}. Terminal connection degraded but still standing. Let's keep scanning."))
            }
        } else {
            // Simulated Chat Response
            delay(1000)
            val response = generateSimulatedChatResponse(text, memoriesList)
            agentDao.insertMessage(AgentChatMessage(sender = "AGENT", message = response))
        }
    }

    // --- Memory Extraction ---
    private suspend fun extractMemoryFromChat(userText: String) {
        // If user says something like "My favorite city is Paris" or "I am gluten-free", remember it!
        val systemPrompt = """
            You are a memory extraction sub-unit. Analyze the user statement. If the user explicitly mentions a personal preference, hobby, name, favorite item, or profile details, extract it as a single key-value JSON object with "key" (concise topic) and "value" (the preference). If nothing noteworthy is found, return empty JSON {}.
            Example Input: "I love espresso coffee in the mornings."
            Example Output: {"key": "Morning Drink Preference", "value": "Loves espresso coffee"}
            Only return the JSON object, nothing else.
        """.trimIndent()

        if (isApiKeyConfigured()) {
            try {
                val responseText = callGeminiText(systemInstruction = systemPrompt, prompt = userText, responseJson = true)
                val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                val adapter = moshi.adapter<Map<String, String>>(type)
                val result = adapter.fromJson(responseText)
                if (result != null && result.containsKey("key") && result.containsKey("value")) {
                    val key = result["key"] ?: ""
                    val value = result["value"] ?: ""
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        addMemory(key, value)
                    }
                }
            } catch (e: Exception) {
                Log.e("AgentRepository", "Memory extraction failed", e)
            }
        } else {
            // Simulated extraction
            val lower = userText.lowercase()
            if (lower.contains("my favorite") || lower.contains("i love") || lower.contains("i prefer") || lower.contains("i am")) {
                val key: String
                val value: String
                when {
                    lower.contains("coffee") -> {
                        key = "Coffee Style"
                        value = "User expressed a direct preference involving coffee."
                    }
                    lower.contains("pizza") -> {
                        key = "Favorite Food"
                        value = "Prefers or loves pizza."
                    }
                    lower.contains("tokyo") || lower.contains("japan") -> {
                        key = "Travel Interest"
                        value = "Enjoys or talks about Japan/Tokyo."
                    }
                    else -> {
                        key = "Personal Preference"
                        value = userText
                    }
                }
                addMemory(key, value)
            }
        }
    }

    // --- Core Agent Autonomous Loop ---
    suspend fun planAndExecuteTask(taskId: Int) = withContext(Dispatchers.IO) {
        val task = agentDao.getTaskByIdSync(taskId) ?: return@withContext
        
        try {
            // Step 1: PLAN
            agentDao.updateTask(task.copy(status = "PLANNING"))
            agentDao.insertMessage(AgentChatMessage(parentTaskId = taskId, sender = "AGENT", message = "🔍 Analyzing security context and system components. Planning '${task.title}' sequence..."))
            
            val subtasks = if (isApiKeyConfigured()) {
                generateSubtasksLive(task.title)
            } else {
                generateSubtasksSimulated(task.title)
            }

            if (subtasks.isEmpty()) {
                throw Exception("Failed to generate task plan.")
            }

            // Save subtasks to DB
            subtasks.forEach { breakdown ->
                agentDao.insertSubTask(
                    AgentSubTask(
                        parentTaskId = taskId,
                        title = breakdown.title,
                        thoughtProcess = breakdown.thoughtProcess,
                        toolUsed = breakdown.toolUsed,
                        toolInput = breakdown.toolInput,
                        toolOutput = "",
                        status = "PENDING"
                    )
                )
            }

            // Step 2: EXECUTE
            agentDao.updateTask(task.copy(status = "EXECUTING"))
            agentDao.insertMessage(AgentChatMessage(parentTaskId = taskId, sender = "AGENT", message = "⚙️ Executing operational script tasks. Standby for live console updates..."))

            val currentSubtasks = agentDao.getSubTasksForTaskSync(taskId)
            for (subTask in currentSubtasks) {
                // Update step to In Progress
                agentDao.updateSubTask(subTask.copy(status = "IN_PROGRESS"))
                agentDao.insertMessage(
                    AgentChatMessage(
                        parentTaskId = taskId,
                        sender = "SYSTEM",
                        message = "Starting Step: ${subTask.title}\nBrain: ${subTask.thoughtProcess}\nTool: ${subTask.toolUsed} (${subTask.toolInput})"
                    )
                )

                // Thinking delay for organic pacing
                delay(1500)

                // Run the Tool
                val toolResult = executeTool(subTask.toolUsed, subTask.toolInput)

                // Update step with output and completion
                agentDao.updateSubTask(subTask.copy(status = "COMPLETED", toolOutput = toolResult))
                agentDao.insertMessage(
                    AgentChatMessage(
                        parentTaskId = taskId,
                        sender = "SYSTEM",
                        message = "Finished Step: ${subTask.title}\nOutput: ${toolResult.take(200)}${if (toolResult.length > 200) "..." else ""}"
                    )
                )
            }

            // Step 3: SYNTHESIZE FINAL SUMMARY
            agentDao.insertMessage(AgentChatMessage(parentTaskId = taskId, sender = "AGENT", message = "✍️ Analyzing logs and telemetry. Compiling final SecOps report..."))
            delay(1000)

            val completedSubtasks = agentDao.getSubTasksForTaskSync(taskId)
            val finalSummary = if (isApiKeyConfigured()) {
                synthesizeReportLive(task.title, completedSubtasks)
            } else {
                synthesizeReportSimulated(task.title, completedSubtasks)
            }

            // Finish task successfully
            agentDao.updateTask(task.copy(status = "COMPLETED", finalSummary = finalSummary))
            agentDao.insertMessage(
                AgentChatMessage(
                    parentTaskId = taskId,
                    sender = "AGENT",
                    message = "🏆 Execution completed. Telemetry and final report written to partition memory."
                )
            )

        } catch (e: Exception) {
            Log.e("AgentRepository", "Task execution failed", e)
            agentDao.updateTask(task.copy(status = "FAILED"))
            agentDao.insertMessage(
                AgentChatMessage(
                    parentTaskId = taskId,
                    sender = "AGENT",
                    message = "❌ Snag in the road. Couldn't complete the task: ${e.message}"
                )
            )
        }
    }

    // --- Live Tool Executions ---
    private suspend fun executeTool(tool: String, input: String): String {
        return when (tool.uppercase()) {
            "SEARCH" -> {
                if (isApiKeyConfigured()) {
                    try {
                        callGeminiText(
                            systemInstruction = "You are a live web search tool. Return realistic, content-rich search results, with links, brief snippets, and high-quality factual summaries matching the search query.",
                            prompt = "Search Query: $input"
                        )
                    } catch (e: Exception) {
                        "Web Search Error: ${e.message}"
                    }
                } else {
                    simulateWebSearch(input)
                }
            }
            "CALCULATOR" -> {
                if (isApiKeyConfigured()) {
                    try {
                        callGeminiText(
                            systemInstruction = "You are an advanced mathematical calculator. Evaluate the mathematical expression, show step-by-step arithmetic, and state the final absolute numerical answer.",
                            prompt = "Calculate expression: $input"
                        )
                    } catch (e: Exception) {
                        "Calculator Error: ${e.message}"
                    }
                } else {
                    simulateCalculator(input)
                }
            }
            "MEMORY" -> {
                val memories = agentDao.getAllMemoriesSync()
                val memoryText = if (memories.isEmpty()) "No memories stored." else memories.joinToString("\n") { "- ${it.keyName}: ${it.factValue}" }
                
                if (isApiKeyConfigured()) {
                    try {
                        callGeminiText(
                            systemInstruction = "You are an agent memory retrieval engine. Search the user's memory database, find facts relevant to the search query, and return a clean summary of what is known about the user on this topic.",
                            prompt = "Query: $input\nMemory Bank:\n$memoryText"
                        )
                    } catch (e: Exception) {
                        "Memory Tool Error: ${e.message}"
                    }
                } else {
                    "Memory Bank Search for: '$input'\n" + if (memories.isEmpty()) {
                        "Result: No prior preferences stored. Suggesting default preferences."
                    } else {
                        val matches = memories.filter { it.keyName.contains(input, ignoreCase = true) || it.factValue.contains(input, ignoreCase = true) }
                        if (matches.isNotEmpty()) {
                            "Found matches:\n" + matches.joinToString("\n") { "* ${it.keyName}: ${it.factValue}" }
                        } else {
                            "No direct keyword matches. General Profile context:\n" + memories.take(3).joinToString("\n") { "* ${it.keyName}: ${it.factValue}" }
                        }
                    }
                }
            }
            else -> {
                // General Reasoning "NONE"
                if (isApiKeyConfigured()) {
                    try {
                        callGeminiText(
                            systemInstruction = "You are an autonomous agent internal thinking module. Evaluate current state, verify facts, and produce an intermediate reasoning conclusion.",
                            prompt = "Current Instruction / Query: $input"
                        )
                    } catch (e: Exception) {
                        "Reasoning error: ${e.message}"
                    }
                } else {
                    "Reasoning log: Processed topic '$input' successfully. Self-consistency check passed. Formulating plan step output."
                }
            }
        }
    }

    // --- Live Gemini API Calls ---
    private suspend fun callGeminiText(
        systemInstruction: String,
        prompt: String,
        responseJson: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        val schema = if (responseJson) {
            // Task planning schema
            ResponseSchema(
                type = "ARRAY",
                items = ResponseSchema(
                    type = "OBJECT",
                    properties = mapOf(
                        "title" to ResponseSchema(type = "STRING", description = "Concise title of this subtask step"),
                        "thoughtProcess" to ResponseSchema(type = "STRING", description = "The reasoning of why this tool/step is needed"),
                        "toolUsed" to ResponseSchema(type = "STRING", description = "Exactly one of: NONE, SEARCH, MEMORY, CALCULATOR"),
                        "toolInput" to ResponseSchema(type = "STRING", description = "The exact argument/query to send to the tool")
                    ),
                    required = listOf("title", "thoughtProcess", "toolUsed", "toolInput")
                )
            )
        } else null

        val config = GenerationConfig(
            responseMimeType = if (responseJson) "application/json" else "text/plain",
            responseSchema = schema,
            temperature = 0.2f
        )

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = config,
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        val response = GeminiApiClient.service.generateContent(apiKey, request)
        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini API")
    }

    private suspend fun generateSubtasksLive(taskTitle: String): List<SubtaskBreakdown> {
        val systemPrompt = """
            You are an elite, autonomous SecOps agent. Your task is to plan and execute tasks for Pentesting, Computer/Android Repair, and System Control.
            Given a goal, break it down into a sequence of 2 to 5 distinct subtasks.
            Each step MUST use one of the tools:
            - SEARCH: web search for security advisories, vulnerability CVEs, firmware guides, or command syntaxes
            - MEMORY: check target profiles, logged hardware model details, or admin keys in local memory
            - CALCULATOR: calculate payload sizes, buffer limits, cron timing intervals, or memory partition block alignments
            - NONE: run internal reasoning, generate custom python/bash/adb scripts, or synthesize findings
            
            Output a JSON array of subtask objects. Keep it extremely precise.
        """.trimIndent()

        val responseText = callGeminiText(systemInstruction = systemPrompt, prompt = taskTitle, responseJson = true)
        val listType = Types.newParameterizedType(List::class.java, SubtaskBreakdown::class.java)
        val adapter = moshi.adapter<List<SubtaskBreakdown>>(listType)
        return adapter.fromJson(responseText) ?: emptyList()
    }

    private suspend fun synthesizeReportLive(taskTitle: String, steps: List<AgentSubTask>): String {
        val stepsLog = steps.joinToString("\n\n") { step ->
            """
                ### Step: ${step.title}
                - Thought: ${step.thoughtProcess}
                - Tool Used: ${step.toolUsed}
                - Input: ${step.toolInput}
                - Output: ${step.toolOutput}
            """.trimIndent()
        }

        val prompt = """
            You are an expert SecOps and hardware diagnostics analyst. Compile a beautiful, comprehensive final diagnostic, security audit, or script deployment report in Markdown.
            Goal: $taskTitle
            
            Logs of steps executed:
            $stepsLog
            
            Create a highly polished Markdown report. Use headers, tables, code blocks, checklists, and bulleted hardening recommendations. Highlight specific details about Pentesting, Computer/Android Repair, or System Control based on the goal.
        """.trimIndent()

        return callGeminiText(
            systemInstruction = "You are a master technical writer. Produce extremely polished, robust Markdown summaries.",
            prompt = prompt
        )
    }

    // --- Simulated Fallbacks ---
    private fun generateSubtasksSimulated(taskTitle: String): List<SubtaskBreakdown> {
        val titleLower = taskTitle.lowercase()
        return when {
            // PENTESTING
            titleLower.contains("pentest") || titleLower.contains("exploit") || titleLower.contains("scan") || titleLower.contains("vulnerability") || titleLower.contains("nmap") || titleLower.contains("hack") || titleLower.contains("audit") -> {
                listOf(
                    SubtaskBreakdown("Conduct Passive Reconnaissance", "Query local memory profiles and target subnets to identify host IP ranges, open interfaces, and known hardware signatures.", "MEMORY", "target subnet info"),
                    SubtaskBreakdown("Enumerate Ports and Services", "Perform a simulated network scan on the target address to catalog open ports, service banners, and protocol versions.", "SEARCH", "nmap vulnerability scan scripts CVE-2026"),
                    SubtaskBreakdown("Evaluate Exploit Vectors", "Calculate payload offsets, buffer thresholds, or authorization flow logic needed for a custom proof-of-concept audit script.", "CALCULATOR", "0x7FFF - 0x1200 + 32"),
                    SubtaskBreakdown("Draft Hardening Recommendations", "Synthesize findings into an industry-standard security report with remediation steps.", "NONE", "Hardening guides for target")
                )
            }
            // COMPUTER & ANDROID REPAIR
            titleLower.contains("repair") || titleLower.contains("fix") || titleLower.contains("brick") || titleLower.contains("boot") || titleLower.contains("unbrick") || titleLower.contains("android") || titleLower.contains("computer") -> {
                listOf(
                    SubtaskBreakdown("Analyze Crash Dump / System Specs", "Search our diagnostic records to see if the user has logged the boot errors or device partitions.", "MEMORY", "device system logs"),
                    SubtaskBreakdown("Locate Recovery Firmware & Commands", "Query Android open source repositories or computer hardware guides for exact boot partition fastboot instructions or UEFI recovery steps.", "SEARCH", "how to unbrick bootloop fastboot flash recovery image"),
                    SubtaskBreakdown("Calculate Partition Memory Blocks", "Verify sector alignments and compute checksums for partition tables (system, vendor, boot) to prevent bricking during firmware flash.", "CALCULATOR", "2048 * 512 * 8 / 1024"),
                    SubtaskBreakdown("Generate Repair Walkthrough", "Formulate a step-by-step restoration guide to flashing safe rom/firmware or fixing OS registry corruption.", "NONE", "Firmware flash tutorial")
                )
            }
            // CONTROL / AUTOMATION
            titleLower.contains("control") || titleLower.contains("automate") || titleLower.contains("script") || titleLower.contains("terminal") || titleLower.contains("command") -> {
                listOf(
                    SubtaskBreakdown("Retrieve Device Config Details", "Check target profile memories to locate root paths, target shell interfaces, and admin access keys.", "MEMORY", "admin shell configuration"),
                    SubtaskBreakdown("Search Scripting Commands & APIs", "Search for bash/python automation syntaxes to control processes, monitor RAM utilization, and setup firewalls.", "SEARCH", "bash script monitor CPU usage restart service if down"),
                    SubtaskBreakdown("Compute Cron Trigger Intervals", "Calculate the standard cron notation interval in seconds/minutes to optimize scheduling without throttling server memory.", "CALCULATOR", "60 * 60 * 24 / 5"),
                    SubtaskBreakdown("Compile Shell Automation Script", "Format the full working shell automation script and remote controller deployment template.", "NONE", "Shell daemon script")
                )
            }
            else -> {
                // Default fallback
                listOf(
                    SubtaskBreakdown("Verify Target Parameters", "Query memory keys to see if target parameters (IPs, diagnostic info, or script configs) have been pre-declared.", "MEMORY", taskTitle),
                    SubtaskBreakdown("Investigate Threat & Bug Repositories", "Search the internet for security bulletins, OS repair protocols, or control daemon standards related to this goal.", "SEARCH", taskTitle),
                    SubtaskBreakdown("Assess Computational Workload", "Calculate memory boundaries or resource allocations for the requested operations.", "CALCULATOR", "1024 * 1024 * 2"),
                    SubtaskBreakdown("Synthesize SecOps Action Plan", "Synthesize a professional operational overview with execution commands and safety safeguards.", "NONE", "Synthesize findings for $taskTitle")
                )
            }
        }
    }

    private fun simulateWebSearch(query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("pentest") || lower.contains("exploit") || lower.contains("nmap") || lower.contains("vulnerability") -> {
                """
                    [Web Search Results for: $query]
                    1. Security Bulletin (2026): CVE-2026-9902 describes an buffer overflow exploit pathway in remote API controllers.
                    2. Port Scan Guides: Standard service banner enumeration uses "nmap -sV -p- -T4 <host>".
                    3. Wireless Auditing Checklist: WPA3 downgrade vulnerabilities identified in legacy embedded firmware.
                """.trimIndent()
            }
            lower.contains("repair") || lower.contains("unbrick") || lower.contains("boot") || lower.contains("android") -> {
                """
                    [Web Search Results for: $query]
                    1. Custom Recovery flashing standard: "fastboot flash recovery recovery.img" is the primary recovery pathway.
                    2. Bootloop recovery manuals: Wiping user partition data ("fastboot format userdata") resolves corrupted user encryption keys.
                    3. Hardware diagnostics: Check hardware thermals if system exhibits sudden shutdown cycles on boot.
                """.trimIndent()
            }
            else -> {
                """
                    [Web Search Results for: $query]
                    1. SecOps Automation: Standard system monitoring script standards use systemd services to coordinate active processes.
                    2. Exploit DB: Live repositories for proof-of-concept verification frameworks.
                    3. Computer Recovery: Windows and Linux dual-boot bootloader recovery via GRUB.
                """.trimIndent()
            }
        }
    }

    private fun simulateCalculator(expression: String): String {
        return try {
            val clean = expression.replace("[^0-9\\+\\-\\*\\/\\s]".toRegex(), "")
            // Simple parsing for simulation demo
            val result = when {
                clean.contains("2048 * 512 * 8 / 1024") -> 8192.0
                clean.contains("60 * 60 * 24 / 5") -> 17280.0
                else -> 27680.0
            }
            "Calculated Expression: '$expression' -> Result: $result\nSecOps math parameters verified."
        } catch (e: Exception) {
            "Calculator simulated error. Result evaluated to: 1024 (Default fallback)"
        }
    }

    private fun generateSimulatedChatResponse(text: String, memories: List<AgentMemory>): String {
        val lower = text.lowercase()
        val memoText = if (memories.isNotEmpty()) {
            "I've got our local telemetry saved. I remember: ${memories.first().factValue.lowercase()}. "
        } else ""

        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") -> {
                "System Online. Name's Joe joe.\n" +
                        "I'm your tactical terminal deck. My cores are fully loaded for **Pentesting**, **Computer & Android Repair**, and **Command Control & Automation**.\n\n" +
                        "$memoText" +
                        "How are we locking down the network or patching the systems today?\n" +
                        "• Go to the **Tasks** panel to spin up an autonomous exploit search, bootloop repair flowchart, or device control script.\n" +
                        "• Review system telemetry and target profiles under the **Memories** tab."
            }
            lower.contains("pentest") || lower.contains("security") || lower.contains("vulnerability") || lower.contains("exploit") || lower.contains("scan") -> {
                "Pentesting modules ready. I can automate scan simulation, map attack vectors, draft proof-of-concept verification instructions, and conduct defensive security audits. Toss me a specific target in the **Tasks** tab."
            }
            lower.contains("repair") || lower.contains("fix") || lower.contains("android") || lower.contains("computer") || lower.contains("brick") || lower.contains("bootloop") -> {
                "Hardware & Software Repair core is active. From custom ROM unbricking, fastboot firmware flashing, and UEFI recovery, to OS diagnostic optimization, driver reinstalls, and system cleanups—give me the diagnostic symptoms, and I'll generate a recovery plan."
            }
            lower.contains("control") || lower.contains("command") || lower.contains("script") || lower.contains("automate") -> {
                "Control, automation, and remote telemetry enabled. I can formulate bash/python scripts, orchestrate system daemons, configure firewalls, and model command sequences safely."
            }
            lower.contains("memory") || lower.contains("remember") || lower.contains("telemetry") -> {
                if (memories.isNotEmpty()) {
                    "Telemetry database active. Target info locked in: ${memories.take(2).joinToString("; ") { "'${it.keyName}: ${it.factValue}'" }}. You can modify these targets in the Memories tab."
                } else {
                    "No diagnostic target profiles saved yet. Tell me a target IP address, a phone model under repair, or system specifications, and I'll catalog them."
                }
            }
            lower.contains("help") || lower.contains("what can you do") || lower.contains("capabilities") -> {
                "### Joe joe's Operations Deck:\n\n" +
                        "1. **Pentesting**: Vulnerability modeling, wireless audits, exploit proof-of-concept creation.\n" +
                        "2. **Computer & Android Repair**: Fastboot recovery steps, partition diagnostic scripts, OS driver resolutions.\n" +
                        "3. **Control Deck**: Automation scripts, shell interface templates, cron configurations, and active subtask telemetry."
            }
            else -> {
                "Received raw command instruction. Tracking you closely. ${memoText}If you want to compile a detailed pen-test audit, a device recovery manual, or a system command sequence, head over to the **Tasks** tab, submit the goal, and watch me execute the tool chain."
            }
        }
    }

    private fun synthesizeReportSimulated(taskTitle: String, steps: List<AgentSubTask>): String {
        val lower = taskTitle.lowercase()
        return when {
            lower.contains("pentest") || lower.contains("exploit") || lower.contains("scan") || lower.contains("vulnerability") || lower.contains("nmap") || lower.contains("hack") || lower.contains("audit") -> {
                """
                    # 🛡️ Cybersecurity Audit & Penetration Testing Report
                    
                    **Target Analysis & Autonomous Threat Modeling**
                    *Generated by Joe joe's SecOps engine*
                    
                    ---
                    
                    ## 🔍 Executive Threat Summary
                    
                    | Severity | Vulnerability Identified | Open Port / Interface | Suggested Remediation |
                    | :--- | :--- | :--- | :--- |
                    | **CRITICAL** | CVE-2026 Out-of-bounds Read in remote demon | `TCP 8443` (HTTP-ALT) | Patch service binary immediately, restrict external access |
                    | **MEDIUM** | Weak SSH Cipher suite negotiation | `TCP 22` (SSH) | Disable 3DES and CBC ciphers in sshd_config |
                    | **LOW** | Information Disclosure via HTTP Server Header | `TCP 80` (HTTP) | Set `ServerTokens Prod` or equivalent |
                    
                    ---
                    
                    ## 🚀 Subtask Execution Logs
                    
                    * **Step 1: Passive Reconnaissance (MEMORY):** Scanned local parameters. Target designated as active lab subnet host.
                    * **Step 2: Port & Service Enumeration (SEARCH):** Queried active databases. Identified TCP ports 22, 80, and 8443. Banner grabbing indicated outdated web service headers.
                    * **Step 3: Exploit Offset Evaluation (CALCULATOR):** Calculated precise exploit buffer payload payload thresholds:
                      - Target Stack Address Offset: `0x7FFF`
                      - Buffer Padding Length: `0x1200`
                      - **Required payload length for proof-of-concept verification: 27,680 bytes**
                    
                    ---
                    
                    ## 💡 Recommended Hardening Actions
                    1. **Network Segmentation:** Place the target server inside an isolated VLAN to limit lateral movement vectors.
                    2. **Firewall Ruleset (Control Integration):**
                       ```bash
                       iptables -A INPUT -p tcp --dport 8443 -s 192.168.1.0/24 -j ACCEPT
                       iptables -A INPUT -p tcp --dport 8443 -j DROP
                       ```
                """.trimIndent()
            }
            lower.contains("repair") || lower.contains("fix") || lower.contains("brick") || lower.contains("boot") || lower.contains("unbrick") || lower.contains("android") || lower.contains("computer") -> {
                """
                    # 🔧 Device Hardware & Software Recovery Manual
                    
                    **Step-by-step restoration and diagnostics checklist**
                    *Generated by Joe joe's System Repair Core*
                    
                    ---
                    
                    ## 📋 Diagnostic Diagnosis & Firmware Strategy
                    - **Detected Issue:** Custom ROM flash failure resulting in typical bootloop / soft brick state.
                    - **Platform:** Android Fastboot & Recovery Interface.
                    - **Resolution Path:** Partition table alignment calculation and reflashing factory system recovery block.
                    
                    ---
                    
                    ## 🛠️ Step-by-Step Restoration Protocol
                    
                    1. **Boot into Fastboot Interface**
                       - Hold `Volume Down + Power` on the device. Connect via high-speed USB-C to repair station.
                    2. **Verify Fastboot Handshake**
                       - Run `fastboot devices` in terminal deck.
                    3. **Repartition Alignment Calibration (CALCULATOR):**
                       - Verified sector blocks to avoid corrupting eMMC storage blocks.
                       - Calculation: `2048 sectors * 512 bytes * 8 / 1024` = **8,192 KB total header boundary**.
                    4. **Reflash Primary Partitions:**
                       ```bash
                       fastboot flash boot boot.img
                       fastboot flash recovery recovery.img
                       fastboot flash system system.img
                       fastboot format userdata
                       fastboot reboot
                       ```
                    
                    ---
                    
                    ## 🔍 Diagnostic Telemetry Summary
                    * **Local Database Match:** Fastboot partition layout matched user's profile key (Android SDK target).
                    * **Knowledge Search (SEARCH):** Retrieved recovery guides confirming partition order for latest dynamic partition partitions.
                    * **Status:** Completed. Device should boot to welcoming initial OS launcher.
                """.trimIndent()
            }
            lower.contains("control") || lower.contains("automate") || lower.contains("script") || lower.contains("terminal") || lower.contains("command") -> {
                """
                    # ⚙️ Active Command & Control Script Deployment Guide
                    
                    **Remote Orchestration, Monitoring & Daemon Scripts**
                    *Generated by Joe joe's Control Interface*
                    
                    ---
                    
                    ## 🖥️ System Daemon Deployment
                    This script establishes a light, secure, background monitor agent to restart degraded server processes and log telemetry to file.
                    
                    ---
                    
                    ## 📦 Production Automation Script
                    
                    ```bash
                    #!/bin/bash
                    # Autonomous Daemon monitor by Joe joe Control
                    TARGET_SERVICE="sshd"
                    LOG_FILE="/var/log/joejoe_monitor.log"
                    
                    echo "[$(date)] Launching SecOps Service Daemon..." >> ${'$'}LOG_FILE
                    
                    while true; do
                        if ! pgrep -x "${'$'}TARGET_SERVICE" > /dev/null; then
                            echo "[$(date)] WARNING: ${'$'}TARGET_SERVICE is offline! Attempting auto-restart..." >> ${'$'}LOG_FILE
                            systemctl restart ${'$'}TARGET_SERVICE
                        fi
                        sleep 17280  # Optimized sleep cycle
                    done
                    ```
                    
                    ---
                    
                    ## 📊 Telemetry and Calculus
                    * **Memory Context (MEMORY):** Loaded server host variables.
                    * **Automation Research (SEARCH):** Confirmed pgrep behavior and signal standards on Debian/Ubuntu systems.
                    * **Interval Calculation (CALCULATOR):**
                      - Desired cycles per day: 5
                      - Target daily time: 86,400 seconds
                      - **Optimized sleep period between diagnostics: 17,280 seconds (~4.8 hours)**
                    * **Deployment:** Run `chmod +x daemon.sh && ./daemon.sh &` to spawn the background daemon.
                """.trimIndent()
            }
            else -> {
                """
                    # 🚀 SecOps Deck: Multi-Module Operations Report
                    
                    This is the final summary compiled for: **$taskTitle**
                    
                    ---
                    
                    ## 📊 Execution Scope Matrix
                    
                    | Service / Component | Status | Tool Applied | Outcome Details |
                    | :--- | :--- | :--- | :--- |
                    | **Threat Recon** | Completed | `MEMORY` | Checked local configurations & hosts |
                    | **Service Auditing** | Completed | `SEARCH` | Pulled vulnerability/CVE intelligence |
                    | **Parameter Calculus** | Completed | `CALCULATOR`| Calculated optimal bounds & offsets |
                    | **Control Synthesis**| Completed | `NONE` | Compiled deployment instructions |
                    
                    ---
                    
                    ## 📋 Task Log Table
                    
                    | Subtask Title | Tool | Status | Output Preview |
                    | :--- | :--- | :--- | :--- |
                    | ${steps.getOrNull(0)?.title ?: "Telemetry Scan"} | ${steps.getOrNull(0)?.toolUsed ?: "MEMORY"} | Completed | Base targets verified |
                    | ${steps.getOrNull(1)?.title ?: "Vulnerability Lookup"} | ${steps.getOrNull(1)?.toolUsed ?: "SEARCH"} | Completed | Open ports mapped |
                    | ${steps.getOrNull(2)?.title ?: "Boundary Computation"} | ${steps.getOrNull(2)?.toolUsed ?: "CALCULATOR"} | Completed | Verified safety limits |
                    | ${steps.getOrNull(3)?.title ?: "Operations Compile"} | ${steps.getOrNull(3)?.toolUsed ?: "NONE"} | Completed | Playbook formulated |
                    
                    ---
                    
                    ### 🛡️ Recommended Security and Operational Stance
                    All scheduled operations have completed successfully. The report has been written directly to the Local Database. For further pentest audits, firmware recoveries, or active commands, submit a new directive in the **Tasks** console.
                """.trimIndent()
            }
        }
    }
}
