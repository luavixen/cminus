package dev.foxgirl.cminus

import dev.foxgirl.cminus.util.UUIDEncoding
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import javax.swing.Action

object DB : AutoCloseable {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor(this::createExecutorThread)
    private val closed: AtomicBoolean = AtomicBoolean(false)

    private val logger: Logger = LogManager.getLogger("CMinus Database")

    private fun createExecutorThread(runnable: Runnable): Thread {
        return Thread(runnable, "CMinus Database Thread").apply { isDaemon = true }
    }
    private fun createShutdownThread(runnable: Runnable): Thread {
        return Thread(runnable, "CMinus Database Shutdown Hook")
    }

    private lateinit var conn: Connection

    private fun <T> execute(block: Supplier<T>): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(block, executor)
    }

    interface Actions {
        fun addBlock(player: UUID, block: String): Boolean
        fun addBlocks(player: UUID, block: Iterable<String>): Int

        fun useBlock(player: UUID, block: String): Boolean
    }

    private lateinit var stmtAddBlock: PreparedStatement
    private lateinit var stmtUseBlock: PreparedStatement

    private val actions: Actions = object : Actions {

        override fun addBlock(player: UUID, block: String): Boolean {
            try {
                stmtAddBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtAddBlock.setString(2, block)
                return stmtAddBlock.executeUpdate() > 0
            } finally {
                stmtAddBlock.clearParameters()
                stmtAddBlock.clearBatch()
            }
        }
        override fun addBlocks(player: UUID, blocks: Iterable<String>): Int {
            try {
                var i = 0
                blocks.forEach { block ->
                    stmtAddBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                    stmtAddBlock.setString(2, block)
                    stmtAddBlock.addBatch()
                }
                stmtAddBlock.executeBatch().forEach { count -> if (count > 0) i++ }
                return i
            } finally {
                stmtAddBlock.clearParameters()
                stmtAddBlock.clearBatch()
            }
        }

        override fun useBlock(player: UUID, block: String): Boolean {
            try {
                stmtUseBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtUseBlock.setString(2, block)
                return stmtUseBlock.executeUpdate() > 0
            } finally {
                stmtUseBlock.clearParameters()
                stmtUseBlock.clearBatch()
            }
        }

    }

    fun <T> acquire(block: (Connection, Actions) -> T): CompletableFuture<T> {
        return execute { block(conn, actions) }
    }

    fun connect(): CompletableFuture<Unit> {
        return execute {
            logger.info("Connecting to database...")

            conn = DriverManager.getConnection("jdbc:h2:file:./config/cminus-data;MODE=MySQL")
            Runtime.getRuntime().addShutdownHook(createShutdownThread(this::close))

            conn.createStatement().use { stmt ->
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS blocks (
                        player BINARY(16), block TEXT,
                        time_added TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        time_used TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (player, block)
                    );
                    CREATE INDEX IF NOT EXISTS blocks_idx_player ON blocks (player);
                """.trimIndent())
            }

            stmtAddBlock = conn.prepareStatement("INSERT IGNORE INTO blocks (player, block) VALUES (?, ?)")
            stmtUseBlock = conn.prepareStatement("UPDATE blocks SET time_used = CURRENT_TIMESTAMP WHERE player = ? AND block = ?")
        }
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        logger.info("Closing database...")
        try {
            executor.submit {
                try {
                    val millisStarted = System.currentTimeMillis()
                    conn.close()
                    val millisFinished = System.currentTimeMillis()
                    logger.info("Closed database connection successfully in ${millisFinished - millisStarted}ms")
                } catch (cause: Throwable) {
                    logger.error("Failed to close database connection", cause)
                }
            }
        } finally {
            try {
                executor.shutdown()
                executor.awaitTermination(3, TimeUnit.SECONDS)
            } catch (cause: InterruptedException) {
                logger.warn("Database executor did not shutdown in time, forcing shutdown...")
                executor.shutdownNow()
            }
        }
    }

}
