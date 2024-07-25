package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.net.SocketException
import java.time.Duration

abstract class RetryApiService(
  maxRetryAttempts: Long,
  backoffMillis: Long,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val backoffSpec = Retry.backoff(maxRetryAttempts, Duration.ofMillis(backoffMillis))
    .filter { isTimeoutException(it) }
    .doBeforeRetry { logRetrySignal(it) }

  fun <T> Mono<T>.withRetryPolicy(): Mono<T> = this.retryWhen(backoffSpec)

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
