package com.alberto.velo

import android.os.CancellationSignal
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bound the number of SQLite workers and interrupt native queries when their screen/filter changes. */
internal object HistoryReads {
    private val executor = Executors.newFixedThreadPool(2)
    suspend fun <T> run(block: (CancellationSignal) -> T): T = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        executor.execute {
            if (!continuation.isActive) return@execute
            try { val result = block(signal); if (continuation.isActive) continuation.resume(result) }
            catch (error: Throwable) { if (continuation.isActive) continuation.resumeWithException(error) }
        }
    }
}

internal data class HistoryKey(val before: EventCursor? = null, val after: EventCursor? = null)
internal class HistoryPagingSource(private val loadPage: (HistoryKey?, Int, CancellationSignal) -> HistoryPage) : PagingSource<HistoryKey,CallEvent>() {
    override suspend fun load(params: LoadParams<HistoryKey>): LoadResult<HistoryKey,CallEvent> = try {
        val page = HistoryReads.run { signal -> loadPage(params.key,params.loadSize,signal) }
        LoadResult.Page(page.events,page.previous?.let { HistoryKey(after=it) },page.next?.let { HistoryKey(before=it) })
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (error: Exception) { LoadResult.Error(error) }
    override fun getRefreshKey(state: PagingState<HistoryKey,CallEvent>): HistoryKey? = null
}
