package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.DeactivateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCapacityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCertificationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

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
      nomisApiService.updateLocationCapacity(
        nomisLocationId,
        UpdateCapacityRequest(
          location.capacity?.maxCapacity,
          location.capacity?.workingCapacity,
        ),
        location.ignoreWorkingCapacity,
      )
      nomisApiService.updateLocationCertification(
        nomisLocationId,
        UpdateCertificationRequest(location.capacity?.certifiedNormalAccommodation ?: 0, location.certifiedCell ?: false),
      )
    }
  }

  suspend fun repair(dpsLocationId: String, cascade: Boolean) {
//    val dpsData = locationsApiService.getLocation(dpsLocationId)
//      ?: throw NotFoundException("Location $dpsLocationId not found")
    mappingService.getMappingGivenDpsIdOrNull(dpsLocationId)
      ?.let {
        amendLocation(
          LocationDomainEvent(
            eventType = "amend",
            version = "1",
            description = "repair",
            additionalInformation = LocationAdditionalInformation(id = dpsLocationId, key = "", source = "DPS"),
          ),
        )
        telemetryClient.trackEvent("location-repaired", mapOf("dpsLocationId" to dpsLocationId, "operation" to "amend"))

      }
      ?: run {
        createLocation(
          LocationDomainEvent(
            eventType = "create",
            version = "1",
            description = "repair",
            additionalInformation = LocationAdditionalInformation(id = dpsLocationId, key = "", source = "DPS"),
          ),
        )
        telemetryClient.trackEvent("location-repaired", mapOf("dpsLocationId" to dpsLocationId, "operation" to "create"))
      }

    if (cascade) {
      val dpsLocation = locationsApiService.getLocationDPS(dpsLocationId)
      dpsLocation.childLocations?.forEach {
        repair(it.id.toString(), true)
      }
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

  suspend fun softDeleteLocation(event: LocationDomainEvent) {
    val telemetryMap = mutableMapOf("dpsLocationId" to event.additionalInformation.id)
    telemetryMap["key"] = event.additionalInformation.key
    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        mappingService.getMappingGivenDpsId(event.additionalInformation.id).apply {
          telemetryMap["nomisLocationId"] = nomisLocationId.toString()
          nomisApiService.deactivateLocation(nomisLocationId, DeactivateRequest(force = false))
          nomisApiService.updateLocationCapacity(nomisLocationId, UpdateCapacityRequest(0, 0), false)
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

  private suspend fun doUpdateLocation(event: LocationDomainEvent, name: String, update: suspend (Long, LegacyLocation) -> Unit) {
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

  private suspend fun toCreateLocationRequest(instance: LegacyLocation) = CreateLocationRequest(
    locationCode = instance.code,
    certified = instance.certifiedCell ?: false,
    cnaCapacity = instance.capacity?.certifiedNormalAccommodation,
    locationType = CreateLocationRequest.LocationType.valueOf(toLocationType(instance.locationType)),
    comment = instance.comments,
    parentLocationId = instance.parentId?.let { mappingService.getMappingGivenDpsId(it.toString()).nomisLocationId },
    prisonId = instance.prisonId,
    description = instance.key,
    operationalCapacity = instance.capacity?.workingCapacity,
    userDescription = instance.localName,
    capacity = instance.capacity?.maxCapacity,
    listSequence = instance.orderWithinParentLocation,
    unitType = instance.residentialHousingType?.let { CreateLocationRequest.UnitType.valueOf(toUnitType(it)) },
    tracking = instance.internalMovementAllowed,
    active = instance.active,
    profiles = instance.attributes?.map { toAttribute(it) },
    usages = instance.usage?.map { toUsage(it) },
  )

  private fun toAttribute(attribute: LegacyLocation.Attributes): ProfileRequest {
    val fullAttribute = ResidentialAttributeValue.valueOf(attribute.name)
    var code: String?
    val type: ProfileRequest.ProfileType = when (fullAttribute.type) {
      ResidentialAttributeType.SANITATION_FITTINGS -> {
        code = when (attribute) {
          LegacyLocation.Attributes.ANTI_BARRICADE_DOOR -> "ABD"
          LegacyLocation.Attributes.AUDITABLE_CELL_BELL -> "ACB"
          LegacyLocation.Attributes.FIXED_BED -> "FIB"
          LegacyLocation.Attributes.METAL_DOOR -> "MD"
          LegacyLocation.Attributes.MOVABLE_BED -> "MOB"
          LegacyLocation.Attributes.PRIVACY_CURTAIN -> "PC"
          LegacyLocation.Attributes.PRIVACY_SCREEN -> "PS"
          LegacyLocation.Attributes.STANDARD_CELL_BELL -> "SCB"
          LegacyLocation.Attributes.SEPARATE_TOILET -> "SETO"
          LegacyLocation.Attributes.WOODEN_DOOR -> "WD"
          else -> null
        }
        ProfileRequest.ProfileType.HOU_SANI_FIT
      }

      ResidentialAttributeType.LOCATION_ATTRIBUTE -> {
        code = when (attribute) {
          LegacyLocation.Attributes.CAT_A_CELL -> "A"
          LegacyLocation.Attributes.DOUBLE_OCCUPANCY -> "DO"
          LegacyLocation.Attributes.E_LIST_CELL -> "ELC"
          LegacyLocation.Attributes.GATED_CELL -> "GC"
          LegacyLocation.Attributes.LISTENER_CELL -> "LC"
          LegacyLocation.Attributes.LOCATE_FLAT -> "LF"
          LegacyLocation.Attributes.MULTIPLE_OCCUPANCY -> "MO"
          LegacyLocation.Attributes.NON_SMOKER_CELL -> "NSMC"
          LegacyLocation.Attributes.OBSERVATION_CELL -> "OC"
          LegacyLocation.Attributes.SAFE_CELL -> "SAFE_CELL"
          LegacyLocation.Attributes.SINGLE_OCCUPANCY -> "SO"
          LegacyLocation.Attributes.SPECIAL_CELL -> "SPC"
          LegacyLocation.Attributes.WHEELCHAIR_ACCESS -> "WA"
          else -> null
        }
        ProfileRequest.ProfileType.HOU_UNIT_ATT
      }

      ResidentialAttributeType.USED_FOR -> {
        code = when (attribute) {
          LegacyLocation.Attributes.UNCONVICTED_JUVENILES -> "1"
          LegacyLocation.Attributes.SENTENCED_JUVENILES -> "2"
          LegacyLocation.Attributes.UNCONVICTED_18_20 -> "3"
          LegacyLocation.Attributes.SENTENCED_18_20 -> "4"
          LegacyLocation.Attributes.UNCONVICTED_ADULTS -> "5"
          LegacyLocation.Attributes.SENTENCED_ADULTS -> "6"
          LegacyLocation.Attributes.VULNERABLE_PRISONER_UNIT -> "7"
          LegacyLocation.Attributes.SPECIAL_UNIT -> "8"
          LegacyLocation.Attributes.RESETTLEMENT_HOSTEL -> "9"
          LegacyLocation.Attributes.HEALTHCARE_CENTRE -> "10"
          LegacyLocation.Attributes.NATIONAL_RESOURCE_HOSPITAL -> "11"
          LegacyLocation.Attributes.OTHER_SPECIFIED -> "12"
          LegacyLocation.Attributes.REMAND_CENTRE -> "A"
          LegacyLocation.Attributes.LOCAL_PRISON -> "B"
          LegacyLocation.Attributes.CLOSED_PRISON -> "C"
          LegacyLocation.Attributes.OPEN_TRAINING -> "D"
          LegacyLocation.Attributes.HOSTEL -> "E"
          LegacyLocation.Attributes.CLOSED_YOUNG_OFFENDER -> "I"
          LegacyLocation.Attributes.OPEN_YOUNG_OFFENDER -> "J"
          LegacyLocation.Attributes.REMAND_UNDER_18 -> "K"
          LegacyLocation.Attributes.SENTENCED_UNDER_18 -> "L"
          LegacyLocation.Attributes.ECL_COMPONENT -> "R"
          LegacyLocation.Attributes.ADDITIONAL_SPECIAL_UNIT -> "T"
          LegacyLocation.Attributes.SECOND_CLOSED_TRAINER -> "Y"
          LegacyLocation.Attributes.IMMIGRATION_DETAINEES -> "Z"
          else -> null
        }
        ProfileRequest.ProfileType.HOU_USED_FOR
      }

      ResidentialAttributeType.SECURITY -> {
        code = when (attribute) {
          LegacyLocation.Attributes.CAT_A -> "A"
          LegacyLocation.Attributes.CAT_A_EX -> "E"
          LegacyLocation.Attributes.CAT_A_HI -> "H"
          LegacyLocation.Attributes.CAT_B -> "B"
          LegacyLocation.Attributes.CAT_C -> "C"
          LegacyLocation.Attributes.CAT_D -> "D"
          LegacyLocation.Attributes.ELIGIBLE -> null
          LegacyLocation.Attributes.PAROLE_GRANTED -> "GRANTED"
          LegacyLocation.Attributes.INELIGIBLE -> null
          LegacyLocation.Attributes.YOI_CLOSED -> "I"
          LegacyLocation.Attributes.YOI_OPEN -> "J"
          LegacyLocation.Attributes.YOI_RESTRICTED -> "V"
          LegacyLocation.Attributes.YOI_SHORT_SENTENCE -> "K"
          LegacyLocation.Attributes.YOI_LONG_TERM_CLOSED -> "L"
          LegacyLocation.Attributes.UNCLASSIFIED -> "Z"
          LegacyLocation.Attributes.UNCATEGORISED_SENTENCED_MALE -> "X"
          LegacyLocation.Attributes.LOW -> "LOW"
          LegacyLocation.Attributes.MEDIUM -> "MED"
          LegacyLocation.Attributes.HIGH -> "HI"
          LegacyLocation.Attributes.NOT_APPLICABLE -> "N/A"
          LegacyLocation.Attributes.PROV_A -> "P"
          LegacyLocation.Attributes.PENDING -> "PEND"
          LegacyLocation.Attributes.REF_REVIEW -> "REF/REVIEW"
          LegacyLocation.Attributes.REFUSED_NO_REVIEW -> "REFUSED"
          LegacyLocation.Attributes.STANDARD -> "STANDARD"
          LegacyLocation.Attributes.FEMALE_RESTRICTED -> "Q"
          LegacyLocation.Attributes.FEMALE_CLOSED -> "R"
          LegacyLocation.Attributes.FEMALE_SEMI -> "S"
          LegacyLocation.Attributes.FEMALE_OPEN -> "T"
          LegacyLocation.Attributes.UN_SENTENCED -> "U"
          LegacyLocation.Attributes.YES -> "Y"
          LegacyLocation.Attributes.NO -> "N"
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
    sequence = u.sequence,
    capacity = u.capacity,
  )

  private suspend fun toUpdateLocationRequest(instance: LegacyLocation) = UpdateLocationRequest(
    locationCode = instance.code,
    locationType = UpdateLocationRequest.LocationType.valueOf(toLocationType(instance.locationType)),
    description = instance.key,
    userDescription = instance.localName,
    parentLocationId = instance.parentId?.let { mappingService.getMappingGivenDpsId(it.toString()).nomisLocationId },
    unitType = instance.residentialHousingType?.let { UpdateLocationRequest.UnitType.valueOf(toUnitType(it)) },
    listSequence = instance.orderWithinParentLocation,
    comment = instance.comments,
    tracking = instance.internalMovementAllowed,
    active = instance.active,
    profiles = instance.attributes?.map { toAttribute(it) },
    usages = instance.usage?.map { toUsage(it) },
  )

  private fun toUnitType(residentialHousingType: LegacyLocation.ResidentialHousingType) = when (residentialHousingType) {
    LegacyLocation.ResidentialHousingType.HEALTHCARE -> "HC"
    LegacyLocation.ResidentialHousingType.HOLDING_CELL -> "HOLC"
    LegacyLocation.ResidentialHousingType.NORMAL_ACCOMMODATION -> "NA"
    LegacyLocation.ResidentialHousingType.OTHER_USE -> "OU"
    LegacyLocation.ResidentialHousingType.RECEPTION -> "REC"
    LegacyLocation.ResidentialHousingType.SEGREGATION -> "SEG"
    LegacyLocation.ResidentialHousingType.SPECIALIST_CELL -> "SPLC"
  }

  private fun toLocationType(locationtype: LegacyLocation.LocationType) = when (locationtype) {
    LegacyLocation.LocationType.WING -> "WING"
    LegacyLocation.LocationType.SPUR -> "SPUR"
    LegacyLocation.LocationType.LANDING,
      -> "LAND"

    LegacyLocation.LocationType.CELL -> "CELL"
    LegacyLocation.LocationType.ADJUDICATION_ROOM -> "ADJU"
    LegacyLocation.LocationType.ADMINISTRATION_AREA -> "ADMI"
    LegacyLocation.LocationType.APPOINTMENTS -> "APP"
    LegacyLocation.LocationType.AREA -> "AREA"
    LegacyLocation.LocationType.ASSOCIATION -> "ASSO"
    LegacyLocation.LocationType.BOOTH -> "BOOT"
    LegacyLocation.LocationType.BOX -> "BOX"
    LegacyLocation.LocationType.CLASSROOM -> "CLAS"
    LegacyLocation.LocationType.EXERCISE_AREA -> "EXER"
    LegacyLocation.LocationType.EXTERNAL_GROUNDS -> "EXTE"
    LegacyLocation.LocationType.FAITH_AREA -> "FAIT"
    LegacyLocation.LocationType.GROUP -> "GROU"
    LegacyLocation.LocationType.HOLDING_CELL -> "HCEL"
    LegacyLocation.LocationType.HOLDING_AREA -> "HOLD"
    LegacyLocation.LocationType.INTERNAL_GROUNDS -> "IGRO"
    LegacyLocation.LocationType.INSIDE_PARTY -> "INSI"
    LegacyLocation.LocationType.INTERVIEW -> "INTE"
    LegacyLocation.LocationType.LOCATION -> "LOCA"
    LegacyLocation.LocationType.MEDICAL -> "MEDI"
    LegacyLocation.LocationType.MOVEMENT_AREA -> "MOVE"
    LegacyLocation.LocationType.OFFICE -> "OFFI"
    LegacyLocation.LocationType.OUTSIDE_PARTY -> "OUTS"
    LegacyLocation.LocationType.POSITION -> "POSI"
    LegacyLocation.LocationType.RESIDENTIAL_UNIT -> "RESI"
    LegacyLocation.LocationType.ROOM -> "ROOM"
    LegacyLocation.LocationType.RETURN_TO_UNIT -> "RTU"
    LegacyLocation.LocationType.SHELF -> "SHEL"
    LegacyLocation.LocationType.SPORTS -> "SPOR"
    LegacyLocation.LocationType.STORE -> "STOR"
    LegacyLocation.LocationType.TABLE -> "TABL"
    LegacyLocation.LocationType.TRAINING_AREA -> "TRAI"
    LegacyLocation.LocationType.TRAINING_ROOM -> "TRRM"
    LegacyLocation.LocationType.VIDEO_LINK -> "VIDE"
    LegacyLocation.LocationType.VISITS -> "VISIT"
    LegacyLocation.LocationType.WORKSHOP -> "WORK"
  }

  private fun toDeactivateRequest(location: LegacyLocation): DeactivateRequest = DeactivateRequest(
    deactivateDate = location.deactivatedDate,
    reasonCode = when (location.deactivatedReason) {
      LegacyLocation.DeactivatedReason.REFURBISHMENT -> DeactivateRequest.ReasonCode.D
      LegacyLocation.DeactivatedReason.OTHER -> DeactivateRequest.ReasonCode.F
      LegacyLocation.DeactivatedReason.MAINTENANCE -> DeactivateRequest.ReasonCode.G
      LegacyLocation.DeactivatedReason.STAFF_SHORTAGE -> DeactivateRequest.ReasonCode.H
      LegacyLocation.DeactivatedReason.MOTHBALLED -> DeactivateRequest.ReasonCode.I
      LegacyLocation.DeactivatedReason.DAMAGED -> DeactivateRequest.ReasonCode.J
      else -> null
    },
    reactivateDate = location.proposedReactivationDate,
    // A deactivation event could occur if just the reason has changed, so always disable the checking of whether it is already inactive
    force = true,
  )

  private fun isDpsCreated(additionalInformation: LocationAdditionalInformation) = additionalInformation.source != CreatingSystem.NOMIS.name

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

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class LocationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val additionalInformation: LocationAdditionalInformation,
)

data class LocationAdditionalInformation(
  val id: String,
  val key: String,
  val source: String? = null,
)
