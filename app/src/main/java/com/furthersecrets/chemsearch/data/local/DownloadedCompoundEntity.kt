package com.furthersecrets.chemsearch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_compounds")
data class DownloadedCompoundEntity(
    @PrimaryKey val cid: Long,
    val name: String,
    val formula: String,
    val molecularWeight: String,
    val iupacName: String,
    val savedAt: Long,
    val stateJson: String,
    val structurePngBase64: String?
)
