package com.mobdev.catgram.data

import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.network.CatgramApiService
import com.mobdev.catgram.network.CatsData.CatsApiData


interface CatgramApiRepository {
    suspend fun getCatsData(
        limit: Int,
        breedIds: List<String>,
        page: Int
    ): List<CatsApiData>

    suspend fun getBreedList(): List<BreedInfo>
}

class NetworkCatgramApiRepository(private val catgramApiService: CatgramApiService) : CatgramApiRepository {
    override suspend fun getCatsData(
        limit: Int,
        breedIds: List<String>,
        page: Int
    ): List<CatsApiData> {
        val apiKey = "live_DLVLuhSCT0Oc54KIL9cYIqIpJeJsYQACOGTL5ajdNIFDSIZxNCibgXMCJRaUXOdE"
        val order = "DESC"
        return catgramApiService.getCatsData(limit, breedIds, page, order, apiKey)
    }

    override suspend fun getBreedList(): List<BreedInfo> {
        return catgramApiService.getBreedList()
    }
}

class MockCatgramApiRepository : CatgramApiRepository {

    private val mockBreeds = listOf(
        BreedInfo(
            id = "abys",
            name = "Abyssinian",
            description = "The Abyssinian is easy to care for, and a joy to have in your home. They're affectionate cats and love both people and other animals."
        ),
        BreedInfo(
            id = "beng",
            name = "Bengal",
            description = "Bengals are a lot of fun to live with, but they're definitely not the cat for everyone. Extremely intelligent, curious and active."
        ),
        BreedInfo(
            id = "siam",
            name = "Siamese",
            description = "The Siamese is a social, intelligent, and vocal cat. They enjoy being around people and are known for their striking blue eyes."
        ),
        BreedInfo(
            id = "pers",
            name = "Persian",
            description = "The Persian is a placid cat that exhibits bursts of kitten-like activity. Known for their long, luxurious coat and calm demeanor."
        ),
        BreedInfo(
            id = "maine",
            name = "Maine Coon",
            description = "The Maine Coon is one of the largest domesticated cat breeds. Known for their intelligence and playful personality."
        )
    )

    private val mockCatsApiData = listOf(
        CatsApiData(
            id = "mock_cat_1",
            url = "https://cdn2.thecatapi.com/images/0XYvRd7oD.jpg",
            breeds = listOf(mockBreeds[0])
        ),
        CatsApiData(
            id = "mock_cat_2",
            url = "https://cdn2.thecatapi.com/images/ozEvzdVM-.jpg",
            breeds = listOf(mockBreeds[1])
        ),
        CatsApiData(
            id = "mock_cat_3",
            url = "https://cdn2.thecatapi.com/images/ai6Jps4sx.jpg",
            breeds = listOf(mockBreeds[2])
        ),
        CatsApiData(
            id = "mock_cat_4",
            url = "https://cdn2.thecatapi.com/images/eHLHxNfsW.jpg",
            breeds = listOf(mockBreeds[3])
        ),
        CatsApiData(
            id = "mock_cat_5",
            url = "https://i.ibb.co/C3F1Z0fL/3e52d1f372eb.jpg",
            breeds = listOf(mockBreeds[4])
        ),
        CatsApiData(
            id = "mock_cat_6",
            url = "https://cdn2.thecatapi.com/images/J2PmlIizw.jpg",
            breeds = listOf(mockBreeds[0])
        ),
        CatsApiData(
            id = "mock_cat_7",
            url = "https://cdn2.thecatapi.com/images/EHG31BKAZ.jpg",
            breeds = listOf(mockBreeds[1])
        ),
        CatsApiData(
            id = "mock_cat_8",
            url = "https://cdn2.thecatapi.com/images/MjWgiD7Gx.jpg",
            breeds = listOf(mockBreeds[2])
        )
    )

    override suspend fun getCatsData(
        limit: Int,
        breedIds: List<String>,
        page: Int
    ): List<CatsApiData> {
        val filteredCats = (if (breedIds.isEmpty()) {
            mockCatsApiData
        } else {
            mockCatsApiData.filter { cat ->
                cat.breeds.any { breed -> breedIds.contains(breed.id) }
            }
        }).let { it + it.map { elem -> elem.copy(id = elem.id + "$")} }

        val startIndex = page * limit
        return filteredCats.drop(startIndex).take(limit)
    }

    override suspend fun getBreedList(): List<BreedInfo> {
        return mockBreeds
    }
}