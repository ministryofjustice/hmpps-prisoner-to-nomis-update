package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "feature.court-scheduler")
class CourtSchedulerFeatureSwitches(
  val ignoreAllSentencingEvents: Boolean = false,
)
