package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerDetails
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

  fun stubGetPrisonerDetails(prisonNumber: String, rootOffenderId: Long = 12345L) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            jsonMapper.writeValueAsString(
              PrisonerDetails(
                offenderNo = prisonNumber,
                offenderId = rootOffenderId + 1,
                bookingId = 1,
                location = "MDI",
                active = true,
                rootOffenderId = rootOffenderId,
              ),
            ),
          ),
      ),
    )
  }

  fun stubCreateOffenderPhone(offenderId: Long, phoneId: Long = 54321L) {
    nomisApi.stubFor(
      post(urlEqualTo("/core-person/$offenderId/phone")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(jsonMapper.writeValueAsString(CreateOffenderPhoneResponse(phoneId = phoneId))),
      ),
    )
  }

  fun stubCreateOffenderEmail(offenderId: Long, emailAddressId: Long = 54321L) {
    nomisApi.stubFor(
      post(urlEqualTo("/core-person/$offenderId/email")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(jsonMapper.writeValueAsString(CreateOffenderEmailResponse(emailAddressId = emailAddressId))),
      ),
    )
  }

  fun stubUpdateOffenderPhone(offenderId: Long, phoneId: Long) {
    nomisApi.stubFor(
      put(urlEqualTo("/core-person/$offenderId/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubUpdateOffenderEmail(offenderId: Long, emailAddressId: Long) {
    nomisApi.stubFor(
      put(urlEqualTo("/core-person/$offenderId/email/$emailAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
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

fun corePersonAddressUsage(code: String = "HOME", active: Boolean = true) = OffenderAddressUsage(
  addressId = 1234,
  usage = code,
  active = active,
  createdDateTime = LocalDateTime.parse("2025-02-03T10:20:30"),
  createdByUsername = "ME",
  lastUpdatedDateTime = null,
  lastUpdatedByUsername = null,
)

fun corePersonAddressPhone(number: String = "0114 555 5555", type: String = "HOME", extension: String? = null) = OffenderPhoneNumber(
  phoneId = 1,
  number = number,
  type = CodeDescription(code = type, description = "$type Description"),
  extension = extension,
  createdDateTime = LocalDateTime.parse("2025-02-03T10:20:30"),
  createdByUsername = "ME",
  lastUpdatedDateTime = null,
  lastUpdatedByUsername = null,
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
