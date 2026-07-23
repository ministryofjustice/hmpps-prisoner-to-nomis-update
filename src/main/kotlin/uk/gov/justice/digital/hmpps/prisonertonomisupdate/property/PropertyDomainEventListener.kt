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
/*
Event type	Topic	Keys and values
prison-property.container.created	Domain
version	1.0
description	A prisoner property container was changed in DPS
detailUrl
occurredAt	2026-07-20T15:01:33.671335777Z
prisonerNumber	G2536GG
source	DPS
additionalInformation	{dpsId=019f800b-ef66-759a-b25e-cc546ef8429b}
* */

/*
prison-property.container.updated	Domain
version	1.0
description	A prisoner property container was changed in DPS
detailUrl
occurredAt	2026-07-20T15:08:21.213612057Z
prisonerNumber	G2536GG
source	DPS
additionalInformation	{dpsId=019f800b-ef66-759a-b25e-cc546ef8429b, changedFields=[sealNumber, proposedDisposalDate]}
 */
/*
prison-property.container.created	Domain
version	1.0
description	A prisoner property container was synchronised from NOMIS
detailUrl
occurredAt	2026-07-20T13:17:39.918894495Z
prisonerNumber	G9958GO
source	NOMIS
additionalInformation	{dpsId=019f7fac-d0bc-760c-a81e-1170e919c412, nomisPropertyContainerId=610858.0}
 */
