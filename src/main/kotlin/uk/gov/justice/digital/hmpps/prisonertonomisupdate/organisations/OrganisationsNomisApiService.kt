package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference

@Service
class OrganisationsNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "OrganisationsNomisApiService"),
  )

  suspend fun getCorporateOrganisation(nomisCorporateId: Long): CorporateOrganisation = webClient.get()
    .uri(
      "/corporates/{corporateId}",
      nomisCorporateId,
    )
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getCorporateOrganisationIds(
    pageNumber: Long = 0,
    pageSize: Long = 1,
  ): PageImpl<CorporateOrganisationIdResponse> = webClient.get()
    .uri {
      it.path("/corporates/ids")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<CorporateOrganisationIdResponse>>())
    .retryWhen(backoffSpec)
    .awaitSingle()
}
