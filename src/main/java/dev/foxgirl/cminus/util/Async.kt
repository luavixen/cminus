package dev.foxgirl.cminus.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.coroutines.*

object Async {

    private class CompletableFutureContinuation<T>(override val context: CoroutineContext) : CompletableFuture<T>(), Continuation<T> {

        fun startCoroutine(coroutine: suspend () -> T): CompletableFutureContinuation<T> {
            coroutine.startCoroutine(this)
            return this
        }
        fun <R> startCoroutine(coroutine: suspend R.() -> T, receiver: R): CompletableFutureContinuation<T> {
            coroutine.startCoroutine(receiver, this)
            return this
        }

        override fun resumeWith(result: Result<T>) {
            result.fold(::complete, ::completeExceptionally)
        }

    }

    private fun <T> resumeContinuation(continuation: Continuation<T>, value: T?, cause: Throwable?) {
        continuation.resumeWith(if (cause != null) Result.failure(cause) else Result.success(value) as Result<T>)
    }

    fun <T> go(context: CoroutineContext = EmptyCoroutineContext, coroutine: suspend () -> T): Promise<T> =
        Promise(CompletableFutureContinuation<T>(context).startCoroutine(coroutine))

    suspend fun <T> await(future: Future<T>): T {
        return suspendCoroutine {
            when (future) {
                is Promise<T> ->
                    future.finally { value, cause -> resumeContinuation(it, value, cause) }
                is CompletableFuture<T> ->
                    future.whenComplete { value, cause -> resumeContinuation(it, value, cause) }
                else ->
                    throw IllegalArgumentException("Cannot await unsupported Future type: ${future.javaClass.name}")
            }
        }
    }

    suspend fun delay(ticks: Int = 0) {
        return suspendCoroutine { dev.foxgirl.cminus.delay(ticks) { it.resume(Unit) } }
    }

    suspend fun until(ticks: Int = 0, condition: suspend () -> Boolean) {
        while (!condition()) delay(ticks)
    }

    suspend fun <T> poll(ticks: Int = 0, condition: suspend () -> T?): T {
        while (true) {
            val result = condition()
            if (result != null) return result
            delay(ticks)
        }
    }

}
