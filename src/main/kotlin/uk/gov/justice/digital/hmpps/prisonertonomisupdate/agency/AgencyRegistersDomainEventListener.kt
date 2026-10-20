package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListenerNoMapping
import java.util.concurrent.CompletableFuture

@Service
class AgencyRegistersDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val service: AgencyRegistersService,
  telemetryClient: TelemetryClient,
) : DomainEventListenerNoMapping(
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "agencyregisters",
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("agencyregisters", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "register.court.inserted" -> service.courtInserted(message.fromJson())
      "register.court.amended" -> service.courtAmended(message.fromJson())
      "register.court.email.inserted" -> service.courtEmailInserted(message.fromJson())
      "register.court.email.amended" -> service.courtEmailAmended(message.fromJson())
      "register.court.email.deleted" -> service.courtEmailDeleted(message.fromJson())
      "register.court.phone.inserted" -> service.courtPhoneInserted(message.fromJson())
      "register.court.phone.amended" -> service.courtPhoneAmended(message.fromJson())
      "register.court.phone.deleted" -> service.courtPhoneDeleted(message.fromJson())
      "register.court.address.inserted" -> service.courtAddressInserted(message.fromJson())
      "register.court.address.amended" -> service.courtAddressAmended(message.fromJson())
      "register.court.address.deleted" -> service.courtAddressDeleted(message.fromJson())
      "register.hospital.inserted" -> service.hospitalInserted(message.fromJson())
      "register.hospital.amended" -> service.hospitalAmended(message.fromJson())
      "register.hospital.phone.inserted" -> service.hospitalPhoneInserted(message.fromJson())
      "register.hospital.phone.amended" -> service.hospitalPhoneAmended(message.fromJson())
      "register.hospital.phone.deleted" -> service.hospitalPhoneDeleted(message.fromJson())
      "register.hospital.address.inserted" -> service.hospitalAddressInserted(message.fromJson())
      "register.hospital.address.amended" -> service.hospitalAddressAmended(message.fromJson())
      "register.hospital.address.deleted" -> service.hospitalAddressDeleted(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

internal interface SourcedAdditionalData {
  val source: String
}

internal interface SourcedEvent {
  val additionalInformation: SourcedAdditionalData
}

data class CourtEvent(
  val eventType: String,
  override val additionalInformation: CourtAdditionalInformation,
) : SourcedEvent

data class CourtAdditionalInformation(
  val courtId: String,
  override val source: String,
) : SourcedAdditionalData

data class CourtEmailEvent(
  val eventType: String,
  override val additionalInformation: CourtEmailAdditionalInformation,
) : SourcedEvent

data class CourtEmailAdditionalInformation(
  val courtId: String,
  val emailId: Long,
  override val source: String,
) : SourcedAdditionalData

data class CourtPhoneEvent(
  val eventType: String,
  override val additionalInformation: CourtPhoneAdditionalInformation,
) : SourcedEvent

data class CourtPhoneAdditionalInformation(
  val courtId: String,
  val phoneId: Long,
  override val source: String,
) : SourcedAdditionalData

data class CourtAddressEvent(
  val eventType: String,
  override val additionalInformation: CourtAddressAdditionalInformation,
) : SourcedEvent

data class CourtAddressAdditionalInformation(
  val courtId: String,
  val addressId: Long,
  override val source: String,
) : SourcedAdditionalData

data class HospitalEvent(
  val eventType: String,
  override val additionalInformation: HospitalAdditionalInformation,
) : SourcedEvent

data class HospitalAdditionalInformation(
  val hospitalId: String,
  override val source: String,
) : SourcedAdditionalData

data class HospitalPhoneEvent(
  val eventType: String,
  override val additionalInformation: HospitalPhoneAdditionalInformation,
) : SourcedEvent

data class HospitalPhoneAdditionalInformation(
  val hospitalId: String,
  val phoneId: Long,
  override val source: String,
) : SourcedAdditionalData

data class HospitalAddressEvent(
  val eventType: String,
  override val additionalInformation: HospitalAddressAdditionalInformation,
) : SourcedEvent

data class HospitalAddressAdditionalInformation(
  val hospitalId: String,
  val addressId: Long,
  override val source: String,
) : SourcedAdditionalData
