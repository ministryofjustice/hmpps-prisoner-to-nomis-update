package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class SentencingDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val sentencingAdjustmentsService: SentencingAdjustmentsService,
) : DomainEventListener(
  service = sentencingAdjustmentsService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("sentencing", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-hmpps_prisoner_to_nomis_sentencing_queue", kind = SpanKind.SERVER)
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "sentencing.sentence.adjustment.created" ->
        sentencingAdjustmentsService.createAdjustment(message.fromJson())

      "sentencing.sentence.adjustment.updated" ->
        sentencingAdjustmentsService.updateAdjustment(message.fromJson())

      "sentencing.sentence.adjustment.deleted" ->
        sentencingAdjustmentsService.deleteAdjustment(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
