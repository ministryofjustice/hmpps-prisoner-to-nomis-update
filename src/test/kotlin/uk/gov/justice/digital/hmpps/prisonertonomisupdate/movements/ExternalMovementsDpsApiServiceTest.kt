package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiExtension.Companion.dpsExternalMovementsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.UUID

@SpringAPIServiceTest
@Import(
  ExternalMovementsDpsApiService::class,
  ExternalMovementsDpsApiMockServer::class,
  ExternalMovementsConfiguration::class,
  RetryApiService::class,
)
class ExternalMovementsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: ExternalMovementsDpsApiService

  @Nested
  inner class GetTapAuthorisation {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      val id = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapAuthorisation(id)

      apiService.getTapAuthorisation(id)

      dpsExternalMovementsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      val id = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapAuthorisation(id)

      apiService.getTapAuthorisation(id)

      dpsExternalMovementsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$id")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      val id = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapAuthorisation(id)

      with(apiService.getTapAuthorisation(id)) {
        assertThat(this.id).isEqualTo(id)
        assertThat(statusCode).isEqualTo("PENDING")
      }
    }

    @Test
    fun `will throw if error`() = runTest {
      val id = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapAuthorisationError(id)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapAuthorisation(id)
      }
    }
  }

  @Nested
  inner class GetTapOccurrence {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      val id = UUID.randomUUID()
      val authorisationId = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapOccurrence(id, authorisationId)

      apiService.getTapOccurrence(id)

      dpsExternalMovementsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      val id = UUID.randomUUID()
      val authorisationId = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapOccurrence(id, authorisationId)

      apiService.getTapOccurrence(id)

      dpsExternalMovementsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/temporary-absence-occurrences/$id")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      val id = UUID.randomUUID()
      val authorisationId = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapOccurrence(id, authorisationId)

      with(apiService.getTapOccurrence(id)) {
        assertThat(this.id).isEqualTo(id)
        assertThat(authorisation.id).isEqualTo(authorisationId)
        assertThat(location.address).isEqualTo("agency address")
      }
    }

    @Test
    fun `will throw if error`() = runTest {
      val id = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapOccurrenceError(id)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapOccurrence(id)
      }
    }
  }

  @Nested
  inner class GetTapMovement {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      val id = UUID.randomUUID()
      val occurrenceId = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapMovement(id, occurrenceId)

      apiService.getTapMovement(id)

      dpsExternalMovementsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      val id = UUID.randomUUID()
      val occurrenceId = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapMovement(id, occurrenceId)

      apiService.getTapMovement(id)

      dpsExternalMovementsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/$id")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      val id = UUID.randomUUID()
      val occurrenceId = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapMovement(id, occurrenceId)

      with(apiService.getTapMovement(id)) {
        assertThat(this.id).isEqualTo(id)
        assertThat(occurrenceId).isEqualTo(occurrenceId)
        assertThat(location.address).isEqualTo("agency address")
      }
    }

    @Test
    fun `will throw if error`() = runTest {
      val id = UUID.randomUUID()
      dpsExternalMovementsServer.stubGetTapMovementError(id)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapMovement(id)
      }
    }
  }

  @Nested
  inner class GetTapReconciliation {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      val personIdentifier = "A1234BC"
      dpsExternalMovementsServer.stubGetTapReconciliation(personIdentifier)

      apiService.getTapReconciliation(personIdentifier)

      dpsExternalMovementsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      val personIdentifier = "A1234BC"
      dpsExternalMovementsServer.stubGetTapReconciliation(personIdentifier)

      apiService.getTapReconciliation(personIdentifier)

      dpsExternalMovementsServer.verify(
        getRequestedFor(urlPathEqualTo("/reconciliation/$personIdentifier/temporary-absences")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      val personIdentifier = "A1234BC"
      dpsExternalMovementsServer.stubGetTapReconciliation(personIdentifier)

      with(apiService.getTapReconciliation(personIdentifier)) {
        assertThat(authorisations.count).isEqualTo(1)
        assertThat(occurrences.count).isEqualTo(2)
        assertThat(movements.scheduled.outCount).isEqualTo(3)
        assertThat(movements.scheduled.inCount).isEqualTo(4)
        assertThat(movements.unscheduled.outCount).isEqualTo(5)
        assertThat(movements.unscheduled.inCount).isEqualTo(6)
      }
    }

    @Test
    fun `will throw if error`() = runTest {
      val personIdentifier = "A1234BC"
      dpsExternalMovementsServer.stubGetTapReconciliation(personIdentifier, status = 500)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapReconciliation(personIdentifier)
      }
    }
  }
}
