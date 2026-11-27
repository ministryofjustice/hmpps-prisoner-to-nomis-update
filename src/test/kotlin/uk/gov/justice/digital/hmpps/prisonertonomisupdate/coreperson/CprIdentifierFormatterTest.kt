package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CprIdentifierFormatterTest {
  @ParameterizedTest
  @CsvSource(
    "PNC, 2000/1234567V, 2000/1234567V",
    "PNC, 00/1234567V, 2000/1234567V",
    "PNC, 001234567V, 2000/1234567V",
    "PNC, 2000/1234567v, 2000/1234567V",
    "PNC, 99/123P, 1999/0000123P",
    "PNC, 19990000123P, 1999/0000123P",
    "PNC, 2000/1234567A, 2000/1234567A",
    "PNC, not a pnc, not a pnc",
    "CRO, 123456/99L, 123456/99L",
    "CRO, 123/99D, 000123/99D",
    "CRO, 123/99d, 000123/99D",
    "CRO, sf99/123R, SF99/123R",
    "CRO, 123456/99A, 123456/99A",
    "CRO, not a cro, not a cro",
    "PORT_REF, 99/123p, 99/123p",
  )
  fun `should format identifiers to match CPR`(type: String, value: String, expected: String) {
    assertThat(CprIdentifierFormatter.format(type, value)).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource("PNC", "CRO")
  fun `should leave empty and null values as received`(type: String) {
    assertThat(CprIdentifierFormatter.format(type, "")).isEqualTo("")
    assertThat(CprIdentifierFormatter.format(type, null)).isNull()
  }
}
