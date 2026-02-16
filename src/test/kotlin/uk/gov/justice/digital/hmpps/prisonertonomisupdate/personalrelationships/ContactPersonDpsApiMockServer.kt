package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

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
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactReconcileDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PagedModelSyncContactId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerRestrictionId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcilePhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcilePrisonerRelationship
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileRelationship
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileRelationshipRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactReconcile
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerReconcile
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerRestriction
import java.time.LocalDate
import java.time.LocalDateTime

class ContactPersonDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsContactPersonServer = ContactPersonDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

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

    fun contactReconcileDetails(contactId: Long = 12345) = SyncContactReconcile(
      contactId = contactId,
      lastName = "KOFI",
      firstName = "KWEKU",
      staffFlag = false,
      phones = emptyList(),
      addresses = emptyList(),
      emails = emptyList(),
      identities = emptyList(),
      restrictions = emptyList(),
      relationships = emptyList(),
      employments = emptyList(),
      middleNames = null,
      dateOfBirth = null,
    )

    fun contactAddressDetails(contactAddressId: Long = 1234567, phoneNumbers: List<ReconcileAddressPhone> = emptyList()) = ReconcileAddress(
      contactAddressId = contactAddressId,
      primaryAddress = true,
      addressPhones = phoneNumbers,
      addressType = "HOME",
      property = null,
      street = null,
      area = null,
    )

    fun contactEmailDetails(emailAddress: String = "test@test.com") = ReconcileEmail(
      contactEmailId = 1234567,
      emailAddress = emailAddress,
    )
    fun contactPhoneDetails(phoneNumber: String = "07973 555 5555") = ReconcilePhone(
      contactPhoneId = 1234567,
      phoneNumber = phoneNumber,
      phoneType = "MOB",
    )
    fun contactAddressPhoneDetails(phoneNumber: String = "07973 555 5555") = ReconcileAddressPhone(
      phoneNumber = phoneNumber,
      phoneType = "MOB",
      contactAddressPhoneId = 111,
    )
    fun contactIdentityDetails(identityValue: String = "SMIT5654DL") = ReconcileIdentity(
      contactIdentityId = 1234567,
      identityValue = identityValue,
      identityType = "DL",
      issuingAuthority = "DVLA",
    )

    fun contactEmploymentDetails(organisationId: Long = 1) = ReconcileEmployment(
      employmentId = 1234567,
      organisationId = organisationId,
      active = true,
    )

    fun contactRestrictionDetails() = ReconcileRestriction(
      contactRestrictionId = 1234567,
      restrictionType = "BAN",
    )

    fun linkedPrisonerDetails() = ReconcileRelationship(
      prisonerNumber = "A1234KT",
      prisonerContactId = 12434567,
      relationshipType = "S",
      relationshipRestrictions = emptyList(),
      contactType = "S",
      nextOfKin = false,
      emergencyContact = false,
      active = true,
      approvedVisitor = false,
    )

    fun prisonerContactDetails() = SyncPrisonerReconcile(
      relationships = listOf(prisonerContactRelationshipDetails()),
    )

    fun prisonerContactRelationshipDetails(contactId: Long = 1, prisonerContactId: Long = 10) = ReconcilePrisonerRelationship(
      contactId = contactId,
      prisonerContactId = prisonerContactId,
      prisonerNumber = "A1234KT",
      relationshipTypeCode = "S",
      relationshipToPrisoner = "BRO",
      nextOfKin = false,
      emergencyContact = false,
      active = true,
      approvedVisitor = true,
      restrictions = emptyList(),
      lastName = "SMITH",
      firstName = "JANE",
    )

    fun prisonerContactRestrictionDetails() = ReconcileRelationshipRestriction(
      prisonerContactRestrictionId = 1234567,
      restrictionType = "BAN",
      startDate = LocalDate.parse("2020-01-01"),
      expiryDate = null,
    )

    fun prisonerRestriction() = SyncPrisonerRestriction(
      prisonerRestrictionId = 1234567,
      prisonerNumber = "A1234KT",
      restrictionType = "BAN",
      effectiveDate = LocalDate.now(),
      authorisedUsername = "T.SMITH",
      currentTerm = true,
      createdBy = "J.SMITH",
      createdTime = LocalDateTime.now(),
      expiryDate = null,
      commentText = "Bannded for LIfe",
      updatedBy = "K.SMITH",
      updatedTime = LocalDateTime.now(),
    )
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsContactPersonServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetPrisonerRestriction(prisonerRestrictionId: Long, response: SyncPrisonerRestriction = prisonerRestriction()) = stubGetPrisonerRestrictionOrNull(prisonerRestrictionId, response = response)

  fun stubGetPrisonerRestrictionOrNull(prisonerRestrictionId: Long, response: SyncPrisonerRestriction? = prisonerRestriction()) {
    if (response != null) {
      stubFor(
        get("/sync/prisoner-restriction/$prisonerRestrictionId")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
          ),
      )
    } else {
      stubFor(
        get("/sync/prisoner-restriction/$prisonerRestrictionId")
          .willReturn(
            aResponse()
              .withStatus(404)
              .withHeader("Content-Type", "application/json"),
          ),
      )
    }
  }

  fun stubGetPrisonerContactRestriction(prisonerContactRestrictionId: Long, response: SyncPrisonerContactRestriction = prisonerContactRestriction()) {
    stubFor(
      get("/sync/prisoner-contact-restriction/$prisonerContactRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetPrisonerContacts(prisonerNumber: String, response: SyncPrisonerReconcile = prisonerContactDetails()) {
    stubFor(
      get(urlPathEqualTo("/sync/prisoner/$prisonerNumber/reconcile")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetContactIds(contactIds: List<Long> = emptyList(), totalElements: Long = contactIds.size.toLong()) {
    stubFor(
      get(urlPathEqualTo("/sync/contact/reconcile")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(PagedModelSyncContactId(page = PageMetadata(totalElements = totalElements), content = contactIds.map { SyncContactId(it) }))),
      ),
    )
  }

  fun stubGetPrisonerRestrictionsIds(prisonerRestrictionsIds: List<Long> = emptyList(), totalElements: Long = prisonerRestrictionsIds.size.toLong()) {
    stubFor(
      get(urlPathEqualTo("/prisoner-restrictions/reconcile")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(PagedModelPrisonerRestrictionId(page = PageMetadata(totalElements = totalElements), content = prisonerRestrictionsIds.map { PrisonerRestrictionId(it) }))),
      ),
    )
  }

  fun stubGetContactDetails(contactId: Long, response: SyncContactReconcile? = contactReconcileDetails()) {
    if (response == null) {
      stubFor(
        get("/sync/contact/$contactId/reconcile")
          .willReturn(
            aResponse()
              .withStatus(404)
              .withHeader("Content-Type", "application/json")
              .withBody("""{}"""),
          ),
      )
    } else {
      stubFor(
        get("/sync/contact/$contactId/reconcile")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(ContactPersonDpsApiExtension.jsonMapper.writeValueAsString(response)),
          ),
      )
    }
  }
}
