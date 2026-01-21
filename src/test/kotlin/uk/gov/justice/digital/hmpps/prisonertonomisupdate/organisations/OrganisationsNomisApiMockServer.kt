package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateInternetAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateOrganisationType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporatePhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporatePhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateWebAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateWebAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateTypesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateWebAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.pageContent
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class OrganisationsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun createCorporateRequest(): CreateCorporateOrganisationRequest = CreateCorporateOrganisationRequest(
      id = 12345,
      name = "Some name",
      active = true,
    )

    fun updateCorporateRequest(): UpdateCorporateOrganisationRequest = UpdateCorporateOrganisationRequest(
      name = "Some name",
      active = true,
    )

    fun createCorporateWebAddressResponse(): CreateCorporateWebAddressResponse = CreateCorporateWebAddressResponse(
      id = 123456,
    )

    fun updateCorporateTypesRequest(): UpdateCorporateTypesRequest = UpdateCorporateTypesRequest(
      typeCodes = setOf("A type", "B type"),
    )

    fun createCorporateWebAddressRequest(): CreateCorporateWebAddressRequest = CreateCorporateWebAddressRequest(
      webAddress = "Some web address",
    )
    fun updateCorporateWebAddressRequest(): UpdateCorporateWebAddressRequest = UpdateCorporateWebAddressRequest(
      webAddress = "Some web address",
    )

    fun createCorporatePhoneResponse(): CreateCorporatePhoneResponse = CreateCorporatePhoneResponse(
      id = 123456,
    )

    fun createCorporatePhoneRequest(): CreateCorporatePhoneRequest = CreateCorporatePhoneRequest(
      number = "07973 555 5555",
      typeCode = "MOB",
    )
    fun updateCorporatePhoneRequest(): UpdateCorporatePhoneRequest = UpdateCorporatePhoneRequest(
      number = "07973 555 5555",
      typeCode = "MOB",
    )

    fun createCorporateEmailResponse(): CreateCorporateEmailResponse = CreateCorporateEmailResponse(
      id = 123456,
    )

    fun createCorporateEmailRequest(): CreateCorporateEmailRequest = CreateCorporateEmailRequest(
      email = "test@test.com",
    )

    fun updateCorporateEmailRequest(): UpdateCorporateEmailRequest = UpdateCorporateEmailRequest(
      email = "test@test.com",
    )
    fun createCorporateAddressResponse(): CreateCorporateAddressResponse = CreateCorporateAddressResponse(
      id = 123456,
    )

    fun createCorporateAddressRequest(): CreateCorporateAddressRequest = CreateCorporateAddressRequest(
      primaryAddress = true,
      mailAddress = true,
      noFixedAddress = false,
      startDate = LocalDate.parse("2021-01-01"),
      isServices = false,
    )

    fun updateCorporateAddressRequest(): UpdateCorporateAddressRequest = UpdateCorporateAddressRequest(
      primaryAddress = true,
      mailAddress = true,
      noFixedAddress = false,
      startDate = LocalDate.parse("2021-01-01"),
      isServices = false,
    )
  }
  fun stubGetCorporateOrganisation(
    corporateId: Long = 123456,
    corporate: CorporateOrganisation = corporateOrganisation(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/corporates/$corporateId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(corporate)),
      ),
    )
  }
  fun stubGetCorporateOrganisationIds(
    content: List<CorporateOrganisationIdResponse> = listOf(
      CorporateOrganisationIdResponse(123456),
    ),
    count: Long = content.size.toLong(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/corporates/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(pageCorporateIdResponse(count = count, content = content)),
      ),
    )
  }

  fun stubDeleteCorporateOrganisation(corporateId: Long = 123456) {
    nomisApi.stubFor(
      delete(urlEqualTo("/corporates/$corporateId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  fun pageCorporateIdResponse(
    count: Long,
    content: List<CorporateOrganisationIdResponse>,
  ) = pageContent(
    jsonMapper = jsonMapper,
    content = content,
    pageSize = 1L,
    pageNumber = 0L,
    totalElements = count,
    size = 1,
  )

  fun stubCreateCorporate() {
    nomisApi.stubFor(
      post(urlEqualTo("/corporates")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  fun stubUpdateCorporate(
    corporateId: Long = 123456,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/corporates/$corporateId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubDeleteCorporate(
    corporateId: Long = 123456,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/corporates/$corporateId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubUpdateCorporateTypes(
    corporateId: Long = 123456,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/corporates/$corporateId/type")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateCorporateAddress(
    corporateId: Long = 123456,
    response: CreateCorporateAddressResponse = createCorporateAddressResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/corporates/$corporateId/address")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubCreateCorporateWebAddress(
    corporateId: Long = 123456,
    response: CreateCorporateWebAddressResponse = createCorporateWebAddressResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/corporates/$corporateId/web-address")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubUpdateCorporateWebAddress(
    corporateId: Long = 123456,
    webAddressId: Long = 765443,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/corporates/$corporateId/web-address/$webAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
  fun stubDeleteCorporateWebAddress(
    corporateId: Long = 123456,
    webAddressId: Long = 765443,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/corporates/$corporateId/web-address/$webAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateCorporatePhone(
    corporateId: Long = 123456,
    response: CreateCorporatePhoneResponse = createCorporatePhoneResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/corporates/$corporateId/phone")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubUpdateCorporatePhone(
    corporateId: Long = 123456,
    phoneId: Long = 73737,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/corporates/$corporateId/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
  fun stubDeleteCorporatePhone(
    corporateId: Long = 123456,
    phoneId: Long = 73737,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/corporates/$corporateId/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateCorporateEmail(
    corporateId: Long = 123456,
    response: CreateCorporateEmailResponse = createCorporateEmailResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/corporates/$corporateId/email")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubUpdateCorporateEmail(
    corporateId: Long = 123456,
    emailId: Long = 765443,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/corporates/$corporateId/email/$emailId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
  fun stubDeleteCorporateEmail(
    corporateId: Long = 123456,
    emailId: Long = 765443,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/corporates/$corporateId/email/$emailId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubUpdateCorporateAddress(
    corporateId: Long = 123456,
    addressId: Long = 123456,
    response: UpdateCorporateAddressRequest = updateCorporateAddressRequest(),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/corporates/$corporateId/address/$addressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubDeleteCorporateAddress(
    corporateId: Long = 123456,
    addressId: Long = 123456,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/corporates/$corporateId/address/$addressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  } fun stubCreateCorporateAddressPhone(
    corporateId: Long = 123456,
    addressId: Long = 78990,
    response: CreateCorporatePhoneResponse = createCorporatePhoneResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/corporates/$corporateId/address/$addressId/phone")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubUpdateCorporateAddressPhone(
    corporateId: Long = 123456,
    addressId: Long = 78990,
    phoneId: Long = 73737,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/corporates/$corporateId/address/$addressId/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
  fun stubDeleteCorporateAddressPhone(
    corporateId: Long = 123456,
    phoneId: Long = 73737,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/corporates/$corporateId/address/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
}

fun corporateOrganisation(corporateId: Long = 123456): CorporateOrganisation = CorporateOrganisation(
  id = corporateId,
  name = "Boots",
  active = true,
  phoneNumbers = emptyList(),
  addresses = emptyList(),
  internetAddresses = emptyList(),
  types = emptyList(),
  audit = nomisAudit(),
)

fun CorporateOrganisation.withAddress(vararg address: CorporateAddress = arrayOf(corporateAddress())): CorporateOrganisation = copy(
  addresses = address.toList(),
)
fun corporateAddress(): CorporateAddress = CorporateAddress(
  id = 12345,
  phoneNumbers = emptyList(),
  comment = "nice area",
  validatedPAF = false,
  primaryAddress = true,
  mailAddress = true,
  noFixedAddress = false,
  type = CodeDescription("HOME", "Home Address"),
  flat = "Flat 1",
  premise = "Brown Court",
  locality = "Broomhill",
  street = "Broomhill Street",
  postcode = "S1 6GG",
  city = CodeDescription("12345", "Sheffield"),
  county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
  country = CodeDescription("GBR", "United Kingdom"),
  startDate = LocalDate.parse("2021-01-01"),
  endDate = LocalDate.parse("2025-01-01"),
  isServices = true,
  contactPersonName = "Bob Brown",
  businessHours = "10am to 10pm Monday to Friday",
  audit = NomisAudit(
    createUsername = "J.SPEAK",
    createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
    modifyUserId = "T.SMITH",
    modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
  ),
)
fun CorporateOrganisation.withPhone(vararg phone: CorporatePhoneNumber = arrayOf(corporatePhone())): CorporateOrganisation = copy(
  phoneNumbers = phone.toList(),
)
fun CorporateAddress.withPhone(vararg phone: CorporatePhoneNumber = arrayOf(corporatePhone())): CorporateAddress = copy(
  phoneNumbers = phone.toList(),
)
fun corporatePhone(): CorporatePhoneNumber = CorporatePhoneNumber(
  id = 12345,
  type = CodeDescription("HOME", "Home Address"),
  number = "0114 555 5555",
  extension = "ext 123",
  audit = NomisAudit(
    createUsername = "J.SPEAK",
    createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
    modifyUserId = "T.SMITH",
    modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
  ),
)
fun CorporateOrganisation.withInternetAddress(vararg internetAddress: CorporateInternetAddress): CorporateOrganisation = this.copy(
  internetAddresses = internetAddress.toList(),
)

fun corporateWebAddress(): CorporateInternetAddress = CorporateInternetAddress(
  id = 12345,
  internetAddress = "www.boots.gov.uk",
  type = "WEB",
  audit = NomisAudit(
    createUsername = "J.SPEAK",
    createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
    modifyUserId = "T.SMITH",
    modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
  ),
)
fun corporateEmail(): CorporateInternetAddress = CorporateInternetAddress(
  id = 12345,
  internetAddress = "jane@test.com",
  type = "EMAIL",
  audit = NomisAudit(
    createUsername = "J.SPEAK",
    createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
    modifyUserId = "T.SMITH",
    modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
  ),
)

fun corporateOrganisationType(type: String) = CorporateOrganisationType(
  type = CodeDescription(type, "$type description"),
  audit = nomisAudit(),
)

fun nomisAudit() = NomisAudit(
  createDatetime = LocalDateTime.now(),
  createUsername = "Q1251T",
)
