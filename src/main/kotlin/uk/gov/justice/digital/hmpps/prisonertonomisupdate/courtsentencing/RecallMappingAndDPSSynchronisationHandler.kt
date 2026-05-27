package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingService.EntityType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceRecallMappingsUpdateDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.QueueService

@Service
class RecallMappingAndDPSSynchronisationHandler(
  private val telemetryClient: TelemetryClient,
  private val courtSentencingRetryQueueService: CourtSentencingRetryQueueService,
  private val queueService: QueueService,
  private val courtCaseMappingService: CourtSentencingMappingService,
) {
  suspend fun tryCreateMappingsAndNotifyDPS(
    mappingsWrapper: RecallAppearanceAndCreateMappingsWrapper,
    offenderNo: String,
    telemetry: Map<String, String>,
  ) {
    try {
      createMappingsAndNotifyDPS(
        mappingsWrapper = mappingsWrapper,
        offenderNo = offenderNo,
        telemetry = telemetry,
      )
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "recall-mappings-inserted-failed",
        telemetry + ("reason" to (e.message ?: "unknown")),
        null,
      )
      courtSentencingRetryQueueService.sendMessage(
        mapping = mappingsWrapper,
        telemetryAttributes = telemetry,
        entityName = EntityType.COURT_APPEARANCE_RECALL.displayName,
      )
    }
  }

  suspend fun createMappingsAndNotifyDPSRetry(message: CreateMappingRetryMessage<RecallAppearanceAndCreateMappingsWrapper>) = with(message) {
    createMappingsAndNotifyDPS(
      mappingsWrapper = mapping,
      offenderNo = mapping.offenderNo,
      telemetry = telemetryAttributes,
    ).also {
      telemetryClient.trackEvent(
        "court-appearance-recall-create-mapping-retry-success",
        telemetryAttributes,
        null,
      )
    }
  }

  private suspend fun createMappingsAndNotifyDPS(
    mappingsWrapper: RecallAppearanceAndCreateMappingsWrapper,
    offenderNo: String,
    telemetry: Map<String, String>,
  ) {
    courtCaseMappingService.createAppearanceRecallMappings(mappingsWrapper.mappings)

    // we might get adjustments that just need updating since the cases have not been cloned,
    // or we might get cases that have been created and the adjustments have been created, in which case they will appear in both lists,
    // or we might get both - so just create one set
    val sentenceAdjustmentsRequiringResync = (
      mappingsWrapper.clonedClonedCourtCaseDetails?.sentenceAdjustments
        ?: emptyList()
      ) + mappingsWrapper.sentenceAdjustmentsActivated.map {
      SentenceIdAndAdjustmentType(
        sentenceId = it.sentenceId,
        adjustmentIds = it.adjustmentIds.sorted(),
      )
    }

    sentenceAdjustmentsRequiringResync.toSet().forEach { adjustment ->
      adjustment.adjustmentIds.forEach { adjustmentId ->
        // since these are new adjustments send these individually given, creating
        // a batch of adjustments is not idempotent if there are failures
        queueService.sendMessageTrackOnFailure(
          queueId = "fromnomiscourtsentencing",
          eventType = "courtsentencing.resync.sentence-adjustments",
          message = SyncSentenceAdjustment(
            offenderNo = offenderNo,
            sentences = listOf(
              SentenceIdAndAdjustmentIds(
                sentenceId = adjustment.sentenceId,
                adjustmentIds = listOf(adjustmentId),
              ),
            ),
          ),
        )
      }
    }
    val courtAppearancesRequiringSync = mappingsWrapper.mappings.nomisCourtAppearanceIds
    courtAppearancesRequiringSync.forEach { courtAppearanceId ->
      queueService.sendMessageTrackOnFailure(
        queueId = "fromnomiscourtsentencing",
        eventType = "courtsentencing.resync.breach-court-appearance.inserted",
        message = SyncRecallBreachCourtAppearanceEvent(
          offenderNo = offenderNo,
          courtAppearanceId = courtAppearanceId,
        ),
      )
    }

    mappingsWrapper.clonedClonedCourtCaseDetails?.also { details ->
      queueService.sendMessageTrackOnFailure(
        queueId = "fromnomiscourtsentencing",
        eventType = "courtsentencing.resync.case.booking",
        message = OffenderCaseBookingResynchronisationEvent(
          offenderNo = offenderNo,
          caseIds = details.clonedCourtCaseIds,
          fromBookingId = details.fromBookingId,
          toBookingId = details.toBookingId,
          casesMoved = details.casesMoved,
        ),
      )

      telemetryClient.trackEvent(
        "recall-create-cases-cloned-success",
        telemetry + ("nomisCourtCaseIds" to details.clonedCourtCaseIds.joinToString()),
        null,
      )
    }
  }

  suspend fun tryUpdateMappingsAndNotifyDPS(
    mappingsWrapper: RecallAppearanceUpdateMappingsWrapper,
    telemetry: Map<String, String>,
  ) {
    try {
      updateMappingsAndNotifyDPS(
        mappingsWrapper = mappingsWrapper,
      )
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "recall-mappings-updated-failed",
        telemetry + ("reason" to (e.message ?: "unknown")),
        null,
      )
      courtSentencingRetryQueueService.sendMessage(
        mapping = mappingsWrapper,
        telemetryAttributes = telemetry,
        entityName = EntityType.COURT_APPEARANCE_RECALL_UPDATE.displayName,
      )
    }
  }

  private suspend fun updateMappingsAndNotifyDPS(
    mappingsWrapper: RecallAppearanceUpdateMappingsWrapper,
  ) {
    courtCaseMappingService.updateAppearanceRecallMappings(
      recallId = mappingsWrapper.dpsRecallId,
      request = CourtAppearanceRecallMappingsUpdateDto(
        nomisCourtAppearanceIds = mappingsWrapper.updatedCourtEventIds + mappingsWrapper.createdCourtEventIds,
      ),
    )

    mappingsWrapper.createdCourtEventIds.forEach { courtAppearanceId ->
      queueService.sendMessageTrackOnFailure(
        queueId = "fromnomiscourtsentencing",
        eventType = "courtsentencing.resync.breach-court-appearance.inserted",
        message = SyncRecallBreachCourtAppearanceEvent(
          offenderNo = mappingsWrapper.offenderNo,
          courtAppearanceId = courtAppearanceId,
        ),
      )
    }
    mappingsWrapper.updatedCourtEventIds.forEach { courtAppearanceId ->
      queueService.sendMessageTrackOnFailure(
        queueId = "fromnomiscourtsentencing",
        eventType = "courtsentencing.resync.breach-court-appearance.updated",
        message = SyncRecallBreachCourtAppearanceEvent(
          offenderNo = mappingsWrapper.offenderNo,
          courtAppearanceId = courtAppearanceId,
        ),
      )
    }
  }
  suspend fun updateMappingsAndNotifyDPSRetry(message: CreateMappingRetryMessage<RecallAppearanceUpdateMappingsWrapper>) = with(message) {
    updateMappingsAndNotifyDPS(
      mappingsWrapper = mapping,
    ).also {
      telemetryClient.trackEvent(
        "court-appearance-recall-update-mapping-retry-success",
        telemetryAttributes,
        null,
      )
    }
  }
}
