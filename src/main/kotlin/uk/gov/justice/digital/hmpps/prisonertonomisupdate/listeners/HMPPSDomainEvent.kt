package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.IncentivesService

data class HMPPSDomainEvent(
  val eventType: String,
  val prisonerId: String? = null,
  val additionalInformation: IncentivesService.AdditionalInformation? = null,
)
