package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_GATEWAY
import org.springframework.http.HttpStatus.NOT_FOUND
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

      nomisApi.verify(
        getRequestedFor(urlPathMatching("/prisoners/A1234BC/profile-details"))
          .withQueryParam("latestBookingOnly", equalTo("true"))
          .withQueryParam("profileTypes", equalTo("MARITAL"))
          .withQueryParam("profileTypes", equalTo("CHILD")),
      )
      dpsApi.verify(getRequestedFor(urlPathEqualTo("/sync/A1234BC/domestic-status")))
      dpsApi.verify(getRequestedFor(urlPathEqualTo("/sync/A1234BC/number-of-children")))
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
      nomisApi.stubGetProfileDetails("A1234BC", NOT_FOUND)
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
    fun `should do nothing if NOMIS has no latest booking`() = runTest {
      nomisApi.stubGetProfileDetails(
        "A1234BC",
        profileDetailsResponse(
          offenderNo = "A1234BC",
          bookings = listOf(),
        ),
      )
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", NOT_FOUND)
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", NOT_FOUND)

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
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", BAD_GATEWAY)

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
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("$TELEMETRY_PREFIX-report-$it"),
              any(),
              isNull(),
            )
          }
        }
    }

    @Nested
    inner class Failures {
      @Test
      fun `should handle a mismatched prisoner`() {
        reset(telemetryClient)
        stubPages()
        forEachPrisoner { prisonerNumber ->
          dpsApi.stubGetDomesticStatus(prisonerNumber, domesticStatus(domesticStatusCode = "M"))
          dpsApi.stubGetNumberOfChildren(prisonerNumber, numberOfChildren(numberOfChildren = "2"))
          // Stub a mismatch on the 4th prisoner
          if (prisonerNumber == "A0004TZ") {
            stubGetProfileDetails(
              prisonerNumber,
              listOf(
                profileDetails(type = "MARITAL", code = "D"),
                profileDetails(type = "CHILD", code = "3"),
              ),
            )
          } else {
            stubGetProfileDetails(
              prisonerNumber,
              listOf(
                profileDetails(type = "MARITAL", code = "M"),
                profileDetails(type = "CHILD", code = "2"),
              ),
            )
          }
        }

        runReconciliation(expectSuccess = false)

        // should publish failure telemetry for prisoner
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-prisoner-failed"),
          check {
            assertThat(it).containsAllEntriesOf(
              mapOf(
                "offenderNo" to "A0004TZ",
                "differences" to "domestic-status, number-of-children",
              ),
            )
          },
          isNull(),
        )

        // should publish failure telemetry for report
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-report-failed"),
          check {
            assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
            assertThat(it).containsEntry("mismatch-count", "1")
            assertThat(it).containsEntry("mismatch-prisoners", "[A0004TZ]")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class Errors {
      @Test
      fun `API call should fail if error getting prisoner count`() {
        reset(telemetryClient)
        NomisApiExtension.nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 0, pageSize = 1, responseCode = 502)

        webTestClient.put().uri("/contact-person/profile-details/reports/reconciliation")
          .exchange()
          .expectStatus().is5xxServerError
      }

      @Test
      fun `should handle an error getting one of the prisoner pages`() {
        reset(telemetryClient)
        NomisApiExtension.nomisApi.apply {
          stubGetActivePrisonersInitialCount(noActivePrisoners)
          stubGetActivePrisonersPage(
            noActivePrisoners,
            pageNumber = 0,
            pageSize = pageSize,
            numberOfElements = pageSize,
          )
          // fail to retrieve page 1
          stubGetActivePrisonersPageWithError(pageNumber = 1, pageSize = pageSize, responseCode = 500)
          stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 2, pageSize = pageSize, numberOfElements = 1)
        }
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

        // should call DPS and NOMIS for each prisoner on successful pages
        forEachPrisoner { prisonerNumber ->
          // for prisoners from the 2nd page, we should not call the APIs
          val count = if (listOf("A0004TZ", "A0005TZ", "A0006TZ").contains(prisonerNumber)) 0 else 1
          nomisApi.verify(count, getRequestedFor(urlPathEqualTo("/prisoners/$prisonerNumber/profile-details")))
          dpsApi.verify(count, getRequestedFor(urlPathEqualTo("/sync/$prisonerNumber/domestic-status")))
          dpsApi.verify(count, getRequestedFor(urlPathEqualTo("/sync/$prisonerNumber/number-of-children")))
        }

        // should publish error telemetry for page
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-page-error"),
          check {
            assertThat(it).containsEntry("page", "1")
            assertThat(it).containsEntry(
              "error",
              "500 Internal Server Error from GET http://localhost:8082/prisoners/ids/active",
            )
          },
          isNull(),
        )

        // should publish success telemetry for report
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-report-success"),
          check {
            assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
            assertThat(it).containsEntry("mismatch-count", "0")
          },
          isNull(),
        )
      }

      @Test
      fun `should handle an error getting a single prisoner`() {
        reset(telemetryClient)
        stubPages()
        forEachPrisoner { prisonerNumber ->
          dpsApi.stubGetDomesticStatus(prisonerNumber, domesticStatus(domesticStatusCode = "M"))
          dpsApi.stubGetNumberOfChildren(prisonerNumber, numberOfChildren(numberOfChildren = "2"))
          // the 4th prisoner's NOMIS call returns an error
          if (prisonerNumber == "A0004TZ") {
            nomisApi.stubGetProfileDetails(prisonerNumber, BAD_GATEWAY)
          } else {
            stubGetProfileDetails(
              prisonerNumber,
              listOf(
                profileDetails(type = "MARITAL", code = "M"),
                profileDetails(type = "CHILD", code = "2"),
              ),
            )
          }
        }

        runReconciliation()

        // should publish error telemetry for prisoner
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-prisoner-error"),
          check {
            assertThat(it).containsEntry("offenderNo", "A0004TZ")
            assertThat(it).containsEntry(
              "error",
              "502 Bad Gateway from GET http://localhost:8082/prisoners/A0004TZ/profile-details",
            )
          },
          isNull(),
        )

        // should publish success telemetry for report despite error
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-report-success"),
          check {
            assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
            assertThat(it).containsEntry("mismatch-count", "0")
          },
          isNull(),
        )
      }

      @Test
      fun `should handle handle connection failures and retry both DPS and NOMIS`() {
        reset(telemetryClient)
        stubPages()
        forEachPrisoner { prisonerNumber ->
          // the 4th prisoner needs a retry on both NOMIS and DPS endpoints
          if (prisonerNumber == "A0004TZ") {
            nomisApi.stubGetProfileDetailsAfterRetry(
              prisonerNumber,
              profileDetailsResponse(
                offenderNo = prisonerNumber,
                bookings = listOf(
                  booking(
                    profileDetails = listOf(
                      profileDetails(type = "MARITAL", code = "M"),
                      profileDetails(type = "CHILD", code = "2"),
                    ),
                  ),
                ),
              ),
            )
            dpsApi.stubGetDomesticStatusAfterRetry(prisonerNumber, domesticStatus(domesticStatusCode = "M"))
            dpsApi.stubGetNumberOfChildrenAfterRetry(prisonerNumber, numberOfChildren(numberOfChildren = "2"))
          } else {
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
        }

        runReconciliation()

        // should retry the NOMIS and DPS calls for the failed prisoner
        nomisApi.verify(2, getRequestedFor(urlPathEqualTo("/prisoners/A0004TZ/profile-details")))
        dpsApi.verify(2, getRequestedFor(urlPathEqualTo("/sync/A0004TZ/domestic-status")))
        dpsApi.verify(2, getRequestedFor(urlPathEqualTo("/sync/A0004TZ/number-of-children")))

        // should NOT publish error telemetry for prisoner
        verify(telemetryClient, times(0)).trackEvent(
          eq("$TELEMETRY_PREFIX-prisoner-error"),
          anyMap(),
          isNull(),
        )

        // should publish success telemetry
        verify(telemetryClient).trackEvent(
          eq("$TELEMETRY_PREFIX-report-success"),
          check {
            assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
            assertThat(it).containsEntry("mismatch-count", "0")
          },
          isNull(),
        )
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
