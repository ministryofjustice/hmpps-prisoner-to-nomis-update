package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferScheduleMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.*

@Component
class TransferSchedulerMappingApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingServer.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

  fun stubGetTransferSchedulerPrisonerMappingIds(
    prisonerNumber: String = "A1234BC",
    bookingId: Long = 12345,
    nomisEventId: Long = 1,
    dpsTransferScheduleId: UUID? = UUID.randomUUID(),
    nomisMovementSeq: Int = 3,
    dpsMovementId: UUID = UUID.randomUUID(),
    nomisUnscheduledMovementSeq: Int = 1,
    dpsUnscheduledMovementId: UUID = UUID.randomUUID(),
    idMappings: TransferSchedulerPrisonerMappingIdsDto = transferSchedulerPrisonerIdMappings(prisonerNumber, bookingId, nomisEventId, dpsTransferScheduleId, nomisMovementSeq, dpsMovementId, nomisUnscheduledMovementSeq, dpsUnscheduledMovementId),
  ) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/transfer-scheduler/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(idMappings)),
      ),
    )
  }

  fun stubGetTransferSchedulerPrisonerMappingIds(
    prisonerNumber: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(
      status = status.value(),
    ),
  ) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/transfer-scheduler/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}

fun transferSchedulerPrisonerIdMappings(
  prisonerNumber: String = "A1234BC",
  bookingId: Long = 12345,
  nomisEventId: Long = 1,
  dpsTransferScheduleId: UUID? = UUID.randomUUID(),
  nomisMovementSeq: Int = 3,
  dpsMovementId: UUID = UUID.randomUUID(),
  nomisUnscheduledMovementSeq: Int = 1,
  dpsUnscheduledMovementId: UUID = UUID.randomUUID(),
) = TransferSchedulerPrisonerMappingIdsDto(
  prisonerNumber = prisonerNumber,
  schedules = listOfNotNull(
    dpsTransferScheduleId?.let {
      TransferScheduleMappingIdsDto(nomisEventId, dpsTransferScheduleId)
    },
  ),
  movements = listOf(
    TransferMovementMappingIdsDto(bookingId, nomisMovementSeq, dpsMovementId),
    TransferMovementMappingIdsDto(bookingId, nomisUnscheduledMovementSeq, dpsUnscheduledMovementId),
  ),
)
