package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  AgencyRegistersReconciliationService::class,
  AgencyNomisApiService::class,
  AgencyRegistersDpsApiService::class,
  AgencyRegistersConfiguration::class,
  RetryApiService::class,
  AgencyNomisApiMockServer::class,
)
class AgencyRegistersReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var nomisApi: AgencyNomisApiMockServer

  private val dpsApi = AgencyRegistersDpsApiExtension.agencyRegistersApi

  @Autowired
  private lateinit var service: AgencyRegistersReconciliationService

  @Nested
  inner class CheckMatch {
    val agencyId = "SHEFCC"

    @Nested
    inner class WhenAgenciesMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(agencyResponse(), legacyAgencyDto())
      }

      @Test
      fun `will return null`() = runTest {
        assertThat(service.checkMatch(agencyId)).isNull()
      }
    }

    @Nested
    inner class WhenDescriptionDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(description = "Sheffield Crown Court"),
          legacyAgencyDto().copy(name = "Sheffield Big Court"),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkMatch(agencyId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("agency-reconciliation-mismatch"),
          check {
            assertThat(it["agencyId"]).isEqualTo(agencyId)
            assertThat(it["reason"]).isEqualTo("different-core-agency-details")
          },
          isNull(),
        )
      }
    }

    fun stubAgency(nomisAgency: AgencyResponse, dpsAgency: LegacyAgencyDto) {
      nomisApi.stubGetAgency(agencyId, response = nomisAgency)
      dpsApi.stubGetAgency(agencyId, response = dpsAgency)
    }
  }
}
