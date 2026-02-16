package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactForPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactForPrisoner
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateContactPersonRestrictionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonEmploymentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePrisonerRestrictionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagePersonIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagePrisonerRestrictionIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonEmailAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonEmploymentCorporate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RestrictionIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.nomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class ContactPersonNomisApiMockServer(private val jsonMapper: JsonMapper) {
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

    fun createPersonEmploymentResponse(): CreatePersonEmploymentResponse = CreatePersonEmploymentResponse(
      sequence = 4,
    )

    fun createPersonEmploymentRequest(): CreatePersonEmploymentRequest = CreatePersonEmploymentRequest(
      corporateId = 123,
      active = true,
    )

    fun updatePersonEmploymentRequest(): UpdatePersonEmploymentRequest = UpdatePersonEmploymentRequest(
      corporateId = 123,
      active = false,
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

    fun prisonerWithContacts() = PrisonerWithContacts(
      contacts = emptyList(),
    )

    fun prisonerContact(personId: Long = 1, contactId: Long = 1) = PrisonerContact(
      id = contactId,
      bookingId = 1,
      bookingSequence = 1,
      contactType = CodeDescription("S", "Social"),
      relationshipType = CodeDescription("BRO", "Brother"),
      active = true,
      approvedVisitor = true,
      nextOfKin = false,
      emergencyContact = false,
      person = ContactForPerson(
        personId = personId,
        lastName = "SMITH",
        firstName = "JANE",
      ),
      restrictions = emptyList(),
      audit = NomisAudit(
        createDatetime = LocalDateTime.now(),
        createUsername = "T.SMOTH",
      ),
    )

    fun contactRestriction() = ContactRestriction(
      id = 123,
      comment = null,
      type = CodeDescription("BAN", "Banned"),
      effectiveDate = LocalDate.parse("2020-01-01"),
      expiryDate = null,
      audit = NomisAudit(
        createDatetime = LocalDateTime.now(),
        createUsername = "T.SMOTH",
      ),
      enteredStaff = ContactRestrictionEnteredStaff(
        staffId = 323,
        username = "T.SMOTH",
      ),
    )

    fun personPhoneNumber(number: String = "0114555555") = PersonPhoneNumber(phoneId = 1, number = number, type = CodeDescription(code = "HOME", description = "Home"), audit = nomisAudit())
    fun personAddress(phoneNumbers: List<PersonPhoneNumber> = listOf(personPhoneNumber().copy(phoneId = 2))) = PersonAddress(addressId = 1, phoneNumbers = phoneNumbers, validatedPAF = false, primaryAddress = true, mailAddress = true, audit = nomisAudit())
    fun personEmailAddress(email: String = "test@justice.gov.uk") = PersonEmailAddress(emailAddressId = 1, email = email, audit = nomisAudit())
    fun personEmployment(corporateId: Long = 1) = PersonEmployment(sequence = 1, active = true, corporate = PersonEmploymentCorporate(id = corporateId, name = "Police"), audit = nomisAudit())
    fun personIdentifier(identifier: String = "SMITH1717171") = PersonIdentifier(sequence = 1, type = CodeDescription(code = "DL", description = "Driving Licence"), identifier = identifier, issuedAuthority = "DVLA", audit = nomisAudit())
    fun personContact(offenderNo: String = "A1234KT", restrictions: List<ContactRestriction> = listOf(contactRestriction().copy(id = 1, enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T")))) = PersonContact(
      id = 1,
      relationshipType = CodeDescription(code = "BOF", description = "Boyfriend"),
      contactType = CodeDescription(code = "S", description = "Social/ Family"),
      active = true,
      emergencyContact = true,
      nextOfKin = false,
      approvedVisitor = false,
      prisoner = ContactForPrisoner(bookingId = 1, offenderNo = offenderNo, lastName = "SMITH", firstName = "JOHN", bookingSequence = 1),
      restrictions = restrictions,
      audit = nomisAudit(),
    )
    fun contactPerson(personId: Long = 123456): ContactPerson = ContactPerson(
      personId = personId,
      firstName = "KWAME",
      lastName = "KOBE",
      interpreterRequired = false,
      audit = nomisAudit(),
      phoneNumbers = listOf(personPhoneNumber()),
      addresses = listOf(personAddress()),
      emailAddresses = listOf(personEmailAddress()),
      employments = listOf(personEmployment()),
      identifiers = listOf(personIdentifier()),
      contacts = listOf(personContact()),
      restrictions = listOf(ContactRestriction(id = 2, type = CodeDescription(code = "BAN", description = "Banned"), enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"), effectiveDate = LocalDate.parse("2020-01-01"), audit = nomisAudit())),
    )

    fun pagePersonIdResponse(total: Long) = PagePersonIdResponse(
      totalElements = total,
      totalPages = 100,
    )

    fun pagePrisonerRestrictionIdResponse(total: Long) = PagePrisonerRestrictionIdResponse(
      totalElements = total,
      totalPages = 100,
    )

    fun nomisPrisonerRestriction() = PrisonerRestriction(
      id = 1234,
      bookingId = 456,
      bookingSequence = 1,
      offenderNo = "A1234KT",
      type = CodeDescription("BAN", "Banned"),
      effectiveDate = LocalDate.now(),
      enteredStaff = ContactRestrictionEnteredStaff(1234, "T.SMITH"),
      authorisedStaff = ContactRestrictionEnteredStaff(1235, "M.SMITH"),
      audit = nomisAudit(),
    )

    fun createPrisonerRestrictionRequest(): CreatePrisonerRestrictionRequest = CreatePrisonerRestrictionRequest(
      typeCode = "BAN",
      comment = "Banned for life",
      effectiveDate = LocalDate.parse("2020-01-01"),
      expiryDate = LocalDate.parse("2026-01-01"),
      enteredStaffUsername = "j.much",
      authorisedStaffUsername = "a.manager",
    )

    fun createPrisonerRestrictionResponse(): CreatePrisonerRestrictionResponse = CreatePrisonerRestrictionResponse(
      id = 12345,
    )

    fun updatePrisonerRestrictionRequest(): UpdatePrisonerRestrictionRequest = UpdatePrisonerRestrictionRequest(
      typeCode = "BAN",
      comment = "Banned for life - updated",
      effectiveDate = LocalDate.parse("2020-01-01"),
      expiryDate = LocalDate.parse("2026-01-01"),
      enteredStaffUsername = "j.much",
      authorisedStaffUsername = "a.manager",
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
          .withBody(jsonMapper.writeValueAsString(response)),
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
          .withBody(jsonMapper.writeValueAsString(response)),
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

  fun stubDeletePersonContact(
    personId: Long = 123456,
    contactId: Long = 7373737,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/persons/$personId/contact/$contactId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
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
          .withBody(jsonMapper.writeValueAsString(response)),
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
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubDeletePersonAddress(
    personId: Long = 123456,
    addressId: Long = 123456,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/persons/$personId/address/$addressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
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
          .withBody(jsonMapper.writeValueAsString(response)),
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
  fun stubDeletePersonEmail(
    personId: Long = 123456,
    emailId: Long = 765443,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/persons/$personId/email/$emailId")).willReturn(
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
          .withBody(jsonMapper.writeValueAsString(response)),
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
          .withBody(jsonMapper.writeValueAsString(response)),
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
          .withBody(jsonMapper.writeValueAsString(response)),
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
  fun stubDeletePersonIdentifier(
    personId: Long = 123456,
    sequence: Long = 4,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/persons/$personId/identifier/$sequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreatePersonEmployment(
    personId: Long = 123456,
    response: CreatePersonEmploymentResponse = createPersonEmploymentResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/persons/$personId/employment")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubUpdatePersonEmployment(
    personId: Long = 123456,
    sequence: Long = 4,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/persons/$personId/employment/$sequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
  fun stubDeletePersonEmployment(
    personId: Long = 123456,
    sequence: Long = 4,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/persons/$personId/employment/$sequence")).willReturn(
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
          .withBody(jsonMapper.writeValueAsString(response)),
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
          .withBody(jsonMapper.writeValueAsString(response)),
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

  fun stubGetPrisonerContacts(prisonerNumber: String, response: PrisonerWithContacts = prisonerWithContacts()) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$prisonerNumber/contacts")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPersonIds(lastPersonId: Long = 0, response: PersonIdsWithLast = PersonIdsWithLast(lastPersonId = 0, personIds = emptyList())) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/persons/ids/all-from-id")).withQueryParam("personId", equalTo("$lastPersonId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPersonIdsTotals(response: PagePersonIdResponse = pagePersonIdResponse(100)) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/persons/ids"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetPrisonerRestrictionIds(lastRestrictionId: Long = 0, response: RestrictionIdsWithLast = RestrictionIdsWithLast(lastRestrictionId = 0, restrictionIds = emptyList())) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/restrictions/ids/all-from-id")).withQueryParam("restrictionId", equalTo("$lastRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPrisonerRestrictionIdsTotals(response: PagePrisonerRestrictionIdResponse = pagePrisonerRestrictionIdResponse(100)) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/restrictions/ids"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetPerson(
    personId: Long = 123456,
    person: ContactPerson = contactPerson(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/persons/$personId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(person)),
      ),
    )
  }

  fun stubGetPrisonerRestrictionById(restrictionId: Long, response: PrisonerRestriction = nomisPrisonerRestriction()) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/restrictions/$restrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPrisonerRestrictionById(restrictionId: Long, status: HttpStatus) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/restrictions/$restrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString("{}")),
      ),
    )
  }

  fun stubCreatePrisonerRestriction(
    offenderNo: String = "A1234KT",
    response: CreatePrisonerRestrictionResponse = createPrisonerRestrictionResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/prisoners/$offenderNo/restriction")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubUpdatePrisonerRestriction(
    offenderNo: String = "A1234KT",
    prisonerRestrictionId: Long = 12345,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/prisoners/$offenderNo/restriction/$prisonerRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubDeletePrisonerRestriction(
    offenderNo: String = "A1234KT",
    prisonerRestrictionId: Long = 12345,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/prisoners/$offenderNo/restriction/$prisonerRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
