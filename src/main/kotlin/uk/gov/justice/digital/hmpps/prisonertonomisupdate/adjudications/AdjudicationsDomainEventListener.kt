package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

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
class AdjudicationsDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val adjudicationsService: AdjudicationsService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = adjudicationsService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "adjudications",
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("adjudication", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "adjudication.report.created" -> adjudicationsService.createAdjudication(message.fromJson())
      "adjudication.damages.updated" -> adjudicationsService.updateAdjudicationDamages(message.fromJson())
      "adjudication.evidence.updated" -> adjudicationsService.updateAdjudicationEvidence(message.fromJson())
      "adjudication.hearing.created" -> adjudicationsService.createHearing(message.fromJson())
      "adjudication.hearing.updated" -> adjudicationsService.updateHearing(message.fromJson())
      "adjudication.hearing.deleted" -> adjudicationsService.deleteHearing(message.fromJson())
      "adjudication.hearingCompleted.created",
      "adjudication.hearingAdjourn.created",
      "adjudication.hearingReferral.created",
      "adjudication.referral.outcome.prosecution",
      "adjudication.referral.outcome.notProceed",
      "adjudication.referral.outcome.referGov",
      "adjudication.outcome.referPolice",
      "adjudication.outcome.notProceed",
      "adjudication.hearingOutcome.updated",
      -> adjudicationsService.upsertOutcome(message.fromJson())

      "adjudication.hearingCompleted.deleted",
      "adjudication.hearingAdjourn.deleted",
      "adjudication.hearingReferral.deleted",
      "adjudication.referral.outcome.deleted",
      "adjudication.referral.deleted",
      "adjudication.outcome.notProceed.deleted",
      -> adjudicationsService.deleteOutcome(message.fromJson())

      "adjudication.punishments.created" -> adjudicationsService.createPunishments(message.fromJson())
      "adjudication.punishments.updated" -> adjudicationsService.updatePunishments(message.fromJson())
      "adjudication.punishments.deleted" -> adjudicationsService.deletePunishments(message.fromJson())
      "adjudication.outcome.quashed" -> adjudicationsService.quashPunishments(message.fromJson())
      "adjudication.outcome.unquashed" -> adjudicationsService.unquashPunishments(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
