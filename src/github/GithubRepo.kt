package github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface GitHubService {
    fun getOrgRepos(callback: (List<Repo>) -> Unit)
    suspend fun getOrgRepos(): List<Repo> = suspendCoroutine { cont ->
        getOrgRepos { cont.resume(it) }
    }

    fun getRepoContributors(repo: String, callback: (List<User>) -> Unit)
    suspend fun getRepoContributors(repo: String): List<User> = suspendCoroutine { cont ->
        getRepoContributors(repo) { cont.resume(it) }
    }
}

fun createGitHubService(username: String, password: String): GitHubService {
    val authToken = "Basic " + Base64.getEncoder().encode("$username:$password".toByteArray()).toString(Charsets.UTF_8)
    val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("Authorization", authToken)
                val request = builder.build()
                chain.proceed(request)
            }
            .build()

    return Retrofit.Builder()
            .baseUrl("https://api.github.com")
            .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
            .client(httpClient)
            .build()
            .create(GitHubServiceApiDef::class.java)
            .let(::GitHubServiceImpl)
}

class GitHubServiceImpl(private val apiService: GitHubServiceApiDef) : GitHubService {
    override fun getOrgRepos(callback: (List<Repo>) -> Unit) =
            apiService.getOrgReposCall().onResponse {
                callback(it.body() ?: throw ApiError(it.code(), it.message()))
            }

    override fun getRepoContributors(repo: String, callback: (List<User>) -> Unit) =
            apiService.getRepoContributorsCall(repo).onResponse {
                callback(it.body() ?: throw ApiError(it.code(), it.message()))
            }
}

class ApiError(val code: Int, message: String) : Throwable(message)

interface GitHubServiceApiDef {
    @GET("orgs/jetbrains/repos?per_page=100")
    fun getOrgReposCall(): Call<List<Repo>>

    @GET("repos/jetbrains/{repo}/contributors?per_page=100")
    fun getRepoContributorsCall(
            @Path("repo") repo: String
    ): Call<List<User>>
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Repo(
        val id: Long,
        val name: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class User(
        val login: String,
        val contributions: Int
)

inline fun <T> Call<T>.onResponse(crossinline callback: (Response<T>) -> Unit) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            callback(response)
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
            throw t
        }
    })
}