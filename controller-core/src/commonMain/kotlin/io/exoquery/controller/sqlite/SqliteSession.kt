package io.exoquery.controller.sqlite

abstract class SqliteSession<Session, Stmt>(
  val conn: Session,
  val statementCacheCapacity: Int,
  val createStatement: (String) -> Stmt,
  val resetStatement: (Stmt) -> Unit,
  val finalizeStatement: (Stmt) -> Unit,
  // Use an the default implementation of StatementCache but allow overrides e.g. for Android which has a different LruCache
  override val cache: StatementCache<Stmt> = TerpalLruStatementCache(statementCacheCapacity, createStatement, resetStatement, finalizeStatement)
):
  StatementCachingSession<Session, Stmt>(conn, cache)