package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitBalanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.BookingMovedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PrisonerReceiveDomainEvent
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

    dpsApiService.getVisitBalanceAdjustment(prisonNumber = prisonNumber, visitBalanceAdjustmentId = visitBalanceAdjustmentId).also {
      visitBalanceNomisApiService.createVisitBalanceAdjustment(
        it.prisonerId,
        it.toNomisCreateVisitBalanceAdjustmentRequest(),
      )
      val telemetry = telemetryOf(
        "visitBalanceAdjustmentId" to visitBalanceAdjustmentId,
        "prisonNumber" to it.prisonerId,
      )
      it.changeToVoBalance?.let { telemetry["visitBalanceChange"] = it }
      it.changeToPvoBalance?.let { telemetry["privilegeVisitBalanceChange"] = it }
      telemetryClient.trackEvent(
        "visitbalance-adjustment-synchronisation-created-success",
        telemetry,
      )
    }
  }

  suspend fun synchronisePrisonerBookingMoved(bookingMovedEvent: BookingMovedEvent) {
    with(bookingMovedEvent.additionalInformation) {
      synchronisePrisoner(movedFromNomsNumber, "bookingId" to bookingId, "booking-moved-from")
      synchronisePrisoner(movedToNomsNumber, "bookingId" to bookingId, "booking-moved-to")
    }
  }

  private suspend fun synchronisePrisoner(
    prisonNumber: String,
    extraTelemetry: Pair<String, Any>,
    eventTypeSuffix: String,
  ) {
    if (isDpsInChargeOfVisitAllocation(prisonNumber)) {
      val fromVisitBalance = dpsApiService.getVisitBalance(prisonNumber)
      visitBalanceNomisApiService.updateVisitBalance(
        prisonNumber,
        fromVisitBalance?.toNomisUpdateVisitBalanceRequest() ?: UpdateVisitBalanceRequest(null, null),
      )
      telemetryClient.trackEvent(
        "visitbalance-adjustment-synchronisation-$eventTypeSuffix",
        mapOf(
          "prisonNumber" to prisonNumber,
          extraTelemetry,
          "voBalance" to fromVisitBalance?.voBalance.toString(),
          "pvoBalance" to fromVisitBalance?.pvoBalance.toString(),
        ),
      )
    }
  }

  suspend fun synchronisePrisonerReceived(prisonerReceiveDomainEvent: PrisonerReceiveDomainEvent) {
    with(prisonerReceiveDomainEvent.additionalInformation) {
      synchronisePrisoner(nomsNumber, "reason" to reason, "prisoner-received")
    }
  }

  suspend fun isDpsInChargeOfVisitAllocation(nomisPrisonNumber: String): Boolean = nomisApiService.isServicePrisonOnForPrisoner(
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
