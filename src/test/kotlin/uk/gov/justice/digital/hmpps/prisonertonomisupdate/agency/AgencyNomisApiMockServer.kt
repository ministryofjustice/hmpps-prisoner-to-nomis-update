package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyEmailAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class AgencyNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {

    fun agencyResponse() = AgencyResponse(
      agencyId = "SHEFCC",
      description = "Sheffield Crown Court",
      type = CodeDescription(code = "CRT", description = "Court"),
      active = true,
      updateAllowed = true,
      localAuthorities = emptyList(),
      addresses = listOf(agencyAddress()),
      phones = listOf(),
      emailAddresses = listOf(agencyEmailAddress()),
      longDescription = "Sheffield Crown Court",
      district = null,
      deactivationDate = null,
      contactName = null,
      courtType = CodeDescription(code = "CC", description = "Crown Court"),
      disabilityAccessCode = null,
      area = CodeDescription(code = "52", description = "South Yorkshire"),
      subArea = null,
      region = CodeDescription(code = "YOHUM", description = "Yorkshire and Humberside"),
      nomsRegion = null,
      payrollRegion = null,
      cjitCode = "C00SH00",
    )

    fun agencyAddress() = AgencyAddress(
      id = 1,
      phoneNumbers = listOf(agencyPhoneNumber()),
      validatedPAF = false,
      primaryAddress = true,
      mailAddress = true,
      type = CodeDescription(code = "BUS", description = "Business Address"),
      flat = null,
      premise = "Sheffield Combined Crt Centre",
      street = "The Law Courts",
      locality = "50 West Bar",
      postcode = "S3 8PH",
      city = CodeDescription(code = "SHEFF", description = "Sheffield"),
      county = CodeDescription(code = "S.YORKSHIRE", description = "South Yorkshire"),
      country = CodeDescription(code = "ENG", description = "England"),
      noFixedAddress = false,
      comment = null,
      startDate = null,
      endDate = null,
    )

    fun agencyPhoneNumber() = AgencyPhoneNumber(
      id = 1,
      number = "0114 555 9898",
      type = CodeDescription(code = "BUS", description = "Business"),
      extension = null,
    )

    fun agencyEmailAddress() = AgencyEmailAddress(
      id = 1,
      emailAddress = "sheffield.crown.court@test.com",
    )

    fun agencyId() = AgencyId(
      agencyId = "SHEFCC",
    )
    fun agencyIdsResponse() = AgencyIdsResponse(
      agencyIds = listOf(agencyId()),
    )
  }

  fun stubGetAgency(
    agencyId: String = "MDI",
    response: AgencyResponse = agencyResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/agency/$agencyId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetAgency(
    agencyId: String = "MDI",
    errorHttpStatus: HttpStatus,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/agency/$agencyId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = errorHttpStatus.value())))
          .withStatus(errorHttpStatus.value()),
      ),
    )
  }
  fun stubGetAgencyIds(
    response: AgencyIdsResponse = agencyIdsResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/agency/ids/all")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
