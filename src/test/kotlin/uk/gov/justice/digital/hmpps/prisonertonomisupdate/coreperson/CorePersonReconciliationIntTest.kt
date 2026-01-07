package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_GATEWAY
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderNationality
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.time.LocalDateTime
import kotlin.collections.listOf

@ExtendWith(MockitoExtension::class)
class CorePersonReconciliationIntTest(
  @Autowired private val nomisApi: CorePersonNomisApiMockServer,
  @Autowired private val service: CorePersonReconciliationService,
  @Value($$"${reports.core-person.reconciliation.page-size}") private val pageSize: Long,
) : IntegrationTestBase() {
  private companion object {
    private const val TELEMETRY_PREFIX = "coreperson-reports-reconciliation"
  }
  private val cprApi = CorePersonCprApiExtension.corePersonCprApi

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @Nested
  inner class SinglePrisoner {
    @Test
    fun `should do nothing if no differences`() = runTest {
      stubGetCorePerson(prisonNumber = "A1234BC", nationality = "BR", religion = "ZOO")
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = "BR", religion = "ZOO"))

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it).isNull()
      }

      nomisApi.verify(getRequestedFor(urlPathMatching("/core-person/A1234BC")))
      cprApi.verify(getRequestedFor(urlPathEqualTo("/person/prison/A1234BC")))
      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should report differences`() = runTest {
      stubGetCorePerson(prisonNumber = "A1234BC", nationality = "BR")
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = "M", religion = "ZOO"))

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it?.prisonNumber).isEqualTo("A1234BC")
        assertThat(it?.differences).containsExactly(
          entry("nationality", "nomis=BR, cpr=M"),
          entry("religion", "nomis=null, cpr=ZOO Description"),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "differences5" to "nationality, religion",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should ignore previous bookings`() = runTest {
      nomisApi.stubGetCorePerson(
        prisonNumber = "A1234BC",
        response = corePerson(prisonNumber = "A1234BC").copy(
          nationalities =
          listOf(
            OffenderNationality(
              bookingId = 1,
              nationality = CodeDescription(code = "M", description = "M Description"),
              latestBooking = false,
              startDateTime = LocalDateTime.now().minusDays(1),
            ),
          ),
        ),
      )
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = "M"))

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it?.prisonNumber).isEqualTo("A1234BC")
        assertThat(it?.differences).containsExactly(entry("nationality", "nomis=null, cpr=M"))
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "differences5" to "nationality",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report null differences`() = runTest {
      stubGetCorePerson(prisonNumber = "A1234BC", nationality = null)
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = "BR"))

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it?.prisonNumber).isEqualTo("A1234BC")
        assertThat(it?.differences).containsExactly(entry("nationality", "nomis=null, cpr=BR"))
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "differences5" to "nationality",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report null differences when not found`() = runTest {
      nomisApi.stubGetCorePerson("A1234BC", status = NOT_FOUND)
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = "BR"))

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it?.prisonNumber).isEqualTo("A1234BC")
        assertThat(it?.differences).containsExactly(entry("nationality", "nomis=null, cpr=BR"))
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "differences5" to "nationality",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should do nothing if both systems have null values`() = runTest {
      stubGetCorePerson(prisonNumber = "A1234BC", nationality = null)
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = null))

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it).isNull()
      }

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should report errors from NOMIS`() = runTest {
      nomisApi.stubGetCorePerson("A1234BC", status = HttpStatus.INTERNAL_SERVER_ERROR)
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = "BR"))

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it).isNull()
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "error" to "500 Internal Server Error from GET http://localhost:8082/core-person/A1234BC",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report errors from DPS`() = runTest {
      stubGetCorePerson(prisonNumber = "A1234BC", nationality = null)
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", status = BAD_GATEWAY)

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it).isNull()
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "error" to "Retries exhausted: 3/3",
            ),
          )
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class FullReconciliation {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)

      NomisApiExtension.nomisApi.stuGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(
          lastBookingId = 10,
          prisonerIds = (1L..10L).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      NomisApiExtension.nomisApi.stuGetAllLatestBookings(
        bookingId = 10,
        response = BookingIdsWithLast(
          lastBookingId = 20,
          prisonerIds = (11L..20L).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      NomisApiExtension.nomisApi.stuGetAllLatestBookings(
        bookingId = 20,
        response = BookingIdsWithLast(
          lastBookingId = 30,
          prisonerIds = (21L..30L).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      NomisApiExtension.nomisApi.stuGetAllLatestBookings(
        bookingId = 30,
        response = BookingIdsWithLast(
          lastBookingId = 34,
          prisonerIds = (31L..34L).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )

      // mock non-matching for first, second and last prisoners
      nomisApi.stubGetCorePerson("A0001TZ", corePerson("A0001TZ", nationality = "GB"))
      cprApi.stubGetCorePerson("A0001TZ", corePersonDto(nationality = "12"))

      nomisApi.stubGetCorePerson("A0002TZ", corePerson("A0002TZ", nationality = "US"))
      cprApi.stubGetCorePerson("A0002TZ", corePersonDto(nationality = "9"))

      nomisApi.stubGetCorePerson("A0034TZ", corePerson("A0034TZ", nationality = "IS"))
      cprApi.stubGetCorePerson("A0034TZ", corePersonDto(nationality = "17"))

      // all others are ok
      (3L..<34L).forEach {
        val offenderNo = generateOffenderNo(sequence = it)
        nomisApi.stubGetCorePerson(offenderNo, corePerson(offenderNo, nationality = "BR"))
        cprApi.stubGetCorePerson(offenderNo, corePersonDto(nationality = "BR"))
      }
    }

    @Test
    fun `will output report requested telemetry for full reconciliation`() = runTest {
      service.generateReconciliationReport(false)

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-requested"),
        check { assertThat(it).containsExactlyEntriesOf(mapOf("activeOnly" to "false")) },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report requested telemetry for active only`() = runTest {
      service.generateReconciliationReport(true)

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-requested"),
        check { assertThat(it).containsExactlyEntriesOf(mapOf("activeOnly" to "true")) },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners for full reconciliation`() = runTest {
      service.generateReconciliationReport(false)

      awaitReportFinished()
      NomisApiExtension.nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("0"))
          .withQueryParam("activeOnly", equalTo("false"))
          .withQueryParam("pageSize", equalTo(pageSize.toString())),
      )
      NomisApiExtension.nomisApi.verify(
        // 34 prisoners will be spread over 4 pages of 10 prisoners each
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("10"))
          .withQueryParam("activeOnly", equalTo("false"))
          .withQueryParam("pageSize", equalTo(pageSize.toString())),
      )
      NomisApiExtension.nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("20"))
          .withQueryParam("activeOnly", equalTo("false"))
          .withQueryParam("pageSize", equalTo(pageSize.toString())),
      )
      NomisApiExtension.nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("30"))
          .withQueryParam("activeOnly", equalTo("false"))
          .withQueryParam("pageSize", equalTo(pageSize.toString())),
      )
      NomisApiExtension.nomisApi.checkForUnmatchedRequests()
    }

    @Test
    fun `should execute batches of prisoners for active only`() = runTest {
      service.generateReconciliationReport(true)

      awaitReportFinished()
      NomisApiExtension.nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("0"))
          .withQueryParam("activeOnly", equalTo("true"))
          .withQueryParam("pageSize", equalTo(pageSize.toString())),
      )
      NomisApiExtension.nomisApi.verify(
        // 34 prisoners will be spread over 4 pages of 10 prisoners each
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("10"))
          .withQueryParam("activeOnly", equalTo("true"))
          .withQueryParam("pageSize", equalTo(pageSize.toString())),
      )
      NomisApiExtension.nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("20"))
          .withQueryParam("activeOnly", equalTo("true"))
          .withQueryParam("pageSize", equalTo(pageSize.toString())),
      )
      NomisApiExtension.nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("30"))
          .withQueryParam("activeOnly", equalTo("true"))
          .withQueryParam("pageSize", equalTo(pageSize.toString())),
      )
      NomisApiExtension.nomisApi.checkForUnmatchedRequests()
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
      service.generateReconciliationReport(true)

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("coreperson-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      val mismatchedRecords = telemetryCaptor.allValues.map { it["prisonNumber"] }

      assertThat(mismatchedRecords).containsOnly("A0001TZ", "A0002TZ", "A0034TZ")
      with(telemetryCaptor.allValues.find { it["prisonNumber"] == "A0001TZ" }) {
        assertThat(this).containsEntry("bookingId", "1")
        assertThat(this).containsEntry("differences5", "nationality")
      }
      with(telemetryCaptor.allValues.find { it["prisonNumber"] == "A0002TZ" }) {
        assertThat(this).containsEntry("bookingId", "2")
        assertThat(this).containsEntry("differences5", "nationality")
      }
      with(telemetryCaptor.allValues.find { it["prisonNumber"] == "A0034TZ" }) {
        assertThat(this).containsEntry("bookingId", "34")
        assertThat(this).containsEntry("differences5", "nationality")
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      cprApi.stubGetCorePerson("A0002TZ", status = HttpStatus.INTERNAL_SERVER_ERROR)

      service.generateReconciliationReport(true)

      awaitReportFinished()

      verify(telemetryClient, times(1)).trackEvent(
        eq("coreperson-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
        },
        isNull(),
      )
    }

    @Test
    fun `will complete a report even if a core person from Nomis does not exist`() = runTest {
      nomisApi.stubGetCorePerson("A0002TZ", status = NOT_FOUND)

      service.generateReconciliationReport(true)

      awaitReportFinished()

      verify(telemetryClient, times(0)).trackEvent(
        eq("coreperson-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )
    }

    @Test
    fun `will complete a report even if a core person from dps does not exist`() = runTest {
      cprApi.stubGetCorePerson("A0002TZ", status = NOT_FOUND)

      service.generateReconciliationReport(true)

      awaitReportFinished()

      verify(telemetryClient, times(0)).trackEvent(
        eq("coreperson-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )
    }

    @Test
    fun `will complete a report even if a core person from both nomis and dps do not exist`() = runTest {
      nomisApi.stubGetCorePerson("A0002TZ", status = NOT_FOUND)
      cprApi.stubGetCorePerson("A0002TZ", status = NOT_FOUND)

      service.generateReconciliationReport(true)

      awaitReportFinished()

      verify(telemetryClient, times(0)).trackEvent(
        eq("coreperson-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
        },
        isNull(),
      )
    }

    @Test
    fun `will attempt to complete a report even if the first page fails`() = runTest {
      NomisApiExtension.nomisApi.stuGetAllLatestBookings(bookingId = 0, errorStatus = HttpStatus.INTERNAL_SERVER_ERROR)

      service.generateReconciliationReport(true)

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() = runTest {
      NomisApiExtension.nomisApi.stuGetAllLatestBookings(bookingId = 20, errorStatus = HttpStatus.INTERNAL_SERVER_ERROR)

      service.generateReconciliationReport(true)

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )
    }
  }

  fun stubGetCorePerson(
    prisonNumber: String = "A1234BC",
    nationality: String? = "BR",
    religion: String? = null,
    fixedDelay: Int = 30,
  ) = nomisApi.stubGetCorePerson(
    response = corePerson(prisonNumber = prisonNumber, nationality = nationality, religion = religion),
    fixedDelay = fixedDelay,
  )

  private fun awaitReportFinished() {
    await untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("coreperson-reports-reconciliation-report"),
        any(),
        isNull(),
      )
    }
  }
}
