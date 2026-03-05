package no.kartverket.serg.mock

data class SergMockConfig(
    val httpPort: Int,
    val maxGenerateBatch: Int,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): SergMockConfig =
            SergMockConfig(
                httpPort = env.int("SERG_MOCK_HTTP_PORT", 8094),
                maxGenerateBatch = env.int("SERG_MOCK_MAX_BATCH", 10_000).coerceAtLeast(1),
            )
    }
}

private fun Map<String, String>.int(
    key: String,
    default: Int,
): Int = this[key]?.toIntOrNull() ?: default
