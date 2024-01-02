package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("HelperApi")

suspend fun <T> doApiCallWithSingleRetry(api: suspend () -> T) = runCatching {
  api()
}.onFailure {
  log.warn("Retrying API call", it)
  api()
}.getOrThrow()
