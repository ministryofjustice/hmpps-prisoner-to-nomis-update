package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateContactPersonRestrictionResponse
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

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

    fun updatePersonRequest(): UpdatePersonRequest = UpdatePersonRequest(
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

    fun updatePersonContactRequest(): UpdatePersonContactRequest = UpdatePersonContactRequest(
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

    fun updatePersonAddressRequest(): UpdatePersonAddressRequest = UpdatePersonAddressRequest(
      primaryAddress = true,
      mailAddress = true,
    )

    fun createPersonEmailResponse(): CreatePersonEmailResponse = CreatePersonEmailResponse(
      emailAddressId = 123456,
    )

    fun createPersonEmailRequest(): CreatePersonEmailRequest = CreatePersonEmailRequest(
      email = "test@test.com",
    )

    fun updatePersonEmailRequest(): UpdatePersonEmailRequest = UpdatePersonEmailRequest(
      email = "test@test.com",
    )

    fun createPersonPhoneResponse(): CreatePersonPhoneResponse = CreatePersonPhoneResponse(
      phoneId = 123456,
    )

    fun createPersonPhoneRequest(): CreatePersonPhoneRequest = CreatePersonPhoneRequest(
      number = "07973 555 5555",
      typeCode = "MOB",
    )
    fun updatePersonPhoneRequest(): UpdatePersonPhoneRequest = UpdatePersonPhoneRequest(
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

    fun updatePersonIdentifierRequest(): UpdatePersonIdentifierRequest = UpdatePersonIdentifierRequest(
      identifier = "SMNI772727DL",
      typeCode = "DL",
      issuedAuthority = "DVLA",
    )

    fun createContactPersonRestrictionResponse(): CreateContactPersonRestrictionResponse = CreateContactPersonRestrictionResponse(
      id = 12345,
    )

    fun createContactPersonRestrictionRequest(): CreateContactPersonRestrictionRequest = CreateContactPersonRestrictionRequest(
      comment = "Banned for life",
      typeCode = "BAN",
      enteredStaffUsername = "j.much",
      effectiveDate = LocalDate.parse("2020-01-01"),
      expiryDate = LocalDate.parse("2026-01-01"),
    )
    fun updateContactPersonRestrictionRequest(): UpdateContactPersonRestrictionRequest = UpdateContactPersonRestrictionRequest(
      comment = "Banned for life",
      typeCode = "BAN",
      enteredStaffUsername = "j.much",
      effectiveDate = LocalDate.parse("2020-01-01"),
      expiryDate = LocalDate.parse("2026-01-01"),
    )
  }

  fun stubCreatePerson(
    response: CreatePersonResponse = createPersonResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubUpdatePerson(
    personId: Long = 123456,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubDeletePerson(
    personId: Long = 123456,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/persons/$personId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
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

  fun stubUpdatePersonContact(
    personId: Long = 123456,
    contactId: Long = 7373737,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/contact/$contactId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
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

  fun stubUpdatePersonAddress(
    personId: Long = 123456,
    addressId: Long = 123456,
    response: UpdatePersonAddressRequest = updatePersonAddressRequest(),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/address/$addressId")).willReturn(
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

  fun stubUpdatePersonEmail(
    personId: Long = 123456,
    emailId: Long = 765443,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/email/$emailId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
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
  fun stubUpdatePersonPhone(
    personId: Long = 123456,
    phoneId: Long = 73737,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
  fun stubDeletePersonPhone(
    personId: Long = 123456,
    phoneId: Long = 73737,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/persons/$personId/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
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
  fun stubUpdatePersonAddressPhone(
    personId: Long = 123456,
    addressId: Long = 78990,
    phoneId: Long = 73737,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/address/$addressId/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
  fun stubDeletePersonAddressPhone(
    personId: Long = 123456,
    addressId: Long = 78990,
    phoneId: Long = 73737,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/persons/$personId/address/$addressId/phone/$phoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
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

  fun stubUpdatePersonIdentifier(
    personId: Long = 123456,
    sequence: Long = 4,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/identifier/$sequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreatePersonRestriction(
    personId: Long = 123456,
    response: CreateContactPersonRestrictionResponse = createContactPersonRestrictionResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/restriction")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubUpdatePersonRestriction(
    personId: Long = 123456,
    personRestrictionId: Long = 543678,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/restriction/$personRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
  fun stubCreateContactRestriction(
    personId: Long = 123456,
    contactId: Long = 678990,
    response: CreateContactPersonRestrictionResponse = createContactPersonRestrictionResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/contact/$contactId/restriction")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubUpdateContactRestriction(
    personId: Long = 123456,
    contactId: Long = 678990,
    contactRestrictionId: Long = 543678,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/contact/$contactId/restriction/$contactRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
