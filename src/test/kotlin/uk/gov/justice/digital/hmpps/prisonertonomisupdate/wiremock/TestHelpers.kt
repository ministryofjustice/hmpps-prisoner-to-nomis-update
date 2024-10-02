package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.StringValuePattern

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, pattern: StringValuePattern): RequestPatternBuilder =
  this.withRequestBody(matchingJsonPath(path, pattern))

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, equalTo: Any): RequestPatternBuilder =
  this.withRequestBodyJsonPath(path, equalTo(equalTo.toString()))
