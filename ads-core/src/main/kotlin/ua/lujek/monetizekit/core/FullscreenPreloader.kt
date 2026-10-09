package ua.lujek.monetizekit.core

/**
 * The loaded candidate selected for one fullscreen placement. It is internal because hosts only
 * need to know whether a placement is ready; adapter and network selection remain library-owned.
 */
internal sealed interface FullscreenPreloadResult {
    data class Loaded(
        val network: AdsNetwork,
        val adapter: AdsNetworkAdapter,
        val adUnitId: String,
    ) : FullscreenPreloadResult

    data class Failed(val error: AdsError) : FullscreenPreloadResult
}

/**
 * Loads one fullscreen candidate at a time in the order supplied by the placement definition.
 *
 * This is intentionally a priority fallback, not a parallel race: a successful lower-priority
 * request must never replace an ad that was already prepared by a higher-priority network.
 */
internal suspend fun preloadFirstFullscreenCandidate(
    placement: FullscreenPlacement,
    candidates: List<NetworkPlacement>,
    adapters: Map<AdsNetwork, AdsNetworkAdapter>,
    readyNetworks: Set<AdsNetwork>,
    eventReporter: AdsEventReporter,
): FullscreenPreloadResult {
    var lastError = AdsError(message = "No configured network could load '${placement.id}'.")

    candidates.forEach { candidate ->
        if (candidate.network !in readyNetworks) return@forEach
        val adapter = adapters[candidate.network] ?: return@forEach
        if (placement.format !in adapter.supportedFormats) {
            lastError = AdsError(
                network = candidate.network,
                message = "${candidate.network} does not support ${placement.format}.",
            )
            return@forEach
        }

        when (
            val loadResult = runCatching {
                adapter.loadFullscreen(placement, candidate.adUnitId, eventReporter)
            }.getOrElse { throwable ->
                AdLoadResult.Failed(
                    AdsError(
                        network = candidate.network,
                        message = throwable.message ?: "Fullscreen ad loading failed.",
                        cause = throwable,
                    ),
                )
            }
        ) {
            AdLoadResult.Loaded -> {
                return FullscreenPreloadResult.Loaded(
                    network = candidate.network,
                    adapter = adapter,
                    adUnitId = candidate.adUnitId,
                )
            }

            is AdLoadResult.Failed -> lastError = loadResult.error
            AdLoadResult.Suppressed -> {
                return FullscreenPreloadResult.Failed(
                    AdsError(message = "Fullscreen loading was suppressed by the ad access policy."),
                )
            }
        }
    }

    return FullscreenPreloadResult.Failed(lastError)
}
