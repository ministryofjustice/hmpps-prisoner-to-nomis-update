package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCancelledMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitUpdatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.PrisonVisitsService

class PrisonerToNomisTest : SqsIntegrationTestBase() {

  @SpyBean
  private lateinit var prisonVisitsService: PrisonVisitsService

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Test
  fun `will consume a prison visits create message`() {

    val message = prisonVisitCreatedMessage()

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    verify(prisonVisitsService).createVisit()
  }

  @Test
  fun `will consume a prison visits cancel message`() {

    val message = prisonVisitCancelledMessage()

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    verify(prisonVisitsService).cancelVisit()
  }

  @Test
  fun `will consume a prison visits update message`() {

    val message = prisonVisitUpdatedMessage()

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    verify(prisonVisitsService).updateVisit()
  }

  private fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    val messagesOnQueue = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    log.info("Number of messages on prisoner queue: $messagesOnQueue")
    return messagesOnQueue
  }
}
