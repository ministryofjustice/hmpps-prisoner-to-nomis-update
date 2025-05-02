package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * TelemetryClient gets altered at runtime by the java agent and so is a no-op otherwise
 */
@Configuration
class ApplicationInsightsConfiguration {
  @Bean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}

fun TelemetryClient.trackEvent(name: String, properties: Map<String, Any>) = this.trackEvent(name, properties.valuesAsStrings(), null)

fun Map<String, Any>.valuesAsStrings(): Map<String, String> = this.entries.associate { it.key to it.value.toString() }

fun telemetryOf(vararg pairs: Pair<String, Any>): MutableMap<String, Any> = mutableMapOf(*pairs)
