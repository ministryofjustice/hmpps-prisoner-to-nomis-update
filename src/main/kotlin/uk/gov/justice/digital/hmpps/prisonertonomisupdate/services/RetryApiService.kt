package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import io.netty.channel.ConnectTimeoutException
import io.netty.channel.unix.Errors
import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.PrematureCloseException
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

  private fun isTimeoutException(e: Throwable): Boolean =
    // Timeouts etc are wrapped in a WebClientRequestException
    e is ReadTimeoutException || e.cause is ReadTimeoutException ||
      e is ConnectTimeoutException || e.cause is ConnectTimeoutException ||
      e is SocketException || e.cause is SocketException ||
      e is PrematureCloseException || e.cause is PrematureCloseException || // server closed the connection
      e is Errors.NativeIoException || e.cause is Errors.NativeIoException || // Connection reset by peer
      e is WebClientResponseException.BadGateway // 502 is also a transient error worth retrying

  private fun logRetrySignal(retrySignal: Retry.RetrySignal) {
    val failure = retrySignal.failure()
    val exception = failure?.cause ?: failure
    val message = exception.message ?: exception.javaClass.canonicalName
    log.warn(
      "Retrying due to [{}], retry number: {}, context: {}",
      message,
      retrySignal.totalRetries(),
      retrySignal.retryContextView(),
    )
  }
}
