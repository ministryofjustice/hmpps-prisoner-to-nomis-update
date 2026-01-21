package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

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
class IncentivesDomainEventListener(
  private val incentivesService: IncentivesService,
  private val incentivesReferenceService: IncentivesReferenceService,
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = incentivesService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "incentives",
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("incentive", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(rawMessage: String): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "incentives.iep-review.inserted" -> incentivesService.createIncentive(message.fromJson())
      "incentives.level.changed" -> incentivesReferenceService.globalIncentiveLevelChange(message.fromJson())
      "incentives.levels.reordered" -> incentivesReferenceService.globalIncentiveLevelsReorder()
      "incentives.prison-level.changed" -> incentivesReferenceService.prisonIncentiveLevelChange(message.fromJson()) // TODO
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
