package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderBelief
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

  fun stubGetCorePersonReligions(
    prisonNumber: String = "AA1234A",
    response: List<OffenderBelief> = corePersonReligions(),
    fixedDelay: Int = 30,
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/$prisonNumber/religions")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error))
          .withFixedDelay(fixedDelay),
      ),
    )
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/$prisonNumber/reconciliation")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) corePerson(prisonNumber, response) else error))
          .withFixedDelay(fixedDelay),
      ),
    )
  }

  fun stubGetCorePersonForReconciliation(
    prisonNumber: String = "AA1234A",
    response: CorePerson = corePerson(prisonNumber = prisonNumber),
    fixedDelay: Int = 30,
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/$prisonNumber/reconciliation")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error))
          .withFixedDelay(fixedDelay),
      ),
    )
  }

  fun stubMergeCorePersonReligions(prisonNumber: String = "AA1234A") {
    nomisApi.stubFor(
      post(urlEqualTo("/core-person/$prisonNumber/merge")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubInsertReligion(prisonNumber: String, beliefId: Long = 12345L) {
    nomisApi.stubFor(
      post(urlEqualTo("/core-person/$prisonNumber/religion")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(beliefId.toString()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun corePersonAddress() = OffenderAddress(
  addressId = 1234,
  primaryAddress = true,
  mailAddress = true,
  createdDateTime = LocalDateTime.parse("2025-02-03T10:20:30"),
  createdByUsername = "ME",
  lastUpdatedDateTime = null,
  lastUpdatedByUsername = null,
  flat = "Sub building 2",
  premise = "102",
  street = "Petty France",
  locality = "Westminster",
  postcode = "SW1H 9AJ",
  city = CodeDescription(code = "765", description = "London"),
  county = CodeDescription(code = "GL", description = "Greater London"),
  country = CodeDescription(code = "GBR", description = "England"),
  noFixedAddress = false,
  comment = "Some comment",
  startDate = LocalDate.parse("2020-02-26"),
  endDate = LocalDate.parse("2023-07-15"),
  usages = listOf(),
)

fun corePerson(prisonNumber: String? = null, religion: String? = null, addresses: List<OffenderAddress>? = null): CorePerson = CorePerson(
  prisonNumber = prisonNumber ?: "A1234KT",
  activeFlag = true,
  inOutStatus = "IN",
  addresses = addresses,
  beliefs = if (religion != null) {
    listOf(
      OffenderBelief(
        beliefId = 1,
        belief = CodeDescription(code = religion, description = "$religion Description"),
        startDate = LocalDate.parse("2024-01-01"),
        audit = NomisAudit(createDatetime = LocalDateTime.parse("2025-02-03T10:20:30"), createUsername = "ME"),
      ),
    )
  } else {
    null
  },
)

private fun corePerson(prisonNumber: String, beliefs: List<OffenderBelief>): CorePerson = CorePerson(
  prisonNumber = prisonNumber,
  activeFlag = true,
  inOutStatus = "IN",
  beliefs = beliefs,
)

fun corePersonReligions(religion: String? = null): List<OffenderBelief> = if (religion != null) {
  listOf(
    OffenderBelief(
      beliefId = 1,
      belief = CodeDescription(code = religion, description = "$religion Description"),
      startDate = LocalDate.parse("2024-01-01"),
      audit = NomisAudit(createDatetime = LocalDateTime.parse("2025-02-03T10:20:30"), createUsername = "ME"),
    ),
  )
} else {
  listOf()
}
