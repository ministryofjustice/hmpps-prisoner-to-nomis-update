package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

abstract class SynchronisationService(internal val objectMapper: ObjectMapper) {
  abstract suspend fun retryCreateMapping(message: String)

  internal inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}
