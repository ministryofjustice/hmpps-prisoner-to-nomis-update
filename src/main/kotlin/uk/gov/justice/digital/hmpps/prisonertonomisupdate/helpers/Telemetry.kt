package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import com.microsoft.applicationinsights.TelemetryClient
import kotlin.collections.associate
import kotlin.to

fun TelemetryClient.trackEvent(name: String, properties: Map<String, Any>) = this.trackEvent(
  name,
  properties.valuesAsStrings(),
  null,
)

fun Map<String, Any>.valuesAsStrings(): Map<String, String> = this.entries.associate { it.key to it.value.toString() }

fun telemetryOf(vararg pairs: Pair<String, Any>): MutableMap<String, Any> = mutableMapOf(*pairs)
