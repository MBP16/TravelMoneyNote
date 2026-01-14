package io.github.mbp16.travelmoneynote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.mbp16.travelmoneynote.data.AppDatabase
import io.github.mbp16.travelmoneynote.data.Travel
import io.github.mbp16.travelmoneynote.ui.theme.TravelMoneyNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 취소 결과를 기본값으로 설정
        setResult(RESULT_CANCELED)

        // 위젯 ID 가져오기
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()

        setContent {
            TravelMoneyNoteTheme {
                WidgetConfigurationScreen(
                    onTravelSelected = { travelId ->
                        saveWidgetConfiguration(travelId)
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun saveWidgetConfiguration(travelId: Long) {
        val prefs = getSharedPreferences("widget_settings", MODE_PRIVATE)
        prefs.edit().putLong("widget_travel_id", travelId).apply()

        // 위젯 업데이트
        val scope = kotlinx.coroutines.MainScope()
        scope.launch {
            try {
                val manager = GlanceAppWidgetManager(this@WidgetConfigurationActivity)
                val glanceIds = manager.getGlanceIds(TravelSummaryWidget::class.java)
                glanceIds.forEach { glanceId ->
                    TravelSummaryWidget().update(this@WidgetConfigurationActivity, glanceId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 결과 설정 및 종료
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigurationScreen(
    onTravelSelected: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var travels by remember { mutableStateOf<List<Travel>>(emptyList()) }
    var selectedTravelId by remember { mutableStateOf<Long?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(context)
                travels = database.travelDao().getAllTravels().first()
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("위젯 설정") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "취소"
                        )
                    }
                },
                actions = {
                    if (selectedTravelId != null) {
                        IconButton(onClick = { onTravelSelected(selectedTravelId!!) }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "확인"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                travels.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flight,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "등록된 여행이 없습니다",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "앱에서 먼저 여행을 추가해주세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "위젯에 표시할 여행을 선택하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(travels) { travel ->
                                TravelItem(
                                    travel = travel,
                                    isSelected = selectedTravelId == travel.id,
                                    onClick = { selectedTravelId = travel.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelItem(
    travel: Travel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = travel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = "${dateFormat.format(Date(travel.startDate))} ~ ${dateFormat.format(Date(travel.endDate))} • ${travel.currency}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Flight,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "선택됨",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
