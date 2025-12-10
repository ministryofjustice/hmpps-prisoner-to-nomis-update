package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase

class CorePersonReconciliationIntTest(
  @Autowired private val nomisApi: CorePersonNomisApiMockServer,
  @Autowired private val service: CorePersonReconciliationService,
  @Value("\${reports.core-person.reconciliation.page-size}") private val pageSize: Long,
) : IntegrationTestBase() {
  private companion object {
    private const val TELEMETRY_PREFIX = "coreperson-reports-reconciliation"
  }
  private val cprApi = CorePersonCprApiExtension.corePersonCprApi

  @Nested
  inner class SinglePrisoner {
    @Test
    fun `should do nothing if no differences`() = runTest {
      stubGetCorePerson(prisonNumber = "A1234BC", nationality = "BR")
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = "BR"))

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
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", corePersonDto(nationality = "M"))

      service.checkCorePersonMatch("A1234BC").also {
        assertThat(it?.prisonNumber).isEqualTo("A1234BC")
        assertThat(it?.differences).containsExactly("nationality: nomis=BR, cpr=M")
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "differences5" to "nationality: nomis=BR, cpr=M",
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
        assertThat(it?.differences).containsExactly("nationality: nomis=null, cpr=BR")
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "differences5" to "nationality: nomis=null, cpr=BR",
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
        assertThat(it?.differences).containsExactly("nationality: nomis=null, cpr=BR")
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-mismatch"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "prisonNumber" to "A1234BC",
              "differences5" to "nationality: nomis=null, cpr=BR",
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
      cprApi.stubGetCorePerson(prisonNumber = "A1234BC", status = HttpStatus.BAD_GATEWAY)

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

  fun stubGetCorePerson(
    prisonNumber: String = "A1234BC",
    nationality: String? = "BR",
    fixedDelay: Int = 30,
  ) = nomisApi.stubGetCorePerson(
    response = corePerson(prisonNumber = prisonNumber, nationality = nationality),
    fixedDelay = fixedDelay,
  )
}
