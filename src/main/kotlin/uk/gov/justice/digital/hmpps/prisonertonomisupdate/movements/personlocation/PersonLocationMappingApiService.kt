package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.PersonLocationMigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationBookingMappingDto

@Service
class PersonLocationMappingApiService(@Qualifier("mappingWebClient") webClient: WebClient) {

  private val migrationApi = PersonLocationMigrationResourceApi(webClient)

  suspend fun getMappings(bookingIds: List<Long>): List<PersonLocationBookingMappingDto> = migrationApi.getPersonLocationMappingsByBookingIds(bookingIds).awaitSingle()
}
