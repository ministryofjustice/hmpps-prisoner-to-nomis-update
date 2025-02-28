package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationAddressDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationEmailDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationTypeDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationWebAddressDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncOrganisationId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.pageContent
import java.time.LocalDate
import java.time.LocalDateTime

class OrganisationsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsOrganisationsServer = OrganisationsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsOrganisationsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
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
          .withBody(objectMapper.writeValueAsString(organisation))
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

  fun pageOrganisationIdResponse(
    count: Long,
    content: List<SyncOrganisationId>,
  ) = pageContent(
    objectMapper = objectMapper,
    content = content,
    pageSize = 1L,
    pageNumber = 0L,
    totalElements = count,
    size = 1,
  )
}
