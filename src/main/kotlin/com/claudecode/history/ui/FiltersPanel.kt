package com.claudecode.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.claudecode.history.domain.MessageRole
import com.claudecode.history.domain.SearchFilters
import com.claudecode.history.service.SearchService
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersPanel(
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
    searchService: SearchService,
    modifier: Modifier = Modifier
) {
    var availableProjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            availableProjects = searchService.getAllProjects()
            availableModels = searchService.getAllModels()
        }
    }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Filters",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = { onFiltersChange(SearchFilters()) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear filters",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // All filters in one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Project filter
                var expandedProjects by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedProjects,
                    onExpandedChange = { expandedProjects = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filters.projectPath ?: "All Projects",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Project", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProjects) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = expandedProjects,
                        onDismissRequest = { expandedProjects = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Projects") },
                            onClick = {
                                onFiltersChange(filters.copy(projectPath = null))
                                expandedProjects = false
                            }
                        )
                        availableProjects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project) },
                                onClick = {
                                    onFiltersChange(filters.copy(projectPath = project))
                                    expandedProjects = false
                                }
                            )
                        }
                    }
                }

                // Date From filter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showDateFromPicker = true }
                ) {
                    OutlinedTextField(
                        value = filters.dateFrom?.let {
                            val date = it.toLocalDateTime(TimeZone.currentSystemDefault()).date
                            "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
                        } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("From Date", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("YYYY-MM-DD", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, "Select date", modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (filters.dateFrom != null) {
                                IconButton(onClick = {
                                    onFiltersChange(filters.copy(dateFrom = null))
                                }) {
                                    Icon(Icons.Default.Clear, "Clear date", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                }

                if (showDateFromPicker) {
                    DatePickerDialog(
                        initialDate = filters.dateFrom,
                        onDateSelected = { instant ->
                            onFiltersChange(filters.copy(dateFrom = instant))
                            showDateFromPicker = false
                        },
                        onDismiss = { showDateFromPicker = false }
                    )
                }

                // Date To filter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showDateToPicker = true }
                ) {
                    OutlinedTextField(
                        value = filters.dateTo?.let {
                            val date = it.toLocalDateTime(TimeZone.currentSystemDefault()).date
                            "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
                        } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("To Date", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("YYYY-MM-DD", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, "Select date", modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (filters.dateTo != null) {
                                IconButton(onClick = {
                                    onFiltersChange(filters.copy(dateTo = null))
                                }) {
                                    Icon(Icons.Default.Clear, "Clear date", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                }

                if (showDateToPicker) {
                    DatePickerDialog(
                        initialDate = filters.dateTo,
                        onDateSelected = { instant ->
                            onFiltersChange(filters.copy(dateTo = instant))
                            showDateToPicker = false
                        },
                        onDismiss = { showDateToPicker = false }
                    )
                }

                // Role filter
                var expandedRoles by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedRoles,
                    onExpandedChange = { expandedRoles = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filters.role?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "All Roles",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRoles) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = expandedRoles,
                        onDismissRequest = { expandedRoles = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Roles") },
                            onClick = {
                                onFiltersChange(filters.copy(role = null))
                                expandedRoles = false
                            }
                        )
                        MessageRole.entries.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    onFiltersChange(filters.copy(role = role))
                                    expandedRoles = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second row of filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Model filter
                var expandedModels by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedModels,
                    onExpandedChange = { expandedModels = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filters.model?.let { model ->
                            when {
                                model.contains("claude-sonnet-4-5") -> "Sonnet 4.5"
                                model.contains("claude-sonnet-4") -> "Sonnet 4"
                                model.contains("claude-sonnet-3-5") -> "Sonnet 3.5"
                                model.contains("claude-opus") -> "Opus"
                                model.contains("claude-haiku") -> "Haiku"
                                else -> model.take(20)
                            }
                        } ?: "All Models",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModels) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = expandedModels,
                        onDismissRequest = { expandedModels = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Models") },
                            onClick = {
                                onFiltersChange(filters.copy(model = null))
                                expandedModels = false
                            }
                        )
                        availableModels.forEach { model ->
                            val displayName = when {
                                model.contains("claude-sonnet-4-5") -> "Sonnet 4.5"
                                model.contains("claude-sonnet-4") -> "Sonnet 4"
                                model.contains("claude-sonnet-3-5") -> "Sonnet 3.5"
                                model.contains("claude-opus") -> "Opus"
                                model.contains("claude-haiku") -> "Haiku"
                                else -> model.take(20)
                            }
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    onFiltersChange(filters.copy(model = model))
                                    expandedModels = false
                                }
                            )
                        }
                    }
                }

                // Placeholder for future filters (language, file path, etc.)
                Spacer(modifier = Modifier.weight(3f))
            }

            // Quick date filters in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Quick:",
                    style = MaterialTheme.typography.labelMedium
                )
                // Today filter - start of current day
                val now = Clock.System.now()
                val timeZone = TimeZone.currentSystemDefault()
                val today = now.toLocalDateTime(timeZone).date
                val startOfToday = today.atStartOfDayIn(timeZone)
                val endOfToday = startOfToday + 1.days

                FilterChip(
                    selected = filters.dateFrom != null &&
                            filters.dateFrom!! >= startOfToday &&
                            filters.dateFrom!! <= startOfToday + 1.days,
                    onClick = {
                        onFiltersChange(filters.copy(dateFrom = startOfToday, dateTo = endOfToday))
                    },
                    label = { Text("Today") }
                )

                // Week filter - 7 days ago from start of today
                val startOfWeek = startOfToday - 7.days
                FilterChip(
                    selected = filters.dateFrom != null &&
                            filters.dateFrom!! >= startOfWeek &&
                            filters.dateFrom!! <= startOfWeek + 1.days,
                    onClick = {
                        onFiltersChange(filters.copy(dateFrom = startOfWeek, dateTo = endOfToday))
                    },
                    label = { Text("Week") }
                )

                // Month filter - 30 days ago from start of today
                val startOfMonth = startOfToday - 30.days
                FilterChip(
                    selected = filters.dateFrom != null &&
                            filters.dateFrom!! >= startOfMonth &&
                            filters.dateFrom!! <= startOfMonth + 1.days,
                    onClick = {
                        onFiltersChange(filters.copy(dateFrom = startOfMonth, dateTo = endOfToday))
                    },
                    label = { Text("Month") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    initialDate: Instant?,
    onDateSelected: (Instant) -> Unit,
    onDismiss: () -> Unit
) {
    val timeZone = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val currentDate = initialDate?.toLocalDateTime(timeZone)?.date ?: now.toLocalDateTime(timeZone).date

    var selectedYear by remember { mutableStateOf(currentDate.year) }
    var selectedMonth by remember { mutableStateOf(currentDate.monthNumber) }
    var selectedDay by remember { mutableStateOf(currentDate.dayOfMonth) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select Date",
                    style = MaterialTheme.typography.titleMedium
                )

                // Year selector
                var expandedYear by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedYear,
                    onExpandedChange = { expandedYear = it }
                ) {
                    OutlinedTextField(
                        value = selectedYear.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Year") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedYear) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedYear,
                        onDismissRequest = { expandedYear = false }
                    ) {
                        (2020..2030).forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString()) },
                                onClick = {
                                    selectedYear = year
                                    expandedYear = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Month selector
                    var expandedMonth by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedMonth,
                        onExpandedChange = { expandedMonth = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedMonth.toString().padStart(2, '0'),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonth) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMonth,
                            onDismissRequest = { expandedMonth = false }
                        ) {
                            (1..12).forEach { month ->
                                DropdownMenuItem(
                                    text = { Text(month.toString().padStart(2, '0')) },
                                    onClick = {
                                        selectedMonth = month
                                        expandedMonth = false
                                    }
                                )
                            }
                        }
                    }

                    // Day selector
                    var expandedDay by remember { mutableStateOf(false) }
                    val daysInMonth = when (selectedMonth) {
                        2 -> if (selectedYear % 4 == 0 && (selectedYear % 100 != 0 || selectedYear % 400 == 0)) 29 else 28
                        4, 6, 9, 11 -> 30
                        else -> 31
                    }
                    ExposedDropdownMenuBox(
                        expanded = expandedDay,
                        onExpandedChange = { expandedDay = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedDay.toString().padStart(2, '0'),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDay) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDay,
                            onDismissRequest = { expandedDay = false }
                        ) {
                            (1..daysInMonth).forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.toString().padStart(2, '0')) },
                                    onClick = {
                                        selectedDay = day
                                        expandedDay = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val selectedDate = LocalDate(selectedYear, selectedMonth, selectedDay)
                        val instant = selectedDate.atStartOfDayIn(timeZone)
                        onDateSelected(instant)
                    }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
