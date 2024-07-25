package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.net.SocketException
import java.time.Duration

@Service
class RetryApiService(
  @Value("\${hmpps.web-client.max-retries:3}") private val maxRetryAttempts: Long,
  @Value("\${hmpps.web-client.backoff-millis:100}") private val backoffMillis: Long,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getBackoffSpec(maxRetryAttempts: Long?, backoffMillis: Long?): RetryBackoffSpec =
    Retry.backoff(
      maxRetryAttempts ?: this.maxRetryAttempts,
      Duration.ofMillis(backoffMillis ?: this.backoffMillis),
    )
      .filter { isTimeoutException(it) }
      .doBeforeRetry { logRetrySignal(it) }

  private fun isTimeoutException(it: Throwable): Boolean =
    // Timeout for NO_RESPONSE is wrapped in a WebClientRequestException
    it is ReadTimeoutException || it.cause is ReadTimeoutException ||
      it is ConnectTimeoutException || it.cause is ConnectTimeoutException ||
      it is SocketException || it.cause is SocketException

  private fun logRetrySignal(retrySignal: Retry.RetrySignal) {
    val failure = retrySignal.failure()
    val exception = failure?.cause ?: failure
    val message = exception.message ?: exception.javaClass.canonicalName
    log.warn("Retrying due to [{}], retry number: {}", message, retrySignal.totalRetries())
  }
}
