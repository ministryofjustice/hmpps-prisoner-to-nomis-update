@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  OrganisationsReconciliationService::class,
  OrganisationsNomisApiService::class,
  OrganisationsDpsApiService::class,
  OrganisationsConfiguration::class,
  RetryApiService::class,
  OrganisationsNomisApiMockServer::class,
)
internal class OrganisationsReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var nomisApi: OrganisationsNomisApiMockServer

  private val dpsApi = OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer

  @Autowired
  private lateinit var service: OrganisationsReconciliationService

  @Nested
  inner class CheckOrganisationMatch {
    val corporateAndOrganisationId = 1L

    @Nested
    inner class WhenBothOrganisationsAreIdentical {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(corporateAndOrganisationId, corporateOrganisation(corporateAndOrganisationId).copy(active = true, name = "South Yorkshire Police"))
        dpsApi.stubGetOrganisation(corporateAndOrganisationId, organisationDetails().copy(corporateAndOrganisationId, active = true, organisationName = "South Yorkshire Police"))
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkOrganisationMatch(corporateAndOrganisationId),
        ).isNull()
      }
    }

    @Nested
    inner class WhenDPSOrganisationIsMissing {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(corporateAndOrganisationId, corporateOrganisation(corporateAndOrganisationId).copy(active = true, name = "South Yorkshire Police"))
        dpsApi.stubGetOrganisation(corporateAndOrganisationId, HttpStatus.NOT_FOUND)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkOrganisationMatch(corporateAndOrganisationId)).isNotNull.extracting { it!!.organisationId }.isEqualTo(corporateAndOrganisationId)
      }

      @Test
      fun `telemetry will show DPS organisation is null`() = runTest {
        service.checkOrganisationMatch(corporateAndOrganisationId)
        verify(telemetryClient).trackEvent(
          eq("organisations-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("organisationId", "1")
            assertThat(it).containsEntry("dpsOrganisation", "null")
            assertThat(it["nomisOrganisation"]).contains("South Yorkshire Police")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenOneOrganisationIsDeactivated {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(corporateAndOrganisationId, corporateOrganisation(corporateAndOrganisationId).copy(active = true, name = "South Yorkshire Police"))
        dpsApi.stubGetOrganisation(corporateAndOrganisationId, organisationDetails().copy(corporateAndOrganisationId, active = false, organisationName = "South Yorkshire Police"))
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkOrganisationMatch(corporateAndOrganisationId)).isNotNull
      }

      @Test
      fun `telemetry will show DPS organisation has different statuses`() = runTest {
        service.checkOrganisationMatch(corporateAndOrganisationId)
        verify(telemetryClient).trackEvent(
          eq("organisations-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("organisationId", "1")
            assertThat(it["dpsOrganisation"]).contains("active=false")
            assertThat(it["nomisOrganisation"]).contains("active=true")
          },
          isNull(),
        )
      }
    }
  }
}
