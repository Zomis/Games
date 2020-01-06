package net.zomis.games.server2.db

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.*
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec
import com.amazonaws.services.dynamodbv2.model.*
import klog.KLoggers
import net.zomis.core.events.EventSystem
import java.time.Instant

fun timeStamp(): AttributeValue {
    return AttributeValue().withN(Instant.now().epochSecond.toString())
}

class DBIntegration {

    private val logger = KLoggers.logger(this)

    val dynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withRegion(Regions.EU_CENTRAL_1)
        .build()

    fun register(events: EventSystem) {
        val tables = listOf<CreateTableRequest>()
            .plus(AuthTable(dynamoDB).register(events))
            .plus(GamesTables(dynamoDB).register(events))
        createTables(dynamoDB, tables)
    }

    private fun createTables(ddb: AmazonDynamoDB, requests: List<CreateTableRequest>) {
        val exists = ddb.listTables()
        logger.info("Existing tables: " + exists.tableNames)
        val existingTables = exists.tableNames.toSet()

        requests.filter { !existingTables.contains(it.tableName) }.forEach {
            val result = ddb.createTable(it)
            println("Creating ${it.tableName}: " + result)
        }
    }

    fun loadGame(gameId: String, gameSpecs: Map<String, Any>): DBGame? {
        return GamesTables(dynamoDB).fetchGame(gameId, gameSpecs)
    }

}

data class MyIndex(
    private val table: MyTable,
    val indexName: String,
    val fields: List<Pair<String, KeyType>>,
    val projectionType: ProjectionType,
    val provisionedThroughput: ProvisionedThroughput = ProvisionedThroughput(1L, 1L)
) {
    fun query(hashKey: Pair<String, Any>, sortKey: Pair<String, Any>? = null): ItemCollection<QueryOutcome> {
        val dt = table.table
        val query = QuerySpec().withHashKey(hashKey.first, hashKey.second).also {
            if (sortKey != null) {
                it.withRangeKeyCondition(RangeKeyCondition(sortKey.first).eq(sortKey.second))
            }
        }
        return dt.getIndex(indexName).query(query)
    }

    fun toGSI(): GlobalSecondaryIndex {
        return GlobalSecondaryIndex()
                .withIndexName(indexName)
                .withProjection(Projection().withProjectionType(projectionType))
                .withProvisionedThroughput(provisionedThroughput)
                .withKeySchema(fields.map {key -> KeySchemaElement(key.first, key.second) })
    }
}

data class MyPrimaryIndex(
    private val table: MyTable,
    val hashKey: String,
    val sortKey: String? = null
) {

    fun keySchema(): List<KeySchemaElement> {
        val result = listOf(KeySchemaElement(hashKey, KeyType.HASH))
        return if (sortKey != null) {
            result.plus(KeySchemaElement(sortKey, KeyType.RANGE))
        } else {
            result
        }
    }

}

class MyTable(dynamoDB: AmazonDynamoDB, val tableName: String) {
    private val indices = mutableListOf<MyIndex>()
    private val attributeDefinitions = mutableListOf<AttributeDefinition>()
    private lateinit var primaryIndex: MyPrimaryIndex
    val table = DynamoDB(dynamoDB).getTable(tableName)!!

    fun strings(vararg fieldNames: String): MyTable = this.fields(ScalarAttributeType.S, fieldNames)
    fun bools(vararg fieldNames: String): MyTable = this.fields(ScalarAttributeType.B, fieldNames)
    fun numbers(vararg fieldNames: String): MyTable = this.fields(ScalarAttributeType.N, fieldNames)
    private fun fields(type: ScalarAttributeType, fieldNames: Array<out String>): MyTable {
        fieldNames.forEach {
            attributeDefinitions.add(AttributeDefinition(it, type))
        }
        return this
    }

    fun primaryIndex(hashKey: String, sortKey: String? = null): MyPrimaryIndex {
        val index = MyPrimaryIndex(this, hashKey, sortKey)
        primaryIndex = index
        return primaryIndex
    }

    fun index(projection: ProjectionType, hashKeys: List<String>, sortKeys: List<String>): MyIndex {
        val fields = hashKeys.map { it to KeyType.HASH }.plus(sortKeys.map { it to KeyType.RANGE })
        val index = MyIndex(this, fields.joinToString("_") { it.first } + "_Index", fields, projection)
        this.indices.add(index)
        return index
    }

    fun createTableRequest(): CreateTableRequest {
        return CreateTableRequest()
            .withTableName(tableName)
            .withAttributeDefinitions(attributeDefinitions)
            .withKeySchema(this.primaryIndex.keySchema())
            .withBillingMode(BillingMode.PROVISIONED)
            .withProvisionedThroughput(ProvisionedThroughput(1L, 1L))
            .let {
                return@let if (indices.isNotEmpty()) {
                    it.withGlobalSecondaryIndexes(indices.map {index -> index.toGSI() })
                } else { it }
            }
    }

}
