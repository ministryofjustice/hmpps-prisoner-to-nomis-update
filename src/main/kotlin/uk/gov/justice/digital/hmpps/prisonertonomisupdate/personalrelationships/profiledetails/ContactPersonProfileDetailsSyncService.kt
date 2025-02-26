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
    val prisonerNumber = event.personReference.identifiers.first { it.type == "prisonerNumber" }.value
    val domesticStatusId = event.additionalInformation.domesticStatusId
    if (event.additionalInformation.source == "DPS") {
      syncDomesticStatus(prisonerNumber, domesticStatusId)
    } else {
      telemetryClient.trackEvent(
        "contact-person-domestic-status-ignored",
        mapOf(
          "offenderNo" to prisonerNumber,
          "domesticStatusId" to domesticStatusId.toString(),
          "reason" to "Domestic status was created in NOMIS",
        ),
        null,
      )
    }
  }

  suspend fun syncDomesticStatus(prisonerNumber: String, domesticStatusId: Long) {
    val telemetry = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "domesticStatusId" to domesticStatusId.toString(),
    )
    runCatching {
      dpsApi.getDomesticStatus(prisonerNumber)
        .also { telemetry["dpsId"] = it.id.toString() }
        .let { nomisApi.upsertProfileDetails(prisonerNumber, "MARITAL", it.domesticStatusCode) }
        .also { telemetry["bookingId"] = it.bookingId.toString() }
        .also {
          telemetryClient.trackEvent(
            """contact-person-domestic-status-${if (it.created) "created" else "updated"}""",
            telemetry,
            null,
          )
        }
    }.onFailure { e ->
      telemetry["error"] = e.message ?: "unknown"
      telemetryClient.trackEvent("contact-person-domestic-status-error", telemetry, null)
      throw e
    }
  }
}
