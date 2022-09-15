package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class IncentivesApiService(@Qualifier("incentivesApiWebClient") private val webClient: WebClient) {

  fun getIncentive(incentiveId: Long): IepDetail {
    return webClient.get()
      .uri("/iep/reviews/id/$incentiveId")
      .retrieve()
      .bodyToMono(IepDetail::class.java)
      .block()!!
  }
}

enum class ReviewType {
  INITIAL, REVIEW, TRANSFER, ADJUSTMENT
}

data class IepDetail(
  val id: Long? = null,
  val iepCode: String,
  val iepLevel: String,
  val comments: String? = null,
  val prisonerNumber: String? = null,
  val bookingId: Long,
  val sequence: Long,
  val iepDate: LocalDate,
  val iepTime: LocalDateTime,
  val agencyId: String,
  val locationId: String? = null,
  val userId: String?,
  val reviewType: ReviewType? = null,
  val auditModuleName: String? = null,
)
