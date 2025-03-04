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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationAddressDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationAddressPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationEmailDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationTypeDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationWebAddressDetails
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

    @Nested
    inner class WhenOneOrganisationHasMoreAddressesThanTheOther {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(
          corporateAndOrganisationId,
          corporateOrganisation(corporateAndOrganisationId).copy(
            name = "South Yorkshire Police",
          ).withAddress(corporateAddress(), corporateAddress()),
        )

        dpsApi.stubGetOrganisation(
          corporateAndOrganisationId,
          organisationDetails().copy(
            corporateAndOrganisationId,
            organisationName = "South Yorkshire Police",
            addresses = listOf(organisationAddressDetails()),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkOrganisationMatch(corporateAndOrganisationId)).isNotNull
      }

      @Test
      fun `telemetry will show DPS organisation has different address counts`() = runTest {
        service.checkOrganisationMatch(corporateAndOrganisationId)
        verify(telemetryClient).trackEvent(
          eq("organisations-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("organisationId", "1")
            assertThat(it["dpsOrganisation"]).contains("addressCount=1")
            assertThat(it["nomisOrganisation"]).contains("addressCount=2")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenOrganisationsHaveDifferentTypes {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(
          corporateAndOrganisationId,
          corporateOrganisation(corporateAndOrganisationId).copy(
            name = "South Yorkshire Police",
            types = listOf(corporateOrganisationType("DOCTOR"), corporateOrganisationType("TEA")),
          ),
        )

        dpsApi.stubGetOrganisation(
          corporateAndOrganisationId,
          organisationDetails().copy(
            corporateAndOrganisationId,
            organisationName = "South Yorkshire Police",
            organisationTypes = listOf(organisationTypeDetails("DOCTOR"), organisationTypeDetails("POLICE")),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkOrganisationMatch(corporateAndOrganisationId)).isNotNull
      }

      @Test
      fun `telemetry will show DPS organisation has different types`() = runTest {
        service.checkOrganisationMatch(corporateAndOrganisationId)
        verify(telemetryClient).trackEvent(
          eq("organisations-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("organisationId", "1")
            assertThat(it["nomisOrganisation"]).contains("types=[DOCTOR, TEA]")
            assertThat(it["dpsOrganisation"]).contains("types=[DOCTOR, POLICE]")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenOrganisationsHaveDifferentPhoneNumbers {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(
          corporateAndOrganisationId,
          corporateOrganisation(corporateAndOrganisationId).copy(
            name = "South Yorkshire Police",
          ).withPhone(corporatePhone().copy(number = "07973 555 5555"), corporatePhone().copy(number = "0114 555 5555")),
        )

        dpsApi.stubGetOrganisation(
          corporateAndOrganisationId,
          organisationDetails().copy(
            corporateAndOrganisationId,
            organisationName = "South Yorkshire Police",
            phoneNumbers = listOf(organisationPhoneDetails().copy(phoneNumber = "0114 555 5555"), organisationPhoneDetails().copy(phoneNumber = "07973 444 5555")),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkOrganisationMatch(corporateAndOrganisationId)).isNotNull
      }

      @Test
      fun `telemetry will show DPS organisation has different phone numbers`() = runTest {
        service.checkOrganisationMatch(corporateAndOrganisationId)
        verify(telemetryClient).trackEvent(
          eq("organisations-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("organisationId", "1")
            assertThat(it["nomisOrganisation"]).contains("phoneNumbers=[0114 555 5555, 07973 555 5555]")
            assertThat(it["dpsOrganisation"]).contains("phoneNumbers=[0114 555 5555, 07973 444 5555]")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAddressesHaveDifferentPhoneNumbers {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(
          corporateAndOrganisationId,
          corporateOrganisation(corporateAndOrganisationId).copy(
            name = "South Yorkshire Police",
          ).withAddress(
            corporateAddress().withPhone(
              corporatePhone().copy(number = "07973 555 5555"),
              corporatePhone().copy(number = "0114 555 5555"),
            ),
            corporateAddress().withPhone(corporatePhone().copy(number = "0115 555 5555")),
          ),
        )

        dpsApi.stubGetOrganisation(
          corporateAndOrganisationId,
          organisationDetails().copy(
            corporateAndOrganisationId,
            organisationName = "South Yorkshire Police",
            addresses = listOf(
              organisationAddressDetails().copy(
                phoneNumbers = listOf(
                  organisationAddressPhoneDetails().copy(phoneNumber = "0114 555 5555"),
                  organisationAddressPhoneDetails().copy(phoneNumber = "07973 444 5555"),
                ),
              ),
              organisationAddressDetails().copy(phoneNumbers = listOf(organisationAddressPhoneDetails().copy(phoneNumber = "0115 555 5555"))),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkOrganisationMatch(corporateAndOrganisationId)).isNotNull
      }

      @Test
      fun `telemetry will show DPS organisation has different address phone numbers`() = runTest {
        service.checkOrganisationMatch(corporateAndOrganisationId)
        verify(telemetryClient).trackEvent(
          eq("organisations-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("organisationId", "1")
            assertThat(it["nomisOrganisation"]).contains("addressPhoneNumbers=[[0114 555 5555, 07973 555 5555], [0115 555 5555]]")
            assertThat(it["dpsOrganisation"]).contains("addressPhoneNumbers=[[0114 555 5555, 07973 444 5555], [0115 555 5555]]")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenOrganisationsHaveDifferentEmailAddresses {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(
          corporateAndOrganisationId,
          corporateOrganisation(corporateAndOrganisationId).copy(
            name = "South Yorkshire Police",
          ).withInternetAddress(
            corporateEmail().copy(internetAddress = "test@test.com"),
            corporateEmail().copy(internetAddress = "test@gmail.com"),
          ),
        )

        dpsApi.stubGetOrganisation(
          corporateAndOrganisationId,
          organisationDetails().copy(
            corporateAndOrganisationId,
            organisationName = "South Yorkshire Police",
            emailAddresses = listOf(
              organisationEmailDetails().copy(emailAddress = "test@test.com"),
              organisationEmailDetails().copy(emailAddress = "test@yahoo.com"),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkOrganisationMatch(corporateAndOrganisationId)).isNotNull
      }

      @Test
      fun `telemetry will show DPS organisation has different email addresses`() = runTest {
        service.checkOrganisationMatch(corporateAndOrganisationId)
        verify(telemetryClient).trackEvent(
          eq("organisations-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("organisationId", "1")
            assertThat(it["nomisOrganisation"]).contains("emailAddresses=[test@gmail.com, test@test.com]")
            assertThat(it["dpsOrganisation"]).contains("emailAddresses=[test@test.com, test@yahoo.com]")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenOrganisationsHaveDifferentWebAddresses {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetCorporateOrganisation(
          corporateAndOrganisationId,
          corporateOrganisation(corporateAndOrganisationId).copy(
            name = "South Yorkshire Police",
          ).withInternetAddress(
            corporateWebAddress().copy(internetAddress = "www.test.com"),
            corporateWebAddress().copy(internetAddress = "www.gmail.com"),
          ),
        )

        dpsApi.stubGetOrganisation(
          corporateAndOrganisationId,
          organisationDetails().copy(
            corporateAndOrganisationId,
            organisationName = "South Yorkshire Police",
            webAddresses = listOf(
              organisationWebAddressDetails().copy(webAddress = "www.test.com"),
              organisationWebAddressDetails().copy(webAddress = "www.yahoo.com"),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkOrganisationMatch(corporateAndOrganisationId)).isNotNull
      }

      @Test
      fun `telemetry will show DPS organisation has different web addresses`() = runTest {
        service.checkOrganisationMatch(corporateAndOrganisationId)
        verify(telemetryClient).trackEvent(
          eq("organisations-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("organisationId", "1")
            assertThat(it["nomisOrganisation"]).contains("webAddresses=[www.gmail.com, www.test.com]")
            assertThat(it["dpsOrganisation"]).contains("webAddresses=[www.test.com, www.yahoo.com]")
          },
          isNull(),
        )
      }
    }
  }
}
