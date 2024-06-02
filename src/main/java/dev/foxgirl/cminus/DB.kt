package dev.foxgirl.cminus

import dev.foxgirl.cminus.util.Promise
import dev.foxgirl.cminus.util.UUIDEncoding
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object DB : AutoCloseable {

    private val logger: Logger = LogManager.getLogger("CMinus/DB")

    private val executor: ExecutorService = Executors.newSingleThreadExecutor(this::createExecutorThread)
    private val closed: AtomicBoolean = AtomicBoolean(false)

    private var executorThread: Thread? = null
    private var shutdownThread: Thread? = null

    private fun createExecutorThread(runnable: Runnable): Thread {
        return Thread(runnable, "CMinus DB").also { it.isDaemon = true; executorThread = it }
    }
    private fun createShutdownThread(runnable: Runnable): Thread {
        return Thread(runnable, "CMinus DB shutdown hook").also { it.isDaemon = false; shutdownThread = it }
    }

    private lateinit var conn: Connection

    private fun <T> execute(block: () -> T): Promise<T> = Promise(executor, block)

    interface Actions {
        data class BlockRecord(val player: UUID, val block: String, val timeAdded: Instant, val timeUsed: Instant)
        data class PlayerRecord(val player: UUID, val name: String, val stand: String, val level: Int)

        fun listBlocks(): Sequence<BlockRecord>
        fun listBlocks(player: UUID): Sequence<BlockRecord>

        fun listPlayers(): Sequence<PlayerRecord>

        fun getBlock(player: UUID, block: String): BlockRecord?
        fun setBlock(record: BlockRecord): Boolean

        fun hasBlock(player: UUID, block: String): Boolean

        fun useBlock(player: UUID, block: String): Boolean

        fun addBlock(player: UUID, block: String): Boolean
        fun addBlocks(player: UUID, blocks: Iterable<String>): Int

        fun removeBlock(player: UUID, block: String): Boolean
        fun removeBlocks(player: UUID, blocks: Iterable<String>): Int

        fun clearBlocks(player: UUID): Int

        fun getPlayer(player: UUID): PlayerRecord?
        fun getPlayer(name: String): PlayerRecord?
        fun setPlayer(record: PlayerRecord): Boolean

        fun addPlayer(player: UUID, name: String): Boolean

        fun getPlayerStand(player: UUID): String?
        fun setPlayerStand(player: UUID, stand: String): Boolean

        fun getPlayerLevel(player: UUID): Int?
        fun setPlayerLevel(player: UUID, level: Int): Boolean

        fun incrementPlayerLevel(player: UUID): Boolean
    }

    private lateinit var stmtListBlocks: PreparedStatement
    private lateinit var stmtListBlocksByPlayer: PreparedStatement
    private lateinit var stmtGetBlock: PreparedStatement
    private lateinit var stmtSetBlock: PreparedStatement
    private lateinit var stmtHasBlock: PreparedStatement
    private lateinit var stmtUseBlock: PreparedStatement
    private lateinit var stmtAddBlock: PreparedStatement
    private lateinit var stmtRemoveBlock: PreparedStatement
    private lateinit var stmtClearBlocks: PreparedStatement
    private lateinit var stmtListPlayers: PreparedStatement
    private lateinit var stmtGetPlayer: PreparedStatement
    private lateinit var stmtGetPlayerByName: PreparedStatement
    private lateinit var stmtSetPlayer: PreparedStatement
    private lateinit var stmtAddPlayer: PreparedStatement
    private lateinit var stmtGetPlayerStand: PreparedStatement
    private lateinit var stmtSetPlayerStand: PreparedStatement
    private lateinit var stmtGetPlayerLevel: PreparedStatement
    private lateinit var stmtSetPlayerLevel: PreparedStatement
    private lateinit var stmtIncrementPlayerLevel: PreparedStatement

    private val actions: Actions = object : Actions {

        private fun assertOpenAndOnThread() {
            if (closed.get()) {
                throw IllegalStateException("Database connection is closed")
            }
            if (executorThread != Thread.currentThread()) {
                throw IllegalStateException("Database action attempted on wrong thread")
            }
        }

        override fun listBlocks(): Sequence<Actions.BlockRecord> {
            assertOpenAndOnThread()
            logger.info("Action: listBlocks")
            return sequence {
                stmtListBlocks.executeQuery().use { rs ->
                    while (rs.next()) {
                        yield(Actions.BlockRecord(
                            UUIDEncoding.fromByteArray(rs.getBytes(1)),
                            rs.getString(2),
                            rs.getTimestamp(3).toInstant(),
                            rs.getTimestamp(4).toInstant()
                        ))
                    }
                }
            }
        }
        override fun listBlocks(player: UUID): Sequence<Actions.BlockRecord> {
            assertOpenAndOnThread()
            logger.info("Action: listBlocks player: {}", player)
            return sequence {
                stmtListBlocksByPlayer.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtListBlocksByPlayer.executeQuery().use { rs ->
                    while (rs.next()) {
                        yield(Actions.BlockRecord(
                            UUIDEncoding.fromByteArray(rs.getBytes(1)),
                            rs.getString(2),
                            rs.getTimestamp(3).toInstant(),
                            rs.getTimestamp(4).toInstant()
                        ))
                    }
                }
            }
        }

        override fun listPlayers(): Sequence<Actions.PlayerRecord> {
            assertOpenAndOnThread()
            logger.info("Action: listPlayers")
            return sequence {
                stmtListPlayers.executeQuery().use { rs ->
                    while (rs.next()) {
                        yield(Actions.PlayerRecord(
                            UUIDEncoding.fromByteArray(rs.getBytes(1)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getInt(4)
                        ))
                    }
                }
            }
        }

        override fun getBlock(player: UUID, block: String): Actions.BlockRecord? {
            assertOpenAndOnThread()
            logger.info("Action: getBlock player: {}, block: {}", player, block)
            stmtGetBlock.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtGetBlock.setString(2, block)
            stmtGetBlock.executeQuery().use { rs ->
                return if (rs.next()) {
                    Actions.BlockRecord(
                        UUIDEncoding.fromByteArray(rs.getBytes(1)),
                        rs.getString(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant()
                    )
                } else {
                    null
                }
            }
        }
        override fun setBlock(record: Actions.BlockRecord): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: setBlock record: {}", record)
            stmtSetBlock.setBytes(1, UUIDEncoding.toByteArray(record.player))
            stmtSetBlock.setString(2, record.block)
            stmtSetBlock.setTimestamp(3, Timestamp.from(record.timeAdded))
            stmtSetBlock.setTimestamp(4, Timestamp.from(record.timeUsed))
            return stmtSetBlock.executeUpdate() > 0
        }

        override fun hasBlock(player: UUID, block: String): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: hasBlock player: {}, block: {}", player, block)
            stmtHasBlock.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtHasBlock.setString(2, block)
            return stmtHasBlock.executeQuery().use { rs -> rs.next() }
        }

        override fun useBlock(player: UUID, block: String): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: useBlock player: {}, block: {}", player, block)
            stmtUseBlock.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtUseBlock.setString(2, block)
            return stmtUseBlock.executeUpdate() > 0
        }

        override fun addBlock(player: UUID, block: String): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: addBlock player: {}, block: {}", player, block)
            stmtAddBlock.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtAddBlock.setString(2, block)
            return stmtAddBlock.executeUpdate() > 0
        }
        override fun addBlocks(player: UUID, blocks: Iterable<String>): Int {
            assertOpenAndOnThread()
            logger.info("Action: addBlocks player: {}, blocks: {}", player, blocks)
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
                stmtAddBlock.clearBatch()
            }
        }

        override fun removeBlock(player: UUID, block: String): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: removeBlock player: {}, block: {}", player, block)
            stmtRemoveBlock.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtRemoveBlock.setString(2, block)
            return stmtRemoveBlock.executeUpdate() > 0
        }
        override fun removeBlocks(player: UUID, blocks: Iterable<String>): Int {
            assertOpenAndOnThread()
            logger.info("Action: removeBlocks player: {}, blocks: {}", player, blocks)
            try {
                var i = 0
                blocks.forEach { block ->
                    stmtRemoveBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                    stmtRemoveBlock.setString(2, block)
                    stmtRemoveBlock.addBatch()
                }
                stmtRemoveBlock.executeBatch().forEach { count -> if (count > 0) i++ }
                return i
            } finally {
                stmtRemoveBlock.clearBatch()
            }
        }

        override fun clearBlocks(player: UUID): Int {
            assertOpenAndOnThread()
            logger.info("Action: clearBlocks player: {}", player)
            stmtClearBlocks.setBytes(1, UUIDEncoding.toByteArray(player))
            return stmtClearBlocks.executeUpdate()
        }

        override fun getPlayer(player: UUID): Actions.PlayerRecord? {
            assertOpenAndOnThread()
            logger.info("Action: getPlayer player: {}", player)
            stmtGetPlayer.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtGetPlayer.executeQuery().use { rs ->
                return if (rs.next()) {
                    Actions.PlayerRecord(
                        UUIDEncoding.fromByteArray(rs.getBytes(1)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4)
                    )
                } else null
            }
        }
        override fun getPlayer(name: String): Actions.PlayerRecord? {
            assertOpenAndOnThread()
            logger.info("Action: getPlayer name: {}", name)
            stmtGetPlayerByName.setString(1, name)
            stmtGetPlayerByName.executeQuery().use { rs ->
                return if (rs.next()) {
                    Actions.PlayerRecord(
                        UUIDEncoding.fromByteArray(rs.getBytes(1)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4)
                    )
                } else null
            }
        }
        override fun setPlayer(record: Actions.PlayerRecord): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: setPlayer record: {}", record)
            stmtSetPlayer.setBytes(1, UUIDEncoding.toByteArray(record.player))
            stmtSetPlayer.setString(2, record.stand)
            stmtSetPlayer.setInt(3, record.level)
            return stmtSetPlayer.executeUpdate() > 0
        }

        override fun addPlayer(player: UUID, name: String): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: addPlayer player: {}, name: {}", player, name)
            stmtAddPlayer.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtAddPlayer.setString(2, name)
            return stmtAddPlayer.executeUpdate() > 0
        }

        override fun getPlayerStand(player: UUID): String? {
            assertOpenAndOnThread()
            logger.info("Action: getPlayerStand player: {}", player)
            stmtGetPlayerStand.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtGetPlayerStand.executeQuery().use { rs ->
                return if (rs.next()) rs.getString(1) else null
            }
        }
        override fun setPlayerStand(player: UUID, stand: String): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: setPlayerStand player: {}, stand: {}", player, stand)
            stmtSetPlayerStand.setString(1, stand)
            stmtSetPlayerStand.setBytes(2, UUIDEncoding.toByteArray(player))
            return stmtSetPlayerStand.executeUpdate() > 0
        }

        override fun getPlayerLevel(player: UUID): Int? {
            assertOpenAndOnThread()
            logger.info("Action: getPlayerLevel player: {}", player)
            stmtGetPlayerLevel.setBytes(1, UUIDEncoding.toByteArray(player))
            stmtGetPlayerLevel.executeQuery().use { rs ->
                return if (rs.next()) rs.getInt(1) else null
            }
        }
        override fun setPlayerLevel(player: UUID, level: Int): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: setPlayerLevel player: {}, level: {}", player, level)
            stmtSetPlayerLevel.setInt(1, level)
            stmtSetPlayerLevel.setBytes(2, UUIDEncoding.toByteArray(player))
            return stmtSetPlayerLevel.executeUpdate() > 0
        }

        override fun incrementPlayerLevel(player: UUID): Boolean {
            assertOpenAndOnThread()
            logger.info("Action: incrementPlayerLevel player: {}", player)
            stmtIncrementPlayerLevel.setBytes(1, UUIDEncoding.toByteArray(player))
            return stmtSetPlayerStand.executeUpdate() > 0
        }

    }

    fun <T> perform(block: (Connection, Actions) -> T): Promise<T> {
        return execute { block(conn, actions) }
    }

    fun connect(): Promise<Unit> {
        return execute {
            try {
                logger.info("Connecting to database...")

                conn = DriverManager.getConnection("jdbc:h2:file:./config/cminus-data;MODE=MySQL")
                Runtime.getRuntime().addShutdownHook(createShutdownThread(this::close))

                conn.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS blocks (
                            player BINARY(16), block TEXT,
                            time_added TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                            time_used TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (player, block)
                        );
                        CREATE INDEX IF NOT EXISTS blocks_idx_player ON blocks (player);
                        CREATE TABLE IF NOT EXISTS players (
                            player BINARY(16) PRIMARY KEY, name TEXT,
                            stand TEXT DEFAULT '',
                            level INT DEFAULT 1
                        );
                        CREATE INDEX IF NOT EXISTS players_idx_name ON players (name);
                    """.trimIndent())
                }

                fun stmt(sql: String) = conn.prepareStatement(sql)

                stmtListBlocks =
                    stmt("SELECT player, block, time_added, time_used FROM blocks ORDER BY time_used")
                stmtListBlocksByPlayer =
                    stmt("SELECT player, block, time_added, time_used FROM blocks WHERE player = ? ORDER BY time_used")
                stmtGetBlock =
                    stmt("SELECT player, block, time_added, time_used FROM blocks WHERE player = ? AND block = ?")
                stmtSetBlock =
                    stmt("MERGE INTO blocks (player, block, time_added, time_used) VALUES (?, ?, ?, ?)")
                stmtHasBlock =
                    stmt("SELECT 1 FROM blocks WHERE player = ? AND block = ?")
                stmtUseBlock =
                    stmt("UPDATE blocks SET time_used = CURRENT_TIMESTAMP WHERE player = ? AND block = ?")
                stmtAddBlock =
                    stmt("INSERT IGNORE INTO blocks (player, block) VALUES (?, ?)")
                stmtRemoveBlock =
                    stmt("DELETE FROM blocks WHERE player = ? AND block = ?")
                stmtClearBlocks =
                    stmt("DELETE FROM blocks WHERE player = ?")
                stmtListPlayers =
                    stmt("SELECT player, name, stand, level FROM players")
                stmtGetPlayer =
                    stmt("SELECT player, name, stand, level FROM players WHERE player = ?")
                stmtGetPlayerByName =
                    stmt("SELECT player, name, stand, level FROM players WHERE name = ?")
                stmtSetPlayer =
                    stmt("MERGE INTO players (player, name, stand, level) VALUES (?, ?, ?, ?)")
                stmtAddPlayer =
                    stmt("INSERT IGNORE INTO players (player, name) VALUES (?, ?)")
                stmtGetPlayerStand =
                    stmt("SELECT stand FROM players WHERE player = ?")
                stmtSetPlayerStand =
                    stmt("UPDATE players SET stand = ? WHERE player = ?")
                stmtGetPlayerLevel =
                    stmt("SELECT level FROM players WHERE player = ?")
                stmtSetPlayerLevel =
                    stmt("UPDATE players SET level = ? WHERE player = ?")
                stmtIncrementPlayerLevel =
                    stmt("UPDATE players SET level = level + 1 WHERE player = ?")
            } catch (cause: Exception) {
                throw RuntimeException("Failed to connect/initialize CMinus database", cause)
            }
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
