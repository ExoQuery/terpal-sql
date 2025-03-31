package io.exoquery.sql.sqlite

/**
 * Since on mobile platforms creating SQL PreparedStatements is expensive, we cache them.
 * This is a base-class for a cached-session type to be used with the DoublePool.
 * This works by wrapping a session and a cache to check/create statements from.
 * (Note that the actual aquisition of new statements is done within in the StatementCache
 * implementors since they are the ones that will deal with a real Stmt type that actually have
 * session-creation semantics). See TerpalLruStatementCache and SqliteSession
 * for examples of how to use this.
 */
open class StatementCachingSession<Session, Stmt>(open val session: Session, open val cache: StatementCache<Stmt>) {
  open fun createStatement(sql: String): Stmt = cache.getOrCreate(sql)
}

interface StatementCache<Stmt> {
  fun getOrCreate(sql: String): Stmt
}
