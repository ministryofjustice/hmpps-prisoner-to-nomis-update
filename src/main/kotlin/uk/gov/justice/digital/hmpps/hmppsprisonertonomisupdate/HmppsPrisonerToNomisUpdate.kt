package uk.gov.justice.digital.hmpps.hmppsprisonertonomisupdate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication()
class HmppsPrisonerToNomisUpdate

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerToNomisUpdate>(*args)
}
