package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryQueueService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class CaseNotesRetryQueueService(
  hmppsQueueService: HmppsQueueService,
  telemetryClient: TelemetryClient,
  objectMapper: ObjectMapper,
) :
  RetryQueueService(
    queueId = "casenotes",
    hmppsQueueService = hmppsQueueService,
    telemetryClient = telemetryClient,
    objectMapper = objectMapper,
  )
