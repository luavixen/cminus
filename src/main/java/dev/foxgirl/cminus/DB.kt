package dev.foxgirl.cminus

import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

object DB : AutoCloseable {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor(this::createExecutorThread)
    private val closed: AtomicBoolean = AtomicBoolean(false)

    private fun createExecutorThread(runnable: Runnable): Thread {
        return Thread(runnable, "CMinus H2 Database Thread").apply { isDaemon = true }
    }
    private fun createShutdownThread(runnable: Runnable): Thread {
        return Thread(runnable, "CMinus H2 Database Shutdown Hook")
    }

    private lateinit var conn: Connection

    fun <T> execute(block: Supplier<T>): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(block, executor)
    }
    fun <T> acquire(block: (Connection) -> T): CompletableFuture<T> {
        return execute { block(conn) }
    }

    fun connect() {
        execute {
            conn = DriverManager.getConnection("jdbc:h2:file:./config/cminus-data;MODE=MySQL")
            Runtime.getRuntime().addShutdownHook(createShutdownThread(this::close))
        }
        acquire { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE IF NOT EXISTS blocks (player BINARY(16), block TEXT, PRIMARY KEY (player, block))")
            }
        }
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        try {
            execute { conn.close() }
        } finally {
            executor.shutdown()
        }
    }

}
