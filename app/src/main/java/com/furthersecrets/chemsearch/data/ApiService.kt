package com.furthersecrets.chemsearch.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import kotlin.time.Duration.Companion.seconds

// PubChem PUG REST

interface PubChemApi {

    @GET("compound/name/{name}/cids/JSON")
    suspend fun getCid(
        @Path("name", encoded = true) name: String
    ): CidResponse

    @GET("compound/cid/{cid}/property/MolecularFormula,MolecularWeight,IUPACName,SMILES,ConnectivitySMILES,InChIKey,InChI,Charge/JSON")
    suspend fun getProperties(@Path("cid") cid: Long): PropertiesResponse

    @GET("compound/cid/{cid}/synonyms/JSON")
    suspend fun getSynonyms(@Path("cid") cid: Long): SynonymsResponse

    @GET("compound/cid/{cid}/description/JSON")
    suspend fun getDescription(@Path("cid") cid: Long): DescriptionResponse

    @GET("compound/cid/{cid}/SDF")
    suspend fun getSdf(
        @Path("cid") cid: Long,
        @Query("record_type") recordType: String = "3d"
    ): ResponseBody

    @GET("compound/fastformula/{formula}/cids/JSON")
    suspend fun getIsomerCids(
        @Path("formula", encoded = true) formula: String,
        @Query("MaxRecords") maxRecords: Int = 20
    ): CidResponse

    @GET("compound/cid/{cids}/property/Title/JSON")
    suspend fun getTitles(
        @Path("cids", encoded = true) cids: String
    ): TitleResponse
}

// PubChem Autocomplete

interface PubChemAutocompleteApi {

    @GET("compound/{query}/JSON")
    suspend fun autocomplete(
        @Path("query", encoded = true) query: String,
        @Query("limit") limit: Int = 8
    ): AutocompleteResponse
}

// Wikipedia

interface WikiApi {

    @GET("page/summary/{title}")
    suspend fun getSummary(
        @Path("title", encoded = false) title: String
    ): WikiResponse
}

// Gemini

interface GeminiApi {

    @POST("models/gemini-flash-latest:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// Groq

interface GroqApi {

    @POST("chat/completions")
    suspend fun generateContent(
        @Header("Authorization") auth: String,
        @Body request: GroqRequest
    ): GroqResponse
}

// GitHub Releases

interface GitHubApi {
    @GET("repos/FurtherSecrets24680/chemsearch-android/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}

// Clients

object ApiClient {

    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(15.seconds)
        .readTimeout(20.seconds)
        .build()

    private val wikiClient = OkHttpClient.Builder()
        .connectTimeout(15.seconds)
        .readTimeout(20.seconds)
        .addInterceptor { chain ->
            val request: Request = chain.request().newBuilder()
                .header("User-Agent", "ChemSearch/1.0 (Android; github.com/FurtherSecrets24680)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val githubClient = OkHttpClient.Builder()
        .connectTimeout(15.seconds)
        .readTimeout(20.seconds)
        .addInterceptor { chain ->
            val request: Request = chain.request().newBuilder()
                .header("User-Agent", "ChemSearch/1.0 (Android; github.com/FurtherSecrets24680)")
                .header("Accept", "application/vnd.github+json")
                .build()
            chain.proceed(request)
        }
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient = defaultClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val pubChem: PubChemApi =
        retrofit("https://pubchem.ncbi.nlm.nih.gov/rest/pug/")
            .create(PubChemApi::class.java)

    val pubChemAutocomplete: PubChemAutocompleteApi =
        retrofit("https://pubchem.ncbi.nlm.nih.gov/rest/autocomplete/")
            .create(PubChemAutocompleteApi::class.java)

    val wiki: WikiApi =
        retrofit("https://en.wikipedia.org/api/rest_v1/", wikiClient)
            .create(WikiApi::class.java)

    val gemini: GeminiApi =
        retrofit("https://generativelanguage.googleapis.com/v1beta/")
            .create(GeminiApi::class.java)

    val groq: GroqApi =
        retrofit("https://api.groq.com/openai/v1/")
            .create(GroqApi::class.java)

    val github: GitHubApi =
        retrofit("https://api.github.com/", githubClient)
            .create(GitHubApi::class.java)

    val rawHttp: OkHttpClient = defaultClient

    val pubChemView: PubChemViewApi =
        retrofit("https://pubchem.ncbi.nlm.nih.gov/rest/pug_view/")
            .create(PubChemViewApi::class.java)
}

// PubChem PUG View

interface PubChemViewApi {
    @GET("data/compound/{cid}/JSON")
    suspend fun getSection(
        @Path("cid") cid: Long,
        @Query("heading") heading: String
    ): com.google.gson.JsonObject
}
