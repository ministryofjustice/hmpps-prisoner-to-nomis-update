package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class EventFeatureSwitch(private val environment: Environment) {
  fun isEnabled(eventType: String): Boolean = environment.getProperty("feature.event.$eventType", Boolean::class.java, true)
}
