package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactDomesticStatusCreatedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactDomesticStatusDeletedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactIdReferencedEvent
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
    val numberOfChildrenId = event.additionalInformation.numberOfChildrenId
    if (event.additionalInformation.source == "DPS") {
      syncProfileDetail(prisonerNumber, numberOfChildrenId, CHILD)
    } else {
      ignoreTelemetry(prisonerNumber, numberOfChildrenId, CHILD)
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

  private fun ContactIdReferencedEvent.prisonerNumber() = personReference.identifiers.first { it.type == "prisonerNumber" }.value

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

  private suspend fun performSync(profileType: ContactPersonProfileType, prisonerNumber: String, delete: Boolean) = when (profileType) {
    MARITAL if (delete) -> {
      nomisApi.upsertProfileDetails(prisonerNumber, "MARITAL", profileCode = null)
        .let { SyncResult(it.bookingId) }
    }

    MARITAL -> {
      dpsApi.getDomesticStatus(prisonerNumber)
        .let { dpsResponse ->
          nomisApi.upsertProfileDetails(prisonerNumber, "MARITAL", dpsResponse.domesticStatusCode)
            .let { SyncResult(it.bookingId, it.created, dpsResponse.id) }
        }
    }

    CHILD if (delete) -> TODO("Delete number of children event not supported yet")

    CHILD -> {
      dpsApi.getNumberOfChildren(prisonerNumber)
        .let { dpsResponse ->
          nomisApi.upsertProfileDetails(prisonerNumber, "CHILD", dpsResponse.numberOfChildren)
            .let { SyncResult(it.bookingId, it.created, dpsResponse.id) }
        }
    }
  }

  private data class SyncResult(val bookingId: Long, val created: Boolean = true, val id: Long? = null)
}
