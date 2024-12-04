package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class ContactPersonNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    fun createPersonResponse(): CreatePersonResponse = CreatePersonResponse(
      personId = 123456,
    )

    fun createPersonRequest(): CreatePersonRequest = CreatePersonRequest(
      firstName = "John",
      lastName = "Smith",
      interpreterRequired = false,
    )

    fun createPersonContactResponse(): CreatePersonContactResponse = CreatePersonContactResponse(
      personContactId = 123456,
    )

    fun createPersonContactRequest(): CreatePersonContactRequest = CreatePersonContactRequest(
      offenderNo = "A1234KT",
      contactTypeCode = "S",
      relationshipTypeCode = "BRO",
      active = true,
      expiryDate = null,
      approvedVisitor = false,
      nextOfKin = false,
      emergencyContact = false,
      comment = "Best friends",
    )

    fun createPersonAddressResponse(): CreatePersonAddressResponse = CreatePersonAddressResponse(
      personAddressId = 123456,
    )

    fun createPersonAddressRequest(): CreatePersonAddressRequest = CreatePersonAddressRequest(
      primaryAddress = true,
      mailAddress = true,
    )

    fun createPersonEmailResponse(): CreatePersonEmailResponse = CreatePersonEmailResponse(
      emailAddressId = 123456,
    )

    fun createPersonEmailRequest(): CreatePersonEmailRequest = CreatePersonEmailRequest(
      email = "test@test.com",
    )

    fun createPersonPhoneResponse(): CreatePersonPhoneResponse = CreatePersonPhoneResponse(
      phoneId = 123456,
    )

    fun createPersonPhoneRequest(): CreatePersonPhoneRequest = CreatePersonPhoneRequest(
      number = "07973 555 5555",
      typeCode = "MOB",
    )
    fun createPersonIdentifierResponse(): CreatePersonIdentifierResponse = CreatePersonIdentifierResponse(
      sequence = 4,
    )

    fun createPersonIdentifierRequest(): CreatePersonIdentifierRequest = CreatePersonIdentifierRequest(
      identifier = "SMNI772727DL",
      typeCode = "DL",
      issuedAuthority = "DVLA",
    )
  }

  fun stubCreatePerson(
    response: CreatePersonResponse = createPersonResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubCreatePersonContact(
    personId: Long = 123456,
    response: CreatePersonContactResponse = createPersonContactResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/contact")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubCreatePersonAddress(
    personId: Long = 123456,
    response: CreatePersonAddressResponse = createPersonAddressResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/address")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubCreatePersonEmail(
    personId: Long = 123456,
    response: CreatePersonEmailResponse = createPersonEmailResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/email")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubCreatePersonPhone(
    personId: Long = 123456,
    response: CreatePersonPhoneResponse = createPersonPhoneResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/phone")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubCreatePersonAddressPhone(
    personId: Long = 123456,
    addressId: Long = 78990,
    response: CreatePersonPhoneResponse = createPersonPhoneResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/address/$addressId/phone")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubCreatePersonIdentifier(
    personId: Long = 123456,
    response: CreatePersonIdentifierResponse = createPersonIdentifierResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/identifier")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
