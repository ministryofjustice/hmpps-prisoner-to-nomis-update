package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

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
class PropertyDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
  private val propertyService: PropertyService,
) : DomainEventListener(
  service = propertyService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "property",
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("property", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "prison-property.container.created" -> propertyService.created(message.fromJson())
      "prison-property.container.updated" -> propertyService.updated(message.fromJson())

      else -> log.info("Received a property message I wasn't expecting: {}", eventType)
    }
  }
}

data class PropertyDomainEvent(
  val eventType: String,
  val version: Int,
  val description: String? = null,
  val detailUrl: String? = null,
  val occurredAt: String,
  val prisonerNumber: String,
  val source: String? = null,
  val additionalInformation: PropertyDomainAdditionalInformation,
)

data class PropertyDomainAdditionalInformation(
  val dpsId: String,
  val nomisPropertyContainerId: Long? = null,
  val changedFields: List<String>? = null,
)

// "Message" : "{\"eventType\":\"prison-property.container.updated\",\"version\":1,\"description\":\"A prisoner property container was changed in DPS\",\"detailUrl\":null,\"occurredAt\":\"2026-07-30T10:38:27.418477578Z\",
// \"prisonerNumber\":\"G0442GA\",\"source\":\"DPS\",\"additionalInformation\":{\"dpsId\":\"019fb292-0229-716f-abd7-3edb724f72a2\",\"changedFields\":[\"removalOutcome\"]}}",
