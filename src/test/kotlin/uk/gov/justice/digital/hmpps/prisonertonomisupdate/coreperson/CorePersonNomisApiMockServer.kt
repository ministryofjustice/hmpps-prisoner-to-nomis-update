package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderNationality
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.String

@Component
class CorePersonNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetCorePerson(
    prisonNumber: String = "AA1234A",
    response: CorePerson = corePerson(prisonNumber = prisonNumber),
    fixedDelay: Int = 30,
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/${response.prisonNumber}")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error))
          .withFixedDelay(fixedDelay),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
fun corePerson(prisonNumber: String? = null, nationality: String? = null, religion: String? = null): CorePerson = CorePerson(
  prisonNumber = prisonNumber ?: "A1234KT",
  activeFlag = true,
  inOutStatus = "IN",
  nationalities = if (nationality != null) {
    listOf(
      OffenderNationality(
        bookingId = 1,
        nationality = CodeDescription(code = nationality, description = "$nationality Description"),
        latestBooking = true,
        startDateTime = LocalDateTime.now().minusDays(1),
      ),
    )
  } else {
    null
  },
  beliefs = if (religion != null) {
    listOf(
      OffenderBelief(
        beliefId = 1,
        belief = CodeDescription(code = religion, description = "$religion Description"),
        startDate = LocalDate.parse("2024-01-01"),
        verified = true,
        audit = NomisAudit(createDatetime = LocalDateTime.now(), createUsername = "ME"),
      ),
    )
  } else {
    null
  },
)
