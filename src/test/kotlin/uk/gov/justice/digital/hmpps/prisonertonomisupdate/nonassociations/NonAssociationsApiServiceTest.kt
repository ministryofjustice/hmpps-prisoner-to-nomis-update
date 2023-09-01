@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApiServer

private const val NON_ASSOCIATION_ID = 1234L

@SpringAPIServiceTest
@Import(NonAssociationsApiService::class, NonAssociationsConfiguration::class)
internal class NonAssociationsApiServiceTest {

  val response = """
    {
      "id": 42,
      "offenderNo": "A1234BC",
      "reasonCode": "VIC",
      "reasonDescription": "Victim",
      "typeCode": "WING",
      "typeDescription": "Do Not Locate on Same Wing",
      "effectiveDate": "2021-07-05T10:35:17",
      "expiryDate": "2021-07-05T10:35:17",
      "authorisedBy": "Officer Alice B.",
      "comments": "Mr. Bloggs assaulted Mr. Hall",
      "offenderNonAssociation": {
        "offenderNo": "B1234CD",
        "reasonCode": "PER",
        "reasonDescription": "Perpetrator"
      }
    }
  """.trimIndent()

  @Autowired
  private lateinit var nonAssociationsApiService: NonAssociationsApiService

  @Nested
  @DisplayName("GET /legacy/api/non-associations/{id}")
  inner class GetNonAssociation {
    @BeforeEach
    internal fun setUp() {
      nonAssociationsApiServer.stubGetNonAssociation(NON_ASSOCIATION_ID, response)
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      nonAssociationsApiService.getNonAssociation(NON_ASSOCIATION_ID)

      nonAssociationsApiServer.verify(
        getRequestedFor(urlEqualTo("/legacy/api/non-associations/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will parse data for a detail field`(): Unit = runTest {
      val na = nonAssociationsApiService.getNonAssociation(NON_ASSOCIATION_ID)

      assertThat(na.offenderNonAssociation.offenderNo).isEqualTo("B1234CD")
    }

    @Test
    fun `when adjudication is not found an exception is thrown`() = runTest {
      nonAssociationsApiServer.stubGetNonAssociationWithError(NON_ASSOCIATION_ID, status = 404)

      assertThrows<NotFound> {
        nonAssociationsApiService.getNonAssociation(NON_ASSOCIATION_ID)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nonAssociationsApiServer.stubGetNonAssociationWithError(NON_ASSOCIATION_ID, status = 503)

      assertThrows<ServiceUnavailable> {
        nonAssociationsApiService.getNonAssociation(NON_ASSOCIATION_ID)
      }
    }
  }
}
