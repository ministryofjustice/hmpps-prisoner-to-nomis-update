package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import java.time.LocalDate
import java.util.UUID

@Service
class VisitBalanceDpsApiService(
  private val visitBalanceApiWebClient: WebClient,
) {
  suspend fun getVisitBalance(prisonNumber: String): PrisonerBalanceDto? = visitBalanceApiWebClient.get()
    .uri("/visits/allocation/prisoner/{prisonNumber}/balance", prisonNumber)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getVisitBalanceAdjustment(visitBalanceAdjustmentId: String): VisitBalanceAdjustmentDto = visitBalanceApiWebClient.get()
    .uri("/visits/allocation/adjustment/{visitBalanceAdjustmentId}", visitBalanceAdjustmentId)
    .retrieve()
    .awaitBody()
}

// TODO remove all classes below when set in DPS
data class VisitBalanceAdjustmentDto(
  val id: UUID,
  val prisonerId: String,

  val oldVoBalance: Int? = null,
  val changeToVoBalance: Int? = null,
  val oldPvoBalance: Int? = null,
  val changeToPvoBalance: Int? = null,

  val adjustmentDate: LocalDate,
  val adjustmentReasonCode: VisitBalanceAdjustmentReasonCode,
  val comment: String? = null,

  val expiryBalance: Int? = null,
  val expiryDate: LocalDate? = null,
  val endorsedStaffId: Long? = null,
  val authorisedStaffId: Long? = null,
)

enum class VisitBalanceAdjustmentReasonCode(
  val value: String,
) {
  AUTO_EXP("AUTO_EXP"),
  CANC("CANC"),
  DISC("DISC"),
  GOV("GOV"),
  IEP("IEP"),
  PVO_CANCEL("PVO_CANCEL"),
  PVO_IEP("PVO_IEP"),
  PVO_ISSUE("PVO_ISSUE"),
  VO_CANCEL("VO_CANCEL"),
  VO_ISSUE("VO_ISSUE"),
  VO_RECREDIT("VO_RECREDIT"),
}
