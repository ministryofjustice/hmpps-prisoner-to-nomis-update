package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryQueueService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class OrganisationsRetryQueueService(
  hmppsQueueService: HmppsQueueService,
  telemetryClient: TelemetryClient,
  objectMapper: ObjectMapper,
) : RetryQueueService(
  queueId = "organisations",
  hmppsQueueService = hmppsQueueService,
  telemetryClient = telemetryClient,
  objectMapper = objectMapper,
)
