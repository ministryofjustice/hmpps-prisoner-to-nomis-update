package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PhysicalAttributesSyncDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.profiledetails.ProfileDetailsNomisApiService

@Service
class PhysicalAttributesSyncService(
  private val dpsApi: PhysicalAttributesDpsApiService,
  private val physicalAttributesNomisApi: PhysicalAttributesNomisApiService,
  private val profileDetailsNomisApi: ProfileDetailsNomisApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun updatePhysicalAttributesEvent(event: PhysicalAttributesDomainEvent) {
    if (event.additionalInformation.source == "DPS") {
      val fields = event.additionalInformation.fields?.parseFields()
      updatePhysicalAttributes(event.additionalInformation.prisonerNumber, fields)
    } else {
      telemetryClient.trackEvent(
        "physical-attributes-update-ignored",
        mutableMapOf("offenderNo" to event.additionalInformation.prisonerNumber),
      )
    }
  }

  private fun String?.parseFields(): List<String>? =
    this?.removeSurrounding("[", "]")
      ?.split(",")
      ?.toList()
      ?.map { it.trim() }

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
            type = "profile-details-update-success",
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
  val fields: String?,
)

class ProfileDetailsSyncException(message: String) : RuntimeException(message)
