package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactDomesticStatusCreatedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiService

@Service
class ContactPersonProfileDetailsSyncService(
  private val dpsApi: ContactPersonProfileDetailsDpsApiService,
  private val nomisApi: ProfileDetailsNomisApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun syncDomesticStatus(event: ContactDomesticStatusCreatedEvent) {
    syncDomesticStatus(
      prisonerNumber = event.personReference.identifiers.first { it.type == "prisonerNumber" }.value,
      domesticStatusId = event.additionalInformation.domesticStatusId,
    )
  }

  suspend fun syncDomesticStatus(prisonerNumber: String, domesticStatusId: Long) {
    dpsApi.getDomesticStatus(prisonerNumber)
      .let { nomisApi.upsertProfileDetails(prisonerNumber, "MARITAL", it.domesticStatusCode) }
      .also {
        telemetryClient.trackEvent(
          """contact-person-domestic-status-${if (it.created) "created" else "updated"}""",
          mapOf(
            "offenderNo" to prisonerNumber,
            "domesticStatusId" to domesticStatusId.toString(),
            "bookingId" to it.bookingId.toString(),
          ),
          null,
        )
      }
  }
}
