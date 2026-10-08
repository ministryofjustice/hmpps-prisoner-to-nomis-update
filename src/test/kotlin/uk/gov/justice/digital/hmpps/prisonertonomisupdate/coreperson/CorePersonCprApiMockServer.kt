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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalAddressStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalAddressUsage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalContactType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalEthnicity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalIdentifiers
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalNationality
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalReligion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalSex
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalSexualOrientation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalTitle
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.DpsPrisonRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligionReadResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import java.time.LocalDate
import java.time.LocalDateTime

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

  fun stubGetCorePerson(prisonNumber: String = "AA1234A", response: DpsPrisonRecord = corePersonDto(), status: HttpStatus = HttpStatus.OK, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get("/person/prison/dps/$prisonNumber").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(if (status == HttpStatus.OK) response else error),
      ),
    )
  }

  fun stubGetReligion(
    prisonNumber: String,
    cprReligionId: String,
    response: PrisonReligionReadResponse = prisonReligionReadResponse(prisonNumber, cprReligionId),
  ) {
    stubFor(
      get("/syscon-sync/person/$prisonNumber/religion/$cprReligionId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(response),
      ),
    )
  }

  fun stubGetPrisonerContact(
    prisonNumber: String,
    cprContactId: String,
    response: PrisonContact = prisonerContact(prisonNumber),
  ) {
    stubFor(
      get("/syscon-sync/person/$prisonNumber/contact/$cprContactId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(response),
      ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(jsonMapper.writeValueAsString(body))
    return this
  }
}

fun prisonReligionReadResponse(prisonNumber: String, cprReligionId: String) = PrisonReligionReadResponse(
  prisonNumber = prisonNumber,
  religion = PrisonReligion(
    religionCode = PrisonReligion.ReligionCode.JEHV,
    changeReasonKnown = true,
    startDate = LocalDate.parse("2024-01-01"),
    current = true,
    createDateTime = LocalDateTime.parse("2025-02-03T10:20:30"),
    createUserId = "ME",
    comments = "Updated religion",
    cprReligionId = cprReligionId,
  ),
)

fun prisonerContact(prisonNumber: String) = PrisonContact(
  type = PrisonContact.Type.HOME,
  createDateTime = LocalDateTime.parse("2025-02-03T10:20:30"),
  createUserId = "ME",
  value = "01234567890",
  prisonNumber = prisonNumber,
)

fun canonicalAddress() = CanonicalAddress(
  cprAddressId = "ec4c7479-218c-4f11-a02d-edd749820679",
  status = CanonicalAddressStatus(),
  usages = listOf(),
  contacts = listOf(),
  noFixedAbode = false,
  startDate = "2020-02-26",
  endDate = "2023-07-15",
  postcode = "SW1H 9AJ",
  subBuildingName = "Sub building 2",
  buildingNumber = "102",
  thoroughfareName = "Petty France",
  dependentLocality = "Westminster",
  postTown = "London",
  county = "Greater London",
  countryCode = CanonicalAddress.CountryCode.GBR,
  comment = "Some comment",
)

fun canonicalAddressUsage(code: CanonicalAddressUsage.Code = CanonicalAddressUsage.Code.HOME, active: Boolean = true) = CanonicalAddressUsage(
  isActive = active,
  code = code,
  description = "${code.value} Description",
)

fun canonicalContact(value: String = "0114 555 5555", type: String = "HOME", extension: String? = null) = CanonicalContact(
  type = CanonicalContactType(code = type, description = "$type Description"),
  value = value,
  extension = extension,
)

fun corePersonDto(nationality: String? = null, religion: String? = null, addresses: List<CanonicalAddress> = listOf()) = DpsPrisonRecord(
  addresses = addresses,
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
    otherIdentifiers = listOf(),
  ),
  lastName = "Smith",
  middleNames = null,
  nationalities = if (nationality != null) listOf(CanonicalNationality(nationality, "$nationality Description")) else listOf(),
  religion = if (religion != null) {
    CanonicalReligion(CanonicalReligion.Code.valueOf(religion), "$religion Description")
  } else {
    CanonicalReligion(null, null)
  },
  sexualOrientation = CanonicalSexualOrientation(CanonicalSexualOrientation.Code.HET, "Hetrosexual Description"),
  sex = CanonicalSex(),
  title = CanonicalTitle(),
  religionHistory = if (religion != null) {
    listOf(
      PrisonReligion(
        religionCode = PrisonReligion.ReligionCode.valueOf(religion),
        religionDescription = "$religion Description",
        changeReasonKnown = false,
        startDate = LocalDate.parse("2024-01-01"),
        createDateTime = LocalDateTime.parse("2025-02-03T10:20:30"),
        createUserId = "ME",
        current = true,
      ),
    )
  } else {
    emptyList()
  },
  contacts = emptyList(),
)
