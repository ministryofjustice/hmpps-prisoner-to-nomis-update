package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import tools.jackson.databind.json.JsonMapper
import kotlin.text.trimIndent

fun pageContent(
  content: String,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
  size: Int,
) = pageContentWithContent(
  outerContent = "[$content]",
  pageSize = pageSize,
  pageNumber = pageNumber,
  totalElements = totalElements,
  size = size,
)

fun pageContent(
  jsonMapper: JsonMapper,
  content: List<Any>,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
  size: Int,
) = pageContentWithContent(
  outerContent = jsonMapper.writeValueAsString(content),
  pageSize = pageSize,
  pageNumber = pageNumber,
  totalElements = totalElements,
  size = size,
)
// language=json

private fun pageContentWithContent(
  outerContent: String,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
  size: Int,
) = // language=json
  """
{

    "content": $outerContent,
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": $pageSize,
        "pageNumber": $pageNumber,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": ${totalElements / pageSize + 1},
    "totalElements": $totalElements,
    "size": $pageSize,
    "number": $pageNumber,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": $size,
    "empty": false
}
  """.trimIndent()
