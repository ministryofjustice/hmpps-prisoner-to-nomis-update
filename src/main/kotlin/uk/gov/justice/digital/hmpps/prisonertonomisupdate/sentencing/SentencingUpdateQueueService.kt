package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateQueueService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class SentencingUpdateQueueService<MAPPING>(
  hmppsQueueService: HmppsQueueService,
  telemetryClient: TelemetryClient,
  objectMapper: ObjectMapper,
) : UpdateQueueService<MAPPING>(
  hmppsQueueService,
  telemetryClient = telemetryClient,
  objectMapper = objectMapper,
  name = "sentencing-adjustment",
  queueId = "sentencing"
)
