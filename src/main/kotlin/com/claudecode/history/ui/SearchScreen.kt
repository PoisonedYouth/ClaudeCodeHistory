package com.claudecode.history.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecode.history.domain.SearchFilters
import com.claudecode.history.domain.SearchResult
import com.claudecode.history.service.SearchService
import kotlinx.coroutines.launch
import kotlinx.datetime.*

@Composable
fun SearchScreen(searchService: SearchService) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(SearchFilters()) }
    var selectedResult by remember { mutableStateOf<SearchResult?>(null) }

    // Pagination state
    var currentPage by remember { mutableStateOf(0) }
    var itemsPerPage by remember { mutableStateOf(20) }
    val listState = rememberLazyListState()

    val scope = rememberCoroutineScope()

    // Calculate paginated results
    val totalPages = (searchResults.size + itemsPerPage - 1) / itemsPerPage
    val paginatedResults = remember(searchResults, currentPage, itemsPerPage) {
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, searchResults.size)
        if (startIndex < searchResults.size) {
            searchResults.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    // Reset to first page when results change
    LaunchedEffect(searchResults.size) {
        currentPage = 0
    }

    // Auto-load conversations from current day on initial load
    LaunchedEffect(Unit) {
        scope.launch {
            isSearching = true
            try {
                val now = kotlinx.datetime.Clock.System.now()
                val today = now.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                val startOfDay = today.atTime(0, 0).toInstant(kotlinx.datetime.TimeZone.currentSystemDefault())
                val endOfDay = today.atTime(23, 59, 59).toInstant(kotlinx.datetime.TimeZone.currentSystemDefault())

                val todayConversations = searchService.getConversationsByDateRange(startOfDay, endOfDay)
                searchResults = todayConversations
            } finally {
                isSearching = false
            }
        }
    }

    // Auto-search when filters change (for date filters when no query)
    LaunchedEffect(filters) {
        // Only auto-search if there's no query but filters are active
        if (searchQuery.isBlank() && filters.hasActiveFilters()) {
            scope.launch {
                isSearching = true
                try {
                    searchResults = searchService.search("", filters)
                } finally {
                    isSearching = false
                }
            }
        }
    }

    // Auto-load the latest conversation when a project is selected
    LaunchedEffect(filters.projectPath) {
        val projectPath = filters.projectPath
        if (projectPath != null) {
            scope.launch {
                isSearching = true
                try {
                    val latestConversation = searchService.getLatestConversationByProject(projectPath)
                    if (latestConversation != null) {
                        searchResults = listOf(latestConversation)
                        selectedResult = latestConversation
                    } else {
                        searchResults = emptyList()
                        selectedResult = null
                    }
                } finally {
                    isSearching = false
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel: Search and Results
        Column(
            modifier = Modifier
                .weight(if (selectedResult != null) 0.5f else 1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search conversations...") },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filters",
                                tint = if (showFilters) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Search button
            Button(
                onClick = {
                    scope.launch {
                        isSearching = true
                        try {
                            searchResults = searchService.search(searchQuery, filters)
                        } finally {
                            isSearching = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = (searchQuery.isNotBlank() || filters.hasActiveFilters()) && !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSearching) "Searching..." else "Search")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filters panel with max height and scrolling
            if (showFilters) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    FiltersPanel(
                        filters = filters,
                        onFiltersChange = { filters = it },
                        searchService = searchService,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Results count and pagination info
            if (searchResults.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${searchResults.size} results found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (totalPages > 1) {
                        Text(
                            "Page ${currentPage + 1} of $totalPages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Results list with scrollbar
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(paginatedResults) { result ->
                        SearchResultCard(
                            result = result,
                            isSelected = selectedResult == result,
                            onClick = {
                                selectedResult = if (selectedResult == result) null else result
                            }
                        )
                    }

                    if (searchResults.isEmpty() && searchQuery.isNotBlank() && !isSearching) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "No results found",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Try adjusting your search query or filters",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(8.dp),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = androidx.compose.foundation.defaultScrollbarStyle().copy(
                        unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                )
            }

            // Pagination controls
            if (searchResults.isNotEmpty() && totalPages > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button
                    IconButton(
                        onClick = { currentPage = maxOf(0, currentPage - 1) },
                        enabled = currentPage > 0
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous page"
                        )
                    }

                    // Page indicator with quick jump
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Page", style = MaterialTheme.typography.bodyMedium)

                        // Page number buttons (show current and nearby pages)
                        val pagesToShow = listOf(
                            maxOf(0, currentPage - 2),
                            maxOf(0, currentPage - 1),
                            currentPage,
                            minOf(totalPages - 1, currentPage + 1),
                            minOf(totalPages - 1, currentPage + 2)
                        ).distinct().sorted()

                        pagesToShow.forEach { page ->
                            if (page == currentPage) {
                                FilledTonalButton(
                                    onClick = {},
                                    modifier = Modifier.size(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("${page + 1}")
                                }
                            } else {
                                TextButton(
                                    onClick = { currentPage = page },
                                    modifier = Modifier.size(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("${page + 1}")
                                }
                            }
                        }
                    }

                    // Next button
                    IconButton(
                        onClick = { currentPage = minOf(totalPages - 1, currentPage + 1) },
                        enabled = currentPage < totalPages - 1
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next page"
                        )
                    }
                }
            }
        }

        // Right panel: Selected conversation detail
        if (selectedResult != null) {
            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                ConversationDetailView(
                    result = selectedResult!!,
                    onClose = { selectedResult = null }
                )
            }
        }
    }
}
