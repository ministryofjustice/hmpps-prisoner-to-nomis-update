package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.QueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryQueueService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class ExternalMovementsRetryQueueService(
  hmppsQueueService: HmppsQueueService,
  telemetryClient: TelemetryClient,
  queueService: QueueService,
) : RetryQueueService(
  queueId = "movements",
  hmppsQueueService = hmppsQueueService,
  telemetryClient = telemetryClient,
  queueService = queueService,
)
