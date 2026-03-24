package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.springframework.test.util.ReflectionTestUtils
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

class CorePersonReconciliationServiceTest {
  private val telemetryClient: TelemetryClient = mock()
  private val cprCorePersonApiService: CorePersonCprApiService = mock()
  private val nomisCorePersonApiService: CorePersonNomisApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val service = CorePersonReconciliationService(
    telemetryClient,
    cprCorePersonApiService,
    nomisCorePersonApiService,
    nomisApiService,
    20,
    "religions",
  )

  @Nested
  @ParameterizedClass
  @CsvSource(
    value = [
      ",,true",
      ",2016-06-01,false",
      "2016-06-01,,false",
      "2016-06-01,2016-06-01,true",
      "2016-06-01,2016-07-01,false",
    ],
  )
  inner class LocalDateTests(private val nomisDate: LocalDate?, private val cprDate: LocalDate?, private val fieldsEquals: Boolean) {
    @Test
    fun `start date test`() {
      val nomis = listOf(createReligion().copy(startDate = nomisDate))
      val cpr = listOf(createReligion().copy(startDate = cprDate))
      comparePrisonerReligions(nomis, cpr, fieldsEquals, "startDate", nomisDate, cprDate)
    }

    @Test
    fun `end date test`() {
      val nomis = listOf(createReligion().copy(endDate = nomisDate))
      val cpr = listOf(createReligion().copy(endDate = cprDate))
      comparePrisonerReligions(nomis, cpr, fieldsEquals, "endDate", nomisDate, cprDate)
    }
  }

  @Nested
  @ParameterizedClass
  @CsvSource(
    value = [
      ",,true",
      ",2016-06-01T01:02:03,false",
      "2016-06-01T01:02:03,,false",
      "2016-06-01T01:02:03,2016-06-01T01:02:03,true",
      "2016-06-01T01:02:03,2016-07-01T01:02:03,false",
      "2016-06-01T01:02:03.123456,2016-06-01T01:02:03.23456,true",
    ],
  )
  inner class LocalDateTimeTests(private val nomisDate: LocalDateTime?, private val cprDate: LocalDateTime?, private val fieldsEquals: Boolean) {
    @Test
    fun `create date time test`() {
      // null tests not valid for create date time
      if (nomisDate == null || cprDate == null) return
      val nomis = listOf(createReligion().copy(createDatetime = nomisDate))
      val cpr = listOf(createReligion().copy(createDatetime = cprDate))
      comparePrisonerReligions(nomis, cpr, fieldsEquals, "createDatetime", nomisDate, cprDate)
    }

    @Test
    fun `modify date time test`() {
      val nomis = listOf(createReligion().copy(modifyDatetime = nomisDate))
      val cpr = listOf(createReligion().copy(modifyDatetime = cprDate))
      comparePrisonerReligions(nomis, cpr, fieldsEquals, "modifyDatetime", nomisDate, cprDate)
    }
  }

  private fun comparePrisonerReligions(
    nomis: List<PrisonerReligion>,
    cpr: List<PrisonerReligion>,
    fieldsEquals: Boolean,
    field: String,
    nomisField: Any?,
    cprField: Any?,
  ) {
    val differences = mutableMapOf<String, String>()
    ReflectionTestUtils.invokeMethod<Void>(
      service,
      "appendReligionsDifference",
      nomis,
      cpr,
      differences,
      "religions",
    )

    if (fieldsEquals) {
      assertThat(differences).isEmpty()
    } else {
      assertThat(differences).containsEntry("religions", "0-$field:nomis=$nomisField, cpr=$cprField")
    }
  }

  private fun createReligion(): PrisonerReligion = PrisonerReligion(
    religion = "BOB",
    startDate = LocalDate.parse("2022-01-01"),
    endDate = LocalDate.parse("2023-01-01"),
    current = true,
    comments = "Some comments",
    createUsername = "ME",
    createDatetime = LocalDateTime.parse("2025-02-03T10:20:30"),
    modifyUsername = null,
    modifyDatetime = null,
  )
}
