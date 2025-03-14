package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class ContactPersonReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: ContactPersonNomisApiService,
  private val nomisPrisonerApiService: NomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  @Value("\${reports.contact-person.prisoner-contact.reconciliation.page-size:20}") private val pageSize: Long = 20,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_PREFIX = "contact-person-prisoner-contact-reconciliation"
  }

  suspend fun generatePrisonerContactReconciliationReport(): List<MismatchPrisonerContacts> {
    val mismatches: MutableList<MismatchPrisonerContacts> = mutableListOf()
    var lastBookingId = 0L
    var pageErrorCount = 0L

    do {
      val result = getNextBookingsForPage(lastBookingId)

      when (result) {
        is SuccessPageResult -> {
          if (result.value.prisonerIds.isNotEmpty()) {
            result.value.checkPageOfPrisonerContactsMatches().also {
              mismatches += it.mismatches
              lastBookingId = it.lastBookingId
            }
          }
        }

        is ErrorPageResult -> {
          // just skip this "page" by moving the bookingId pointer up
          lastBookingId += pageSize.toInt()
          pageErrorCount++
        }
      }
    } while (result.notLastPage() && notManyPageErrors(pageErrorCount))

    return mismatches.toList()
  }

  private suspend fun BookingIdsWithLast.checkPageOfPrisonerContactsMatches(): MismatchPageResult {
    val prisonerIds = this.prisonerIds
    val mismatches = withContext(Dispatchers.Unconfined) {
      prisonerIds.map { async { checkPrisonerContactsMatch(it) } }
    }.awaitAll().filterNotNull()
    return MismatchPageResult(mismatches, prisonerIds.last().bookingId)
  }

  private fun notManyPageErrors(errors: Long): Boolean = errors < 30

  data class MismatchPageResult(val mismatches: List<MismatchPrisonerContacts>, val lastBookingId: Long)

  private suspend fun getNextBookingsForPage(lastBookingId: Long): PageResult = runCatching { nomisPrisonerApiService.getAllLatestBookings(lastBookingId = lastBookingId, activeOnly = true, pageSize = pageSize.toInt()) }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-mismatch-page-error",
        mapOf(
          "booking" to lastBookingId.toString(),
        ),
      )
      log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
    }
    .map { SuccessPageResult(it) }
    .getOrElse { ErrorPageResult(it) }
    .also { log.info("Page requested from booking: $lastBookingId, with $pageSize bookings") }

  suspend fun checkPrisonerContactsMatch(prisonerId: PrisonerIds): MismatchPrisonerContacts? = runCatching {
    val (nomisContacts, dpsContacts) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getContactsForPrisoner(prisonerId.offenderNo).contacts } to
        async { dpsApiService.getPrisonerContacts(prisonerId.offenderNo).content!! }
    }.awaitBoth()

    val dpsContactSummaries = dpsContacts.map { it.asSummary() }.sortedBy { it.contactId }
    val nomisContactSummaries = nomisContacts.map { it.asSummary() }.sortedBy { it.contactId }

    checkContactsMatch(
      prisonerId = prisonerId,
      dpsContactSummaries = dpsContactSummaries,
      nomisContactSummaries = nomisContactSummaries,
    )
  }.onFailure {
    log.error("Unable to match contacts for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()

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
        log.info("Prisoner contact sizes do not match  $mismatch")
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-mismatch",
          telemetry + ("reason" to "different-number-of-contacts"),
        )
      }
    }

    if (contactIdsMissingFromNomis.isNotEmpty() || contactIdsMissingFromDps.isNotEmpty()) {
      return MismatchPrisonerContacts(prisonerId.offenderNo, nomisContactCount = nomisContactSummaries.size, dpsContactCount = dpsContactSummaries.size).also { mismatch ->
        log.info("Prisoner contact ids do not match  $mismatch")
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-mismatch",
          telemetry + ("reason" to "different-contacts"),
        )
      }
    }
    return nomisContactSummaries.zip(dpsContactSummaries).filter { (nomisSummary, dpsSummary) -> nomisSummary != dpsSummary }.map { (_, dpsSummary) ->
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-mismatch",
        telemetry + mapOf("reason" to "different-contacts-details", "contactId" to dpsSummary.contactId.toString()),
      )
      MismatchPrisonerContacts(prisonerId.offenderNo, nomisContactCount = nomisContactSummaries.size, dpsContactCount = dpsContactSummaries.size)
    }.firstOrNull()
  }

  private fun findMissingContacts(nomisContactSummaries: List<ContactSummary>, dpsContactSummaries: List<ContactSummary>): Pair<Set<Long>, Set<Long>> {
    val nomisContactIds = nomisContactSummaries.map { it.contactId }.toSet()
    val dpsContactIds = dpsContactSummaries.map { it.contactId }.toSet()

    val contactIdsMissingFromDps = nomisContactIds - dpsContactIds
    val contactIdsMissingFromNomis = dpsContactIds - nomisContactIds

    return contactIdsMissingFromNomis to contactIdsMissingFromDps
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
    currentTerm = this.currentTerm,
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
    currentTerm = this.bookingSequence == 1L,
  )

  // Last page will be a non-null page with items less than page size
  private fun PageResult.notLastPage(): Boolean = when (this) {
    is SuccessPageResult -> this.value.prisonerIds.size == pageSize.toInt()
    is ErrorPageResult -> true
  }

  sealed class PageResult
  class SuccessPageResult(val value: BookingIdsWithLast) : PageResult()
  class ErrorPageResult(val error: Throwable) : PageResult()
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
  val currentTerm: Boolean,
)
data class MismatchPrisonerContacts(
  val offenderNo: String,
  val dpsContactCount: Int,
  val nomisContactCount: Int,
)
