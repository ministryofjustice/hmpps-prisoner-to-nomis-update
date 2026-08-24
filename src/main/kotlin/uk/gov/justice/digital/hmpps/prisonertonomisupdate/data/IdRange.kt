package uk.gov.justice.digital.hmpps.prisonertonomisupdate.data

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Nomis PK ID range.")
data class IdRange(
  @Schema(description = "The lowest NOMIS id in the range", example = "123456789")
  val fromId: Long,
  @Schema(description = "The highest NOMIS id in the range", example = "987654321")
  val toId: Long,
)
