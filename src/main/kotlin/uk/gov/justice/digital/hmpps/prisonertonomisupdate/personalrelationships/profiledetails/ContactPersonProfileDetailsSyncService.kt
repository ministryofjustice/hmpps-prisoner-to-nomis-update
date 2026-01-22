package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactDomesticStatusCreatedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactDomesticStatusDeletedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactIdReferencedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactNumberOfChildrenCreatedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactNumberOfChildrenDeletedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ReadmissionSwitchBookingEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.CHILD
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.MARITAL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiService

enum class ContactPersonProfileType(val identifier: String) {
  MARITAL("domestic-status"),
  CHILD("number-of-children"),
  ;

  companion object {
    fun all() = entries.map { it.name }
  }
}

@Service
class ContactPersonProfileDetailsSyncService(
  private val dpsApi: ContactPersonProfileDetailsDpsApiService,
  private val nomisApi: ProfileDetailsNomisApiService,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun syncDomesticStatus(event: ContactDomesticStatusCreatedEvent) {
    val prisonerNumber = event.prisonerNumber()
    val domesticStatusId = event.additionalInformation.domesticStatusId
    if (event.additionalInformation.source == "DPS") {
      syncProfileDetail(prisonerNumber, domesticStatusId, MARITAL)
    } else {
      ignoreTelemetry(prisonerNumber, domesticStatusId, MARITAL)
    }
  }

  suspend fun syncNumberOfChildren(event: ContactNumberOfChildrenCreatedEvent) {
    val prisonerNumber = event.prisonerNumber()
    val prisonerNumberOfChildrenId = event.additionalInformation.prisonerNumberOfChildrenId
    if (event.additionalInformation.source == "DPS") {
      syncProfileDetail(prisonerNumber, prisonerNumberOfChildrenId, CHILD)
    } else {
      ignoreTelemetry(prisonerNumber, prisonerNumberOfChildrenId, CHILD)
    }
  }

  suspend fun deleteDomesticStatus(event: ContactDomesticStatusDeletedEvent) {
    val prisonerNumber = event.prisonerNumber()
    val domesticStatusId = event.additionalInformation.domesticStatusId
    if (event.additionalInformation.source == "DPS") {
      syncProfileDetail(prisonerNumber, domesticStatusId, MARITAL, deleted = true)
    } else {
      ignoreTelemetry(prisonerNumber, domesticStatusId, MARITAL)
    }
  }

  suspend fun deleteNumberOfChildren(event: ContactNumberOfChildrenDeletedEvent) {
    val prisonerNumber = event.prisonerNumber()
    val prisonerNumberOfChildrenId = event.additionalInformation.prisonerNumberOfChildrenId
    if (event.additionalInformation.source == "DPS") {
      syncProfileDetail(prisonerNumber, prisonerNumberOfChildrenId, CHILD, deleted = true)
    } else {
      ignoreTelemetry(prisonerNumber, prisonerNumberOfChildrenId, CHILD)
    }
  }

  private fun ContactIdReferencedEvent.prisonerNumber() = personReference.identifiers.first { it.type == "NOMS" }.value

  private fun ignoreTelemetry(prisonerNumber: String, dpsId: Long, profileType: ContactPersonProfileType) = telemetryClient.trackEvent(
    "contact-person-${profileType.identifier}-ignored",
    mapOf(
      "offenderNo" to prisonerNumber,
      "requestedDpsId" to dpsId.toString(),
      "reason" to "Entity was created in NOMIS",
    ),
    null,
  )

  suspend fun syncProfileDetail(
    prisonerNumber: String,
    requestedDpsId: Long,
    profileType: ContactPersonProfileType,
    deleted: Boolean = false,
  ) {
    val telemetry = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "requestedDpsId" to requestedDpsId.toString(),
    )
    runCatching {
      val (bookingId, created, dpsId: Long?) = performSync(profileType, prisonerNumber, deleted)
      val action = when {
        deleted -> "deleted"
        created -> "created"
        else -> "updated"
      }
      telemetry["bookingId"] = bookingId.toString()
      dpsId?.run { telemetry["dpsId"] = dpsId.toString() }
      telemetryClient.trackEvent(
        """contact-person-${profileType.identifier}-$action""",
        telemetry,
        null,
      )
    }.onFailure { e ->
      telemetry["error"] = e.message ?: "unknown"
      telemetryClient.trackEvent("contact-person-${profileType.identifier}-error", telemetry, null)
      throw e
    }
  }

  suspend fun readmissionSwitchBooking(event: ReadmissionSwitchBookingEvent) {
    // We're filtering for this reason in the Topic subscription filter, but this is just in case we listen for other prisoner received events later
    if (event.additionalInformation.reason != "READMISSION_SWITCH_BOOKING") return

    val prisonerNumber = event.additionalInformation.nomsNumber

    if (eventFeatureSwitch.isEnabled("personal-relationships-api.domestic-status.created", "personcontacts")) {
      syncProfileDetail(prisonerNumber, 0, MARITAL)
    }

    if (eventFeatureSwitch.isEnabled("personal-relationships-api.number-of-children.created", "personcontacts")) {
      syncProfileDetail(prisonerNumber, 0, CHILD)
    }
  }

  private suspend fun performSync(profileType: ContactPersonProfileType, prisonerNumber: String, delete: Boolean) = when (profileType) {
    MARITAL if (delete) -> {
      nomisApi.upsertProfileDetails(prisonerNumber, "MARITAL", profileCode = null)
        .let { SyncResult(it.bookingId) }
    }

    MARITAL -> {
      dpsApi.getDomesticStatus(prisonerNumber)
        .let { dpsResponse ->
          nomisApi.upsertProfileDetails(prisonerNumber, "MARITAL", dpsResponse?.domesticStatusCode)
            .let { SyncResult(it.bookingId, it.created, dpsResponse?.id) }
        }
    }

    CHILD if (delete) -> {
      nomisApi.upsertProfileDetails(prisonerNumber, "CHILD", profileCode = null)
        .let { SyncResult(it.bookingId) }
    }

    CHILD -> {
      dpsApi.getNumberOfChildren(prisonerNumber)
        .let { dpsResponse ->
          nomisApi.upsertProfileDetails(prisonerNumber, "CHILD", dpsResponse?.numberOfChildren)
            .let { SyncResult(it.bookingId, it.created, dpsResponse?.id) }
        }
    }
  }

  private data class SyncResult(val bookingId: Long, val created: Boolean = true, val id: Long? = null)
}
