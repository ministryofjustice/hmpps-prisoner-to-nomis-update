package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class EventFeatureSwitch(private val environment: Environment) {
  fun isEnabled(eventType: String, domain: String? = null): Boolean = isEnabled("feature.event.$eventType") &&
    (domain == null || isEnabled("feature.event.$domain.$eventType"))

  private fun isEnabled(property: String): Boolean = environment.getProperty(property, Boolean::class.java, true)
}
