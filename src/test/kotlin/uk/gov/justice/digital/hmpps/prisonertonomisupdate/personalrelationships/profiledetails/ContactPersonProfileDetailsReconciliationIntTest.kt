package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import org.springframework.beans.factory.annotation.Value
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
  @Value("\${reports.contact-person.profile-details.reconciliation.page-size}") private val pageSize: Long,
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
          .withQueryParam("profileTypes", havingExactly("MARITAL", "CHILD")),
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
    private var noActivePrisoners: Long = 0
    private fun pages() = noActivePrisoners / pageSize + if (noActivePrisoners % pageSize == 0L) 0 else 1
    private fun lastPrisonerNumber() = generateOffenderNo(sequence = noActivePrisoners)

    private fun stubPages() {
      NomisApiExtension.nomisApi.apply {
        stubGetActivePrisonersInitialCount(noActivePrisoners)
        (0..pages() - 1).forEach { pageNumber ->
          val elements = if (pageNumber < (pages() - 1) || noActivePrisoners % pageSize == 0L) pageSize else noActivePrisoners % pageSize
          stubGetActivePrisonersPage(noActivePrisoners, pageNumber = pageNumber, pageSize = pageSize, numberOfElements = elements, fixedDelay = 550)
        }
      }
    }

    private fun forEachPrisoner(action: (offenderNo: String) -> Any) {
      (1..noActivePrisoners)
        .map { generateOffenderNo(sequence = it) }
        .forEach { offenderNo -> action(offenderNo) }
    }

    @Nested
    inner class HappyPath {
      private fun setUp(count: Long) {
        noActivePrisoners = count

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

      @ParameterizedTest
      @ValueSource(longs = [7, 6])
      fun `should run a reconciliation with no problems`(noActivePrisoner: Long) {
        setUp(noActivePrisoner)

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
          pages().toInt(),
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

    private fun runReconciliation(expectSuccess: Boolean = true) = runTest {
      service.reconciliationReport()
        .also { awaitReportFinished(expectSuccess) }
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
      @ParameterizedTest
      @ValueSource(longs = [7, 6])
      fun `should handle a mismatched prisoner`(count: Long) {
        noActivePrisoners = count

        reset(telemetryClient)
        stubPages()
        forEachPrisoner { prisonerNumber ->
          dpsApi.stubGetDomesticStatus(prisonerNumber, domesticStatus(domesticStatusCode = "M"))
          dpsApi.stubGetNumberOfChildren(prisonerNumber, numberOfChildren(numberOfChildren = "2"))
          // Stub a mismatch on the last prisoner - using the last prisoner should find errors with closing channels too soon
          if (prisonerNumber == lastPrisonerNumber()) {
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
                "offenderNo" to lastPrisonerNumber(),
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
            assertThat(it).containsEntry("mismatch-prisoners", "[${lastPrisonerNumber()}]")
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
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_UPDATE__RECONCILIATION__R")))
          .exchange()
          .expectStatus().is5xxServerError
      }

      @ParameterizedTest
      @ValueSource(longs = [7, 6])
      fun `should handle an error getting one of the prisoner pages`(count: Long) {
        noActivePrisoners = count

        reset(telemetryClient)
        NomisApiExtension.nomisApi.apply {
          stubGetActivePrisonersInitialCount(noActivePrisoners)
          (0..pages() - 1).forEach { pageNumber ->
            // stub an error on page 1
            if (pageNumber == 1L) {
              stubGetActivePrisonersPageWithError(pageNumber = pageNumber, pageSize = pageSize, responseCode = 500)
            } else {
              val elements = if (pageNumber < (pages() - 1) || noActivePrisoners % pageSize == 0L) pageSize else noActivePrisoners % pageSize
              stubGetActivePrisonersPage(noActivePrisoners, pageNumber = pageNumber, pageSize = pageSize, numberOfElements = elements, fixedDelay = 1250)
            }
          }
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
          val count = if (getPrisonerOnPage(1).contains(prisonerNumber)) 0 else 1
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

      @ParameterizedTest
      @ValueSource(longs = [7, 6])
      fun `should handle an error getting a single prisoner`(count: Long) {
        noActivePrisoners = count

        reset(telemetryClient)
        stubPages()
        forEachPrisoner { prisonerNumber ->
          dpsApi.stubGetDomesticStatus(prisonerNumber, domesticStatus(domesticStatusCode = "M"))
          dpsApi.stubGetNumberOfChildren(prisonerNumber, numberOfChildren(numberOfChildren = "2"))
          // the last prisoner's NOMIS call returns an error
          if (prisonerNumber == lastPrisonerNumber()) {
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
            assertThat(it).containsEntry("offenderNo", lastPrisonerNumber())
            assertThat(it).containsEntry(
              "error",
              "502 Bad Gateway from GET http://localhost:8082/prisoners/${lastPrisonerNumber()}/profile-details",
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

      @ParameterizedTest
      @ValueSource(longs = [7, 6])
      fun `should handle handle connection failures and retry both DPS and NOMIS`(count: Long) {
        noActivePrisoners = count

        reset(telemetryClient)
        stubPages()
        forEachPrisoner { prisonerNumber ->
          // the last prisoner needs a retry on both NOMIS and DPS endpoints
          if (prisonerNumber == lastPrisonerNumber()) {
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
        nomisApi.verify(2, getRequestedFor(urlPathEqualTo("/prisoners/${lastPrisonerNumber()}/profile-details")))
        dpsApi.verify(2, getRequestedFor(urlPathEqualTo("/sync/${lastPrisonerNumber()}/domestic-status")))
        dpsApi.verify(2, getRequestedFor(urlPathEqualTo("/sync/${lastPrisonerNumber()}/number-of-children")))

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

    private fun getPrisonerOnPage(page: Int) = (1..pageSize).map { page * pageSize + it }.map { generateOffenderNo(sequence = it) }
  }

  fun stubGetProfileDetails(
    offenderNo: String = "A1234BC",
    profileDetails: List<ProfileDetailsResponse>,
    fixedDelay: Int = 30,
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
    fixedDelay = fixedDelay,
  )
}
