package com.pogoascent.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** One shared, lenient JSON configuration: data files may omit keys (defaults apply) and add comments-free extras. */
@OptIn(ExperimentalSerializationApi::class)
object GameJson {
  val pretty: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
    coerceInputValues = true
  }
  val compact: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
  }
}
