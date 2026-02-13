package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class OfficialVisitsDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
  private val officialVisitsService: OfficialVisitsService,
  private val visitSlotsService: VisitSlotsService,
  mappingRetry: MappingRetry,
) : DomainEventListener(
  service = mappingRetry,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "officialvisits",
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("officialvisits", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "official-visits-api.time-slot.created" -> visitSlotsService.timeSlotCreated(message.fromJson())
      "official-visits-api.time-slot.updated" -> visitSlotsService.timeSlotUpdated(message.fromJson())
      "official-visits-api.time-slot.deleted" -> visitSlotsService.timeSlotDeleted(message.fromJson())
      "official-visits-api.visit-slot.created" -> visitSlotsService.visitSlotCreated(message.fromJson())
      "official-visits-api.visit-slot.updated" -> visitSlotsService.visitSlotUpdated(message.fromJson())
      "official-visits-api.visit-slot.deleted" -> visitSlotsService.visitSlotDeleted(message.fromJson())
      "official-visits-api.visit.created" -> officialVisitsService.visitCreated(message.fromJson())
      "official-visits-api.visitor.created" -> officialVisitsService.visitorCreated(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

@Service
class MappingRetry(
  private val jsonMapper: JsonMapper,
  private val officialVisitsService: OfficialVisitsService,
  private val visitSlotsService: VisitSlotsService,
) : CreateMappingRetryable {
  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)

  enum class MappingTypes(val entityName: String) {
    TIME_SLOT("time-slot"),
    VISIT_SLOT("visit-slot"),
    OFFICIAL_VISIT("official-visit"),
    OFFICIAL_VISITOR("official-visitor"),
  }

  fun fromEntityName(entityName: String) = MappingTypes.entries.find { it.entityName == entityName } ?: throw IllegalStateException("Mapping type $entityName does not exist")

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (fromEntityName(baseMapping.entityName)) {
      MappingTypes.TIME_SLOT -> visitSlotsService.createTimeSlotMapping(message.fromJson())
      MappingTypes.VISIT_SLOT -> visitSlotsService.createVisitSlotMapping(message.fromJson())
      MappingTypes.OFFICIAL_VISIT -> officialVisitsService.createVisitMapping(message.fromJson())
      MappingTypes.OFFICIAL_VISITOR -> officialVisitsService.createVisitorMapping(message.fromJson())
    }
  }
}

interface SourcedAdditionalData {
  val source: String
}

data class TimeSlotAdditionalData(
  val prisonId: String,
  val timeSlotId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class VisitSlotAdditionalData(
  val prisonId: String,
  val visitSlotId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class VisitAdditionalData(
  val prisonId: String,
  val officialVisitId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class VisitorAdditionalData(
  val prisonId: String,
  val officialVisitId: Long,
  val officialVisitorId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

interface SourcedEvent {
  val additionalInformation: SourcedAdditionalData
}

data class TimeSlotEvent(
  override val additionalInformation: TimeSlotAdditionalData,
) : SourcedEvent

data class VisitSlotEvent(
  override val additionalInformation: VisitSlotAdditionalData,
) : SourcedEvent

data class VisitEvent(
  override val additionalInformation: VisitAdditionalData,
  override val personReference: PrisonerIdentifiers,
) : SourcedEvent,
  PrisonerReferencedEvent

data class VisitorEvent(
  override val additionalInformation: VisitorAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedEvent,
  ContactIdReferencedEvent

interface PrisonerReferencedEvent {
  val personReference: PrisonerIdentifiers
}

fun PrisonerReferencedEvent.prisonerNumber() = personReference.identifiers.first { it.type == "NOMS" }.value

interface ContactIdReferencedEvent {
  val personReference: ContactIdentifiers
}

fun ContactIdReferencedEvent.contactId() = personReference.identifiers.first { it.type == "CONTACT_ID" }.value.toLong()

data class ContactIdentifiers(val identifiers: List<ContactPersonReference>)
data class ContactPersonReference(val type: String, val value: String)
data class PrisonerIdentifiers(val identifiers: List<PrisonerReference>)
data class PrisonerReference(val type: String, val value: String)
