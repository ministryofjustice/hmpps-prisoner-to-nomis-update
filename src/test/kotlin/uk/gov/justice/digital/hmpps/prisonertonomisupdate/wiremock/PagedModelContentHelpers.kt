package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.fasterxml.jackson.databind.ObjectMapper

fun pagedModelContent(
  content: String,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
) = pagedModelContentWithContent(
  outerContent = "[$content]",
  pageSize = pageSize,
  pageNumber = pageNumber,
  totalElements = totalElements,
)

fun pagedModelContent(
  objectMapper: ObjectMapper,
  content: List<Any>,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
) = pagedModelContentWithContent(
  outerContent = objectMapper.writeValueAsString(content),
  pageSize = pageSize,
  pageNumber = pageNumber,
  totalElements = totalElements,
)
// language=json

private fun pagedModelContentWithContent(
  outerContent: String,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
): String = // language=json
  """
{
  "content": $outerContent,
  "page": {
    "size": $pageSize,
    "number": $pageNumber,
    "totalElements": $totalElements,
    "totalPages": ${totalElements / pageSize + 1}
  }
}
  """.trimIndent()
