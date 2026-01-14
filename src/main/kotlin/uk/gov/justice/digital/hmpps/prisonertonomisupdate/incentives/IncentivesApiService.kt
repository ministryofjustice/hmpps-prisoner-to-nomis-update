package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class IncentivesApiService(@Qualifier("incentivesApiWebClient") private val webClient: WebClient) {

  suspend fun getIncentive(incentiveId: Long): IepDetail = webClient.get()
    .uri("/incentive-reviews/id/{incentiveId}", incentiveId)
    .retrieve()
    .awaitBody()

  suspend fun getCurrentIncentive(bookingId: Long): IepSummary? = webClient.get()
    .uri("/incentive-reviews/booking/{bookingId}?with-details=false", bookingId)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getGlobalIncentiveLevel(incentiveLevelCode: String): IncentiveLevel = webClient.get()
    .uri("/incentive/levels/{incentiveLevelCode}?with-inactive=true", incentiveLevelCode)
    .retrieve()
    .awaitBody()

  suspend fun getGlobalIncentiveLevelsInOrder(): List<IncentiveLevel> = webClient.get()
    .uri("/incentive/levels?with-inactive=true")
    .retrieve()
    .awaitBody()

  suspend fun getPrisonIncentiveLevel(prisonId: String, incentiveLevelCode: String): PrisonIncentiveLevel = webClient.get()
    .uri("/incentive/prison-levels/{prisonId}/level/{incentiveLevelCode}", prisonId, incentiveLevelCode)
    .retrieve()
    .awaitBody()
}

fun List<IncentiveLevel>.toCodeList(): List<String> = this.map { it.code }

data class IncentiveLevel(
  val code: String,
  val name: String,
  val active: Boolean = true,
  val required: Boolean = false,
)

data class IepDetail(
  val id: Long? = null,
  val iepCode: String,
  val iepLevel: String,
  val comments: String? = null,
  val prisonerNumber: String? = null,
  val bookingId: Long,
  val iepDate: LocalDate,
  val iepTime: LocalDateTime,
  val agencyId: String,
  val userId: String?,
  val auditModuleName: String? = null,
)

data class PrisonIncentiveLevel(
  val levelCode: String,
  val prisonId: String,
  val active: Boolean = true,
  val defaultOnAdmission: Boolean = true,
  val remandTransferLimitInPence: Int?,
  val remandSpendLimitInPence: Int?,
  val convictedTransferLimitInPence: Int?,
  val convictedSpendLimitInPence: Int?,
  val visitOrders: Int?,
  val privilegedVisitOrders: Int?,
)

data class IepSummary(
  val id: Long,
  val prisonerNumber: String,
  val iepCode: String,
)
