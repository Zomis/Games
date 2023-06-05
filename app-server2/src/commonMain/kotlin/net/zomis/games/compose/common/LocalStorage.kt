package net.zomis.games.compose.common

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.reflect.KClass

interface LocalStorage {
    suspend fun save(key: String, value: Any)
    suspend fun <T: Any> load(key: String, type: KClass<T>): T
    suspend fun <T: Any> loadOptional(key: String, type: KClass<T>): T?
}

class FileLocalStorage(private val directory: Path) : LocalStorage {
    private val mapper = jacksonObjectMapper()
    init {
        directory.createDirectories()
    }

    override suspend fun save(key: String, value: Any) {
        mapper.writeValue(directory.resolve(key).toFile(), value)
    }

    override suspend fun <T: Any> load(key: String, type: KClass<T>): T {
        return mapper.readValue(directory.resolve(key).toFile(), type.java)
    }

    override suspend fun <T: Any> loadOptional(key: String, type: KClass<T>): T? {
        return try {
            mapper.readValue(directory.resolve(key).toFile(), type.java)
        } catch (e: Exception) {
            null
        }
    }
}
