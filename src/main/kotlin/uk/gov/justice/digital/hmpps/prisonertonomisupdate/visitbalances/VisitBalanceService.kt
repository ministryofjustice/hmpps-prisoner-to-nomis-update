package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitBalanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.BalanceResetDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.BookingMovedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.VisitAllocationPrisonerAdjustmentResponseDto

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
    val prisonNumber = event.personReference.findNomsNumber()!!
    val telemetry = telemetryOf(
      "visitBalanceAdjustmentId" to visitBalanceAdjustmentId,
      "prisonNumber" to prisonNumber,
    )
    // only process if the balance has actually changed
    if (event.additionalInformation.hasBalanceChanged) {
      // We can assume it is safe to consume this event as it will only fire when Dps is in charge Of Visit Allocation
      dpsApiService.getVisitBalanceAdjustment(
        prisonNumber = prisonNumber,
        visitBalanceAdjustmentId = visitBalanceAdjustmentId,
      ).also {
        visitBalanceNomisApiService.createVisitBalanceAdjustment(
          it.prisonerId,
          it.toNomisCreateVisitBalanceAdjustmentRequest(),
        )
        it.changeToVoBalance?.let { telemetry["visitBalanceChange"] = it }
        it.changeToPvoBalance?.let { telemetry["privilegeVisitBalanceChange"] = it }
        telemetryClient.trackEvent("visitbalance-synchronisation-adjustment-created-success", telemetry)
      }
    } else {
      telemetryClient.trackEvent("visitbalance-synchronisation-adjustment-ignored", telemetry)
    }
  }

  suspend fun synchronisePrisonerBookingMoved(bookingMovedEvent: BookingMovedEvent) {
    with(bookingMovedEvent.additionalInformation) {
      synchronisePrisoner(movedFromNomsNumber, "bookingId" to bookingId, "booking-moved-from")
      synchronisePrisoner(movedToNomsNumber, "bookingId" to bookingId, "booking-moved-to")
    }
  }

  suspend fun synchronisePrisoner(
    prisonNumber: String,
    extraTelemetry: Pair<String, Any>? = null,
    eventTypeSuffix: String,
  ) {
    val telemetry = telemetryOf("prisonNumber" to prisonNumber)
    extraTelemetry?.let { telemetry.plusAssign(extraTelemetry) }
    if (isDpsInChargeOfVisitAllocation(prisonNumber)) {
      val visitBalance = dpsApiService.getVisitBalance(prisonNumber)
      visitBalanceNomisApiService.updateVisitBalance(
        prisonNumber,
        visitBalance?.toNomisUpdateVisitBalanceRequest() ?: UpdateVisitBalanceRequest(null, null),
      )
      telemetry.put("voBalance", visitBalance?.voBalance.toString())
      telemetry.put("pvoBalance", visitBalance?.pvoBalance.toString())
      telemetryClient.trackEvent("visitbalance-synchronisation-$eventTypeSuffix-success", telemetry)
    } else {
      telemetryClient.trackEvent("visitbalance-synchronisation-$eventTypeSuffix-ignored", telemetry)
    }
  }

  suspend fun synchroniseBalanceReset(balanceResetDomainEvent: BalanceResetDomainEvent) {
    with(balanceResetDomainEvent.additionalInformation) {
      synchronisePrisoner(prisonNumber = prisonerId, eventTypeSuffix = "balance-reset")
    }
  }

  suspend fun isDpsInChargeOfVisitAllocation(nomisPrisonNumber: String): Boolean = nomisApiService.isAgencySwitchOnForPrisoner(
    serviceCode = VISIT_ALLOCATION_SERVICE,
    prisonNumber = nomisPrisonNumber,
  )
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
  val hasBalanceChanged: Boolean,
)

fun VisitAllocationPrisonerAdjustmentResponseDto.toNomisCreateVisitBalanceAdjustmentRequest() = CreateVisitBalanceAdjustmentRequest(
  adjustmentDate = changeTimestamp.toLocalDate(),
  visitOrderChange = changeToVoBalance,
  previousVisitOrderCount = voBalance,
  privilegedVisitOrderChange = changeToPvoBalance,
  previousPrivilegedVisitOrderCount = pvoBalance,
  comment = comment,
  authorisedUsername = userId.takeIf { changeLogSource != VisitAllocationPrisonerAdjustmentResponseDto.ChangeLogSource.SYSTEM },
)

fun PrisonerBalanceDto.toNomisUpdateVisitBalanceRequest() = UpdateVisitBalanceRequest(
  remainingVisitOrders = voBalance,
  remainingPrivilegedVisitOrders = pvoBalance,
)
