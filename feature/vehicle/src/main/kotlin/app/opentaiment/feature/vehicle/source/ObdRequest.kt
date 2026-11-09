package app.opentaiment.feature.vehicle.source

import app.opentaiment.feature.vehicle.data.DtcReport
import kotlinx.coroutines.CompletableDeferred

/** One-off commands executed between polling cycles on the open connection. */
sealed interface ObdRequest {
    class ReadDtcs(val result: CompletableDeferred<DtcReport> = CompletableDeferred()) : ObdRequest
    class ClearDtcs(val result: CompletableDeferred<Unit> = CompletableDeferred()) : ObdRequest
}
