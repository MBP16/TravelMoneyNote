package io.github.mbp16.travelmoneynote.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.mbp16.travelmoneynote.MainActivity
import io.github.mbp16.travelmoneynote.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

class TravelSummaryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        
        provideContent {
            WidgetContent(context, data)
        }
    }

    private suspend fun loadWidgetData(context: Context): WidgetData = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val widgetPrefs = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
        
        val selectedTravelId = widgetPrefs.getLong("widget_travel_id", prefs.getLong("selectedTravelId", -1L))
        
        if (selectedTravelId <= 0) {
            return@withContext WidgetData(
                travelName = "여행을 선택해주세요",
                currency = "",
                totalExpense = 0.0,
                totalCash = 0.0,
                remainingCash = 0.0,
                hasData = false
            )
        }

        val database = AppDatabase.getDatabase(context)
        val travel = database.travelDao().getTravelById(selectedTravelId)
        
        if (travel == null) {
            return@withContext WidgetData(
                travelName = "여행을 선택해주세요",
                currency = "",
                totalExpense = 0.0,
                totalCash = 0.0,
                remainingCash = 0.0,
                hasData = false
            )
        }

        val persons = database.personDao().getPersonsByTravelOnce(selectedTravelId)
        val expenses = database.expenseDao().getExpensesByTravelOnce(selectedTravelId)
        
        var totalCash = 0.0
        var totalCashSpent = 0.0
        
        for (person in persons) {
            val cashEntries = database.cashEntryDao().getCashEntriesForPersonOnce(person.id)
            totalCash += cashEntries.sumOf { it.amount }
        }
        
        for (expense in expenses) {
            val payments = database.paymentDao().getPaymentsForExpenseOnce(expense.id)
            totalCashSpent += payments.filter { it.method.name == "CASH" }.sumOf { it.amount }
        }
        
        val totalExpense = expenses.sumOf { it.totalAmount }
        val remainingCash = totalCash - totalCashSpent

        // 여행 일수 계산
        val today = LocalDate.now()
        val startDate = Instant.ofEpochMilli(travel.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val endDate = Instant.ofEpochMilli(travel.endDate).atZone(ZoneId.systemDefault()).toLocalDate()
        
        val travelDays = if (today >= startDate) {
            ChronoUnit.DAYS.between(startDate, minOf(today, endDate)).toInt() + 1
        } else {
            0
        }
        
        // 일평균 지출
        val dailyAverage = if (travelDays > 0) totalExpense / travelDays else 0.0
        
        // 오늘 지출
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayExpense = expenses
            .filter { it.createdAt in todayStart until todayEnd }
            .sumOf { it.totalAmount }
        
        // 남은 일수 및 하루 예산 계산
        val remainingDays = if (today <= endDate) {
            ChronoUnit.DAYS.between(today, endDate).toInt() + 1
        } else {
            0
        }
        val budgetPerDay = if (remainingDays > 0) remainingCash / remainingDays else 0.0

        WidgetData(
            travelName = travel.name,
            currency = travel.currency,
            totalExpense = totalExpense,
            totalCash = totalCash,
            remainingCash = remainingCash,
            hasData = true,
            travelDays = travelDays,
            dailyAverage = dailyAverage,
            todayExpense = todayExpense,
            remainingDays = remainingDays,
            budgetPerDay = budgetPerDay
        )
    }

    @Composable
    private fun WidgetContent(context: Context, data: WidgetData) {
        val intent = Intent(context, MainActivity::class.java)
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(16.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.TopStart
        ) {
            if (!data.hasData) {
                NoDataContent()
            } else {
                DataContent(data)
            }
        }
    }

    @Composable
    private fun NoDataContent() {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✈️ 여행 가계부",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "탭하여 여행을 시작하세요",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 14.sp
                )
            )
        }
    }

    @Composable
    private fun DataContent(data: WidgetData) {
        val formatter = NumberFormat.getNumberInstance(Locale.getDefault())

        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // 여행 이름 + 여행 일수
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Text(
                    text = "✈️",
                    style = TextStyle(fontSize = 24.sp)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = data.travelName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (data.travelDays > 0) {
                    Text(
                        text = "D+${data.travelDays}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // 총 지출 + 오늘 지출
            Row(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "총 지출",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "${formatter.format(data.totalExpense)} ${data.currency}",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "오늘 지출",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "${formatter.format(data.todayExpense)} ${data.currency}",
                        style = TextStyle(
                            color = GlanceTheme.colors.tertiary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // 일평균 지출
            Row(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "일평균",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "${formatter.format(data.dailyAverage)} ${data.currency}",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "남은 현금",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "${formatter.format(data.remainingCash)} ${data.currency}",
                        style = TextStyle(
                            color = GlanceTheme.colors.error,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

data class WidgetData(
    val travelName: String,
    val currency: String,
    val totalExpense: Double,
    val totalCash: Double,
    val remainingCash: Double,
    val hasData: Boolean,
    val travelDays: Int = 0,
    val dailyAverage: Double = 0.0,
    val todayExpense: Double = 0.0,
    val remainingDays: Int = 0,
    val budgetPerDay: Double = 0.0
)
