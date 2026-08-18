package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.agencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.AgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.AgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.LegacyAgencyAddressDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.LegacyAgencyEmailDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.LegacyAgencyPhoneDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.LegacyAgencyType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody

class AgencyRegistersDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {

    @JvmField
    val agencyRegistersApi = AgencyRegistersDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = agencyRegistersApi.getRequestBody(pattern, jsonMapper)

    fun legacyAgencyDto() = LegacyAgencyDto(
      name = "Sheffield Crown Ct",
      active = true,
      addresses = listOf(legacyAgencyAddressDto()),
      emailAddresses = listOf(legacyAgencyEmailDto()),
      phoneNumbers = listOf(legacyAgencyPhoneDto()),
      description = "Sheffield Crown Court",
      inactiveDate = null,
      cjitCode = "C00SH00",
      areaCode = "52",
      regionCode = "YOHUM",
      courtTypeCode = "CC",
      agencyType = LegacyAgencyType.COURT,
      subareaCode = "SHEFF",
      geographicalAreaCode = "WYORKS",
      payrollRegionCode = "TODO",
      localAuthorityCode = "00CG",
      accessibleAccess = LegacyAgencyDto.AccessibleAccess.WHEELCHAIR_ACCESS,
      contact = "JANE SMITH",
    )

    fun legacyAgencyAddressDto() = LegacyAgencyAddressDto(
      addressLine1 = "Sheffield Combined Crt Centre, The Law Courts",
      addressLine2 = "50 West Bar",
      town = "Sheffield",
      county = "South Yorkshire",
      postcode = "S3 8PH",
      country = "England",
    )

    fun legacyAgencyEmailDto() = LegacyAgencyEmailDto(
      address = "sheffield.crown.court@test.com",
    )

    fun legacyAgencyPhoneDto() = LegacyAgencyPhoneDto(
      number = "0114 555 9898",
    )

    fun agencyIdsResponse() = AgencyIdsResponse(agencyIds = listOf(agencyId()))
    fun agencyId() = AgencyId("SHEFCC")
  }

  override fun beforeAll(context: ExtensionContext) {
    agencyRegistersApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    agencyRegistersApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    agencyRegistersApi.stop()
  }
}

class AgencyRegistersDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8109
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubGetAgency(agencyId: String, response: LegacyAgencyDto = legacyAgencyDto()) {
    stubFor(
      get("/legacy/reconciliation/$agencyId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(response))
          .withStatus(200),
      ),
    )
  }
  fun stubGetAgency(agencyId: String, errorHttpStatus: HttpStatus) {
    stubFor(
      get("/legacy/reconciliation/$agencyId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = errorHttpStatus.value())))
          .withStatus(errorHttpStatus.value()),
      ),
    )
  }
  fun stubGetAgencyIds(response: AgencyIdsResponse = agencyIdsResponse()) {
    stubFor(
      get("/legacy/reconciliation/ids/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(response))
          .withStatus(200),
      ),
    )
  }
}
