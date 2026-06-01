package com.furthersecrets.chemsearch.data

import com.google.gson.JsonObject
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
        @Path("name") name: String
    ): CidResponse

    @GET("compound/cid/{cid}/property/MolecularFormula,MolecularWeight,IUPACName,SMILES,ConnectivitySMILES,InChIKey,InChI,Charge,CovalentUnitCount,Title,XLogP,ExactMass,MonoisotopicMass,TPSA,Complexity,HBondDonorCount,HBondAcceptorCount,RotatableBondCount,HeavyAtomCount,IsotopeAtomCount,AtomStereoCount,DefinedAtomStereoCount,UndefinedAtomStereoCount,BondStereoCount,DefinedBondStereoCount,UndefinedBondStereoCount,Volume3D,FeatureCount3D,FeatureAcceptorCount3D,FeatureDonorCount3D,FeatureAnionCount3D,FeatureCationCount3D,FeatureRingCount3D,FeatureHydrophobeCount3D,ConformerModelRMSD3D,EffectiveRotorCount3D,ConformerCount3D/JSON")
    suspend fun getProperties(@Path("cid") cid: Long): PropertiesResponse

    @GET("compound/cid/{cid}/record/JSON")
    suspend fun getRecord(@Path("cid") cid: Long): JsonObject

    @GET("compound/cid/{cid}/synonyms/JSON")
    suspend fun getSynonyms(@Path("cid") cid: Long): SynonymsResponse

    @GET("compound/cid/{cid}/description/JSON")
    suspend fun getDescription(@Path("cid") cid: Long): DescriptionResponse

    @GET("compound/cid/{cid}/SDF")
    suspend fun getSdf(
        @Path("cid") cid: Long,
        @Query("record_type") recordType: String = "3d"
    ): ResponseBody

    @GET("compound/formula/{formula}/cids/JSON")
    suspend fun getIsomerCids(
        @Path("formula") formula: String,
        @Query("MaxRecords") maxRecords: Int = 20
    ): CidResponse

    @GET("compound/listkey/{listKey}/cids/JSON")
    suspend fun getCidsByListKey(
        @Path("listKey") listKey: String
    ): CidResponse

    @GET("compound/cid/{cids}/property/Title,IsotopeAtomCount/JSON")
    suspend fun getTitles(
        @Path("cids", encoded = true) cids: String
    ): TitleResponse

    @GET("compound/cid/{cids}/property/Title,MolecularFormula,MolecularWeight/JSON")
    suspend fun getStructureResultProperties(
        @Path("cids", encoded = true) cids: String
    ): PropertiesResponse

    @GET("compound/cid/{cids}/property/Title,MolecularFormula,MolecularWeight,Charge/JSON")
    suspend fun getAdvancedSearchProperties(
        @Path("cids", encoded = true) cids: String
    ): PropertiesResponse

    @FormUrlEncoded
    @POST("compound/{operation}/sdf/cids/JSON")
    suspend fun searchStructureBySdf(
        @Path("operation") operation: String,
        @Field("sdf") sdf: String,
        @Query("MaxRecords") maxRecords: Int = 30,
        @Query("Threshold") threshold: Int? = null
    ): CidResponse

    @FormUrlEncoded
    @POST("standardize/sdf/SDF")
    suspend fun standardizeSdf(
        @Field("sdf") sdf: String
    ): ResponseBody

    @FormUrlEncoded
    @POST("standardize/smiles/SDF")
    suspend fun standardizeSmiles(
        @Field("smiles") smiles: String
    ): ResponseBody

    @FormUrlEncoded
    @POST("standardize/inchi/SDF")
    suspend fun standardizeInchi(
        @Field("inchi") inchi: String
    ): ResponseBody
}

// PubChem Autocomplete

interface PubChemAutocompleteApi {

    @GET("compound/{query}/JSON")
    suspend fun autocomplete(
        @Path("query") query: String,
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

    @POST("models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @GET("models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): GeminiModelsResponse
}

// OpenAI-compatible chat providers

interface ChatCompletionsApi {

    @POST("chat/completions")
    suspend fun generateContent(
        @Header("Authorization") auth: String,
        @Body request: GroqRequest
    ): GroqResponse

    @GET("models")
    suspend fun listModels(
        @Header("Authorization") auth: String
    ): ChatModelsResponse
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

    val groq: ChatCompletionsApi =
        retrofit("https://api.groq.com/openai/v1/")
            .create(ChatCompletionsApi::class.java)

    val openAi: ChatCompletionsApi =
        retrofit("https://api.openai.com/v1/")
            .create(ChatCompletionsApi::class.java)

    val openRouter: ChatCompletionsApi =
        retrofit("https://openrouter.ai/api/v1/")
            .create(ChatCompletionsApi::class.java)

    val mistral: ChatCompletionsApi =
        retrofit("https://api.mistral.ai/v1/")
            .create(ChatCompletionsApi::class.java)

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
