package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonInsertReligionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonMergeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonReligionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOffenderPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@SpringAPIServiceTest
@Import(
  CorePersonNomisApiService::class,
  CorePersonNomisApiMockServer::class,
  RetryApiService::class,
)
class CorePersonNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CorePersonNomisApiService

  @Autowired
  private lateinit var mockServer: CorePersonNomisApiMockServer

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  @Nested
  inner class GetCorePersonForReconciliation {
    @Test
    fun `will pass prison number to service`() = runTest {
      mockServer.stubGetCorePersonForReconciliation("A1234BC")

      apiService.getPrisonerForReconciliation(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A1234BC/reconciliation")),
      )
    }

    @Test
    fun `will return core person`() = runTest {
      mockServer.stubGetCorePersonForReconciliation(
        prisonNumber = "A1234BC",
        response = corePerson(prisonNumber = "A1234BC", religion = "JEHV"),
      )

      val corePerson = apiService.getPrisonerForReconciliation(prisonNumber = "A1234BC")!!

      assertThat(corePerson.beliefs!!.first().belief.code).isEqualTo("JEHV")
    }
  }

  @Nested
  inner class GetCorePersonReligion {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetCorePersonReligions("A1234BC")

      apiService.getPrisonerReligions(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A1234BC/religions")),
      )
    }

    @Test
    fun `will return core person`() = runTest {
      mockServer.stubGetCorePersonReligions(
        prisonNumber = "A1234BC",
        corePersonReligions(
          religion = "JEHV",
        ),
      )

      val corePerson = apiService.getPrisonerReligions(prisonNumber = "A1234BC")!!

      assertThat(corePerson.first().belief.code).isEqualTo("JEHV")
    }
  }

  @Nested
  inner class MergeReligions {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubMergeCorePersonReligions("A1234BC")

      apiService.mergeReligions("A1234BC", mergeRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/core-person/A1234BC/merge")),
      )
    }

    @Test
    fun `will post merge request`() = runTest {
      val mergeRequest = mergeRequest()

      mockServer.stubMergeCorePersonReligions("A1234BC")

      apiService.mergeReligions("A1234BC", mergeRequest)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/core-person/A1234BC/merge"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(mergeRequest))),
      )
    }
  }

  @Nested
  inner class InsertReligion {
    private val prisonNumber = "A1234BC"
    private val request = CorePersonInsertReligionRequest(
      beliefCode = "JEHV",
      startDate = LocalDate.parse("2024-01-01"),
      comments = "Updated religion",
    )

    @Test
    fun `will post the prisoner religion request`() = runTest {
      mockServer.stubInsertReligion(prisonNumber)

      apiService.insertReligion(prisonNumber, request)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/religion"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(request))),
      )
    }

    @Test
    fun `will return the inserted religion id`() = runTest {
      mockServer.stubInsertReligion(prisonNumber, beliefId = 98765L)

      val beliefId = apiService.insertReligion(prisonNumber, request)

      assertThat(beliefId).isEqualTo(98765L)
    }
  }

  @Nested
  inner class GetRootOffenderId {
    private val prisonNumber = "A1234BC"

    @Test
    fun `will request the prisoner details`() = runTest {
      mockServer.stubGetPrisonerDetails(prisonNumber)

      apiService.getRootOffenderId(prisonNumber)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/$prisonNumber")),
      )
    }

    @Test
    fun `will return the root offender id`() = runTest {
      mockServer.stubGetPrisonerDetails(prisonNumber, rootOffenderId = 98765L)

      val rootOffenderId = apiService.getRootOffenderId(prisonNumber)

      assertThat(rootOffenderId).isEqualTo(98765L)
    }
  }

  @Nested
  inner class CreateOffenderPhone {
    private val offenderId = 12345L
    private val request = CreateOffenderPhoneRequest(
      number = "07700 900000",
      extension = "123",
      typeCode = "MOB",
    )

    @Test
    fun `will post the phone request`() = runTest {
      mockServer.stubCreateOffenderPhone(offenderId)

      apiService.createOffenderPhone(offenderId, request)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/core-person/$offenderId/phone"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(request))),
      )
    }

    @Nested
    inner class CreateOffenderAddressPhone {
      private val offenderId = 12345L
      private val addressId = 67890L
      private val request = CreateOffenderPhoneRequest(
        number = "07700 900000",
        extension = "123",
        typeCode = "MOB",
      )

      @Test
      fun `will post the phone request for an address`() = runTest {
        mockServer.stubCreateOffenderAddressPhone(offenderId, addressId)

        apiService.createOffenderAddressPhone(offenderId, addressId, request)

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/core-person/$offenderId/address/$addressId/phone"))
            .withRequestBody(equalToJson(jsonMapper.writeValueAsString(request))),
        )
      }

      @Test
      fun `will return the created phone id`() = runTest {
        mockServer.stubCreateOffenderAddressPhone(offenderId, addressId, phoneId = 98765L)

        val response = apiService.createOffenderAddressPhone(offenderId, addressId, request)

        assertThat(response.phoneId).isEqualTo(98765L)
      }
    }

    @Test
    fun `will return the created phone id`() = runTest {
      mockServer.stubCreateOffenderPhone(offenderId, phoneId = 98765L)

      val response = apiService.createOffenderPhone(offenderId, request)

      assertThat(response.phoneId).isEqualTo(98765L)
    }
  }

  @Nested
  inner class CreateOffenderEmail {
    private val offenderId = 12345L
    private val request = CreateOffenderEmailRequest(email = "test@justice.gov.uk")

    @Test
    fun `will post the email request`() = runTest {
      mockServer.stubCreateOffenderEmail(offenderId)

      apiService.createOffenderEmail(offenderId, request)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/core-person/$offenderId/email"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(request))),
      )
    }

    @Test
    fun `will return the created email address id`() = runTest {
      mockServer.stubCreateOffenderEmail(offenderId, emailAddressId = 98765L)

      val response = apiService.createOffenderEmail(offenderId, request)

      assertThat(response.emailAddressId).isEqualTo(98765L)
    }
  }

  @Nested
  inner class UpdateOffenderPhone {
    private val offenderId = 12345L
    private val phoneId = 54321L
    private val request = UpdateOffenderPhoneRequest(
      number = "07700 900000",
      extension = "123",
      typeCode = "MOB",
    )

    @Test
    fun `will put the phone request`() = runTest {
      mockServer.stubUpdateOffenderPhone(offenderId, phoneId)

      apiService.updateOffenderPhone(offenderId, phoneId, request)

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/core-person/$offenderId/phone/$phoneId"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(request))),
      )
    }
  }

  @Nested
  inner class UpdateOffenderAddressPhone {
    private val offenderId = 12345L
    private val addressId = 67890L
    private val phoneId = 54321L
    private val request = UpdateOffenderPhoneRequest(
      number = "07700 900000",
      extension = "123",
      typeCode = "MOB",
    )

    @Test
    fun `will put the phone request for an address`() = runTest {
      mockServer.stubUpdateOffenderAddressPhone(offenderId, addressId, phoneId)

      apiService.updateOffenderAddressPhone(offenderId, addressId, phoneId, request)

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/core-person/$offenderId/address/$addressId/phone/$phoneId"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(request))),
      )
    }
  }

  @Nested
  inner class UpdateOffenderEmail {
    private val offenderId = 12345L
    private val emailAddressId = 54321L
    private val request = UpdateOffenderEmailRequest(email = "test@justice.gov.uk")

    @Test
    fun `will put the email request`() = runTest {
      mockServer.stubUpdateOffenderEmail(offenderId, emailAddressId)

      apiService.updateOffenderEmail(offenderId, emailAddressId, request)

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/core-person/$offenderId/email/$emailAddressId"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(request))),
      )
    }
  }

  private fun mergeRequest() = CorePersonMergeRequest(
    listOf(
      CorePersonReligionRequest(
        beliefId = 12345L,
        endDate = LocalDate.parse("2024-01-01"),
      ),
    ),
  )
}
