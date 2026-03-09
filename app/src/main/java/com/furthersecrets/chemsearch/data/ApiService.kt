package com.furthersecrets.chemsearch.data

import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ─── PubChem PUG REST ──────────────────────────────────────────────────────────

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
}

// ─── PubChem Autocomplete ──────────────────────────────────────────────────────

interface PubChemAutocompleteApi {

    @GET("compound/{query}/JSON")
    suspend fun autocomplete(
        @Path("query", encoded = true) query: String,
        @Query("limit") limit: Int = 8
    ): AutocompleteResponse
}

// ─── Wikipedia ─────────────────────────────────────────────────────────────────
// Wikipedia REST API requires a User-Agent header or it returns 403 silently.
// We use a dedicated OkHttpClient that injects this header for every Wikipedia request.

interface WikiApi {

    @GET("page/summary/{title}")
    suspend fun getSummary(
        @Path("title", encoded = false) title: String
    ): WikiResponse
}

// ─── Gemini ────────────────────────────────────────────────────────────────────
// Model name must be exactly "gemini-1.5-flash" — "gemini-2.0-flash" does not exist
// as a valid model ID and causes the API to return 404.

interface GeminiApi {

    @POST("models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// ─── Clients ───────────────────────────────────────────────────────────────────

object ApiClient {

    // Default client for PubChem and Gemini
    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Wikipedia requires a descriptive User-Agent or it silently returns empty/403.
    // Format: AppName/version (contact or repo URL)
    private val wikiClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request: Request = chain.request().newBuilder()
                .header("User-Agent", "ChemSearch/1.0 (Android; github.com/FurtherSecrets24680)")
                .header("Accept", "application/json")
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

    // Wiki uses its own client with the required User-Agent
    val wiki: WikiApi =
        retrofit("https://en.wikipedia.org/api/rest_v1/", wikiClient)
            .create(WikiApi::class.java)

    val gemini: GeminiApi =
        retrofit("https://generativelanguage.googleapis.com/v1beta/")
            .create(GeminiApi::class.java)

    // Raw client for plain-text responses (not needed for 3D anymore — handled in WebView JS)
    val rawHttp: OkHttpClient = defaultClient
}