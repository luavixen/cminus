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
import java.util.concurrent.CompletableFuture
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

        fun listBlocks(): List<BlockRecord>
        fun listBlocks(player: UUID): List<BlockRecord>

        fun listPlayers(): List<PlayerRecord>

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
        fun getPlayers(players: Iterable<UUID>): List<PlayerRecord>
        fun getPlayerByName(name: String): PlayerRecord?
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

    private fun trimToMaxLength(string: String, length: Int): String {
        return if (string.length > length) string.substring(0, length - 1) + "\u2026" else string
    }

    private val actions: Actions = object : Actions {

        private fun <T> guardAction(name: String, vararg params: Pair<String, *>, block: () -> T): T {
            if (closed.get()) {
                throw IllegalStateException("Database action $name failed, connection is closed")
            }
            if (executorThread != Thread.currentThread()) {
                throw IllegalStateException("Database action $name failed, on wrong thread")
            }

            val params = trimToMaxLength(params.joinToString(", ") { (k, v) -> "$k: $v" }, 60)

            val timeStarted = System.nanoTime()
            fun timeTaken(): String {
                return String.format("%.4f", (System.nanoTime() - timeStarted).toDouble() * 1E-6)
            }

            val result = try {
                block()
            } catch (cause: Exception) {
                logger.error("Action {}({}) failed in {}ms: {}", name, params, timeTaken(), cause.message ?: cause.javaClass.name)
                throw cause
            }

            logger.info("Action {}({}) done in {}ms: {}", name, params, timeTaken(), trimToMaxLength(result.toString(), 40))
            return result
        }

        private inline fun <T> collectResults(block: MutableList<T>.() -> Unit): MutableList<T> {
            return mutableListOf<T>().apply(block)
        }

        override fun listBlocks(): List<Actions.BlockRecord> {
            return guardAction("listBlocks") { collectResults {
                stmtListBlocks.executeQuery().use { rs ->
                    while (rs.next()) add(Actions.BlockRecord(
                        UUIDEncoding.fromByteArray(rs.getBytes(1)),
                        rs.getString(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant()
                    ))
                }
            } }
        }
        override fun listBlocks(player: UUID): List<Actions.BlockRecord> {
            return guardAction("listBlocks", "player" to player) { collectResults {
                stmtListBlocksByPlayer.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtListBlocksByPlayer.executeQuery().use { rs ->
                    while (rs.next()) add(Actions.BlockRecord(
                        UUIDEncoding.fromByteArray(rs.getBytes(1)),
                        rs.getString(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant()
                    ))
                }
            } }
        }

        override fun listPlayers(): List<Actions.PlayerRecord> {
            return guardAction("listPlayers") { collectResults {
                stmtListPlayers.executeQuery().use { rs ->
                    while (rs.next()) add(Actions.PlayerRecord(
                        UUIDEncoding.fromByteArray(rs.getBytes(1)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4)
                    ))
                }
            } }
        }

        override fun getBlock(player: UUID, block: String): Actions.BlockRecord? {
            return guardAction("getBlock", "player" to player, "block" to block) {
                stmtGetBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtGetBlock.setString(2, block)
                stmtGetBlock.executeQuery().use { rs ->
                    if (rs.next()) {
                        Actions.BlockRecord(
                            UUIDEncoding.fromByteArray(rs.getBytes(1)),
                            rs.getString(2),
                            rs.getTimestamp(3).toInstant(),
                            rs.getTimestamp(4).toInstant()
                        )
                    } else null
                }
            }
        }
        override fun setBlock(record: Actions.BlockRecord): Boolean {
            return guardAction("setBlock", "record" to record) {
                stmtSetBlock.setBytes(1, UUIDEncoding.toByteArray(record.player))
                stmtSetBlock.setString(2, record.block)
                stmtSetBlock.setTimestamp(3, Timestamp.from(record.timeAdded))
                stmtSetBlock.setTimestamp(4, Timestamp.from(record.timeUsed))
                stmtSetBlock.executeUpdate() > 0
            }
        }

        override fun hasBlock(player: UUID, block: String): Boolean {
            return guardAction("hasBlock", "player" to player, "block" to block) {
                stmtHasBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtHasBlock.setString(2, block)
                stmtHasBlock.executeQuery().use { rs -> rs.next() }
            }
        }

        override fun useBlock(player: UUID, block: String): Boolean {
            return guardAction("useBlock", "player" to player, "block" to block) {
                stmtUseBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtUseBlock.setString(2, block)
                stmtUseBlock.executeUpdate() > 0
            }
        }

        override fun addBlock(player: UUID, block: String): Boolean {
            return guardAction("addBlock", "player" to player, "block" to block) {
                stmtAddBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtAddBlock.setString(2, block)
                stmtAddBlock.executeUpdate() > 0
            }
        }
        override fun addBlocks(player: UUID, blocks: Iterable<String>): Int {
            return guardAction("addBlock", "player" to player, "blocks" to blocks) {
                try {
                    var i = 0
                    blocks.forEach { block ->
                        stmtAddBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                        stmtAddBlock.setString(2, block)
                        stmtAddBlock.addBatch()
                    }
                    stmtAddBlock.executeBatch().forEach { count -> if (count > 0) i++ }
                    return@guardAction i
                } finally {
                    stmtAddBlock.clearBatch()
                }
            }
        }

        override fun removeBlock(player: UUID, block: String): Boolean {
            return guardAction("removeBlock", "player" to player, "block" to block) {
                stmtRemoveBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtRemoveBlock.setString(2, block)
                stmtRemoveBlock.executeUpdate() > 0
            }
        }
        override fun removeBlocks(player: UUID, blocks: Iterable<String>): Int {
            return guardAction("removeBlocks", "player" to player, "blocks" to blocks) {
                try {
                    var i = 0
                    blocks.forEach { block ->
                        stmtRemoveBlock.setBytes(1, UUIDEncoding.toByteArray(player))
                        stmtRemoveBlock.setString(2, block)
                        stmtRemoveBlock.addBatch()
                    }
                    stmtRemoveBlock.executeBatch().forEach { count -> if (count > 0) i++ }
                    return@guardAction i
                } finally {
                    stmtRemoveBlock.clearBatch()
                }
            }
        }

        override fun clearBlocks(player: UUID): Int {
            return guardAction("clearBlocks", "player" to player) {
                logger.info("Action: clearBlocks player: {}", player)
                stmtClearBlocks.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtClearBlocks.executeUpdate()
            }
        }

        override fun getPlayer(player: UUID): Actions.PlayerRecord? {
            return guardAction("getPlayer", "player" to player) {
                stmtGetPlayer.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtGetPlayer.executeQuery().use { rs ->
                    if (rs.next()) {
                        Actions.PlayerRecord(
                            UUIDEncoding.fromByteArray(rs.getBytes(1)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getInt(4)
                        )
                    } else null
                }
            }
        }
        override fun getPlayers(players: Iterable<UUID>): List<Actions.PlayerRecord> {
            return guardAction("getPlayers", "players" to players) { collectResults {
                for (player in players) {
                    stmtGetPlayer.setBytes(1, UUIDEncoding.toByteArray(player))
                    stmtGetPlayer.executeQuery().use { rs ->
                        if (rs.next()) add(Actions.PlayerRecord(
                            UUIDEncoding.fromByteArray(rs.getBytes(1)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getInt(4)
                        ))
                    }
                }
            } }
        }
        override fun getPlayerByName(name: String): Actions.PlayerRecord? {
            return guardAction("getPlayerByName", "name" to name) {
                stmtGetPlayerByName.setString(1, name)
                stmtGetPlayerByName.executeQuery().use { rs ->
                    if (rs.next()) {
                        Actions.PlayerRecord(
                            UUIDEncoding.fromByteArray(rs.getBytes(1)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getInt(4)
                        )
                    } else null
                }
            }
        }
        override fun setPlayer(record: Actions.PlayerRecord): Boolean {
            return guardAction("setPlayer", "record" to record) {
                stmtSetPlayer.setBytes(1, UUIDEncoding.toByteArray(record.player))
                stmtSetPlayer.setString(2, record.name)
                stmtSetPlayer.setString(3, record.stand)
                stmtSetPlayer.setInt(4, record.level)
                stmtSetPlayer.executeUpdate() > 0
            }
        }

        override fun addPlayer(player: UUID, name: String): Boolean {
            return guardAction("addPlayer", "player" to player, "name" to name) {
                stmtAddPlayer.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtAddPlayer.setString(2, name)
                stmtAddPlayer.executeUpdate() > 0
            }
        }

        override fun getPlayerStand(player: UUID): String? {
            return guardAction("getPlayerStand", "player" to player) {
                stmtGetPlayerStand.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtGetPlayerStand.executeQuery().use { rs ->
                    if (rs.next()) rs.getString(1) else null
                }
            }
        }
        override fun setPlayerStand(player: UUID, stand: String): Boolean {
            return guardAction("setPlayerStand", "player" to player, "stand" to stand) {
                stmtSetPlayerStand.setString(1, stand)
                stmtSetPlayerStand.setBytes(2, UUIDEncoding.toByteArray(player))
                stmtSetPlayerStand.executeUpdate() > 0
            }
        }

        override fun getPlayerLevel(player: UUID): Int? {
            return guardAction("getPlayerLevel", "player" to player) {
                stmtGetPlayerLevel.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtGetPlayerLevel.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else null
                }
            }
        }
        override fun setPlayerLevel(player: UUID, level: Int): Boolean {
            return guardAction("setPlayerLevel", "player" to player, "level" to level) {
                stmtSetPlayerLevel.setInt(1, level)
                stmtSetPlayerLevel.setBytes(2, UUIDEncoding.toByteArray(player))
                stmtSetPlayerLevel.executeUpdate() > 0
            }
        }

        override fun incrementPlayerLevel(player: UUID): Boolean {
            return guardAction("incrementPlayerLevel", "player" to player) {
                stmtIncrementPlayerLevel.setBytes(1, UUIDEncoding.toByteArray(player))
                stmtIncrementPlayerLevel.executeUpdate() > 0
            }
        }

    }

    fun <T> perform(block: (Connection, Actions) -> T): Promise<T> {
        val serverThreadFuture = CompletableFuture<T>()
        val databaseThreadPromise = execute { block(conn, actions) }
        databaseThreadPromise
            .finally(executor = Promise.serverExecutor) { value, cause ->
                if (cause == null) serverThreadFuture.complete(value)
                else serverThreadFuture.completeExceptionally(cause)
            }
        return Promise(serverThreadFuture)
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
                            stand TEXT DEFAULT 'VILLAGER',
                            level INT DEFAULT 1
                        );
                        CREATE INDEX IF NOT EXISTS players_idx_name ON players (name);
                    """.trimIndent())
                }

                fun stmt(sql: String) = conn.prepareStatement(sql)

                stmtListBlocks =
                    stmt("SELECT player, block, time_added, time_used FROM blocks ORDER BY time_used ASC")
                stmtListBlocksByPlayer =
                    stmt("SELECT player, block, time_added, time_used FROM blocks WHERE player = ? ORDER BY time_used ASC")
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
