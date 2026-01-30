package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.LegacyNonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.BookingMovedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MergeEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class NonAssociationsService(
  private val nonAssociationsApiService: NonAssociationsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: NonAssociationMappingService,
  private val nonAssociationsUpdateQueueService: NonAssociationsUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createNonAssociation(event: NonAssociationDomainEvent) {
    val telemetryMap = mapOf("nonAssociationId" to event.additionalInformation.id.toString())
    if (isDpsCreated(event.additionalInformation)) {
      synchronise {
        name = "non-association"
        telemetryClient = this@NonAssociationsService.telemetryClient
        retryQueueService = nonAssociationsUpdateQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingService.getMappingGivenNonAssociationIdOrNull(event.additionalInformation.id)
        }
        transform {
          nonAssociationsApiService.getNonAssociation(event.additionalInformation.id).run {
            val request = toCreateNonAssociationRequest(this)

            eventTelemetry += "offenderNo" to request.offenderNo
            eventTelemetry += "nsOffenderNo" to request.nsOffenderNo

            NonAssociationMappingDto(
              firstOffenderNo = if (request.offenderNo < request.nsOffenderNo) request.offenderNo else request.nsOffenderNo,
              secondOffenderNo = if (request.offenderNo < request.nsOffenderNo) request.nsOffenderNo else request.offenderNo,
              nomisTypeSequence = nomisApiService.createNonAssociation(request).typeSequence,
              nonAssociationId = id,
              mappingType = "NON_ASSOCIATION_CREATED",
            )
          }
        }
        saveMapping { mappingService.createMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("non-association-create-ignored", telemetryMap)
    }
  }

  suspend fun amendNonAssociation(event: NonAssociationDomainEvent) {
    val telemetryMap = mutableMapOf("nonAssociationId" to event.additionalInformation.id.toString())

    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        val nonAssociation = nonAssociationsApiService.getNonAssociation(event.additionalInformation.id)

        mappingService.getMappingGivenNonAssociationId(event.additionalInformation.id)
          .apply {
            telemetryMap["offender1"] = firstOffenderNo
            telemetryMap["offender2"] = secondOffenderNo
            telemetryMap["sequence"] = nomisTypeSequence.toString()
            nomisApiService.amendNonAssociation(firstOffenderNo, secondOffenderNo, nomisTypeSequence, toUpdateNonAssociationRequest(nonAssociation))
          }
      }.onSuccess {
        telemetryClient.trackEvent("non-association-amend-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("non-association-amend-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("non-association-amend-ignored", telemetryMap)
    }
  }

  suspend fun closeNonAssociation(event: NonAssociationDomainEvent) {
    val telemetryMap = mutableMapOf("nonAssociationId" to event.additionalInformation.id.toString())

    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        mappingService.getMappingGivenNonAssociationId(event.additionalInformation.id)
          .apply {
            telemetryMap["offender1"] = firstOffenderNo
            telemetryMap["offender2"] = secondOffenderNo
            telemetryMap["sequence"] = nomisTypeSequence.toString()
            nomisApiService.closeNonAssociation(firstOffenderNo, secondOffenderNo, nomisTypeSequence)
          }
      }.onSuccess {
        telemetryClient.trackEvent("non-association-close-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("non-association-close-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("non-association-close-ignored", telemetryMap)
    }
  }

  suspend fun deleteNonAssociation(event: NonAssociationDomainEvent) {
    val telemetryMap = mutableMapOf("nonAssociationId" to event.additionalInformation.id.toString())

    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        mappingService.getMappingGivenNonAssociationId(event.additionalInformation.id)
          .apply {
            telemetryMap["offender1"] = firstOffenderNo
            telemetryMap["offender2"] = secondOffenderNo
            telemetryMap["sequence"] = nomisTypeSequence.toString()
            nomisApiService.deleteNonAssociation(firstOffenderNo, secondOffenderNo, nomisTypeSequence)
            mappingService.deleteNonAssociation(nonAssociationId)
          }
      }.onSuccess {
        telemetryClient.trackEvent("non-association-delete-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("non-association-delete-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("non-association-delete-ignored", telemetryMap)
    }
  }

  suspend fun processMerge(event: MergeEvent) {
    val (reason, nomsNumber, removedNomsNumber) = event.additionalInformation

    if (reason == "MERGE") {
      val thirdParties = mappingService.findCommon(removedNomsNumber, nomsNumber)
      val sequenceReport = mutableMapOf<String, String>()
      thirdParties.groupBy { if (it.firstOffenderNo == nomsNumber || it.firstOffenderNo == removedNomsNumber) it.secondOffenderNo else it.firstOffenderNo }
        /*
         If thirdParties exist, the scenario is :
        •  - a duplicate prisoner record exists and an NA is created with the same other prisoner as for the existing prisoner’s NA
        •  - a mapping is therefore added for this NA (with seq 1, same as the old one)
        •  - a merge event occurs, and the mapping update which tries to change the old to the new offenderno fails with duplicate key

        The process is to identify which DPS NA corresponds to which Nomis NA, then update the relevant sequence no to 2 (usually, or
        whatever the Nomis value is) in the mapping table. This will cause the merge event message to go through avoiding a mapping duplicate key error.
        Note that in Nomis the NA of the merged FROM offender no has the sequence set to the next highest available no.

        Eg we might be merging A1234AA to A1234AB, there could be 2 third party offenders COMMON1 and COMMON2 and the mapping
        table might contain:
          101,COMMON1, A1234AA, sequence 1
          102,A1234AB, COMMON1, sequence 1
          103,COMMON2, A1234AA, sequence 1
          104,A1234AB, COMMON2, sequence 1

        In Nomis (already merged) these may look something like
          COMMON1, A1234AB, sequence 1
          A1234AB, COMMON1, sequence 2
          COMMON2, A1234AB, sequence 1
          COMMON2, A1234AB, sequence 2 (a different NA, closed)
          A1234AB, COMMON2, sequence 3

        So we need to set the 2 correct mapping table entries to sequence 2.
         */
        .forEach { entry ->
          if (entry.value.size != 2) {
            throw IllegalStateException("Entry not size 2: $entry")
          }
          val mappingRecord1 = entry.value.first()
          val mappingRecord2 = entry.value.last()
          if (mappingRecord1.nomisTypeSequence != mappingRecord2.nomisTypeSequence) {
            throw IllegalStateException("Entry sequences not equal: $entry")
          }
          // Find from Nomis & DPS which one needs a change of sequence
          val dpsRecord1 = nonAssociationsApiService.getNonAssociation(mappingRecord1.nonAssociationId)
          val dpsRecord2 = nonAssociationsApiService.getNonAssociation(mappingRecord2.nonAssociationId)

          val nomisData = nomisApiService.getNonAssociationDetails(entry.key, nomsNumber)
          // NomisData may contain many NAs (e.g. old closed ones); we need the one with this sequence no,
          // the DPS NA which corresponds with this,
          // and then the Nomis NA which corresponds to the other DPS NA
          val nomisRecordMatchingSeq = nomisData.find { it.typeSequence == mappingRecord1.nomisTypeSequence }
          val otherDpsRecord = if (dpsRecord1.effectiveDate.toLocalDate() == nomisRecordMatchingSeq?.effectiveDate) dpsRecord2 else dpsRecord1

          val nomisRecordNotMatching = nomisData.find {
            it.typeSequence != mappingRecord1.nomisTypeSequence &&
              otherDpsRecord.effectiveDate.toLocalDate() == it.effectiveDate
          }
            ?: throw IllegalStateException("Cannot match up DPS and Nomis NAs:\nentry = $entry\ndpsRecord1 = $dpsRecord1\ndpsRecord2 = $dpsRecord2\nnomisRecordMatchingSeq = $nomisRecordMatchingSeq\notherDpsRecord = $otherDpsRecord")

          mappingService.setSequence(otherDpsRecord.id, nomisRecordNotMatching.typeSequence)
          sequenceReport[entry.key] = "Reset nonAssociationId ${otherDpsRecord.id} sequence from ${mappingRecord1.nomisTypeSequence} to ${nomisRecordNotMatching.typeSequence}"
        }

      mappingService.mergeNomsNumber(removedNomsNumber, nomsNumber)

      telemetryClient.trackEvent(
        "non-association-merge-success",
        mapOf(
          "removedNomsNumber" to removedNomsNumber,
          "nomsNumber" to nomsNumber,
          "reason" to reason,
        ) + sequenceReport,
      )
    }
  }

  suspend fun processBookingMoved(event: BookingMovedEvent) {
    val (bookingId, movedFromNomsNumber, movedToNomsNumber, bookingStartDateTime) = event.additionalInformation

    val nonAssociationsByBooking = nomisApiService.getNonAssociationsByBooking(bookingId.toLong())

    nonAssociationsByBooking
      .takeIf(List<*>::isNotEmpty)
      ?.apply {
        mappingService.updateList(
          movedFromNomsNumber,
          movedToNomsNumber,
          map {
            if (movedToNomsNumber == it.offenderNo1) {
              it.offenderNo2
            } else {
              it.offenderNo1
            }
          },
        )
      }

    telemetryClient.trackEvent(
      "non-association-booking-moved-success",
      mapOf(
        "movedFromNomsNumber" to movedFromNomsNumber,
        "movedToNomsNumber" to movedToNomsNumber,
        "bookingId" to bookingId,
        "bookingStartDateTime" to bookingStartDateTime,
        "count" to nonAssociationsByBooking.size.toString(),
      ),
    )
  }

  private fun toCreateNonAssociationRequest(instance: LegacyNonAssociation) = CreateNonAssociationRequest(
    offenderNo = instance.offenderNo,
    nsOffenderNo = instance.offenderNonAssociation.offenderNo,
    reason = instance.reasonCode.value,
    recipReason = instance.offenderNonAssociation.reasonCode.value,
    type = instance.typeCode.value,
    effectiveDate = instance.effectiveDate.toLocalDate(),
    authorisedBy = instance.authorisedBy,
    comment = instance.comments,
  )

  private fun toUpdateNonAssociationRequest(instance: LegacyNonAssociation) = UpdateNonAssociationRequest(
    reason = instance.reasonCode.value,
    recipReason = instance.offenderNonAssociation.reasonCode.value,
    type = instance.typeCode.value,
    effectiveDate = instance.effectiveDate.toLocalDate(),
    authorisedBy = instance.authorisedBy,
    comment = instance.comments,
  )

  private fun isDpsCreated(additionalInformation: NonAssociationAdditionalInformation) = additionalInformation.source != CreatingSystem.NOMIS.name

  suspend fun createRetry(message: CreateMappingRetryMessage<NonAssociationMappingDto>) {
    mappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "non-association-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class NonAssociationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val additionalInformation: NonAssociationAdditionalInformation,
)

data class NonAssociationAdditionalInformation(
  val id: Long,
  val source: String? = null,
)
