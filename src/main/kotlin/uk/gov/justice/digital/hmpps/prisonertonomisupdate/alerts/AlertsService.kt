package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class AlertsService : CreateMappingRetryable {
  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }

  fun createAlert(alertEvent: AlertEvent) {
    TODO("Not yet implemented")
  }

  fun updateAlert(alertEvent: AlertEvent) {
    TODO("Not yet implemented")
  }

  fun deleteAlert(alertEvent: AlertEvent) {
    TODO("Not yet implemented")
  }
}

data class AlertEvent(val description: String?)
