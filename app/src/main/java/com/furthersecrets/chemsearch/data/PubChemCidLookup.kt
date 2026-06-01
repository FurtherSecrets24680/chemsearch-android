package com.furthersecrets.chemsearch.data

sealed interface PubChemCidLookupStatus {
    data class Ready(val cids: List<Long>) : PubChemCidLookupStatus
    data class Waiting(val listKey: String) : PubChemCidLookupStatus
    data object Empty : PubChemCidLookupStatus
}

fun pubChemCidLookupStatus(response: CidResponse): PubChemCidLookupStatus {
    val cids = response.identifierList?.cid.orEmpty()
    if (cids.isNotEmpty()) return PubChemCidLookupStatus.Ready(cids)

    val listKey = response.waiting?.listKey?.trim().orEmpty()
    if (listKey.isNotEmpty()) return PubChemCidLookupStatus.Waiting(listKey)

    return PubChemCidLookupStatus.Empty
}
