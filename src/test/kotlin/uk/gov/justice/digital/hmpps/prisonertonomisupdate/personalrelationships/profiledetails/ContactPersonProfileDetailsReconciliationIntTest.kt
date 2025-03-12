package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsReconciliationService.Companion.TELEMETRY_PREFIX
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.booking
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.profileDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.profileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

class ContactPersonProfileDetailsReconciliationIntTest(
  @Autowired val nomisApi: ProfileDetailsNomisApiMockServer,
  @Autowired val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired val service: ContactPersonProfileDetailsReconciliationService,
) : IntegrationTestBase() {

  @Nested
  inner class SinglePrisoner {
    @Test
    fun `should do nothing if no differences`() = runTest {
      stubGetProfileDetails(
        "A1234BC",
        listOf(
          profileDetails(type = "MARITAL", code = "M"),
          profileDetails(type = "CHILD", code = "2"),
        ),
      )
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", domesticStatus(domesticStatusCode = "M"))
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", numberOfChildren(numberOfChildren = "2"))

      service.checkPrisoner("A1234BC").also {
        assertThat(it).isNull()
      }

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should report differences`() = runTest {
      stubGetProfileDetails(
        "A1234BC",
        listOf(
          profileDetails(type = "MARITAL", code = "M"),
          profileDetails(type = "CHILD", code = "2"),
        ),
      )
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", domesticStatus(domesticStatusCode = "N"))
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", numberOfChildren(numberOfChildren = "3"))

      service.checkPrisoner("A1234BC").also {
        assertThat(it).isEqualTo("A1234BC")
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234BC",
              "differences" to "domestic-status, number-of-children",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report null differences`() = runTest {
      stubGetProfileDetails(
        "A1234BC",
        listOf(
          profileDetails(type = "MARITAL", code = "M"),
          profileDetails(type = "CHILD", code = null),
        ),
      )
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", domesticStatus(domesticStatusCode = null))
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", numberOfChildren(numberOfChildren = "2"))

      service.checkPrisoner("A1234BC").also {
        assertThat(it).isEqualTo("A1234BC")
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234BC",
              "differences" to "domestic-status-null-dps, number-of-children-null-nomis",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report null differences when not found`() = runTest {
      nomisApi.stubGetProfileDetails("A1234BC", HttpStatus.NOT_FOUND)
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", domesticStatus(domesticStatusCode = "M"))
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", numberOfChildren(numberOfChildren = "2"))

      service.checkPrisoner("A1234BC").also {
        assertThat(it).isEqualTo("A1234BC")
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234BC",
              "differences" to "domestic-status-null-nomis, number-of-children-null-nomis",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should do nothing if both systems have null values`() = runTest {
      stubGetProfileDetails(
        "A1234BC",
        listOf(
          profileDetails(type = "MARITAL", code = null),
          profileDetails(type = "CHILD", code = null),
        ),
      )
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", domesticStatus(domesticStatusCode = null))
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", numberOfChildren(numberOfChildren = null))

      service.checkPrisoner("A1234BC").also {
        assertThat(it).isNull()
      }

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should report errors from NOMIS`() = runTest {
      nomisApi.stubGetProfileDetails("A1234BC", HttpStatus.INTERNAL_SERVER_ERROR)
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", domesticStatus(domesticStatusCode = "M"))
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", numberOfChildren(numberOfChildren = "2"))

      service.checkPrisoner("A1234BC").also {
        assertThat(it).isNull()
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-prisoner-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234BC",
              "error" to "500 Internal Server Error from GET http://localhost:8082/prisoners/A1234BC/profile-details",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report errors from DPS`() = runTest {
      stubGetProfileDetails(
        "A1234BC",
        listOf(
          profileDetails(type = "MARITAL", code = "M"),
          profileDetails(type = "CHILD", code = "2"),
        ),
      )
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", domesticStatus(domesticStatusCode = "M"))
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", HttpStatus.BAD_GATEWAY)

      service.checkPrisoner("A1234BC").also {
        assertThat(it).isNull()
      }

      verify(telemetryClient).trackEvent(
        eq("$TELEMETRY_PREFIX-prisoner-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234BC",
              "error" to "502 Bad Gateway from GET http://localhost:8099/sync/A1234BC/number-of-children",
            ),
          )
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class FullReconciliation {
    private val noActivePrisoners = 7L
    private val pageSize = 3L

    private fun stubPages() {
      NomisApiExtension.nomisApi.apply {
        stubGetActivePrisonersInitialCount(noActivePrisoners)
        stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 0, pageSize = pageSize, numberOfElements = pageSize)
        stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 1, pageSize = pageSize, numberOfElements = pageSize)
        stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 2, pageSize = pageSize, numberOfElements = 1)
      }
    }

    private fun forEachPrisoner(action: (offenderNo: String) -> Any) {
      (1..noActivePrisoners)
        .map { generateOffenderNo(sequence = it) }
        .forEach { offenderNo -> action(offenderNo) }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setup() {
        reset(telemetryClient)
        stubPages()
        forEachPrisoner { prisonerNumber ->
          stubGetProfileDetails(
            prisonerNumber,
            listOf(
              profileDetails(type = "MARITAL", code = "M"),
              profileDetails(type = "CHILD", code = "2"),
            ),
          )
          dpsApi.stubGetDomesticStatus(prisonerNumber, domesticStatus(domesticStatusCode = "M"))
          dpsApi.stubGetNumberOfChildren(prisonerNumber, numberOfChildren(numberOfChildren = "2"))
        }

        runReconciliation()
      }

      @Test
      fun `should run a reconciliation with no problems`() {
        // should publish requested telemetry
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-report-requested"),
          check {
            assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
          },
          isNull(),
        )

        // should request pages of prisoners
        NomisApiExtension.nomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/ids/active"))
            .withQueryParam("size", equalTo("1")),
        )
        NomisApiExtension.nomisApi.verify(
          3,
          getRequestedFor(urlPathEqualTo("/prisoners/ids/active"))
            .withQueryParam("size", equalTo("$pageSize")),
        )

        // should call NOMIS and DPS for each prisoner
        forEachPrisoner { prisonerNumber ->
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/$prisonerNumber/profile-details")))
          dpsApi.verify(getRequestedFor(urlPathEqualTo("/sync/$prisonerNumber/domestic-status")))
          dpsApi.verify(getRequestedFor(urlPathEqualTo("/sync/$prisonerNumber/number-of-children")))
        }

        // `should publish success telemetry
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-report-success"),
          check {
            assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
            assertThat(it).containsEntry("mismatch-count", "0")
            assertThat(it).containsEntry("mismatch-prisoners", "[]")
          },
          isNull(),
        )
      }
    }

    private fun runReconciliation(expectSuccess: Boolean = true) {
      webTestClient.put().uri("/contact-person/profile-details/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
        .also {
          awaitReportFinished(expectSuccess)
        }
    }

    private fun awaitReportFinished(expectSuccess: Boolean = true) {
      expectSuccess
        .let { if (it) "success" else "failed" }
        .also {
          await untilAsserted { verify(telemetryClient).trackEvent(eq("$TELEMETRY_PREFIX-report-$it"), any(), isNull()) }
        }
    }
  }

  fun stubGetProfileDetails(
    offenderNo: String = "A1234BC",
    profileDetails: List<ProfileDetailsResponse>,
  ) = nomisApi.stubGetProfileDetails(
    offenderNo,
    profileDetailsResponse(
      offenderNo = offenderNo,
      bookings = listOf(
        booking(
          profileDetails = profileDetails,
        ),
      ),
    ),
  )
}
