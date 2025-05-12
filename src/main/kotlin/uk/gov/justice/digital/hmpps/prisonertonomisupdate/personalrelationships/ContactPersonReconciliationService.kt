package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactRestrictionDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileRelationship
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcileRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactReconcile
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.plusAssign

@OptIn(ExperimentalCoroutinesApi::class)
@Service
class ContactPersonReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: ContactPersonNomisApiService,
  private val nomisPrisonerApiService: NomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  @Value("\${reports.contact-person.prisoner-contact.reconciliation.page-size:10}") private val prisonerContactPageSize: Long = 10,
  @Value("\${reports.contact-person.person-contact.reconciliation.page-size:10}") private val personContactPageSize: Int = 10,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_PRISONER_PREFIX = "contact-person-prisoner-contact-reconciliation"
    const val TELEMETRY_PERSON_PREFIX = "contact-person-reconciliation"
  }

  suspend fun generatePrisonerContactReconciliationReport(): List<MismatchPrisonerContacts> {
    val mismatches: MutableList<MismatchPrisonerContacts> = mutableListOf()
    var lastBookingId = 0L
    var pageErrorCount = 0L

    do {
      val result = getNextBookingsForPage(lastBookingId)

      when (result) {
        is BookingSuccessPageResult -> {
          if (result.value.prisonerIds.isNotEmpty()) {
            result.value.checkPageOfPrisonerContactsMatches().also {
              mismatches += it.mismatches
              lastBookingId = it.lastBookingId
            }
          }
        }

        is BookingErrorPageResult -> {
          // just skip this "page" by moving the bookingId pointer up
          lastBookingId += prisonerContactPageSize.toInt()
          pageErrorCount++
        }
      }
    } while (result.notLastPage() && notManyPageErrors(pageErrorCount))

    return mismatches.toList()
  }

  suspend fun generatePersonContactReconciliationReport(): PersonContactsReconciliationResult {
    val contactsCount = AtomicInteger(0)
    val pagesCount = AtomicInteger(0)
    checkTotalsMatch()

    return coroutineScope {
      val mismatchesChannel = Channel<MismatchPersonContacts>(capacity = UNLIMITED)
      val channel = producePersonIds(pagesCount)
      val jobs = (1L..personContactPageSize).map { launchCheckPersonMatcher(channel, mismatchesChannel, contactsCount) }
      launch {
        // when all jobs finished (no more contacts to process) we can shut down the mismatch channel and return the results
        jobs.forEach { it.join() }
        mismatchesChannel.close()
      }
      PersonContactsReconciliationResult(
        mismatches = mismatchesChannel.toList(),
        contactsChecked = contactsCount.get(),
        pagesChecked = pagesCount.get(),
      )
    }
  }

  fun CoroutineScope.launchCheckPersonMatcher(
    channel: ReceiveChannel<Long>,
    mismatchesChannel: Channel<MismatchPersonContacts>,
    contactsCount: AtomicInteger,
  ) = launch {
    for (personId in channel) {
      checkPersonContactMatch(personId)?.also { mismatchesChannel.send(it) }
      contactsCount.incrementAndGet()
    }
  }

  fun CoroutineScope.producePersonIds(pagesCount: AtomicInteger) = produce<Long>(capacity = personContactPageSize * 2) {
    var lastPersonId = 0L
    var pageErrorCount = 0L

    do {
      val result = getNextPersonsForPage(lastPersonId)

      when (result) {
        is PersonSuccessPageResult -> {
          if (result.value.personIds.isNotEmpty()) {
            pagesCount.incrementAndGet()
            result.value.personIds.forEach {
              send(it)
            }
            lastPersonId = result.value.lastPersonId
          }
        }

        is PersonErrorPageResult -> {
          // just skip this "page" by moving the personId pointer up
          lastPersonId += personContactPageSize.toInt()
          pageErrorCount++
        }
      }
    } while (result.notLastPage() && notManyPageErrors(pageErrorCount))

    // no more person ids so signal a close of the channel
    channel.close()
  }

  private suspend fun checkTotalsMatch() = runCatching {
    val (nomisTotal, dpsTotal) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getPersonIdsTotals().totalElements } to
        async { dpsApiService.getContactIds().page!!.totalElements!! }
    }.awaitBoth()

    if (nomisTotal != dpsTotal) {
      telemetryClient.trackEvent(
        "$TELEMETRY_PERSON_PREFIX-mismatch-totals",
        mapOf(
          "nomisTotal" to nomisTotal.toString(),
          "dpsTotal" to dpsTotal.toString(),
        ),
      )
    }
  }.onFailure {
    log.error("Unable to get person contacts totals", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-mismatch-totals-error",
      mapOf(),
    )
  }

  private suspend fun BookingIdsWithLast.checkPageOfPrisonerContactsMatches(): PrisonerMismatchPageResult {
    val prisonerIds = this.prisonerIds
    val mismatches = withContext(Dispatchers.Unconfined) {
      prisonerIds.map { async { checkPrisonerContactsMatch(it) } }
    }.awaitAll().filterNotNull()
    return PrisonerMismatchPageResult(mismatches, prisonerIds.last().bookingId)
  }

  private fun notManyPageErrors(errors: Long): Boolean = errors < 30

  data class PrisonerMismatchPageResult(val mismatches: List<MismatchPrisonerContacts>, val lastBookingId: Long)

  private suspend fun getNextBookingsForPage(lastBookingId: Long): BookingPageResult = runCatching { nomisPrisonerApiService.getAllLatestBookings(lastBookingId = lastBookingId, activeOnly = true, pageSize = prisonerContactPageSize.toInt()) }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch-page-error",
        mapOf(
          "booking" to lastBookingId.toString(),
        ),
      )
      log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
    }
    .map { BookingSuccessPageResult(it) }
    .getOrElse { BookingErrorPageResult(it) }
    .also { log.info("Page requested from booking: $lastBookingId, with $prisonerContactPageSize bookings") }

  private suspend fun getNextPersonsForPage(lastPersonId: Long): PersonPageResult = runCatching { nomisApiService.getPersonIds(lastPersonId = lastPersonId, pageSize = personContactPageSize.toInt()) }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_PERSON_PREFIX-mismatch-page-error",
        mapOf(
          "person" to lastPersonId.toString(),
        ),
      )
      log.error("Unable to match entire page of persons from person: $lastPersonId", it)
    }
    .map { PersonSuccessPageResult(it) }
    .getOrElse { PersonErrorPageResult(it) }
    .also { log.info("Page requested from person: $lastPersonId, with $personContactPageSize person") }

  suspend fun checkPrisonerContactsMatch(prisonerId: PrisonerIds): MismatchPrisonerContacts? = runCatching {
    val (nomisContacts, dpsContacts) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getContactsForPrisoner(prisonerId.offenderNo).contacts } to
        async { dpsApiService.getPrisonerContacts(prisonerId.offenderNo).content!! }
    }.awaitBoth()

    val dpsContactSummaries = dpsContacts.map { it.asSummary() }
    val nomisContactSummaries = nomisContacts.map { it.asSummary() }

    checkContactsMatch(
      prisonerId = prisonerId,
      dpsContactSummaries = dpsContactSummaries,
      nomisContactSummaries = nomisContactSummaries,
    ) ?: run {
      // don't bother checking restrictions unless we have matching contacts
      checkContactRestrictionsMatch(
        prisonerId = prisonerId,
        nomisContacts = nomisContacts,
        dpsContacts = dpsContacts,
      )
    }
  }.onFailure {
    log.error("Unable to match contacts for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()

  suspend fun checkPersonContactMatch(personId: Long): MismatchPersonContacts? = runCatching {
    val (nomisPerson, dpsContact) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getPerson(personId) } to
        async { dpsApiService.getContactDetails(personId) }
    }.awaitBoth()

    val nomisPersonSummary = nomisPerson.asSummary()
    val dpsPersonSummary = dpsContact?.asSummary()
    return checkPersonMatch(
      personId = personId,
      nomisPersonSummary = nomisPersonSummary,
      dpsPersonSummary = dpsPersonSummary,
    )
  }.onFailure {
    log.error(
      "Unable to match contacts for person with $personId",
      it,
    )
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_PREFIX-mismatch-error",
      mapOf(
        "personId" to personId.toString(),
      ),
    )
  }.getOrNull()

  private fun checkPersonMatch(
    personId: Long,
    nomisPersonSummary: PersonSummary,
    dpsPersonSummary: PersonSummary?,
  ): MismatchPersonContacts? {
    val telemetry = mutableMapOf("personId" to personId.toString())
    if (dpsPersonSummary == null) {
      return MismatchPersonContacts(
        personId = personId,
        dpsSummary = dpsPersonSummary,
        nomisSummary = nomisPersonSummary,
      ).also { mismatch ->
        telemetryClient.trackEvent(
          "$TELEMETRY_PERSON_PREFIX-mismatch",
          telemetry + ("reason" to "dps-person-missing"),
        )
      }
    }

    if (nomisPersonSummary != dpsPersonSummary) {
      return MismatchPersonContacts(
        personId = personId,
        dpsSummary = dpsPersonSummary,
        nomisSummary = nomisPersonSummary,
      ).also { mismatch ->
        telemetryClient.trackEvent(
          "$TELEMETRY_PERSON_PREFIX-mismatch",
          telemetry + mapOf("reason" to "different-person-details"),
        )
      }
    }

    return null
  }

  suspend fun checkContactsMatch(prisonerId: PrisonerIds, nomisContactSummaries: List<ContactSummary>, dpsContactSummaries: List<ContactSummary>): MismatchPrisonerContacts? {
    val (contactIdsMissingFromNomis, contactIdsMissingFromDps) = findMissingContacts(nomisContactSummaries, dpsContactSummaries)

    val telemetry = mutableMapOf(
      "offenderNo" to prisonerId.offenderNo,
      "dpsContactCount" to (dpsContactSummaries.size.toString()),
      "nomisContactCount" to (nomisContactSummaries.size.toString()),
      "contactIdsMissingFromNomis" to (contactIdsMissingFromNomis.toString()),
      "contactIdsMissingFromDps" to (contactIdsMissingFromDps.toString()),
    )

    if (nomisContactSummaries.size != dpsContactSummaries.size) {
      return MismatchPrisonerContacts(prisonerId.offenderNo, nomisContactCount = nomisContactSummaries.size, dpsContactCount = dpsContactSummaries.size).also { mismatch ->
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          telemetry + ("reason" to "different-number-of-contacts"),
        )
      }
    }

    if (contactIdsMissingFromNomis.isNotEmpty() || contactIdsMissingFromDps.isNotEmpty()) {
      return MismatchPrisonerContacts(prisonerId.offenderNo, nomisContactCount = nomisContactSummaries.size, dpsContactCount = dpsContactSummaries.size).also { mismatch ->
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          telemetry + mapOf("reason" to "different-contacts", "contactIdsMissingFromNomis" to contactIdsMissingFromNomis.toString(), "contactIdsMissingFromDps" to contactIdsMissingFromDps.toString()),
        )
      }
    }
    return dpsContactSummaries.filter { !nomisContactSummaries.contains(it) }.map { dpsSummary ->
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch",
        telemetry + mapOf("reason" to "different-contacts-details", "contactId" to dpsSummary.contactId.toString()),
      )
      MismatchPrisonerContacts(prisonerId.offenderNo, nomisContactCount = nomisContactSummaries.size, dpsContactCount = dpsContactSummaries.size)
    }.firstOrNull()
  }
  suspend fun checkContactRestrictionsMatch(prisonerId: PrisonerIds, nomisContacts: List<PrisonerContact>, dpsContacts: List<PrisonerContactSummary>): MismatchPrisonerContacts? = dpsContacts.map { dpsContact ->
    val dpsPrisonerContactRestrictions = dpsApiService.getPrisonerContactRestrictions(dpsContact.prisonerContactId).prisonerContactRestrictions
    // since we only do this if contacts match we must be able to find the associated relationship in NOMIS
    val nomisPrisonerContactRestrictions = nomisContacts.find { it.person.personId == dpsContact.contactId && it.relationshipType.code == dpsContact.relationshipToPrisonerCode }?.restrictions ?: emptyList()
    val telemetry = mutableMapOf(
      "offenderNo" to prisonerId.offenderNo,
      "contactId" to dpsContact.contactId.toString(),
      "relationshipType" to dpsContact.relationshipToPrisonerCode,
      "dpsPrisonerContactRestrictionCount" to (dpsPrisonerContactRestrictions.size.toString()),
      "nomisPrisonerContactRestrictionCount" to (nomisPrisonerContactRestrictions.size.toString()),
    )

    val dpsPrisonerContactRestrictionsSummaries = dpsPrisonerContactRestrictions.map { it.asSummary(dpsContact.relationshipToPrisonerCode) }
    val nomisPrisonerContactRestrictionsSummaries = nomisPrisonerContactRestrictions.map { it.asSummary(dpsContact.contactId, dpsContact.relationshipToPrisonerCode) }

    val (prisonerRestrictionsTypesMissingFromNomis, prisonerRestrictionsTypesMissingFromDps) = findMissingPrisonerContactRestrictions(nomisPrisonerContactRestrictionsSummaries, dpsPrisonerContactRestrictionsSummaries)

    if (nomisPrisonerContactRestrictions.size != dpsPrisonerContactRestrictions.size) {
      return MismatchPrisonerContacts(prisonerId.offenderNo, nomisContactCount = nomisContacts.size, dpsContactCount = dpsContacts.size).also { mismatch ->
        log.info("Prisoner contact restriction sizes do not match  $mismatch")
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          telemetry + mapOf(
            "reason" to "different-number-of-contact-restrictions",
            "prisonerRestrictionsTypesMissingFromNomis" to prisonerRestrictionsTypesMissingFromNomis.toString(),
            "prisonerRestrictionsTypesMissingFromDps" to prisonerRestrictionsTypesMissingFromDps.toString(),
          ),
        )
      }
    }

    if (prisonerRestrictionsTypesMissingFromNomis.isNotEmpty() || prisonerRestrictionsTypesMissingFromDps.isNotEmpty()) {
      return MismatchPrisonerContacts(prisonerId.offenderNo, nomisContactCount = nomisContacts.size, dpsContactCount = dpsContacts.size).also { mismatch ->
        log.info("Prisoner contact restrictions types do not match  $mismatch")
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          telemetry + mapOf(
            "reason" to "different-prisoner-contact-restrictions-types",
            "prisonerRestrictionsTypesMissingFromNomis" to prisonerRestrictionsTypesMissingFromNomis.toString(),
            "prisonerRestrictionsTypesMissingFromDps" to prisonerRestrictionsTypesMissingFromDps.toString(),
          ),
        )
      }
    }

    return dpsPrisonerContactRestrictionsSummaries.filter { !nomisPrisonerContactRestrictionsSummaries.contains(it) }.map { dpsSummary ->
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch",
        telemetry + mapOf(
          "reason" to "different-prisoner-contact-restrictions-details",
          "restrictionType" to dpsSummary.restrictionType,
        ),
      )
      MismatchPrisonerContacts(prisonerId.offenderNo, nomisContactCount = nomisContacts.size, dpsContactCount = dpsContacts.size)
    }.firstOrNull()
  }.firstOrNull()

  private fun findMissingContacts(nomisContactSummaries: List<ContactSummary>, dpsContactSummaries: List<ContactSummary>): Pair<Set<Long>, Set<Long>> {
    val nomisContactIds = nomisContactSummaries.map { it.contactId }.toSet()
    val dpsContactIds = dpsContactSummaries.map { it.contactId }.toSet()

    val contactIdsMissingFromDps = nomisContactIds - dpsContactIds
    val contactIdsMissingFromNomis = dpsContactIds - nomisContactIds

    return contactIdsMissingFromNomis to contactIdsMissingFromDps
  }

  private fun findMissingPrisonerContactRestrictions(nomisPrisonerContactRestrictionsSummaries: List<PrisonerContactRestrictionSummary>, dpsPrisonerContactRestrictionsSummaries: List<PrisonerContactRestrictionSummary>): Pair<Set<String>, Set<String>> {
    val nomisRestrictionTypes = nomisPrisonerContactRestrictionsSummaries.map { it.restrictionType }.toSet()
    val dpsRestrictionTypes = dpsPrisonerContactRestrictionsSummaries.map { it.restrictionType }.toSet()

    val nomisRestrictionTypesMissingFromDps = nomisRestrictionTypes - dpsRestrictionTypes
    val nomisRestrictionTypesMissingFromNomis = dpsRestrictionTypes - nomisRestrictionTypes

    return nomisRestrictionTypesMissingFromNomis to nomisRestrictionTypesMissingFromDps
  }
  private fun PrisonerContactSummary.asSummary() = ContactSummary(
    contactId = this.contactId,
    firstName = this.firstName,
    lastName = this.lastName,
    relationshipCode = this.relationshipToPrisonerCode,
    contactType = this.relationshipTypeCode,
    emergencyContact = this.isEmergencyContact,
    nextOfKin = this.isNextOfKin,
    approvedVisitor = this.isApprovedVisitor,
    active = this.isRelationshipActive,
  )

  private fun PrisonerContact.asSummary() = ContactSummary(
    contactId = this.person.personId,
    firstName = this.person.firstName,
    lastName = this.person.lastName,
    relationshipCode = this.relationshipType.code,
    contactType = this.contactType.code,
    emergencyContact = this.emergencyContact,
    nextOfKin = this.nextOfKin,
    approvedVisitor = this.approvedVisitor,
    active = this.active,
  )

  private fun ContactPerson.asSummary() = PersonSummary(
    personId = this.personId,
    firstName = this.firstName,
    lastName = this.lastName,
    dateOfBirth = this.dateOfBirth?.takeIf { it.validDateOfBirth() },
    addressCount = this.addresses.size,
    phoneNumbers = this.phoneNumbers.map { it.number }.toSortedSet(),
    emailAddresses = this.emailAddresses.map { it.email }.toSortedSet(),
    addressPhoneNumbers = this.addresses.map { it.phoneNumbers.map { it.number }.toSortedSet() }.toSortedSet { o1, o2 -> o1.joinToString().compareTo(o2.joinToString()) },
    employmentOrganisations = this.employments.map { it.corporate.id }.toSortedSet(),
    identifiers = this.identifiers.map { it.identifier }.toSortedSet(),
    prisonerContacts = this.contacts.filter { it.prisoner.bookingSequence == 1L }.map { it.asSummary() }.toSortedSet(),
    restrictions = this.restrictions.map { it.asSummary() }.toSortedSet(),
  )
  private fun SyncContactReconcile.asSummary() = PersonSummary(
    personId = this.contactId,
    firstName = this.firstName,
    lastName = this.lastName,
    dateOfBirth = this.dateOfBirth?.takeIf { it.validDateOfBirth() },
    addressCount = this.addresses.size,
    phoneNumbers = this.phones.map { it.phoneNumber }.toSortedSet(),
    emailAddresses = this.emails.map { it.emailAddress }.toSortedSet(),
    addressPhoneNumbers = this.addresses.map { it.addressPhones.map { it.phoneNumber }.toSortedSet() }.toSortedSet { o1, o2 -> o1.joinToString().compareTo(o2.joinToString()) },
    employmentOrganisations = this.employments.map { it.organisationId }.toSortedSet(),
    identifiers = this.identities.map { it.identityValue.toString() }.toSortedSet(),
    prisonerContacts = this.relationships.map { it.asSummary() }.toSortedSet(),
    restrictions = this.restrictions.map { it.asSummary() }.toSortedSet(),
  )

  // There is no reason why we have ever registered a visitor born before 1800
  // this is to ignore invalid dates e.g "0200-01-01" which can causes mismatches
  // due to LocalDate conversion for ancient dates
  private fun LocalDate.validDateOfBirth(): Boolean = this.isAfter(LocalDate.parse("1800-01-01"))

  private fun PersonContact.asSummary() = PrisonerRelationship(
    offenderNo = this.prisoner.offenderNo,
    relationshipType = this.relationshipType.code,
    active = this.active,
  )

  private fun ReconcileRelationship.asSummary() = PrisonerRelationship(
    offenderNo = this.prisonerNumber,
    relationshipType = this.relationshipType,
    active = this.active,
  )

  private fun ContactRestriction.asSummary() = PersonContactRestrictionSummary(
    restrictionType = this.type.code,
    startDate = this.effectiveDate,
    expiryDate = this.expiryDate,
  )

  private fun ReconcileRestriction.asSummary() = PersonContactRestrictionSummary(
    restrictionType = this.restrictionType,
    startDate = this.startDate,
    expiryDate = this.expiryDate,
  )
  private fun ContactRestriction.asSummary(contactId: Long, relationshipCode: String) = PrisonerContactRestrictionSummary(
    contactId = contactId,
    relationshipCode = relationshipCode,
    restrictionType = this.type.code,
    startDate = this.effectiveDate,
    expiryDate = this.expiryDate,
  )

  private fun PrisonerContactRestrictionDetails.asSummary(relationshipCode: String) = PrisonerContactRestrictionSummary(
    contactId = this.contactId,
    relationshipCode = relationshipCode,
    restrictionType = this.restrictionType,
    startDate = this.startDate,
    expiryDate = this.expiryDate,
  )

  // Last page will be a non-null page with items less than page size
  private fun BookingPageResult.notLastPage(): Boolean = when (this) {
    is BookingSuccessPageResult -> this.value.prisonerIds.size == prisonerContactPageSize.toInt()
    is BookingErrorPageResult -> true
  }

  // Last page will be a non-null page with items less than page size
  private fun PersonPageResult.notLastPage(): Boolean = when (this) {
    is PersonSuccessPageResult -> this.value.personIds.size == personContactPageSize.toInt()
    is PersonErrorPageResult -> true
  }

  sealed class BookingPageResult
  class BookingSuccessPageResult(val value: BookingIdsWithLast) : BookingPageResult()
  class BookingErrorPageResult(val error: Throwable) : BookingPageResult()

  sealed class PersonPageResult
  class PersonSuccessPageResult(val value: PersonIdsWithLast) : PersonPageResult()
  class PersonErrorPageResult(val error: Throwable) : PersonPageResult()
}

data class ContactSummary(
  val contactId: Long,
  val firstName: String,
  val lastName: String,
  val relationshipCode: String,
  val contactType: String,
  val emergencyContact: Boolean,
  val nextOfKin: Boolean,
  val approvedVisitor: Boolean,
  val active: Boolean,
)
data class PrisonerContactRestrictionSummary(
  val contactId: Long,
  val relationshipCode: String,
  val restrictionType: String,
  val startDate: LocalDate?,
  val expiryDate: LocalDate?,
)
data class MismatchPrisonerContacts(
  val offenderNo: String,
  val dpsContactCount: Int,
  val nomisContactCount: Int,
)

data class PersonSummary(
  val personId: Long,
  val firstName: String,
  val lastName: String,
  val dateOfBirth: LocalDate?,
  val addressCount: Int,
  val phoneNumbers: SortedSet<String>,
  val emailAddresses: SortedSet<String>,
  val identifiers: SortedSet<String>,
  val employmentOrganisations: SortedSet<Long>,
  val addressPhoneNumbers: SortedSet<SortedSet<String>>,
  val prisonerContacts: SortedSet<PrisonerRelationship>,
  val restrictions: SortedSet<PersonContactRestrictionSummary>,
)

data class PrisonerRelationship(val offenderNo: String, val relationshipType: String, val active: Boolean) : Comparable<PrisonerRelationship> {
  override fun compareTo(other: PrisonerRelationship): Int = compareValuesBy(this, other, { it.offenderNo }, { it.relationshipType }, { it.active })
}
data class PersonContactRestrictionSummary(
  val restrictionType: String,
  val startDate: LocalDate?,
  val expiryDate: LocalDate?,
) : Comparable<PersonContactRestrictionSummary> {
  override fun compareTo(other: PersonContactRestrictionSummary): Int = compareValuesBy(this, other, { it.restrictionType }, { it.startDate }, { it.expiryDate })
}
data class MismatchPersonContacts(
  val personId: Long,
  val dpsSummary: PersonSummary?,
  val nomisSummary: PersonSummary?,
)

data class PersonContactsReconciliationResult(
  val mismatches: List<MismatchPersonContacts>,
  val contactsChecked: Int,
  val pagesChecked: Int,
)
