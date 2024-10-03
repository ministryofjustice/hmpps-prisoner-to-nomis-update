package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PhysicalAttributesSyncDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.profiledetails.ProfileDetailsNomisApiService

@Service
class PhysicalAttributesSyncService(
  private val dpsApi: PhysicalAttributesDpsApiService,
  private val physicalAttributesNomisApi: PhysicalAttributesNomisApiService,
  private val profileDetailsNomisApi: ProfileDetailsNomisApiService,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun updatePhysicalAttributesEvent(event: PhysicalAttributesDomainEvent) {
    if (event.additionalInformation.source == "DPS") {
      val fields = event.additionalInformation.fields
      updatePhysicalAttributes(event.additionalInformation.prisonerNumber, fields)
    } else {
      telemetryClient.trackEvent(
        "physical-attributes-update-ignored",
        mutableMapOf("offenderNo" to event.additionalInformation.prisonerNumber),
      )
    }
  }

  suspend fun updatePhysicalAttributes(offenderNo: String, fields: List<String>? = null) =
    runCatching {
      dpsApi.getPhysicalAttributes(offenderNo)
        ?.also { updateNomis(offenderNo, it, fields) }
        ?: throw ProfileDetailsSyncException("No physical attributes found for offenderNo: $offenderNo")
    }.onFailure { e ->
      publishTelemetry(
        type = "physical-attributes-update-error",
        offenderNo = offenderNo,
        fields = fields,
        reason = (e.message ?: "Unknown error"),
      )
      throw e
    }

  suspend fun readmissionSwitchBookingEvent(event: PrisonerReceivedEvent) {
    // We should be able to filter for this reason in the Topic subscription filter, but this is just in case
    if (event.additionalInformation.reason != "READMISSION_SWITCH_BOOKING") return

    // We only synchronise a readmission back to NOMIS if the DPS to NOMIS sync is turned on
    if (!eventFeatureSwitch.isEnabled("prison-person.physical-attributes.updated")) {
      log.info("Physical attributes sync is disabled, ignoring readmission event for offenderNo: ${event.additionalInformation.nomsNumber}")
      return
    }

    // For now we only sync back the height and weight, but will add profile details when they are proven to be required too
    updatePhysicalAttributes(event.additionalInformation.nomsNumber, listOf("HEIGHT", "WEIGHT"))
  }

  private suspend fun updateNomis(offenderNo: String, dpsAttributes: PhysicalAttributesSyncDto, fields: List<String>?) {
    if (fields == null || "HEIGHT" in fields || "WEIGHT" in fields) {
      physicalAttributesNomisApi.upsertPhysicalAttributes(offenderNo, dpsAttributes.height, dpsAttributes.weight)
        .also {
          publishTelemetry(
            type = "physical-attributes-update-success",
            offenderNo = offenderNo,
            fields = fields?.filter { it == "HEIGHT" || it == "WEIGHT" },
            bookingId = it.bookingId,
            created = it.created,
          )
        }
    }
    fields?.filterNot { it == "HEIGHT" || it == "WEIGHT" }?.forEach { field ->
      profileDetailsNomisApi.upsertProfileDetails(offenderNo, field.toNomisProfileType(), dpsAttributes.valueOf(field))
        .also {
          publishTelemetry(
            type = "physical-attributes-profile-details-update-success",
            offenderNo = offenderNo,
            fields = listOf(field),
            bookingId = it.bookingId,
            created = it.created,
          )
        }
    }
  }

  private fun publishTelemetry(
    type: String,
    offenderNo: String,
    fields: List<String>? = null,
    bookingId: Long? = null,
    created: Boolean? = null,
    reason: String? = null,
  ) {
    telemetryClient.trackEvent(
      type,
      mutableMapOf(
        "offenderNo" to offenderNo,
        "fields" to fields.toString(),
      ).apply {
        bookingId?.run { put("bookingId", this.toString()) }
        created?.run { put("created", this.toString()) }
        reason?.run { put("reason", this) }
      }.toMap(),
    )
  }

  private fun String.toNomisProfileType() =
    when (this) {
      "LEFT_EYE_COLOUR" -> "L_EYE_C"
      "RIGHT_EYE_COLOUR" -> "R_EYE_C"
      "SHOE_SIZE" -> "SHOESIZE"
      else -> this
    }

  private fun PhysicalAttributesSyncDto.valueOf(field: String) =
    when (field) {
      "BUILD" -> build
      "FACE" -> face
      "FACIAL_HAIR" -> facialHair
      "HAIR" -> hair
      "LEFT_EYE_COLOUR" -> leftEyeColour
      "RIGHT_EYE_COLOUR" -> rightEyeColour
      "SHOE_SIZE" -> shoeSize
      else -> throw ProfileDetailsSyncException("Unknown field: $field")
    }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class PhysicalAttributesDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val additionalInformation: PhysicalAttributesAdditionalInformation,
)

data class PhysicalAttributesAdditionalInformation(
  val source: String,
  val prisonerNumber: String,
  val fields: List<String>?,
)

data class PrisonerReceivedEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val additionalInformation: PrisonerReceivedEventAdditionalInformation,
)

data class PrisonerReceivedEventAdditionalInformation(
  val reason: String,
  val nomsNumber: String,
)

class ProfileDetailsSyncException(message: String) : RuntimeException(message)
