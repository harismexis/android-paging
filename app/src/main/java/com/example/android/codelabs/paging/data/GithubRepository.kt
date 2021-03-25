/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.codelabs.paging.data

import android.util.Log
import com.example.android.codelabs.paging.api.GithubService
import com.example.android.codelabs.paging.api.IN_QUALIFIER
import com.example.android.codelabs.paging.model.Repo
import com.example.android.codelabs.paging.model.RepoSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import retrofit2.HttpException
import java.io.IOException

// GitHub page API is 1 based: https://developer.github.com/v3/#pagination
private const val GITHUB_STARTING_PAGE_INDEX = 1

/**
 * Repository class that works with local and remote data sources.
 */
class GithubRepository(private val service: GithubService) {

    companion object {
        private const val NETWORK_PAGE_SIZE = 50
    }

    private val inMemoryCache = mutableListOf<Repo>()

    // shared flow of results, which allows us to broadcast updates so
    // the subscriber will have the latest data
    private val searchResults = MutableSharedFlow<RepoSearchResult>(replay = 1)

    private var lastRequestedPage = GITHUB_STARTING_PAGE_INDEX

    private var isRequestInProgress = false

    /**
     * Search repositories whose names match the query, exposed as a stream of data that will emit
     * every time we get more data from the network.
     */
    suspend fun getSearchResultStream(query: String): Flow<RepoSearchResult> {
        Log.d("GithubRepository", "New query: $query")
        lastRequestedPage = 1
        inMemoryCache.clear()
        requestAndSaveData(query)
        return searchResults
    }

    suspend fun requestMore(query: String) {
        if (isRequestInProgress) return
        val successful = requestAndSaveData(query)
        if (successful) lastRequestedPage++
    }

    suspend fun retry(query: String) {
        if (isRequestInProgress) return
        requestAndSaveData(query)
    }

    private suspend fun requestAndSaveData(query: String): Boolean {
        isRequestInProgress = true
        var successful = false

        val apiQuery = query + IN_QUALIFIER
        try {
            val response = service.searchRepos(apiQuery, lastRequestedPage, NETWORK_PAGE_SIZE)
            Log.d("GithubRepository", "response $response")
            val repos = response.items ?: emptyList()
            inMemoryCache.addAll(repos)
            val filteredRepos = filterRepos(query)
            searchResults.emit(RepoSearchResult.Success(filteredRepos))
            successful = true
        } catch (e: IOException) {
            searchResults.emit(RepoSearchResult.Error(e))
        } catch (e: HttpException) {
            searchResults.emit(RepoSearchResult.Error(e))
        }
        isRequestInProgress = false
        return successful
    }

    private fun filterRepos(query: String): List<Repo> {
        return inMemoryCache.filter {
            filterRepo(it, query)
        }.sortedWith(compareByDescending<Repo> { it.stars }.thenBy { it.name })
    }

    private fun filterRepo(repo: Repo, query: String): Boolean {
        return repo.name.contains(query, true) ||
                (repo.description != null && repo.description.contains(query, true))
    }

}
