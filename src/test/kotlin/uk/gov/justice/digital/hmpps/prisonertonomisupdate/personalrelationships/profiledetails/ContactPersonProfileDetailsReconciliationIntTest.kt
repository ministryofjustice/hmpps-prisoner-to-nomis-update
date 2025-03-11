package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.booking
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.profileDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.profileDetailsResponse

class ContactPersonProfileDetailsReconciliationIntTest(
  @Autowired val nomisApi: ProfileDetailsNomisApiMockServer,
  @Autowired val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired val service: ContactPersonReconciliationService,
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
        eq("contact-person-profile-details-reconciliation-prisoner-failed"),
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
