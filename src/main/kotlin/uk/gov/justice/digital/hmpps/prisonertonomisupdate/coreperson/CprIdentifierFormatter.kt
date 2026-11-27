package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import java.time.LocalDate

/**
 * Replicates the PNC and CRO canonicalisation performed by hmpps-person-record (CPR) when storing prison identifiers,
 * so that NOMIS values can be compared with the CPR values.
 * Valid PNC and CRO values are converted into canonical form, invalid values are left as received.
 */
internal object CprIdentifierFormatter {
  private const val VALID_LETTERS = "ZABCDEFGHJKLMNPQRTUVWXY"

  private const val PNC_SERIAL_NUM_LENGTH = 7
  private const val PNC_YEAR_END = 4
  private const val LONG_PNC_ID_LENGTH = 12
  private const val CENTURY = 100
  private val PNC_REGEX = Regex("\\d{2,$PNC_YEAR_END}(/?)\\d{1,$PNC_SERIAL_NUM_LENGTH}[A-Z]$")

  private const val CRO_SERIAL_NUM_LENGTH = 6
  private val SF_CRO_REGEX = Regex("^SF\\d{2}/\\d{1,$CRO_SERIAL_NUM_LENGTH}[A-Z]$")
  private val CRO_REGEX = Regex("^\\d{1,$CRO_SERIAL_NUM_LENGTH}/\\d{2}[A-Z]$")

  fun format(type: String?, value: String?): String? = when (type) {
    "PNC" -> value?.let { canonicalPnc(it) ?: it }
    "CRO" -> value?.let { canonicalCro(it) ?: it }
    else -> value
  }

  fun canonicalPnc(input: String): String? {
    val pnc = input.ifEmpty { null }?.uppercase()?.takeIf { it.matches(PNC_REGEX) }?.replace("/", "") ?: return null
    val checkChar = pnc.last()
    val (year, serialNum) = if (pnc.length < LONG_PNC_ID_LENGTH) {
      yearFromLastTwoDigits(pnc.take(2).toInt()) to pnc.substring(2).dropLast(1)
    } else {
      pnc.take(PNC_YEAR_END) to pnc.dropLast(1).takeLast(PNC_SERIAL_NUM_LENGTH)
    }
    val paddedSerialNum = serialNum.padStart(PNC_SERIAL_NUM_LENGTH, '0')
    return "$year/$paddedSerialNum$checkChar".takeIf { isValid(checkChar, year.takeLast(2) + paddedSerialNum) }
  }

  fun canonicalCro(input: String): String? {
    val cro = input.ifEmpty { null }?.uppercase() ?: return null
    val checkChar = cro.last()
    return when {
      cro.matches(SF_CRO_REGEX) -> {
        val (yearDigits, serialNum) = cro.drop(2).dropLast(1).split("/")
        "SF$yearDigits/$serialNum$checkChar".takeIf { isValid(checkChar, yearDigits + serialNum) }
      }
      cro.matches(CRO_REGEX) -> {
        val (serialNum, yearDigits) = cro.dropLast(1).split("/")
        val paddedSerialNum = serialNum.padStart(CRO_SERIAL_NUM_LENGTH, '0')
        "$paddedSerialNum/$yearDigits$checkChar".takeIf { isValid(checkChar, yearDigits + paddedSerialNum) }
      }
      else -> null
    }
  }

  private fun isValid(checkChar: Char, numericPortion: String): Boolean = checkChar == VALID_LETTERS[numericPortion.toInt().mod(VALID_LETTERS.length)]

  private fun yearFromLastTwoDigits(year: Int): String {
    val currentYear = LocalDate.now().year % CENTURY
    val prefix = if (year <= currentYear) "20" else "19"
    return prefix + year.toString().padStart(2, '0')
  }
}
