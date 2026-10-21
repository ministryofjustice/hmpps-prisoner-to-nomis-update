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
      "register.court.deleted" -> service.courtDeleted(message.fromJson())
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
      "register.hospital.deleted" -> service.hospitalDeleted(message.fromJson())
      "register.hospital.phone.inserted" -> service.hospitalPhoneInserted(message.fromJson())
      "register.hospital.phone.amended" -> service.hospitalPhoneAmended(message.fromJson())
      "register.hospital.phone.deleted" -> service.hospitalPhoneDeleted(message.fromJson())
      "register.hospital.address.inserted" -> service.hospitalAddressInserted(message.fromJson())
      "register.hospital.address.amended" -> service.hospitalAddressAmended(message.fromJson())
      "register.hospital.address.deleted" -> service.hospitalAddressDeleted(message.fromJson())
      "register.policecustodysuite.inserted" -> service.policeCustodySuiteInserted(message.fromJson())
      "register.policecustodysuite.amended" -> service.policeCustodySuiteAmended(message.fromJson())
      "register.policecustodysuite.deleted" -> service.policeCustodySuiteDeleted(message.fromJson())
      "register.policecustodysuite.email.inserted" -> service.policeCustodySuiteEmailInserted(message.fromJson())
      "register.policecustodysuite.email.amended" -> service.policeCustodySuiteEmailAmended(message.fromJson())
      "register.policecustodysuite.email.deleted" -> service.policeCustodySuiteEmailDeleted(message.fromJson())
      "register.policecustodysuite.phone.inserted" -> service.policeCustodySuitePhoneInserted(message.fromJson())
      "register.policecustodysuite.phone.amended" -> service.policeCustodySuitePhoneAmended(message.fromJson())
      "register.policecustodysuite.phone.deleted" -> service.policeCustodySuitePhoneDeleted(message.fromJson())
      "register.policecustodysuite.address.inserted" -> service.policeCustodySuiteAddressInserted(message.fromJson())
      "register.policecustodysuite.address.amended" -> service.policeCustodySuiteAddressAmended(message.fromJson())
      "register.policecustodysuite.address.deleted" -> service.policeCustodySuiteAddressDeleted(message.fromJson())
      "register.approvedpremises.inserted" -> service.approvedPremisesInserted(message.fromJson())
      "register.approvedpremises.amended" -> service.approvedPremisesAmended(message.fromJson())
      "register.approvedpremises.deleted" -> service.approvedPremisesDeleted(message.fromJson())
      "register.approvedpremises.email.inserted" -> service.approvedPremisesEmailInserted(message.fromJson())
      "register.approvedpremises.email.amended" -> service.approvedPremisesEmailAmended(message.fromJson())
      "register.approvedpremises.email.deleted" -> service.approvedPremisesEmailDeleted(message.fromJson())
      "register.approvedpremises.phone.inserted" -> service.approvedPremisesPhoneInserted(message.fromJson())
      "register.approvedpremises.phone.amended" -> service.approvedPremisesPhoneAmended(message.fromJson())
      "register.approvedpremises.phone.deleted" -> service.approvedPremisesPhoneDeleted(message.fromJson())
      "register.approvedpremises.address.inserted" -> service.approvedPremisesAddressInserted(message.fromJson())
      "register.approvedpremises.address.amended" -> service.approvedPremisesAddressAmended(message.fromJson())
      "register.approvedpremises.address.deleted" -> service.approvedPremisesAddressDeleted(message.fromJson())
      "register.probationoffice.inserted" -> service.probationOfficeInserted(message.fromJson())
      "register.probationoffice.amended" -> service.probationOfficeAmended(message.fromJson())
      "register.probationoffice.deleted" -> service.probationOfficeDeleted(message.fromJson())
      "register.probationoffice.email.inserted" -> service.probationOfficeEmailInserted(message.fromJson())
      "register.probationoffice.email.amended" -> service.probationOfficeEmailAmended(message.fromJson())
      "register.probationoffice.email.deleted" -> service.probationOfficeEmailDeleted(message.fromJson())
      "register.probationoffice.phone.inserted" -> service.probationOfficePhoneInserted(message.fromJson())
      "register.probationoffice.phone.amended" -> service.probationOfficePhoneAmended(message.fromJson())
      "register.probationoffice.phone.deleted" -> service.probationOfficePhoneDeleted(message.fromJson())
      "register.probationoffice.address.inserted" -> service.probationOfficeAddressInserted(message.fromJson())
      "register.probationoffice.address.amended" -> service.probationOfficeAddressAmended(message.fromJson())
      "register.probationoffice.address.deleted" -> service.probationOfficeAddressDeleted(message.fromJson())
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

data class PoliceCustodySuiteEvent(
  val eventType: String,
  override val additionalInformation: PoliceCustodySuiteAdditionalInformation,
) : SourcedEvent

data class PoliceCustodySuiteAdditionalInformation(
  val policeCustodySuiteId: String,
  override val source: String,
) : SourcedAdditionalData

data class PoliceCustodySuiteEmailEvent(
  val eventType: String,
  override val additionalInformation: PoliceCustodySuiteEmailAdditionalInformation,
) : SourcedEvent

data class PoliceCustodySuiteEmailAdditionalInformation(
  val policeCustodySuiteId: String,
  val emailId: Long,
  override val source: String,
) : SourcedAdditionalData

data class PoliceCustodySuitePhoneEvent(
  val eventType: String,
  override val additionalInformation: PoliceCustodySuitePhoneAdditionalInformation,
) : SourcedEvent

data class PoliceCustodySuitePhoneAdditionalInformation(
  val policeCustodySuiteId: String,
  val phoneId: Long,
  override val source: String,
) : SourcedAdditionalData

data class PoliceCustodySuiteAddressEvent(
  val eventType: String,
  override val additionalInformation: PoliceCustodySuiteAddressAdditionalInformation,
) : SourcedEvent

data class PoliceCustodySuiteAddressAdditionalInformation(
  val policeCustodySuiteId: String,
  val addressId: Long,
  override val source: String,
) : SourcedAdditionalData

data class ApprovedPremisesEvent(
  val eventType: String,
  override val additionalInformation: ApprovedPremisesAdditionalInformation,
) : SourcedEvent

data class ApprovedPremisesAdditionalInformation(
  val approvedPremisesId: String,
  override val source: String,
) : SourcedAdditionalData

data class ApprovedPremisesEmailEvent(
  val eventType: String,
  override val additionalInformation: ApprovedPremisesEmailAdditionalInformation,
) : SourcedEvent

data class ApprovedPremisesEmailAdditionalInformation(
  val approvedPremisesId: String,
  val emailId: Long,
  override val source: String,
) : SourcedAdditionalData

data class ApprovedPremisesPhoneEvent(
  val eventType: String,
  override val additionalInformation: ApprovedPremisesPhoneAdditionalInformation,
) : SourcedEvent

data class ApprovedPremisesPhoneAdditionalInformation(
  val approvedPremisesId: String,
  val phoneId: Long,
  override val source: String,
) : SourcedAdditionalData

data class ApprovedPremisesAddressEvent(
  val eventType: String,
  override val additionalInformation: ApprovedPremisesAddressAdditionalInformation,
) : SourcedEvent

data class ApprovedPremisesAddressAdditionalInformation(
  val approvedPremisesId: String,
  val addressId: Long,
  override val source: String,
) : SourcedAdditionalData

data class ProbationOfficeEvent(
  val eventType: String,
  override val additionalInformation: ProbationOfficeAdditionalInformation,
) : SourcedEvent

data class ProbationOfficeAdditionalInformation(
  val probationOfficeId: String,
  override val source: String,
) : SourcedAdditionalData

data class ProbationOfficeEmailEvent(
  val eventType: String,
  override val additionalInformation: ProbationOfficeEmailAdditionalInformation,
) : SourcedEvent

data class ProbationOfficeEmailAdditionalInformation(
  val probationOfficeId: String,
  val emailId: Long,
  override val source: String,
) : SourcedAdditionalData

data class ProbationOfficePhoneEvent(
  val eventType: String,
  override val additionalInformation: ProbationOfficePhoneAdditionalInformation,
) : SourcedEvent

data class ProbationOfficePhoneAdditionalInformation(
  val probationOfficeId: String,
  val phoneId: Long,
  override val source: String,
) : SourcedAdditionalData

data class ProbationOfficeAddressEvent(
  val eventType: String,
  override val additionalInformation: ProbationOfficeAddressAdditionalInformation,
) : SourcedEvent

data class ProbationOfficeAddressAdditionalInformation(
  val probationOfficeId: String,
  val addressId: Long,
  override val source: String,
) : SourcedAdditionalData
