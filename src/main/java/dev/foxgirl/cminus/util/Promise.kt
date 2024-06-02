package dev.foxgirl.cminus.util

import dev.foxgirl.cminus.logger
import dev.foxgirl.cminus.server
import net.minecraft.util.Util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future

class Promise<T>(private val future: CompletableFuture<T>) : Future<T> by future {

    constructor(value: T) : this(CompletableFuture.completedFuture(value))
    constructor(cause: Throwable) : this(CompletableFuture.failedFuture(cause))

    constructor(executor: Executor? = serverExecutor, supplier: () -> T) : this(evaluateFutureSupplier(executor, supplier))

    init {
        future.handle { _, cause -> if (cause != null) logger.warn("Exception in promise: {}", cause.message ?: cause.javaClass.name) }
    }

    fun <U> then(executor: Executor? = serverExecutor, block: (T) -> U): Promise<U> {
        return if (executor != null) {
            Promise(future.thenApplyAsync(block, executor))
        } else {
            Promise(future.thenApply(block))
        }
    }
    fun <U> thenCompose(executor: Executor? = serverExecutor, block: (T) -> Promise<U>): Promise<U> {
        return if (executor != null) {
            Promise(future.thenComposeAsync({ block(it).future }, executor))
        } else {
            Promise(future.thenCompose { block(it).future })
        }
    }
    fun <U, V> thenCombine(other: Promise<U>, executor: Executor? = serverExecutor, block: (T, U) -> V): Promise<V> {
        return if (executor != null) {
            Promise(future.thenCombineAsync(other.future, block, executor))
        } else {
            Promise(future.thenCombine(other.future, block))
        }
    }

    fun exceptionally(executor: Executor? = serverExecutor, block: (Throwable) -> T): Promise<T> {
        return if (executor != null) {
            Promise(future.exceptionallyAsync(block, executor))
        } else {
            Promise(future.exceptionally(block))
        }
    }
    fun finally(executor: Executor? = serverExecutor, block: (T?, Throwable?) -> Unit): Promise<T> {
        return if (executor != null) {
            Promise(future.whenCompleteAsync(block, executor))
        } else {
            Promise(future.whenComplete(block))
        }
    }

    fun <U> handle(executor: Executor? = serverExecutor, block: (T?, Throwable?) -> U): Promise<U> {
        return if (executor != null) {
            Promise(future.handleAsync(block, executor))
        } else {
            Promise(future.handle(block))
        }
    }

    companion object {

        @JvmStatic val serverExecutor: Executor get() = server

        @JvmStatic val mainWorkerExecutor: Executor get() = Util.getMainWorkerExecutor()
        @JvmStatic val ioWorkerExecutor: Executor get() = Util.getIoWorkerExecutor()

        private fun <T> evaluateFutureSupplier(executor: Executor?, block: () -> T): CompletableFuture<T> {
            return if (executor != null) {
                CompletableFuture.supplyAsync(block, executor)
            } else {
                try {
                    CompletableFuture.completedFuture(block())
                } catch (cause: Throwable) {
                    CompletableFuture.failedFuture(cause)
                }
            }
        }

    }

}
