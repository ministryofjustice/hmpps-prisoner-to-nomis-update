package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalEthnicity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalIdentifiers
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalNationality
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalReligion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalSex
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalSexualOrientation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalTitle
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse

class CorePersonCprApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val corePersonCprApi = CorePersonCprApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    corePersonCprApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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

  fun stubGetCorePerson(prisonNumber: String = "AA1234A", response: CanonicalRecord = corePersonDto(), status: HttpStatus = HttpStatus.OK, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get("/person/prison/$prisonNumber").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
      ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(jsonMapper.writeValueAsString(body))
    return this
  }
}

fun corePersonDto(nationality: String? = null, religion: String? = null) = CanonicalRecord(
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
  nationalities = if (nationality != null) listOf(CanonicalNationality(nationality, "$nationality Description")) else listOf(),
  religion = CanonicalReligion(religion, if (religion != null) "$religion Description" else null),
  sexualOrientation = CanonicalSexualOrientation("HET", "Hetrosexual Description"),
  sex = CanonicalSex(),
  title = CanonicalTitle(),
)
