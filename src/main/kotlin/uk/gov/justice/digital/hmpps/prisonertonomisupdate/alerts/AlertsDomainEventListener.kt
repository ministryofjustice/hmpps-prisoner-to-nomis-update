package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "false", matchIfMissing = true)
@Service
class AlertsDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val alertsService: AlertsService,
  private val alertsReferenceDataService: AlertsReferenceDataService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = alertsService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("alerts", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "person.alert.created" -> alertsService.createAlert(message.fromJson())

      "person.alert.updated" -> alertsService.updateAlert(message.fromJson())

      "person.alert.deleted" -> alertsService.deleteAlert(message.fromJson())

      "prisoner-alerts.alert-code-created" ->
        alertsReferenceDataService.createAlertCode(message.fromJson())

      "prisoner-alerts.alert-code-deactivated" ->
        alertsReferenceDataService.deactivatingAlertCode(message.fromJson())

      "prisoner-alerts.alert-code-reactivated" ->
        alertsReferenceDataService.reactivatingAlertCode(message.fromJson())

      "prisoner-alerts.alert-code-updated" ->
        alertsReferenceDataService.updateAlertCode(message.fromJson())

      "prisoner-alerts.alert-type-created" ->
        alertsReferenceDataService.createAlertType(message.fromJson())

      "prisoner-alerts.alert-type-deactivated" ->
        alertsReferenceDataService.deactivatingAlertType(message.fromJson())

      "prisoner-alerts.alert-type-reactivated" ->
        alertsReferenceDataService.reactivatingAlertType(message.fromJson())

      "prisoner-alerts.alert-type-updated" ->
        alertsReferenceDataService.updateAlertType(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
