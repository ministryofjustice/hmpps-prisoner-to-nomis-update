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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.courtDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.AgencyAddressDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.AgencyEmailDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.AgencyPhoneDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.CourtDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.ErrorResponse
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

    fun courtDto() = CourtDto(
      courtId = "SHEFCC",
      courtName = "Sheffield Crown Ct",
      active = true,
      addresses = listOf(agencyAddressDto()),
      emailAddresses = listOf(agencyEmailDto()),
      phoneNumbers = listOf(agencyPhoneDto()),
      description = "Sheffield Crown Court",
      inactiveDate = null,
      cjitCode = "C00SH00",
      area = CodeDescription("52", "South Yorkshire"),
      region = CodeDescription("YOHUM", "Yorkshire and Humber"),
      courtType = CodeDescription("CC", "Crown Court"),
    )

    fun agencyAddressDto() = AgencyAddressDto(
      id = 1,
      addressLine1 = "Sheffield Combined Crt Centre, The Law Courts",
      addressLine2 = "50 West Bar",
      town = "Sheffield",
      county = "South Yorkshire",
      postcode = "S3 8PH",
      country = "England",
    )

    fun agencyEmailDto() = AgencyEmailDto(
      id = 1,
      address = "sheffield.crown.court@test.com",
    )

    fun agencyPhoneDto() = AgencyPhoneDto(
      id = 1,
      number = "0114 555 9898",
    )
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

  fun stubGetCourt(agencyId: String, response: CourtDto = courtDto()) {
    stubFor(
      get("/courts/id/$agencyId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(response))
          .withStatus(200),
      ),
    )
  }
  fun stubGetCourt(agencyId: String, errorHttpStatus: HttpStatus) {
    stubFor(
      get("/courts/id/$agencyId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = errorHttpStatus.value())))
          .withStatus(errorHttpStatus.value()),
      ),
    )
  }
}
