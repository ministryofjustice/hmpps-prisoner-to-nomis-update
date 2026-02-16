package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationTypes
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationWeb
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationAddressDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationAddressPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationEmailDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationTypeDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationWebAddressDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.PagedModelSyncOrganisationId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncAddressPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncOrganisationId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncOrganisationType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncTypesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncWebResponse
import java.time.LocalDate
import java.time.LocalDateTime

class OrganisationsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsOrganisationsServer = OrganisationsDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    fun organisation() = SyncOrganisationResponse(
      organisationId = 234324,
      organisationName = "Test organisation",
      caseloadId = "MDI",
      comments = "some comments",
      programmeNumber = "prog",
      vatNumber = "123 34",
      active = true,
      createdBy = "a user",
      createdTime = LocalDateTime.parse("2022-01-01T12:13"),
    )

    fun organisationTypes() = SyncTypesResponse(
      organisationId = 234324,
      types = listOf(
        SyncOrganisationType(
          "some type",
          createdBy = "a user",
          createdTime = LocalDateTime.parse("2022-01-01T12:13"),
        ),
      ),
    )

    fun organisationPhone() = SyncPhoneResponse(
      organisationPhoneId = 1234567,
      organisationId = 34535,
      phoneType = "SOME_TYPE",
      phoneNumber = "12345 234",
      createdBy = "a user",
      createdTime = LocalDateTime.parse("2022-01-01T12:13"),
    )

    fun organisationEmail() = SyncEmailResponse(
      organisationEmailId = 1234567,
      organisationId = 324524,
      emailAddress = "joe@joe.com",
      createdBy = "a user",
      createdTime = LocalDateTime.parse("2022-01-01T12:13"),
    )

    fun organisationWeb() = SyncWebResponse(
      organisationWebAddressId = 1234567,
      organisationId = 234324,
      webAddress = "web address",
      createdBy = "a user",
      createdTime = LocalDateTime.parse("2022-01-01T12:13"),
    )

    fun organisationAddress() = SyncAddressResponse(
      organisationAddressId = 1234567,
      organisationId = 234324,
      primaryAddress = false,
      mailAddress = true,
      serviceAddress = false,
      noFixedAddress = false,
      addressType = "aType",
      flat = "4a",
      `property` = "something",
      street = "where",
      area = "an area",
      cityCode = "a city",
      countyCode = "a country",
      postcode = "S1 4UP",
      countryCode = "UK",
      specialNeedsCode = "needs",
      contactPersonName = "Joe Bloggs",
      businessHours = "9-5",
      comments = "some comments",
      startDate = LocalDate.parse("2022-01-01"),
      createdBy = "a user",
      createdTime = LocalDateTime.parse("2022-01-01T12:13"),
    )

    fun organisationAddressPhone() = SyncAddressPhoneResponse(
      organisationAddressPhoneId = 1234567,
      organisationAddressId = 12346,
      organisationPhoneId = 12345,
      organisationId = 34535,
      phoneType = "SOME_TYPE",
      phoneNumber = "12345 234",
      createdBy = "a user",
      createdTime = LocalDateTime.parse("2022-01-01T12:13"),
    )
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsOrganisationsServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsOrganisationsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsOrganisationsServer.stop()
  }
}

class OrganisationsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8100

    fun organisationDetails() = OrganisationDetails(
      organisationId = 12345,
      organisationName = "Boots",
      active = true,
      organisationTypes = emptyList(),
      phoneNumbers = emptyList(),
      emailAddresses = emptyList(),
      webAddresses = emptyList(),
      addresses = emptyList(),
      createdBy = "A.TEST",
      createdTime = LocalDateTime.now().minusDays(1),
    )

    fun organisationAddressDetails() = OrganisationAddressDetails(
      organisationId = 1,
      organisationAddressId = 12345,
      phoneNumbers = emptyList(),
      comments = "nice area",
      primaryAddress = true,
      mailAddress = true,
      noFixedAddress = false,
      addressType = "HOME",
      flat = "Flat 1",
      property = "Brown Court",
      area = "Broomhill",
      street = "Broomhill Street",
      postcode = "S1 6GG",
      cityCode = "12345",
      cityDescription = "Sheffield",
      countyCode = "S.YORKSHIRE",
      countyDescription = "South Yorkshire",
      countryCode = "GBR",
      countryDescription = "United Kingdom",
      startDate = LocalDate.parse("2021-01-01"),
      endDate = LocalDate.parse("2025-01-01"),
      serviceAddress = true,
      contactPersonName = "Bob Brown",
      businessHours = "10am to 10pm Monday to Friday",
      createdBy = "j.smith",
      createdTime = LocalDateTime.now(),
    )

    fun organisationTypeDetails(type: String) = OrganisationTypeDetails(
      organisationId = 123,
      organisationType = type,
      organisationTypeDescription = "Description for $type",
      createdBy = "j.smith",
      createdTime = LocalDateTime.now(),
    )

    fun organisationPhoneDetails() = OrganisationPhoneDetails(
      organisationPhoneId = 1234,
      organisationId = 43221,
      phoneType = "MOB",
      phoneTypeDescription = "Mobile",
      phoneNumber = "0114 2222 2222",
      createdBy = "j.smith",
      createdTime = LocalDateTime.now(),
    )
    fun organisationAddressPhoneDetails() = OrganisationAddressPhoneDetails(
      organisationAddressPhoneId = 12346,
      organisationPhoneId = 1234,
      organisationId = 43221,
      organisationAddressId = 87665,
      phoneType = "MOB",
      phoneTypeDescription = "Mobile",
      phoneNumber = "0114 2222 2222",
      createdBy = "j.smith",
      createdTime = LocalDateTime.now(),
    )
    fun organisationEmailDetails() = OrganisationEmailDetails(
      organisationEmailId = 1234,
      organisationId = 43221,
      emailAddress = "test@email.com",
      createdBy = "j.smith",
      createdTime = LocalDateTime.now(),
    )
    fun organisationWebAddressDetails() = OrganisationWebAddressDetails(
      organisationWebAddressId = 1234,
      organisationId = 43221,
      webAddress = "www.internet.com",
      createdBy = "j.smith",
      createdTime = LocalDateTime.now(),
    )
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

  fun stubGetOrganisation(organisationId: Long, organisation: OrganisationDetails = organisationDetails()) {
    stubFor(
      get("/organisation/$organisationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(organisation))
          .withStatus(200),
      ),
    )
  }
  fun stubGetOrganisation(organisationId: Long, httpStatus: HttpStatusCode) {
    stubFor(
      get("/organisation/$organisationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("{}")
          .withStatus(httpStatus.value()),
      ),
    )
  }

  fun stubGetOrganisationIds(
    content: List<SyncOrganisationId> = listOf(
      SyncOrganisationId(123456),
    ),
    count: Long = content.size.toLong(),
  ) {
    stubFor(
      get(urlPathEqualTo("/sync/organisations/reconcile")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(pageOrganisationIdResponse(count = count, content = content)),
      ),
    )
  }

  fun stubGetSyncOrganisation(organisationId: Long, response: SyncOrganisationResponse = organisation()) {
    stubFor(
      get("/sync/organisation/$organisationId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetSyncOrganisationTypes(organisationId: Long, response: SyncTypesResponse = organisationTypes()) {
    stubFor(
      get("/sync/organisation-types/$organisationId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetSyncOrganisationWeb(organisationWebId: Long, response: SyncWebResponse = organisationWeb()) {
    stubFor(
      get("/sync/organisation-web/$organisationWebId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetSyncOrganisationAddress(organisationAddressId: Long, response: SyncAddressResponse = organisationAddress()) {
    stubFor(
      get("/sync/organisation-address/$organisationAddressId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetSyncOrganisationEmail(organisationEmailId: Long, response: SyncEmailResponse = organisationEmail()) {
    stubFor(
      get("/sync/organisation-email/$organisationEmailId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetSyncOrganisationPhone(organisationPhoneId: Long, response: SyncPhoneResponse = organisationPhone()) {
    stubFor(
      get("/sync/organisation-phone/$organisationPhoneId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetSyncOrganisationAddressPhone(organisationAddressPhoneId: Long, response: SyncAddressPhoneResponse = organisationAddressPhone()) {
    stubFor(
      get("/sync/organisation-address-phone/$organisationAddressPhoneId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun pageOrganisationIdResponse(
    count: Long,
    content: List<SyncOrganisationId>,
  ): String = jsonMapper.writeValueAsString(
    PagedModelSyncOrganisationId(
      content = content,
      page = pageMetadata(count),
    ),
  )

  fun pageMetadata(
    count: Long,
  ) = PageMetadata(
    propertySize = 1L,
    number = 0L,
    totalElements = count,
    totalPages = 1,
  )
}
