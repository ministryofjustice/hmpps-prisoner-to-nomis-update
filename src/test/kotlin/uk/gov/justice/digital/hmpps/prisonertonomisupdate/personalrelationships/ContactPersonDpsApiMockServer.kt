package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

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
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactSummaryPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactSummaryPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerContactRestriction
import java.time.LocalDate
import java.time.LocalDateTime

class ContactPersonDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsContactPersonServer = ContactPersonDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper

    fun contact() = SyncContact(
      id = 12345,
      lastName = "KOFI",
      firstName = "KWEKU",
      isStaff = false,
      interpreterRequired = false,
      remitter = false,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun prisonerContact() = SyncPrisonerContact(
      id = 12345,
      contactId = 1234567,
      prisonerNumber = "A1234KT",
      contactType = "S",
      relationshipType = "BRO",
      nextOfKin = true,
      emergencyContact = true,
      approvedVisitor = false,
      currentTerm = true,
      active = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactAddress() = SyncContactAddress(
      contactAddressId = 1234567,
      contactId = 12345,
      primaryAddress = true,
      verified = false,
      mailFlag = false,
      noFixedAddress = false,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactEmail() = SyncContactEmail(
      contactEmailId = 1234567,
      contactId = 12345,
      emailAddress = "test@test.com",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactPhone() = SyncContactPhone(
      contactPhoneId = 1234567,
      contactId = 12345,
      phoneNumber = "07973 555 5555",
      phoneType = "MOB",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactAddressPhone() = SyncContactAddressPhone(
      contactAddressPhoneId = 1234567,
      contactPhoneId = 78890,
      contactAddressId = 73390,
      contactId = 12345,
      phoneNumber = "07973 555 5555",
      phoneType = "MOB",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactIdentity() = SyncContactIdentity(
      contactIdentityId = 1234567,
      contactId = 12345,
      identityValue = "SMIT5654DL",
      identityType = "DL",
      issuingAuthority = "DVLA",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactEmployment() = SyncEmployment(
      employmentId = 1234567,
      contactId = 12345,
      organisationId = 43321,
      active = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactRestriction() = SyncContactRestriction(
      contactRestrictionId = 1234567,
      contactId = 12345,
      restrictionType = "BAN",
      startDate = LocalDate.parse("2020-01-01"),
      expiryDate = LocalDate.parse("2025-01-01"),
      comments = "Banned for life",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun prisonerContactRestriction() = SyncPrisonerContactRestriction(
      prisonerContactRestrictionId = 1234567,
      prisonerContactId = 65432,
      prisonerNumber = "A1234KT",
      contactId = 12345,
      restrictionType = "BAN",
      startDate = LocalDate.parse("2020-01-01"),
      expiryDate = LocalDate.parse("2025-01-01"),
      comments = "Banned for life",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun prisonerContactSummaryPage() = PrisonerContactSummaryPage(
      content = emptyList(),
    )

    fun prisonerContactSummary(id: Long) = PrisonerContactSummary(
      prisonerContactId = id,
      contactId = 1,
      prisonerNumber = "A1234KT",
      lastName = "SMITH",
      firstName = "JANE",
      relationshipTypeCode = "S",
      relationshipTypeDescription = "Social",
      relationshipToPrisonerCode = "BRO",
      relationshipToPrisonerDescription = "Brother",
      isApprovedVisitor = true,
      isNextOfKin = false,
      isEmergencyContact = false,
      isRelationshipActive = true,
      currentTerm = true,
    )
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsContactPersonServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsContactPersonServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsContactPersonServer.stop()
  }
}

class ContactPersonDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8099
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

  fun stubGetContact(contactId: Long, response: SyncContact = contact()) {
    stubFor(
      get("/sync/contact/$contactId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubGetPrisonerContact(prisonerContactId: Long, response: SyncPrisonerContact = prisonerContact()) {
    stubFor(
      get("/sync/prisoner-contact/$prisonerContactId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetContactAddress(contactAddressId: Long, response: SyncContactAddress = contactAddress()) {
    stubFor(
      get("/sync/contact-address/$contactAddressId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetContactEmail(contactEmailId: Long, response: SyncContactEmail = contactEmail()) {
    stubFor(
      get("/sync/contact-email/$contactEmailId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubGetContactPhone(contactPhoneId: Long, response: SyncContactPhone = contactPhone()) {
    stubFor(
      get("/sync/contact-phone/$contactPhoneId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubGetContactAddressPhone(contactAddressPhoneId: Long, response: SyncContactAddressPhone = contactAddressPhone()) {
    stubFor(
      get("/sync/contact-address-phone/$contactAddressPhoneId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetContactIdentity(contactIdentityId: Long, response: SyncContactIdentity = contactIdentity()) {
    stubFor(
      get("/sync/contact-identity/$contactIdentityId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubGetContactEmployment(contactEmploymentId: Long, response: SyncEmployment = contactEmployment()) {
    stubFor(
      get("/sync/employment/$contactEmploymentId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubGetContactRestriction(contactRestrictionId: Long, response: SyncContactRestriction = contactRestriction()) {
    stubFor(
      get("/sync/contact-restriction/$contactRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetPrisonerContactRestriction(prisonerContactRestrictionId: Long, response: SyncPrisonerContactRestriction = prisonerContactRestriction()) {
    stubFor(
      get("/sync/prisoner-contact-restriction/$prisonerContactRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetPrisonerContacts(prisonerNumber: String, response: PrisonerContactSummaryPage = prisonerContactSummaryPage()) {
    stubFor(
      get(urlPathEqualTo("/prisoner/$prisonerNumber/contact")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
      ),
    )
  }
}
