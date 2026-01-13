package io.github.mbp16.travelmoneynote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class ExchangeRates(
    val base: String,
    val rates: Map<String, Double>,
    val fetchedAt: Long = System.currentTimeMillis(),
    val rateDate: String = "" // API에서 반환하는 날짜 (yyyy-MM-dd)
)

class ExchangeRateService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // In-memory cache
    private var cachedRates: ExchangeRates? = null
    
    /**
     * Frankfurter API는 매일 16:01 CET에 업데이트됨
     * 캐시가 유효한지 확인: 마지막 16:01 CET 이후에 fetch했으면 유효
     */
    private fun isCacheValid(cached: ExchangeRates, baseCurrency: String): Boolean {
        if (cached.base != baseCurrency) return false
        
        val now = System.currentTimeMillis()
        val cetZone = TimeZone.getTimeZone("CET")
        
        // 오늘 16:01 CET 계산
        val todayUpdateTime = Calendar.getInstance(cetZone).apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // 마지막 업데이트 시간 (오늘 16:01 이전이면 어제 16:01)
        val lastUpdateTime = if (now >= todayUpdateTime) todayUpdateTime 
                             else todayUpdateTime - 24 * 60 * 60 * 1000L
        
        // 캐시가 마지막 업데이트 시간 이후에 fetch됐으면 유효
        return cached.fetchedAt >= lastUpdateTime
    }
    
    suspend fun getExchangeRates(baseCurrency: String): ExchangeRates? = withContext(Dispatchers.IO) {
        // Check cache
        cachedRates?.let { cached ->
            if (isCacheValid(cached, baseCurrency)) {
                return@withContext cached
            }
        }
        
        // Fetch from API (using exchangerate-api.com free tier or frankfurter.app)
        try {
            val rates = fetchFromFrankfurter(baseCurrency)
            if (rates != null) {
                cachedRates = rates
                return@withContext rates
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        cachedRates
    }
    
    private fun fetchFromFrankfurter(baseCurrency: String): ExchangeRates? {
        val url = "https://api.frankfurter.app/latest?from=$baseCurrency"
        val request = Request.Builder()
            .url(url)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            
            val body = response.body?.string() ?: return null
            val jsonObject = json.parseToJsonElement(body).jsonObject
            
            val rates = mutableMapOf<String, Double>()
            rates[baseCurrency] = 1.0
            
            jsonObject["rates"]?.jsonObject?.forEach { (currency, rate) ->
                rates[currency] = rate.jsonPrimitive.double
            }
            
            return ExchangeRates(
                base = baseCurrency,
                rates = rates
            )
        }
    }
    
    fun convertAmount(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        rates: ExchangeRates?
    ): Double? {
        if (fromCurrency == toCurrency) return amount
        if (rates == null) return null
        
        // If base is fromCurrency, direct conversion
        if (rates.base == fromCurrency) {
            val rate = rates.rates[toCurrency] ?: return null
            return amount * rate
        }
        
        // If base is toCurrency, inverse conversion
        if (rates.base == toCurrency) {
            val rate = rates.rates[fromCurrency] ?: return null
            return amount / rate
        }
        
        // Cross conversion through base
        val fromRate = rates.rates[fromCurrency] ?: return null
        val toRate = rates.rates[toCurrency] ?: return null
        return amount * (toRate / fromRate)
    }
}
