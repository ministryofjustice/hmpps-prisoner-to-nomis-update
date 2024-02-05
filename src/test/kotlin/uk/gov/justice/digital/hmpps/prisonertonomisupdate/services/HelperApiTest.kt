package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class HelperApiTest {

  @Test
  fun `test doApiCallWithSingleRetry with successful call`() = runTest {
    val result = doApiCallWithRetries { DummyApi().api() }

    assertEquals("Success", result)
  }

  @Test
  fun `test doApiCallWithSingleRetry with failed first call and successful retry`() = runBlocking {
    val dummyApi: DummyApi = mock()
    whenever(dummyApi.api())
      .thenThrow(RuntimeException("API call failed"))
      .thenReturn("Mock Success")

    val result = doApiCallWithRetries { dummyApi.api() }

    assertEquals("Mock Success", result)
  }

  @Test
  fun `test doApiCallWithSingleRetry with failed first call and failed retry`() = runTest {
    assertThrows<RuntimeException> { doApiCallWithRetries { DummyApi().apiFails() } }
  }
}

class DummyApi {
  fun api(): String {
    return "Success"
  }

  fun apiFails(): String {
    throw RuntimeException("API call failed")
  }
}
