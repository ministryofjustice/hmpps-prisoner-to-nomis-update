package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class SQSMessage(val Type: String, val Message: String, val MessageId: String)
