package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactDomesticStatusCreatedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactNumberOfChildrenCreatedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.CHILD
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.MARITAL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiService

enum class ContactPersonProfileType(val identifier: String) {
  MARITAL("domestic-status"),
  CHILD("number-of-children"),
}

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
      syncProfileDetail(prisonerNumber, domesticStatusId, MARITAL)
    } else {
      ignoreTelemetry(prisonerNumber, domesticStatusId, MARITAL)
    }
  }

  suspend fun syncNumberOfChildren(event: ContactNumberOfChildrenCreatedEvent) {
    val prisonerNumber = event.personReference.identifiers.first { it.type == "prisonerNumber" }.value
    val numberOfChildrenId = event.additionalInformation.numberOfChildrenId
    if (event.additionalInformation.source == "DPS") {
      syncProfileDetail(prisonerNumber, numberOfChildrenId, CHILD)
    } else {
      ignoreTelemetry(prisonerNumber, numberOfChildrenId, CHILD)
    }
  }

  private fun ignoreTelemetry(prisonerNumber: String, dpsId: Long, profileType: ContactPersonProfileType) = telemetryClient.trackEvent(
    "contact-person-${profileType.identifier}-ignored",
    mapOf(
      "offenderNo" to prisonerNumber,
      "dpsId" to dpsId.toString(),
      "reason" to "Entity was created in NOMIS",
    ),
    null,
  )

  private suspend fun syncProfileDetail(prisonerNumber: String, dpsId: Long, profileType: ContactPersonProfileType) {
    val telemetry = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsId" to dpsId.toString(),
    )
    runCatching {
      val (bookingId, created) = performSync(profileType, prisonerNumber)
      telemetry["bookingId"] = bookingId.toString()
      telemetryClient.trackEvent(
        """contact-person-${profileType.identifier}-${if (created) "created" else "updated"}""",
        telemetry,
        null,
      )
    }.onFailure { e ->
      telemetry["error"] = e.message ?: "unknown"
      telemetryClient.trackEvent("contact-person-${profileType.identifier}-error", telemetry, null)
      throw e
    }
  }

  private suspend fun performSync(profileType: ContactPersonProfileType, prisonerNumber: String) = when (profileType) {
    MARITAL -> {
      dpsApi.getDomesticStatus(prisonerNumber)
        .let { nomisApi.upsertProfileDetails(prisonerNumber, "MARITAL", it.domesticStatusCode) }
        .let { it.bookingId to it.created }
    }
    CHILD -> {
      dpsApi.getNumberOfChildren(prisonerNumber)
        .let { nomisApi.upsertProfileDetails(prisonerNumber, "CHILD", it.numberOfChildren) }
        .let { it.bookingId to it.created }
    }
  }
}
