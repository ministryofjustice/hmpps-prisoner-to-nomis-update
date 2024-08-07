/**
 *
 * Please note:
 * This class was auto generated by OpenAPI Generator (https://openapi-generator.tech).
 *
 */

@file:Suppress(
  "ArrayInDataClass",
  "EnumEntryName",
  "RemoveRedundantQualifierName",
  "UnusedImport",
)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.dpsmodel

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Weight (in kilograms)
 *
 * @param lastModifiedAt Timestamp this field was last modified
 * @param lastModifiedBy Username of the user that last modified this field
 * @param `value` Value
 */

data class ValueWithMetadataInteger(

  /* Timestamp this field was last modified */
  @field:JsonProperty("lastModifiedAt")
  val lastModifiedAt: kotlin.String,

  /* Username of the user that last modified this field */
  @field:JsonProperty("lastModifiedBy")
  val lastModifiedBy: kotlin.String,

  /* Value */
  @field:JsonProperty("value")
  val `value`: kotlin.Int? = null,

)
