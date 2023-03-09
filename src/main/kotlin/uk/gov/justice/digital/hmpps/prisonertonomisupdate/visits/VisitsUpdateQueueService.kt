package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryQueueService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class VisitsUpdateQueueService(
  hmppsQueueService: HmppsQueueService,
  telemetryClient: TelemetryClient,
  objectMapper: ObjectMapper,
) :
  RetryQueueService(
    queueId = "visit",
    hmppsQueueService = hmppsQueueService,
    telemetryClient = telemetryClient,
    objectMapper = objectMapper,
  )

data class VisitMapping(val nomisId: String, val vsipId: String)
