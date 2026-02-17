package io.github.mbp16.travelmoneynote.data

import io.github.mbp16.travelmoneynote.R

data class Currency(
    val code: String,
    val nameResId: Int,
    val symbol: String
)

val availableCurrencies = listOf(
    Currency("KRW", R.string.currency_krw, "₩"),
    Currency("USD", R.string.currency_usd, "$"),
    Currency("EUR", R.string.currency_eur, "€"),
    Currency("JPY", R.string.currency_jpy, "¥"),
    Currency("CNY", R.string.currency_cny, "¥"),
    Currency("GBP", R.string.currency_gbp, "£"),
    Currency("THB", R.string.currency_thb, "฿"),
    Currency("SGD", R.string.currency_sgd, "S$"),
    Currency("AUD", R.string.currency_aud, "A$"),
    Currency("BRL", R.string.currency_brl, "R$"),
    Currency("CAD", R.string.currency_cad, "CA$"),
    Currency("CHF", R.string.currency_chf, "CHF"),
    Currency("CZK", R.string.currency_czk, "Kč"),
    Currency("DKK", R.string.currency_dkk, "kr"),
    Currency("HKD", R.string.currency_hkd, "HK$"),
    Currency("HUF", R.string.currency_huf, "Ft"),
    Currency("IDR", R.string.currency_idr, "Rp"),
    Currency("ILS", R.string.currency_ils, "₪"),
    Currency("INR", R.string.currency_inr, "₹"),
    Currency("ISK", R.string.currency_isk, "kr"),
    Currency("MXN", R.string.currency_mxn, "Mex$"),
    Currency("MYR", R.string.currency_myr, "RM"),
    Currency("NOK", R.string.currency_nok, "kr"),
    Currency("NZD", R.string.currency_nzd, "NZ$"),
    Currency("PHP", R.string.currency_php, "₱"),
    Currency("PLN", R.string.currency_pln, "zł"),
    Currency("RON", R.string.currency_ron, "lei"),
    Currency("SEK", R.string.currency_sek, "kr"),
    Currency("ZAR", R.string.currency_zar, "R"),

    Currency("VND", R.string.currency_vnd, "₫"),
    Currency("TWD", R.string.currency_twd, "NT$"),
)