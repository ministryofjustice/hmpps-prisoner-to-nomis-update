package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Service
class CsraDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
  private val csraService: CsraService,
) : DomainEventListener(
  service = csraService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "csra",
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("csra", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "cell.sharing.risk.assessment.created" -> csraService.created(message.fromJson())
      "cell.sharing.risk.assessment.amended" -> csraService.updated(message.fromJson())

      else -> log.info("Received a CSRA message I wasn't expecting: {}", eventType)
    }
  }
}

data class CsraDomainEvent(
  val eventType: String,
  val version: String,
  val description: String? = null,
  val occurredAt: String,
  val additionalInformation: CsraDomainAdditionalInformation,
)

data class CsraDomainAdditionalInformation(
  val id: UUID? = null,
  val nomsNumber: String? = null,
  val key: String? = null,
  val source: InformationSource? = null,
)

enum class InformationSource {
  DPS,
  NOMIS,
}
