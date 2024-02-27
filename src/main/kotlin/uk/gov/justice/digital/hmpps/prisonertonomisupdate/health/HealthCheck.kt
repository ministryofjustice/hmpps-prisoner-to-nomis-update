package uk.gov.justice.digital.hmpps.prisonertonomisupdate.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.ReactiveHealthPingCheck

@Component("nomisApi")
class NomisApiHealth(@Qualifier("nomisApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)

@Component("hmppsAuthApi")
class HmppsAuthApiHealth(@Qualifier("hmppsAuthApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)

@Component("mappingApi")
class MappingApiHealth(@Qualifier("mappingHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
