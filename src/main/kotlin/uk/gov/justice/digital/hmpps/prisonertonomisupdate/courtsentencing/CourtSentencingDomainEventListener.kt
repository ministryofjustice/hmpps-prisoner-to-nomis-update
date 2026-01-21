package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class CourtSentencingDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val courtSentencingService: CourtSentencingService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = courtSentencingService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "courtsentencing",
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("courtsentencing", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "court-case.inserted" ->
        courtSentencingService.createCourtCase(message.fromJson())
      "court-appearance.inserted" ->
        courtSentencingService.createCourtAppearance(message.fromJson())
      "court-case.deleted" ->
        courtSentencingService.deleteCourtCase(message.fromJson())
      "court-appearance.deleted" ->
        courtSentencingService.deleteCourtAppearance(message.fromJson())
      // includes removing or adding any charges that are associated with the appearance
      "court-appearance.updated" ->
        courtSentencingService.updateCourtAppearance(message.fromJson())
      "legacy.court-case-references.updated" ->
        courtSentencingService.refreshCaseReferences(message.fromJson())
      "charge.inserted" ->
        courtSentencingService.createCharge(message.fromJson())
      "charge.updated" ->
        courtSentencingService.updateCharge(message.fromJson())
      "sentence.inserted" ->
        courtSentencingService.createSentence(message.fromJson())
      "sentence.fix-single-charge.inserted" ->
        courtSentencingService.createSentence(message.fromJson())
      "sentence.updated" ->
        courtSentencingService.updateSentence(message.fromJson())
      "sentence.deleted" ->
        courtSentencingService.deleteSentence(message.fromJson())
      "sentence.period-length.inserted" ->
        courtSentencingService.createSentenceTerm(message.fromJson())
      "sentence.period-length.updated" ->
        courtSentencingService.updateSentenceTerm(message.fromJson())
      "sentence.period-length.deleted" ->
        courtSentencingService.deleteSentenceTerm(message.fromJson())
      "recall.inserted" ->
        courtSentencingService.createRecallSentences(message.fromJson())
      "recall.updated" ->
        courtSentencingService.updateRecallSentences(message.fromJson())
      "recall.deleted" ->
        courtSentencingService.deleteRecallSentences(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
