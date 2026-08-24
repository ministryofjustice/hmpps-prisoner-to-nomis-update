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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

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

    @Nested
    inner class WhenLongDescriptionDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(longDescription = "Sheffield Crown Court (NOMIS)"),
          legacyAgencyDto().copy(description = "Sheffield Crown Court (DPS)"),
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

    @Nested
    inner class WhenActiveDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(active = false),
          legacyAgencyDto().copy(active = true),
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

    @Nested
    inner class WhenDeactivationDateDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(deactivationDate = LocalDate.of(2020, 1, 1)),
          legacyAgencyDto().copy(inactiveDate = LocalDate.of(2021, 6, 15)),
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

    @Nested
    inner class WhenCjitCodeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(cjitCode = "A00XX00"),
          legacyAgencyDto().copy(cjitCode = "B00YY00"),
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

    @Nested
    inner class WhenLocalAuthorityDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(localAuthorities = listOf(CodeDescription(code = "00CG", description = "Sheffield City Council"))),
          legacyAgencyDto().copy(localAuthorityCode = "00AA"),
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

    @Nested
    inner class WhenAreaCodeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(area = CodeDescription(code = "NW", description = "North West")),
          legacyAgencyDto().copy(areaCode = "SE"),
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

    @Nested
    inner class WhenSubareaCodeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(subArea = CodeDescription(code = "MANCH", description = "Manchester")),
          legacyAgencyDto().copy(subareaCode = "LEEDS"),
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

    @Nested
    inner class WhenRegionCodeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(nomsRegion = CodeDescription(code = "NWEST", description = "North West")),
          legacyAgencyDto().copy(regionCode = "SEAST"),
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

    @Nested
    inner class WhenGeographicalAreaCodeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(region = CodeDescription(code = "NYORKS", description = "North Yorkshire")),
          legacyAgencyDto().copy(geographicalAreaCode = "SYORKS"),
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

    @Nested
    inner class WhenPayrollRegionCodeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(payrollRegion = CodeDescription(code = "NE", description = "North East")),
          legacyAgencyDto().copy(payrollRegionCode = "SW"),
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

    @Nested
    inner class WhenCourtTypeCodeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(courtType = CodeDescription(code = "MC", description = "Magistrates Court")),
          legacyAgencyDto().copy(courtTypeCode = "CC"),
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

    @Nested
    inner class WhenDisabilityAccessCodeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(disabilityAccessCode = "Y"),
          legacyAgencyDto().copy(accessibleAccess = LegacyAgencyDto.AccessibleAccess.WHEELCHAIR_ACCESS),
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

    @Nested
    inner class WhenContactDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubAgency(
          agencyResponse().copy(contactName = "John Smith"),
          legacyAgencyDto().copy(contact = "Jane Doe"),
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
