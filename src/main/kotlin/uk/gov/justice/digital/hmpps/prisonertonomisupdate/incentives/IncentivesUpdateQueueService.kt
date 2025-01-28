package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryQueueService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class IncentivesUpdateQueueService(
  hmppsQueueService: HmppsQueueService,
  telemetryClient: TelemetryClient,
  objectMapper: ObjectMapper,
) : RetryQueueService(
  queueId = "incentive",
  hmppsQueueService = hmppsQueueService,
  telemetryClient = telemetryClient,
  objectMapper = objectMapper,
)

data class IncentiveMapping(val nomisBookingId: Long, val nomisIncentiveSequence: Int, val incentiveId: Long)
