package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("HelperApi")

suspend fun <T> doApiCallWithRetries(api: suspend () -> T) = runCatching {
  api()
}.recoverCatching {
  log.warn("Retrying API call 1", it)
  api()
}.recoverCatching {
  log.warn("Retrying API call 2", it)
  api()
}.getOrThrow()
