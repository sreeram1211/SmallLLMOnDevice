package com.pocketsloth.app.native

import org.json.JSONObject

/**
 * Hyperparameters for a LoRA fine-tuning session.
 * Can be constructed from fields or parsed from a JSON string consumed by JNI.
 */
data class LoraParams(
    val rank: Int = 8,
    val alpha: Float = 16f,
    val dropout: Float = 0.05f,
    val learningRate: Float = 2e-4f,
    val batchSize: Int = 1,
    val epochs: Int = 1,
    val maxSteps: Int = 100,
    val warmupSteps: Int = 10,
    val weightDecay: Float = 0.01f,
    val seed: Long = 42L,
    val targetModules: List<String> = listOf("q_proj", "v_proj")
) {
    fun toJson(): String = JSONObject().apply {
        put("rank", rank)
        put("alpha", alpha.toDouble())
        put("dropout", dropout.toDouble())
        put("learning_rate", learningRate.toDouble())
        put("batch_size", batchSize)
        put("epochs", epochs)
        put("max_steps", maxSteps)
        put("warmup_steps", warmupSteps)
        put("weight_decay", weightDecay.toDouble())
        put("seed", seed)
        put("target_modules", org.json.JSONArray(targetModules))
    }.toString()

    companion object {
        fun fromJson(json: String): LoraParams {
            val o = JSONObject(json)
            val modules = mutableListOf<String>()
            o.optJSONArray("target_modules")?.let { arr ->
                for (i in 0 until arr.length()) {
                    modules += arr.getString(i)
                }
            }
            return LoraParams(
                rank = o.optInt("rank", 8),
                alpha = o.optDouble("alpha", 16.0).toFloat(),
                dropout = o.optDouble("dropout", 0.05).toFloat(),
                learningRate = o.optDouble("learning_rate", 2e-4).toFloat(),
                batchSize = o.optInt("batch_size", 1),
                epochs = o.optInt("epochs", 1),
                maxSteps = o.optInt("max_steps", 100),
                warmupSteps = o.optInt("warmup_steps", 10),
                weightDecay = o.optDouble("weight_decay", 0.01).toFloat(),
                seed = o.optLong("seed", 42L),
                targetModules = modules.ifEmpty { listOf("q_proj", "v_proj") }
            )
        }
    }
}
