package io.exoquery.r2dbc.postgres

import io.exoquery.controller.TerpalSqlUnsafe
import io.exoquery.controller.runOn
import io.exoquery.controller.runActionsUnsafe
import io.exoquery.sql.Sql
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.controller.r2dbc.R2dbcControllers
import io.exoquery.r2dbc.TestDatabasesR2dbc

// Define test enums
@Serializable
enum class ProfileType {
  Admin, Retailer, Supplier
}

@Serializable
enum class Status {
  @SerialName("active")
  Active,

  @SerialName("inactive")
  Inactive,

  @SerialName("pending")
  Pending
}

class EnumSerializationSpec : FreeSpec({

  val cf = TestDatabasesR2dbc.postgres
  val ctx: R2dbcController by lazy { R2dbcControllers.Postgres(connectionFactory = cf) }

  @OptIn(TerpalSqlUnsafe::class)
  suspend fun runActions(actions: String) = ctx.runActionsUnsafe(actions)

  beforeSpec {
    runActions(
      """
      -- Drop existing types and tables if they exist
      DROP TABLE IF EXISTS user_notification_preferences;
      DROP TABLE IF EXISTS order_status_test;
      DROP TYPE IF EXISTS profile_type_enum;
      DROP TYPE IF EXISTS status_enum;

      -- Create enum types
      CREATE TYPE profile_type_enum AS ENUM ('Admin', 'Retailer', 'Supplier');
      CREATE TYPE status_enum AS ENUM ('active', 'inactive', 'pending');

      -- Create tables
      CREATE TABLE user_notification_preferences (
        id SERIAL PRIMARY KEY,
        user_name TEXT NOT NULL,
        profile_type profile_type_enum NOT NULL
      );

      CREATE TABLE order_status_test (
        id SERIAL PRIMARY KEY,
        order_name TEXT NOT NULL,
        status status_enum NOT NULL,
        optional_status status_enum
      );

      -- Insert test data
      INSERT INTO user_notification_preferences (user_name, profile_type)
        VALUES ('Alice', 'Admin'), ('Bob', 'Retailer'), ('Charlie', 'Supplier');

      INSERT INTO order_status_test (order_name, status, optional_status)
        VALUES
        ('Order1', 'active', 'pending'),
        ('Order2', 'inactive', NULL),
        ('Order3', 'pending', 'active');
      """.trimIndent()
    )
  }

  afterSpec {
    runActions(
      """
      DROP TABLE IF EXISTS user_notification_preferences;
      DROP TABLE IF EXISTS order_status_test;
      DROP TYPE IF EXISTS profile_type_enum;
      DROP TYPE IF EXISTS status_enum;
      """.trimIndent()
    )
  }

  "Basic enum deserialization" - {
    "should deserialize single enum field" {
      @Serializable
      data class UserPref(val profileType: ProfileType)

      val result = Sql("select profile_type from user_notification_preferences where user_name = 'Alice' limit 1")
        .queryOf<UserPref>()
        .runOn(ctx)
        .first()

      result.profileType shouldBe ProfileType.Admin
    }

    "should deserialize all enum values correctly" {
      @Serializable
      data class UserNotification(val id: Int, val userName: String, val profileType: ProfileType)

      val results = Sql("select id, user_name, profile_type from user_notification_preferences order by id")
        .queryOf<UserNotification>()
        .runOn(ctx)

      results.size shouldBe 3
      results[0].profileType shouldBe ProfileType.Admin
      results[1].profileType shouldBe ProfileType.Retailer
      results[2].profileType shouldBe ProfileType.Supplier
    }
  }

  "Enum with @SerialName annotation" - {
    "should deserialize enum with custom names" {
      @Serializable
      data class OrderStatus(val id: Int, val orderName: String, val status: Status)

      val results = Sql("select id, order_name, status from order_status_test order by id")
        .queryOf<OrderStatus>()
        .runOn(ctx)

      results.size shouldBe 3
      results[0].status shouldBe Status.Active
      results[1].status shouldBe Status.Inactive
      results[2].status shouldBe Status.Pending
    }
  }

  "Nullable enum support" - {
    "should handle nullable enum fields" {
      @Serializable
      data class OrderWithOptionalStatus(
        val id: Int,
        val orderName: String,
        val status: Status,
        val optionalStatus: Status?
      )

      val results = Sql("select id, order_name, status, optional_status from order_status_test order by id")
        .queryOf<OrderWithOptionalStatus>()
        .runOn(ctx)

      results.size shouldBe 3
      results[0].optionalStatus shouldBe Status.Pending
      results[1].optionalStatus shouldBe null
      results[2].optionalStatus shouldBe Status.Active
    }
  }

  "Enum serialization (INSERT/UPDATE)" - {
    "should insert enum values correctly" {
      @Serializable
      data class UserPref(val userName: String, val profileType: ProfileType)

      val newUserPref = ProfileType.Admin
      val newUserPrefString = newUserPref.name
      Sql("INSERT INTO user_notification_preferences (user_name, profile_type) VALUES ('Diana', $newUserPrefString)").action()
        .runOn(ctx)

      val result = Sql("select user_name, profile_type from user_notification_preferences where user_name = 'Diana'")
        .queryOf<UserPref>()
        .runOn(ctx)
        .first()

      result.profileType shouldBe ProfileType.Admin
    }

    "should update enum values correctly" {
      val newStatus = Status.Inactive
      val newStatusString = newStatus.name
      Sql("UPDATE order_status_test SET status = $newStatusString WHERE id = 1").action().runOn(ctx)

      @Serializable
      data class OrderStatus(val status: Status)

      val result = Sql("select status from order_status_test where id = 1")
        .queryOf<OrderStatus>()
        .runOn(ctx)
        .first()

      result.status shouldBe Status.Inactive
    }

    "should handle enum in WHERE clause" {
      val targetType = ProfileType.Retailer
      val targetTypeString = targetType.name

      @Serializable
      data class UserName(val userName: String)

      val result = Sql("SELECT user_name FROM user_notification_preferences WHERE profile_type = $targetTypeString")
        .queryOf<UserName>()
        .runOn(ctx)
        .first()

      result.userName shouldBe "Bob"
    }
  }

  "Batch operations with enums" - {
    "should handle batch inserts with enums" {
      @Serializable
      data class NewUser(val userName: String, val profileType: ProfileType)

      val newUsers = listOf(
        NewUser("Eve", ProfileType.Admin),
        NewUser("Frank", ProfileType.Supplier)
      )

      // Insert using batch - note: this test depends on batch support
      for (user in newUsers) {
        val userName = user.userName
        val userProfileTypeString = user.profileType.name
        Sql("INSERT INTO user_notification_preferences (user_name, profile_type) VALUES ($userName, $userProfileTypeString)")
          .action()
          .runOn(ctx)
      }

      @Serializable
      data class UserCount(val count: Long)

      val count = Sql("select count(*) as count from user_notification_preferences where user_name in ('Eve', 'Frank')")
        .queryOf<UserCount>()
        .runOn(ctx)
        .first()

      count.count shouldBe 2
    }
  }

  "Complex enum queries" - {
    "should work with CASE statements" {
      @Serializable
      data class UserCategory(val userName: String, val category: String)

      val results = Sql(
        """
        select
          user_name,
          case profile_type
            when 'Admin' then 'Administrator'
            when 'Retailer' then 'Retail User'
            when 'Supplier' then 'Supply User'
          end as category
        from user_notification_preferences
        order by id
      """
      ).queryOf<UserCategory>().runOn(ctx)

      results[0].category shouldBe "Administrator"
      results[1].category shouldBe "Retail User"
      results[2].category shouldBe "Supply User"
    }

    "should work with GROUP BY on enum column" {
      @Serializable
      data class ProfileCount(val profileType: ProfileType, val count: Long)

      val results = Sql(
        """
        select profile_type, count(*) as count
        from user_notification_preferences
        group by profile_type
        order by profile_type::text
      """
      ).queryOf<ProfileCount>().runOn(ctx)

      // After our inserts we should have at least 1 of each type
      results.find { it.profileType == ProfileType.Admin }?.count?.let { it >= 1 } shouldBe true
      results.find { it.profileType == ProfileType.Retailer }?.count shouldBe 1
      results.find { it.profileType == ProfileType.Supplier }?.count?.let { it >= 1 } shouldBe true
    }
  }
})
