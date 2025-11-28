import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

data class Repo(
  val key: String,
  val state: String,
  val description: String? = null,
  val portal_deployment_id: String? = null
) {
  val encodedKey get() = java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8)
  val showName get() = description?.let { "${it}-${key}" } ?: key
}

data class Wrapper(val repositories: List<Repo>) {
  val repositoriesSorted = repositories.sortedBy { it.showName }
}

fun HttpClient.listStagingRepos(user: String, pass: String): Wrapper {
  val pid   = "io.exoquery"
  val auth     = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
  val request  = HttpRequest.newBuilder()
    .uri(URI.create("https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?profile_id=$pid&ip=any"))
    .header("Content-Type", "application/json")
    .header("Authorization", "Basic $auth")
    .GET()
    .build()

  val mapper = jacksonObjectMapper()

  fun tryPrintJson(json: String) {
    try {
      mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
    } catch (e: Exception) {
      json
    }
  }
  val response = this.send(request, HttpResponse.BodyHandlers.ofString())
  println("================ /manual/search/repositories Response Code: ${response.statusCode()}: ================\n${tryPrintJson(response.body())}")

  /* 1.  Sanity-check the HTTP call */
  if (response.statusCode() !in 200..299) {
    val msg = "================ OSS RH search failed Code:${response.statusCode()} ================\n${response.body()}"
    logger.error(msg)
    throw GradleException("Search request was not successful because of:\n${msg}")
  }

  val payload: Wrapper = mapper.readValue<Wrapper>(response.body())
  return payload
}

val publishSonatypeStaging by tasks.registering {
  description = "Creates a new OSSRH staging repository and records its ID"

  doLast {
    /* ---- gather inputs exactly as before ---- */
    val user  = System.getenv("SONATYPE_USERNAME")   ?: error("SONATYPE_USERNAME not set")
    val pass  = System.getenv("SONATYPE_PASSWORD")   ?: error("SONATYPE_PASSWORD not set")
    val http = HttpClient.newHttpClient()
    val auth = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())

    /* Pick the repositories whose description matches `desc` */
    val matching = http.listStagingRepos(user, pass).repositoriesSorted.filter { it.portal_deployment_id == null }

    if (matching.isEmpty()) {
      logger.lifecycle("No repositories found.")
      return@doLast
    } else {
      println("---------------- Found ${matching.size} matching repositories ---------------\n${matching.joinToString("\n")}")
    }

    var ok = 0
    var failed = 0

    matching.forEach { repo ->
      println("==== Processing Repo: ${repo.showName} ====")

      // Encode the key exactly like `jq -sRr @uri`
      val enc = repo.encodedKey
      val promoteRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/$enc?publishing_type=user_managed"))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .header("Authorization", "Basic $auth")
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build()

      val promoteResp = http.send(promoteRequest, HttpResponse.BodyHandlers.ofString())
      if (promoteResp.statusCode() in 200..299) {
        println("--- Promoted staging repo ${repo.showName} ---")
        ok++
      } else {
        println("--- Failed to promote repo ${repo.showName} - HTTP Code ${promoteResp.statusCode()} ---\n${promoteResp.body()}")
        failed++
      }
    }
    println("==== Processing of Repos Completed ====")

    if (failed > 0) {
      throw GradleException("Some repositories failed to publish: $failed of ${matching.size}")
    } else {
      println("All $ok staging repositories successfully switched to user-managed.")
    }
  }
}
