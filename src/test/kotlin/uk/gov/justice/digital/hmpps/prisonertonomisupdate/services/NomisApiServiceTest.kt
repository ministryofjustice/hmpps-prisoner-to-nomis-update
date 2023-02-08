package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@SpringAPIServiceTest
@Import(NomisApiService::class)
internal class NomisApiServiceTest {

  @Autowired
  private lateinit var nomisApiService: NomisApiService

  @Nested
  inner class CreateVisit {

    @Test
    fun `should call nomis api with OAuth2 token`() {
      NomisApiExtension.nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit(newVisit())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post visit data to nomis api`() {
      NomisApiExtension.nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit(newVisit(offenderNo = "AB123D"))

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withRequestBody(matchingJsonPath("$.offenderNo", equalTo("AB123D")))
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitCreateWithError("AB123D", 404)

      assertThatThrownBy {
        nomisApiService.createVisit(newVisit())
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitCreateWithError("AB123D", 503)

      assertThatThrownBy {
        nomisApiService.createVisit(newVisit())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class CancelVisit {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitCancel(prisonerId = "AB123D", visitId = "12")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() {
      nomisApiService.cancelVisit(cancelVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12/cancel"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post cancel request to nomis api`() {
      nomisApiService.cancelVisit(cancelVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12/cancel"))
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitCancelWithError("AB123D", "12", 404)

      assertThatThrownBy {
        nomisApiService.cancelVisit(cancelVisit())
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitCancelWithError("AB123D", "12", 503)

      assertThatThrownBy {
        nomisApiService.cancelVisit(cancelVisit())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class UpdateVisit {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitUpdate(prisonerId = "AB123D", visitId = "12")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() {
      nomisApiService.updateVisit("AB123D", "12", updateVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post cancel request to nomis api`() {
      nomisApiService.updateVisit("AB123D", "12", updateVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12"))
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitUpdateWithError("AB123D", "12", 404)

      assertThatThrownBy {
        nomisApiService.updateVisit("AB123D", "12", updateVisit())
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitUpdateWithError("AB123D", "12", 503)

      assertThatThrownBy {
        nomisApiService.updateVisit("AB123D", "12", updateVisit())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class CreateIncentive {

    @Test
    fun `should call nomis api with OAuth2 token`() {
      NomisApiExtension.nomisApi.stubIncentiveCreate(456)

      nomisApiService.createIncentive(456, newIncentive())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/456/incentives"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to nomis api`() {
      NomisApiExtension.nomisApi.stubIncentiveCreate(456)

      nomisApiService.createIncentive(456, newIncentive())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/456/incentives"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("MDI")))
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubIncentiveCreateWithError(456, 404)

      assertThatThrownBy {
        nomisApiService.createIncentive(456, newIncentive())
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubIncentiveCreateWithError(456, 503)

      assertThatThrownBy {
        nomisApiService.createIncentive(456, newIncentive())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class CreateActivity {

    @Test
    fun `should call nomis api with OAuth2 token`() {
      NomisApiExtension.nomisApi.stubActivityCreate("""{ "courseActivityId": 456 }""")

      nomisApiService.createActivity(newActivity())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to nomis api`() {
      NomisApiExtension.nomisApi.stubActivityCreate("""{ "courseActivityId": 456 }""")

      nomisApiService.createActivity(newActivity())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("WWI")))
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubActivityCreateWithError(503)

      assertThatThrownBy {
        nomisApiService.createActivity(newActivity())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class Allocation {

    @Test
    fun `should call nomis api with OAuth2 token`() {
      NomisApiExtension.nomisApi.stubAllocationCreate(12)

      nomisApiService.createAllocation(12, newAllocation())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities/12"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to nomis api`() {
      NomisApiExtension.nomisApi.stubAllocationCreate(12)

      nomisApiService.createAllocation(12, newAllocation())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities/12"))
          .withRequestBody(matchingJsonPath("$.bookingId", equalTo("456")))
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubAllocationCreateWithError(12, 400)

      assertThatThrownBy {
        nomisApiService.createAllocation(12, newAllocation())
      }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class Deallocation {

    @Test
    fun `should call nomis api with OAuth2 token`() {
      NomisApiExtension.nomisApi.stubDeallocate(12, 456)

      nomisApiService.deallocate(12, 456, newDeallocation())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/12/booking-id/456/end"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to nomis api`() {
      NomisApiExtension.nomisApi.stubDeallocate(12, 456)

      nomisApiService.deallocate(12, 456, newDeallocation())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/12/booking-id/456/end"))
          .withRequestBody(matchingJsonPath("$.endDate", equalTo("2023-01-21")))
          .withRequestBody(matchingJsonPath("$.endReason", equalTo("REASON")))
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubDeallocateWithError(12, 456, 400)

      assertThatThrownBy {
        nomisApiService.deallocate(12, 456, newDeallocation())
      }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class CreateSentenceAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runBlocking {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentCreate(bookingId = 12345, sentenceSequence = 2)

      nomisApiService.createSentenceAdjustment(12345, 2, newSentencingAdjustment())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/sentences/2/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post sentence adjustment data to nomis api`() = runBlocking {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentCreate(bookingId = 12345, sentenceSequence = 2)

      nomisApiService.createSentenceAdjustment(
        bookingId = 12345,
        sentenceSequence = 2,
        request = newSentencingAdjustment(
          adjustmentTypeCode = "RX",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
          adjustmentFomDate = LocalDate.parse("2020-07-19"),
          comment = "Adjusted for remand"
        )
      )

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/sentences/2/adjustments"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
          .withRequestBody(matchingJsonPath("adjustmentFomDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand")))
      )
    }

    @Test
    internal fun `when booking or sentence is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentCreateWithError(
        bookingId = 12345,
        sentenceSequence = 2,
        status = 404
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.createSentenceAdjustment(12345, 2, newSentencingAdjustment()) }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentCreateWithError(
        bookingId = 12345,
        sentenceSequence = 2,
        status = 503
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.createSentenceAdjustment(12345, 2, newSentencingAdjustment()) }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class UpdateSentenceAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runBlocking {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateSentenceAdjustment(98765, updateSentencingAdjustment())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will put sentence adjustment data to nomis api`() = runBlocking {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateSentenceAdjustment(
        98765,
        request = updateSentencingAdjustment(
          adjustmentTypeCode = "RX",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
          adjustmentFomDate = LocalDate.parse("2020-07-19"),
          comment = "Adjusted for remand"
        )
      )

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
          .withRequestBody(matchingJsonPath("adjustmentFomDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand")))
      )
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 404
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.updateSentenceAdjustment(98765, updateSentencingAdjustment()) }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 503
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.updateSentenceAdjustment(98765, updateSentencingAdjustment()) }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class DeleteSentenceAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runBlocking {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteSentenceAdjustment(98765)

      NomisApiExtension.nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will delete sentence adjustment from nomis`() = runBlocking {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteSentenceAdjustment(98765)

      NomisApiExtension.nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
      )
    }

    @Test
    internal fun `if 404 - which is not expected - is returned an exception is thrown`() {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 404
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.deleteSentenceAdjustment(98765) }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 503
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.deleteSentenceAdjustment(98765) }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class CreateKeyDateAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runBlocking {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentCreate(bookingId = 12345)

      nomisApiService.createKeyDateAdjustment(12345, newSentencingAdjustment())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post key date adjustment data to nomis api`() = runBlocking {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentCreate(bookingId = 12345)

      nomisApiService.createKeyDateAdjustment(
        bookingId = 12345,
        request = newSentencingAdjustment(
          adjustmentTypeCode = "ADA",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
        )
      )

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/adjustments"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("ADA")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
      )
    }

    @Test
    internal fun `when booking is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentCreateWithError(
        bookingId = 12345,
        status = 404
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.createKeyDateAdjustment(12345, newSentencingAdjustment()) }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentCreateWithError(
        bookingId = 12345,
        status = 503
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.createKeyDateAdjustment(12345, newSentencingAdjustment()) }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class UpdateKeyDateAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runBlocking {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateKeyDateAdjustment(98765, updateSentencingAdjustment())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will put key date adjustment data to nomis api`() = runBlocking {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateKeyDateAdjustment(
        98765,
        request = updateSentencingAdjustment(
          adjustmentTypeCode = "RX",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
          adjustmentFomDate = LocalDate.parse("2020-07-19"),
          comment = "Adjusted for remand"
        )
      )

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
          .withRequestBody(matchingJsonPath("adjustmentFomDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand")))
      )
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 404
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.updateKeyDateAdjustment(98765, updateSentencingAdjustment()) }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 503
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.updateKeyDateAdjustment(98765, updateSentencingAdjustment()) }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class DeleteKeyDateAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runBlocking {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteKeyDateAdjustment(98765)

      NomisApiExtension.nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will delete key date adjustment from nomis`() = runBlocking {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteKeyDateAdjustment(98765)

      NomisApiExtension.nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
      )
    }

    @Test
    internal fun `if 404 - which is not expected - is returned an exception is thrown`() {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 404
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.deleteKeyDateAdjustment(98765) }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 503
      )

      assertThatThrownBy {
        runBlocking { nomisApiService.deleteKeyDateAdjustment(98765) }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }
}

fun newVisit(offenderNo: String = "AB123D"): CreateVisitDto = CreateVisitDto(
  offenderNo = offenderNo,
  prisonId = "MDI",
  startDateTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT,
  visitorPersonIds = listOf(),
  visitType = "SCON",
  issueDate = LocalDate.now(),
  visitComment = "VSIP ref: 123",
  visitOrderComment = "VO VSIP ref: 123",
  room = "Main visits room",
  openClosedStatus = "CLOSED",
)

fun cancelVisit(): CancelVisitDto = CancelVisitDto(offenderNo = "AB123D", nomisVisitId = "12", outcome = "VISCANC")
fun updateVisit(): UpdateVisitDto = UpdateVisitDto(
  startDateTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT,
  visitorPersonIds = listOf(),
  room = "Main visits room",
  openClosedStatus = "CLOSED",
)

fun newIncentive() = CreateIncentiveDto(
  iepDateTime = LocalDateTime.now(),
  prisonId = "MDI",
  iepLevel = "High"
)

fun newActivity() = CreateActivityRequest(
  code = "code",
  startDate = LocalDate.now(),
  prisonId = "WWI",
  internalLocationId = 703000,
  capacity = 14,
  payRates = emptyList(),
  description = "the description",
  programCode = "IRS",
  payPerSession = "H",
)

fun newAllocation() = CreateOffenderProgramProfileRequest(
  bookingId = 456L,
  startDate = LocalDate.parse("2023-01-20"),
  endDate = LocalDate.parse("2023-01-21"),
  payBandCode = "PAY",
)

fun newDeallocation() = EndOffenderProgramProfileRequest(
  endDate = LocalDate.parse("2023-01-21"),
  endReason = "REASON",
)

private fun newSentencingAdjustment(
  adjustmentTypeCode: String = "RX",
  adjustmentDate: LocalDate = LocalDate.now(),
  adjustmentFomDate: LocalDate? = null,
  adjustmentDays: Long = 99,
  comment: String? = "Adjustment comment"
) = CreateSentencingAdjustmentRequest(
  adjustmentTypeCode = adjustmentTypeCode,
  adjustmentDate = adjustmentDate,
  adjustmentFomDate = adjustmentFomDate,
  adjustmentDays = adjustmentDays,
  comment = comment,
)

private fun updateSentencingAdjustment(
  adjustmentTypeCode: String = "RX",
  adjustmentDate: LocalDate = LocalDate.now(),
  adjustmentFomDate: LocalDate? = null,
  adjustmentDays: Long = 99,
  comment: String? = "Adjustment comment"
) = UpdateSentencingAdjustmentRequest(
  adjustmentTypeCode = adjustmentTypeCode,
  adjustmentDate = adjustmentDate,
  adjustmentFomDate = adjustmentFomDate,
  adjustmentDays = adjustmentDays,
  comment = comment,
)
