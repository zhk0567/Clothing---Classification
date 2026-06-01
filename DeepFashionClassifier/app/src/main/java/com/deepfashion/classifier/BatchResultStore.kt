package com.deepfashion.classifier

data class BatchResultItem(
    val imagePath: String,
    val category: String,
    val confidence: Float,
    val description: String
)

object BatchResultStore {
    var items: List<BatchResultItem> = emptyList()
}
