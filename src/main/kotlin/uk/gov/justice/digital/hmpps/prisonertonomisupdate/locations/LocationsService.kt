package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.DeactivateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCapacityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCertificationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime

@Service
class LocationsService(
  private val locationsApiService: LocationsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: LocationsMappingService,
  private val locationsRetryQueueService: LocationsRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createLocation(event: LocationDomainEvent) {
    val telemetryMap = mutableMapOf(
      "key" to event.additionalInformation.key,
      "dpsLocationId" to event.additionalInformation.id,
    )
    if (isDpsCreated(event.additionalInformation)) {
      synchronise {
        name = "location"
        telemetryClient = this@LocationsService.telemetryClient
        retryQueueService = locationsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingService.getMappingGivenDpsIdOrNull(event.additionalInformation.id)
        }
        transform {
          locationsApiService.getLocation(event.additionalInformation.id).run {
            val request = toCreateLocationRequest(this)

            telemetryMap["prisonId"] = prisonId

            LocationMappingDto(
              dpsLocationId = id.toString(),
              nomisLocationId = nomisApiService.createLocation(request).locationId,
              mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
            )
          }
        }
        saveMapping { mappingService.createMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("location-create-ignored", telemetryMap)
    }
  }

  suspend fun amendLocation(event: LocationDomainEvent) {
    doUpdateLocation(event, "amend") { nomisLocationId, location ->
      nomisApiService.updateLocation(nomisLocationId, toUpdateLocationRequest(location))
    }
  }

  suspend fun deactivateLocation(event: LocationDomainEvent) {
    doUpdateLocation(event, "deactivate") { nomisLocationId, location ->
      nomisApiService.deactivateLocation(nomisLocationId, toDeactivateRequest(location))
    }
  }

  suspend fun reactivateLocation(event: LocationDomainEvent) {
    doUpdateLocation(event, "reactivate") { nomisLocationId, _ ->
      nomisApiService.reactivateLocation(nomisLocationId)
    }
  }

  suspend fun changeCapacity(event: LocationDomainEvent) {
    doUpdateLocation(event, "capacity") { nomisLocationId, location ->
      nomisApiService.updateLocationCapacity(nomisLocationId, UpdateCapacityRequest(location.capacity?.capacity, location.capacity?.operationalCapacity))
    }
  }

  suspend fun changeCertification(event: LocationDomainEvent) {
    doUpdateLocation(event, "certification") { nomisLocationId, location ->
      nomisApiService.updateLocationCertification(
        nomisLocationId,
        UpdateCertificationRequest(location.certification?.capacityOfCertifiedCell ?: 0, location.certification?.certified ?: false),
      )
    }
  }

  suspend fun softDeleteLocation(event: LocationDomainEvent) {
    val telemetryMap = mutableMapOf("dpsLocationId" to event.additionalInformation.id)
    telemetryMap["key"] = event.additionalInformation.key
    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        mappingService.getMappingGivenDpsId(event.additionalInformation.id).apply {
          telemetryMap["nomisLocationId"] = nomisLocationId.toString()
          nomisApiService.deactivateLocation(nomisLocationId, DeactivateRequest())
          nomisApiService.updateLocationCapacity(nomisLocationId, UpdateCapacityRequest(0, 0))
          nomisApiService.updateLocationCertification(nomisLocationId, UpdateCertificationRequest(0, false))
          // TODO: Need to decide how to define a soft-deleted location
        }
      }.onSuccess {
        telemetryClient.trackEvent("location-delete-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("location-delete-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("location-delete-ignored", telemetryMap)
    }
  }

  private suspend fun doUpdateLocation(event: LocationDomainEvent, name: String, update: suspend (Long, Location) -> Unit) {
    val telemetryMap = mutableMapOf("dpsId" to event.additionalInformation.id)
    telemetryMap["key"] = event.additionalInformation.key
    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        val location = locationsApiService.getLocation(event.additionalInformation.id)

        mappingService.getMappingGivenDpsId(event.additionalInformation.id)
          .apply {
            telemetryMap["nomisId"] = nomisLocationId.toString()
            update(nomisLocationId, location)
          }
      }.onSuccess {
        telemetryClient.trackEvent("location-$name-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("location-$name-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("location-$name-ignored", telemetryMap)
    }
  }

  private suspend fun toCreateLocationRequest(instance: Location) = CreateLocationRequest(
    locationCode = instance.code,
    certified = instance.certification?.certified ?: false,
    cnaCapacity = instance.certification?.capacityOfCertifiedCell,
    locationType = CreateLocationRequest.LocationType.valueOf(toLocationType(instance.locationType)),
    comment = instance.comments,
    parentLocationId = instance.parentId?.let { mappingService.getMappingGivenDpsId(it.toString()).nomisLocationId },
    prisonId = instance.prisonId,
    description = "${instance.prisonId}-${instance.pathHierarchy}",
    operationalCapacity = instance.capacity?.operationalCapacity,
    userDescription = instance.description,
    capacity = instance.capacity?.capacity,
    listSequence = instance.orderWithinParentLocation,
    unitType = instance.residentialHousingType?.let { CreateLocationRequest.UnitType.valueOf(toUnitType(it)) },
    profiles = instance.attributes?.map { toAttribute(it) },
    usages = instance.usage?.map { toUsage(it) },
  )

  private fun toAttribute(attribute: Location.Attributes): ProfileRequest {
    val fullAttribute = ResidentialAttributeValue.valueOf(attribute.name)
    var code: String? = null
    val type: ProfileRequest.ProfileType = when (fullAttribute.type) {
      ResidentialAttributeType.SANITATION_FITTINGS -> {
        code = when (attribute) {
          Location.Attributes.ANTI_BARRICADE_DOOR -> "ABD"
          Location.Attributes.AUDITABLE_CELL_BELL -> "ACB"
          Location.Attributes.FIXED_BED -> "FIB"
          Location.Attributes.METAL_DOOR -> "MD"
          Location.Attributes.MOVABLE_BED -> "MOB"
          Location.Attributes.PRIVACY_CURTAIN -> "PC"
          Location.Attributes.PRIVACY_SCREEN -> "PS"
          Location.Attributes.STANDARD_CELL_BELL -> "SCB"
          Location.Attributes.SEPARATE_TOILET -> "SETO"
          Location.Attributes.WOODEN_DOOR -> "WD"
          else -> null
        }
        ProfileRequest.ProfileType.HOU_SANI_FIT
      }

      ResidentialAttributeType.LOCATION_ATTRIBUTE -> {
        code = when (attribute) {
          Location.Attributes.CAT_A_CELL -> "A"
          Location.Attributes.DOUBLE_OCCUPANCY -> "DO"
          Location.Attributes.E_LIST_CELL -> "ELC"
          Location.Attributes.GATED_CELL -> "GC"
          Location.Attributes.LISTENER_CELL -> "LC"
          Location.Attributes.LOCATE_FLAT -> "LF"
          Location.Attributes.MULTIPLE_OCCUPANCY -> "MO"
          Location.Attributes.NON_SMOKER_CELL -> "NSMC"
          Location.Attributes.OBSERVATION_CELL -> "OC"
          Location.Attributes.SAFE_CELL -> "SAFE_CELL"
          Location.Attributes.SINGLE_OCCUPANCY -> "SO"
          Location.Attributes.SPECIAL_CELL -> "SPC"
          Location.Attributes.WHEELCHAIR_ACCESS -> "WA"
          else -> null
        }
        ProfileRequest.ProfileType.HOU_UNIT_ATT
      }

      ResidentialAttributeType.USED_FOR -> {
        code = when (attribute) {
          Location.Attributes.UNCONVICTED_JUVENILES -> "1"
          Location.Attributes.SENTENCED_JUVENILES -> "2"
          Location.Attributes.UNCONVICTED_18_20 -> "3"
          Location.Attributes.SENTENCED_18_20 -> "4"
          Location.Attributes.UNCONVICTED_ADULTS -> "5"
          Location.Attributes.SENTENCED_ADULTS -> "6"
          Location.Attributes.VULNERABLE_PRISONER_UNIT -> "7"
          Location.Attributes.SPECIAL_UNIT -> "8"
          Location.Attributes.RESETTLEMENT_HOSTEL -> "9"
          Location.Attributes.HEALTHCARE_CENTRE -> "10"
          Location.Attributes.NATIONAL_RESOURCE_HOSPITAL -> "11"
          Location.Attributes.OTHER_SPECIFIED -> "12"
          Location.Attributes.REMAND_CENTRE -> "A"
          Location.Attributes.LOCAL_PRISON -> "B"
          Location.Attributes.CLOSED_PRISON -> "C"
          Location.Attributes.OPEN_TRAINING -> "D"
          Location.Attributes.HOSTEL -> "E"
          Location.Attributes.CLOSED_YOUNG_OFFENDER -> "I"
          Location.Attributes.OPEN_YOUNG_OFFENDER -> "J"
          Location.Attributes.REMAND_UNDER_18 -> "K"
          Location.Attributes.SENTENCED_UNDER_18 -> "L"
          Location.Attributes.ECL_COMPONENT -> "R"
          Location.Attributes.ADDITIONAL_SPECIAL_UNIT -> "T"
          Location.Attributes.SECOND_CLOSED_TRAINER -> "Y"
          Location.Attributes.IMMIGRATION_DETAINEES -> "Z"
          else -> null
        }
        ProfileRequest.ProfileType.HOU_USED_FOR
      }

      ResidentialAttributeType.SECURITY -> {
        code = when (attribute) {
          Location.Attributes.CAT_A -> "A"
          Location.Attributes.CAT_A_EX -> "E"
          Location.Attributes.CAT_A_HI -> "H"
          Location.Attributes.CAT_B -> "B"
          Location.Attributes.CAT_C -> "C"
          Location.Attributes.CAT_D -> "D"
          Location.Attributes.ELIGIBLE -> null
          Location.Attributes.PAROLE_GRANTED -> "GRANTED"
          Location.Attributes.INELIGIBLE -> null
          Location.Attributes.YOI_CLOSED -> "I"
          Location.Attributes.YOI_OPEN -> "J"
          Location.Attributes.YOI_RESTRICTED -> "V"
          Location.Attributes.YOI_SHORT_SENTENCE -> "K"
          Location.Attributes.YOI_LONG_TERM_CLOSED -> "L"
          Location.Attributes.UNCLASSIFIED -> "Z"
          Location.Attributes.UNCATEGORISED_SENTENCED_MALE -> "X"
          Location.Attributes.LOW -> "LOW"
          Location.Attributes.MEDIUM -> "MED"
          Location.Attributes.HIGH -> "HI"
          Location.Attributes.NOT_APPLICABLE -> "N/A"
          Location.Attributes.PROV_A -> "P"
          Location.Attributes.PENDING -> "PEND"
          Location.Attributes.REF_REVIEW -> "REF/REVIEW"
          Location.Attributes.REFUSED_NO_REVIEW -> "REFUSED"
          Location.Attributes.STANDARD -> "STANDARD"
          Location.Attributes.FEMALE_RESTRICTED -> "Q"
          Location.Attributes.FEMALE_CLOSED -> "R"
          Location.Attributes.FEMALE_SEMI -> "S"
          Location.Attributes.FEMALE_OPEN -> "T"
          Location.Attributes.UN_SENTENCED -> "U"
          Location.Attributes.YES -> "Y"
          Location.Attributes.NO -> "N"
          else -> null
        }
        ProfileRequest.ProfileType.SUP_LVL_TYPE
      }

      ResidentialAttributeType.NON_ASSOCIATIONS -> throw RuntimeException("non-association attribute type not supported")
    }

    return ProfileRequest(type, code ?: throw RuntimeException("Attribute $attribute not recognised"))
  }

  private fun toUsage(u: NonResidentialUsageDto) = UsageRequest(
    internalLocationUsageType = when (u.usageType) {
      NonResidentialUsageDto.UsageType.APPOINTMENT -> UsageRequest.InternalLocationUsageType.APP
      NonResidentialUsageDto.UsageType.VISIT -> UsageRequest.InternalLocationUsageType.VISIT
      NonResidentialUsageDto.UsageType.MOVEMENT -> UsageRequest.InternalLocationUsageType.MOVEMENT
      NonResidentialUsageDto.UsageType.OCCURRENCE -> UsageRequest.InternalLocationUsageType.OCCUR
      NonResidentialUsageDto.UsageType.ADJUDICATION_HEARING -> UsageRequest.InternalLocationUsageType.OIC
      NonResidentialUsageDto.UsageType.OTHER -> UsageRequest.InternalLocationUsageType.OTHER
      NonResidentialUsageDto.UsageType.PROGRAMMES_ACTIVITIES -> UsageRequest.InternalLocationUsageType.PROG
      NonResidentialUsageDto.UsageType.PROPERTY -> UsageRequest.InternalLocationUsageType.PROP
    },
    usageLocationType = null,
    sequence = u.sequence,
    capacity = u.capacity,
  )

  private suspend fun toUpdateLocationRequest(instance: Location) = UpdateLocationRequest(
    locationCode = instance.code,
    locationType = UpdateLocationRequest.LocationType.valueOf(toLocationType(instance.locationType)),
    description = instance.key,
    userDescription = instance.description,
    parentLocationId = instance.parentId?.let { mappingService.getMappingGivenDpsId(it.toString()).nomisLocationId },
    unitType = instance.residentialHousingType?.let { UpdateLocationRequest.UnitType.valueOf(toUnitType(it)) },
    listSequence = instance.orderWithinParentLocation,
    comment = instance.comments,
  )

  private fun toUnitType(residentialHousingType: Location.ResidentialHousingType) = when (residentialHousingType) {
    Location.ResidentialHousingType.HEALTHCARE -> "HC"
    Location.ResidentialHousingType.HOLDING_CELL -> "HOLC"
    Location.ResidentialHousingType.NORMAL_ACCOMMODATION -> "NA"
    Location.ResidentialHousingType.OTHER_USE -> "OU"
    Location.ResidentialHousingType.RECEPTION -> "REC"
    Location.ResidentialHousingType.SEGREGATION -> "SEG"
    Location.ResidentialHousingType.SPECIALIST_CELL -> "SPLC"
  }

  private fun toLocationType(locationtype: Location.LocationType) = when (locationtype) {
    Location.LocationType.WING -> "WING"
    Location.LocationType.SPUR -> "SPUR"
    Location.LocationType.TIER, // TODO not sure about this yet
    Location.LocationType.LANDING,
    -> "LAND"

    Location.LocationType.CELL -> "CELL"
    Location.LocationType.ADJUDICATION_ROOM -> "ADJU"
    Location.LocationType.ADMINISTRATION_AREA -> "ADMI"
    Location.LocationType.APPOINTMENTS -> "APP"
    Location.LocationType.AREA -> "AREA"
    Location.LocationType.ASSOCIATION -> "ASSO"
    Location.LocationType.BOOTH -> "BOOT"
    Location.LocationType.BOX -> "BOX"
    Location.LocationType.CLASSROOM -> "CLAS"
    Location.LocationType.EXERCISE_AREA -> "EXER"
    Location.LocationType.EXTERNAL_GROUNDS -> "EXTE"
    Location.LocationType.FAITH_AREA -> "FAIT"
    Location.LocationType.GROUP -> "GROU"
    Location.LocationType.HOLDING_CELL -> "HCEL"
    Location.LocationType.HOLDING_AREA -> "HOLD"
    Location.LocationType.INTERNAL_GROUNDS -> "IGRO"
    Location.LocationType.INSIDE_PARTY -> "INSI"
    Location.LocationType.INTERVIEW -> "INTE"
    Location.LocationType.LOCATION -> "LOCA"
    Location.LocationType.MEDICAL -> "MEDI"
    Location.LocationType.MOVEMENT_AREA -> "MOVE"
    Location.LocationType.OFFICE -> "OFFI"
    Location.LocationType.OUTSIDE_PARTY -> "OUTS"
    Location.LocationType.POSITION -> "POSI"
    Location.LocationType.RESIDENTIAL_UNIT -> "RESI"
    Location.LocationType.ROOM -> "ROOM"
    Location.LocationType.RETURN_TO_UNIT -> "RTU"
    Location.LocationType.SHELF -> "SHEL"
    Location.LocationType.SPORTS -> "SPOR"
    Location.LocationType.STORE -> "STOR"
    Location.LocationType.TABLE -> "TABL"
    Location.LocationType.TRAINING_AREA -> "TRAI"
    Location.LocationType.TRAINING_ROOM -> "TRRM"
    Location.LocationType.VIDEO_LINK -> "VIDE"
    Location.LocationType.VISITS -> "VISIT"
    Location.LocationType.WORKSHOP -> "WORK"
  }

  private fun toDeactivateRequest(location: Location): DeactivateRequest = DeactivateRequest(
    deactivateDate = location.deactivatedDate,
    reasonCode = when (location.deactivatedReason) {
      Location.DeactivatedReason.NEW_BUILDING -> DeactivateRequest.ReasonCode.A
      Location.DeactivatedReason.CELL_RECLAIMS -> DeactivateRequest.ReasonCode.B
      Location.DeactivatedReason.CHANGE_OF_USE -> DeactivateRequest.ReasonCode.C
      Location.DeactivatedReason.REFURBISHMENT -> DeactivateRequest.ReasonCode.D
      Location.DeactivatedReason.CLOSURE -> DeactivateRequest.ReasonCode.E
      Location.DeactivatedReason.OTHER -> DeactivateRequest.ReasonCode.F
      Location.DeactivatedReason.LOCAL_WORK -> DeactivateRequest.ReasonCode.G
      Location.DeactivatedReason.STAFF_SHORTAGE -> DeactivateRequest.ReasonCode.H
      Location.DeactivatedReason.MOTHBALLED -> DeactivateRequest.ReasonCode.I
      Location.DeactivatedReason.DAMAGED -> DeactivateRequest.ReasonCode.J
      Location.DeactivatedReason.OUT_OF_USE -> DeactivateRequest.ReasonCode.K
      Location.DeactivatedReason.CELLS_RETURNING_TO_USE -> DeactivateRequest.ReasonCode.L
      else -> null
    },
    reactivateDate = location.reactivatedDate,
  )

  private fun isDpsCreated(additionalInformation: LocationAdditionalInformation) =
    additionalInformation.source != CreatingSystem.NOMIS.name

  suspend fun createRetry(message: CreateMappingRetryMessage<LocationMappingDto>) {
    mappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "location-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class LocationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
  val additionalInformation: LocationAdditionalInformation,
)

data class LocationAdditionalInformation(
  val id: String,
  val key: String,
  val source: String? = null,
)
