package com.soloadmin.hub.domain.logic

/**
 * Money is stored as integer paise so that rupee arithmetic can never drift the way
 * JavaScript floating point did in the legacy application.
 *
 * Rendering matches the legacy `toLocaleString('en-IN')` grouping (12,34,567) while using a
 * proper minus sign before the currency symbol for negative balances.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    val isZero: Boolean get() = paise == 0L
    val isNegative: Boolean get() = paise < 0L

    operator fun plus(other: Money): Money = Money(paise + other.paise)
    operator fun minus(other: Money): Money = Money(paise - other.paise)
    operator fun times(factor: Int): Money = Money(paise * factor)
    fun absolute(): Money = Money(if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise))

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    companion object {
        val ZERO = Money(0L)

        fun ofRupees(rupees: Long): Money = Money(rupees * 100L)
        fun ofRupees(rupees: Int): Money = ofRupees(rupees.toLong())

        /** Accepts "1450", "1,450.50", "₹1450", "1450.5" and returns null for junk. */
        fun parseRupees(raw: String?): Money? {
            val cleaned = raw?.trim()?.replace("₹", "")?.replace(",", "")?.replace(" ", "")
            if (cleaned.isNullOrEmpty()) return null
            val negative = cleaned.startsWith("-")
            val body = if (negative) cleaned.substring(1) else cleaned
            if (body.isEmpty() || body.count { it == '.' } > 1) return null
            val wholePart = body.substringBefore('.')
            val fractionPart = body.substringAfter('.', "")
            if (wholePart.isNotEmpty() && wholePart.any { !it.isDigit() }) return null
            if (fractionPart.any { !it.isDigit() }) return null
            if (fractionPart.length > 2) return null
            val whole = if (wholePart.isEmpty()) 0L else wholePart.toLongOrNull() ?: return null
            val fraction = when (fractionPart.length) {
                0 -> 0L
                1 -> fractionPart.toLong() * 10L
                else -> fractionPart.toLong()
            }
            if (whole > (Long.MAX_VALUE / 100L) - 1L) return null
            val total = whole * 100L + fraction
            return Money(if (negative) -total else total)
        }

        /** Used when importing JavaScript numbers (which may be fractional rupees). */
        fun fromRupeeDouble(rupees: Double): Money {
            if (rupees.isNaN() || rupees.isInfinite()) return ZERO
            return Money(Math.round(rupees * 100.0))
        }
    }
}

/** Formats paise as `₹45,000`, `₹1,450.50` or `−₹1,200`. */
fun formatInr(money: Money): String {
    val negative = money.paise < 0L
    val absolute = Math.abs(money.paise)
    val whole = absolute / 100L
    val fraction = (absolute % 100L).toInt()
    val body = StringBuilder()
    if (negative) body.append('\u2212')
    body.append('\u20B9')
    body.append(groupIndian(whole))
    if (fraction != 0) {
        body.append('.').append(fraction.toString().padStart(2, '0'))
    }
    return body.toString()
}

fun formatInr(paise: Long): String = formatInr(Money(paise))

/** 1234567 -> "12,34,567" (Indian lakh/crore grouping). */
fun groupIndian(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    val lastThree = digits.substring(digits.length - 3)
    val rest = digits.substring(0, digits.length - 3)
    val builder = StringBuilder()
    var index = rest.length
    while (index > 2) {
        builder.insert(0, "," + rest.substring(index - 2, index))
        index -= 2
    }
    if (index > 0) builder.insert(0, rest.substring(0, index))
    return builder.toString() + "," + lastThree
}

/** Compact form for chart axes: ₹1.2L, ₹45k. */
fun formatInrCompact(money: Money): String {
    val negative = money.isNegative
    val rupees = Math.abs(money.paise) / 100L
    val text = when {
        rupees >= 10_000_000L -> "₹" + trimmedDecimal(rupees / 10_000_000.0) + "Cr"
        rupees >= 100_000L -> "₹" + trimmedDecimal(rupees / 100_000.0) + "L"
        rupees >= 1_000L -> "₹" + trimmedDecimal(rupees / 1_000.0) + "k"
        else -> "₹" + rupees
    }
    return if (negative) "\u2212$text" else text
}

private fun trimmedDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
