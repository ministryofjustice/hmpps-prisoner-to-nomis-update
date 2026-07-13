package uk.gov.justice.digital.hmpps.prisonertonomisupdate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication()
@ConfigurationPropertiesScan
class HmppsPrisonerToNomisUpdate

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerToNomisUpdate>(*args) { applicationStartup = BufferingApplicationStartup(10000) }
}
