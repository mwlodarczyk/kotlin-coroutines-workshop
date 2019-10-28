package github

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

fun main() = runBlocking(Dispatchers.Default) {
    val username = "<user-name>"
    // Link https://github.com/settings/tokens/new
    // No permissions needed
    val token = "<user-token>"
    val service: GitHubService = createGitHubService(username, token)

    val usersChannel = getContributions(service)
    for (contributions in usersChannel) {
        println(contributions)
    }

    println("Phase 1 is done")

    /*val aggregatedUsersChannel = Channel<List<User>>()
    launch {
        getAggregatedContributionsChannel(service, aggregatedUsersChannel)
    }
    for (aggregatedContributions in aggregatedUsersChannel) {
        println(aggregatedContributions.sortedByDescending { it.contributions })
    }*/
}

@ExperimentalCoroutinesApi
suspend fun CoroutineScope.getContributions(service: GitHubService): ReceiveChannel<List<User>> = produce {
    service.getOrgRepos()
            .forEach { repo -> launch { send(service.getRepoContributors(repo.name)) } }
}

suspend fun getAggregatedContributionsChannel(service: GitHubService, usersChannel: SendChannel<List<User>>) = coroutineScope {
    service.getOrgRepos()
            .map { repo -> async { service.getRepoContributors(repo.name) } }
            .flatMap { it.await() }
            .groupBy { it.login }
            .map { (login, list) -> User(login, list.sumBy { it.contributions }) }
}