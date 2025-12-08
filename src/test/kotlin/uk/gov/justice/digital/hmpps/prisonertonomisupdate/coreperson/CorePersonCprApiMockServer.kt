package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalEthnicity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalIdentifiers
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalReligion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalSex
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalTitle

class CorePersonCprApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val corePersonCprApi = CorePersonCprApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    corePersonCprApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    corePersonCprApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    corePersonCprApi.stop()
  }
}

class CorePersonCprApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8103
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

  fun stubGetCorePerson(personNumber: String = "AA1234A", response: CanonicalRecord = corePersonDto()) {
    stubFor(
      get("/person/prison/$personNumber").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(objectMapper.writeValueAsString(body))
    return this
  }
}

fun corePersonDto() = CanonicalRecord(
  addresses = listOf(),
  aliases = listOf(),
  dateOfBirth = null,
  ethnicity = CanonicalEthnicity(),
  firstName = "John",
  identifiers = CanonicalIdentifiers(
    crns = listOf(),
    prisonNumbers = listOf(),
    defendantIds = listOf(),
    cids = listOf(),
    pncs = listOf(),
    cros = listOf(),
    nationalInsuranceNumbers = listOf(),
    driverLicenseNumbers = listOf(),
    arrestSummonsNumbers = listOf(),
  ),
  lastName = "Smith",
  middleNames = null,
  nationalities = listOf(),
  religion = CanonicalReligion(),
  sex = CanonicalSex(),
  title = CanonicalTitle(),
)
