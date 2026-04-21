package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.QueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryQueueService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class TapRetryQueueService(
  hmppsQueueService: HmppsQueueService,
  telemetryClient: TelemetryClient,
  queueService: QueueService,
) : RetryQueueService(
  queueId = "externalmovements",
  hmppsQueueService = hmppsQueueService,
  telemetryClient = telemetryClient,
  queueService = queueService,
)
