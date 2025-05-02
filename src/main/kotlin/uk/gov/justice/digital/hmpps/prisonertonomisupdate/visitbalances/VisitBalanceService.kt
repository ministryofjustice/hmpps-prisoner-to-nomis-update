package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.PrisonerReceiveDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitBalanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.VisitAllocationPrisonerAdjustmentResponseDto
import java.time.LocalDate

@Service
class VisitBalanceService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: VisitBalanceDpsApiService,
  private val nomisApiService: NomisApiService,
  private val visitBalanceNomisApiService: VisitBalanceNomisApiService,
) {
  companion object {
    const val VISIT_ALLOCATION_SERVICE = "VISIT_ALLOCATION"
  }
  suspend fun visitBalanceAdjustmentCreated(event: VisitBalanceAdjustmentEvent) {
    val visitBalanceAdjustmentId = event.additionalInformation.adjustmentId
    val telemetry = telemetryOf("visitBalanceAdjustmentId" to visitBalanceAdjustmentId)

    dpsApiService.getVisitBalanceAdjustment(visitBalanceAdjustmentId).also {
      visitBalanceNomisApiService.createVisitBalanceAdjustment(it.prisonerId, it.toNomisCreateVisitBalanceAdjustmentRequest())
      telemetryClient.trackEvent(
        "visitbalance-adjustment-synchronisation-created-success",
        telemetry + ("prisonNumber" to it.prisonerId) + ("visitBalanceChange" to it.changeToVoBalance.toString()) +
          ("privilegeVisitBalanceChange" to it.changeToPvoBalance.toString()),
      )
    }
  }

  suspend fun synchronisePrisonerBookingMoved(bookingMovedEvent: PrisonerBookingMovedDomainEvent) {
    /*
    val movedFromPrisoner = bookingMovedEvent.additionalInformation.movedFromNomsNumber
    val movedToPrisoner = bookingMovedEvent.additionalInformation.movedToNomsNumber

    val telemetry = telemetryOf(
      "bookingId" to bookingMovedEvent.additionalInformation.bookingId,
      "movedFromPrisonNumber" to movedFromPrisoner,
      "movedToPrisonNumber" to movedToPrisoner,
    )

    if (isDpsInChargeOfVisitAllocation(movedFromPrisoner)) {
      // BUT THEY COULD NOW BE OUT - do we care?
      val fromVisitBalance = dpsApiService.getVisitBalance(movedFromPrisoner)
      // TODO what if it is now null? can it be
      visitBalanceNomisApiService.updateVisitBalance(movedFromPrisoner, fromVisitBalance?.toNomisUpdateVisitBalanceRequest() ?: UpdateVisitBalanceRequest(0, 0))
      telemetry + mapOf(
        "movedFromBalance" to mapOf("voBalance" to fromVisitBalance?.voBalance.toString(), "pvoBalance" to fromVisitBalance?.pvoBalance.toString()),
      )
    }

    if (isDpsInChargeOfVisitAllocation(movedToPrisoner)) {
      val toVisitBalance = dpsApiService.getVisitBalance(movedToPrisoner)
      // TODO what if it is now null?
      visitBalanceNomisApiService.updateVisitBalance(movedToPrisoner, toVisitBalance?.toNomisUpdateVisitBalanceRequest() ?: UpdateVisitBalanceRequest(0, 0))
      telemetry + mapOf(
        "movedToBalance" to mapOf("voBalance" to toVisitBalance?.voBalance.toString(), "pvoBalance" to toVisitBalance?.pvoBalance.toString()),
      )
    }
    telemetryClient.trackEvent("visitbalance-adjustment-synchronisation-booking-moved", telemetry)
     */
  }

  suspend fun synchronisePrisonerReceived(prisonerReceiveDomainEvent: PrisonerReceiveDomainEvent) {
    // TODO: process receive reason to synchronise
  }

  suspend fun isDpsInChargeOfVisitAllocation(nomisPrisonNumber: String): Boolean = nomisApiService.isServicePrisonOnForPrisoner(serviceCode = VISIT_ALLOCATION_SERVICE, prisonNumber = nomisPrisonNumber)
}

data class VisitBalanceAdjustmentEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: VisitBalanceAdjustmentAdditionalInformation,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value
  fun findNomsNumber() = get(NOMS_NUMBER_TYPE)

  companion object {
    const val NOMS_NUMBER_TYPE = "NOMIS"
  }

  data class Identifier(val type: String, val value: String)
}
data class VisitBalanceAdjustmentAdditionalInformation(
  val adjustmentId: String,
)

fun VisitAllocationPrisonerAdjustmentResponseDto.toNomisCreateVisitBalanceAdjustmentRequest() = CreateVisitBalanceAdjustmentRequest(
  // TODO - pick up adjustment reason code set correctly
  adjustmentReasonCode = "GOV",
  // TODO - check if adjustment date should be passed in
  adjustmentDate = LocalDate.now(),
  visitOrderChange = changeToVoBalance,
  previousVisitOrderCount = voBalance,
  privilegedVisitOrderChange = changeToPvoBalance,
  previousPrivilegedVisitOrderCount = pvoBalance,
  comment = comment,
  // TODO check we don't need these
  // expiryBalance = expiryBalance,
  // expiryDate = expiryDate,
  // endorsedStaffId = endorsedStaffId,
  // authorisedStaffId = authorisedStaffId,
)

fun PrisonerBalanceDto.toNomisUpdateVisitBalanceRequest() = UpdateVisitBalanceRequest(
  remainingVisitOrders = voBalance,
  remainingPrivilegedVisitOrders = pvoBalance,
)
