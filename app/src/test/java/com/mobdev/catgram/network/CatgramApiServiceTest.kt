package com.mobdev.catgram.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mobdev.catgram.data.NetworkCatgramApiRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class CatgramApiServiceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `api key header is sent for image and breed requests`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val service = createService("test-api-key")
        val repository = NetworkCatgramApiRepository(service)

        repository.getCatsData(
            limit = 10,
            breedIds = listOf("abys", "beng"),
            page = 0,
        )
        service.getBreedList()

        val imagesRequest = server.takeRequest()
        val breedsRequest = server.takeRequest()
        assertEquals("test-api-key", imagesRequest.getHeader("x-api-key"))
        assertEquals("test-api-key", breedsRequest.getHeader("x-api-key"))
        assertEquals("/v1/images/search", imagesRequest.requestUrl?.encodedPath)
        assertEquals("/v1/breeds", breedsRequest.requestUrl?.encodedPath)
        assertNull(imagesRequest.requestUrl?.queryParameter("api_key"))
        assertEquals("abys,beng", imagesRequest.requestUrl?.queryParameter("breed_ids"))
        assertEquals(
            1,
            imagesRequest.requestUrl?.queryParameterValues("breed_ids")?.size,
        )
        assertFalse(imagesRequest.body.readUtf8().contains("test-api-key"))
    }

    @Test
    fun `empty breed selection omits breed ids query parameter`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val repository = NetworkCatgramApiRepository(createService("test-api-key"))

        repository.getCatsData(limit = 10, breedIds = emptyList(), page = 0)

        val request = server.takeRequest()
        assertNull(request.requestUrl?.queryParameter("breed_ids"))
    }

    @Test
    fun `blank api key fails before a network request is made`() = runBlocking {
        val service = createService("   ")

        val error = runCatching { service.getBreedList() }.exceptionOrNull()

        assertTrue(error is CatApiConfigurationException)
        assertEquals(0, server.requestCount)
    }

    private fun createService(apiKey: String): CatgramApiService {
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .addInterceptor(CatApiKeyInterceptor(apiKey))
            .build()
        return Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CatgramApiService::class.java)
    }
}
