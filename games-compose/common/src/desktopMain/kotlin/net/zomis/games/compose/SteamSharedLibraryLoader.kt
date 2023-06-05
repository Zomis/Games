package net.zomis.games.compose

import com.codedisaster.steamworks.SteamException
import com.codedisaster.steamworks.SteamLibraryLoader
import com.codedisaster.steamworks.Version
import java.io.*
import java.util.*

class SteamSharedLibraryLoader : SteamLibraryLoader {
    private val OS: PLATFORM
    private val IS_64_BIT: Boolean
    private val SHARED_LIBRARY_EXTRACT_DIRECTORY = System.getProperty(
        "com.codedisaster.steamworks.SharedLibraryExtractDirectory", "steamworks4j"
    )
    private val SHARED_LIBRARY_EXTRACT_PATH = System.getProperty(
        "com.codedisaster.steamworks.SharedLibraryExtractPath", null
    )
    private val SDK_REDISTRIBUTABLE_BIN_PATH = System.getProperty(
        "com.codedisaster.steamworks.SDKRedistributableBinPath", "sdk/redistributable_bin"
    )
    private val SDK_LIBRARY_PATH = System.getProperty(
        "com.codedisaster.steamworks.SDKLibraryPath", "sdk/public/steam/lib"
    )
    val DEBUG = java.lang.Boolean.parseBoolean(
        System.getProperty(
            "com.codedisaster.steamworks.Debug", "false"
        )
    )

    init {
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")
        OS = if (osName.contains("Windows")) {
            PLATFORM.Windows
        } else if (osName.contains("Linux")) {
            PLATFORM.Linux
        } else if (osName.contains("Mac")) {
            PLATFORM.MacOS
        } else {
            throw RuntimeException("Unknown host architecture: $osName, $osArch")
        }
        IS_64_BIT = osArch == "amd64" || osArch == "x86_64"
    }

    private fun getPlatformLibName(libName: String): String {
        return when (OS) {
            PLATFORM.Windows -> libName + (if (IS_64_BIT) "64" else "") + ".dll"
            PLATFORM.Linux -> "lib$libName.so"
            PLATFORM.MacOS -> "lib$libName.dylib"
        }
        throw RuntimeException("Unknown host architecture")
    }

    val sdkRedistributableBinPath: String?
        get() {
            val path: File
            path = when (OS) {
                PLATFORM.Windows -> File(
                    SDK_REDISTRIBUTABLE_BIN_PATH,
                    if (IS_64_BIT) "win64" else ""
                )

                PLATFORM.Linux -> File(SDK_REDISTRIBUTABLE_BIN_PATH, "linux64")
                PLATFORM.MacOS -> File(SDK_REDISTRIBUTABLE_BIN_PATH, "osx")
                else -> return null
            }
            return if (path.exists()) path.path else null
        }
    val sdkLibraryPath: String?
        get() {
            val path: File
            path = when (OS) {
                PLATFORM.Windows -> File(
                    SDK_LIBRARY_PATH,
                    if (IS_64_BIT) "win64" else "win32"
                )

                PLATFORM.Linux -> File(SDK_LIBRARY_PATH, "linux64")
                PLATFORM.MacOS -> File(SDK_LIBRARY_PATH, "osx")
                else -> return null
            }
            return if (path.exists()) path.path else null
        }

    override fun loadLibrary(libraryName: String): Boolean {
        return loadLibrary(libraryName, null)
    }

    @Throws(SteamException::class)
    fun loadLibrary(libraryName: String, libraryPath: String?): Boolean {
        try {
            val librarySystemName = getPlatformLibName(libraryName)
            var librarySystemPath = discoverExtractLocation(
                SHARED_LIBRARY_EXTRACT_DIRECTORY + "/" + Version.getVersion(), librarySystemName
            )
            if (libraryPath == null) {
                // extract library from resource
                extractLibrary(librarySystemPath, librarySystemName)
            } else {
                // read library from given path
                val librarySourcePath = File(libraryPath, librarySystemName)
                if (OS != PLATFORM.Windows) {
                    // on MacOS & Linux, "extract" (copy) from source location
                    extractLibrary(librarySystemPath, librarySourcePath)
                } else {
                    // on Windows, load the library from the source location
                    librarySystemPath = librarySourcePath
                }
            }
            val absolutePath = librarySystemPath.canonicalPath
            System.load(absolutePath)
            return true
        } catch (e: IOException) {
            throw SteamException(e)
        }
    }

    @Throws(IOException::class)
    private fun extractLibrary(librarySystemPath: File, librarySystemName: String) {
        extractLibrary(
            librarySystemPath,
            SteamSharedLibraryLoader::class.java.getResourceAsStream("/$librarySystemName")
        )
    }

    @Throws(IOException::class)
    private fun extractLibrary(librarySystemPath: File, librarySourcePath: File) {
        extractLibrary(librarySystemPath, FileInputStream(librarySourcePath))
    }

    @Throws(IOException::class)
    private fun extractLibrary(librarySystemPath: File, input: InputStream?) {
        if (input != null) {
            try {
                FileOutputStream(librarySystemPath).use { output ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val length = input.read(buffer)
                        if (length == -1) break
                        output.write(buffer, 0, length)
                    }
                    output.close()
                }
            } catch (e: IOException) {
                /*
					Extracting the library may fail, for example because 'nativeFile' already exists and is in
					use by another process. In this case, we fail silently and just try to load the existing file.
				 */
                if (!librarySystemPath.exists()) {
                    throw e
                }
            } finally {
                input.close()
            }
        } else {
            throw IOException("Failed to read input stream for " + librarySystemPath.canonicalPath)
        }
    }

    @Throws(IOException::class)
    private fun discoverExtractLocation(folderName: String, fileName: String): File {
        var path: File?

        // system property
        if (SHARED_LIBRARY_EXTRACT_PATH != null) {
            path = File(SHARED_LIBRARY_EXTRACT_PATH, fileName)
            if (canWrite(path)) {
                return path
            }
        }

        // Java tmpdir
        path = File(System.getProperty("java.io.tmpdir") + "/" + folderName, fileName)
        if (canWrite(path)) {
            return path
        }

        // NIO temp file
        try {
            val file = File.createTempFile(folderName, null)
            if (file.delete()) {
                // uses temp file path as destination folder
                path = File(file, fileName)
                if (canWrite(path)) {
                    return path
                }
            }
        } catch (ignored: IOException) {
        }

        // user home
        path = File(System.getProperty("user.home") + "/." + folderName, fileName)
        if (canWrite(path)) {
            return path
        }

        // working directory
        path = File(".tmp/$folderName", fileName)
        if (canWrite(path)) {
            return path
        }
        throw IOException("No suitable extraction path found")
    }

    private fun canWrite(file: File): Boolean {
        val folder = file.parentFile
        if (file.exists()) {
            if (!file.canWrite() || !canExecute(file)) {
                return false
            }
        } else {
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    return false
                }
            }
            if (!folder.isDirectory) {
                return false
            }
        }
        val testFile = File(folder, UUID.randomUUID().toString())
        return try {
            FileOutputStream(testFile).close()
            canExecute(testFile)
        } catch (e: IOException) {
            false
        } finally {
            testFile.delete()
        }
    }

    private fun canExecute(file: File): Boolean {
        try {
            if (file.canExecute()) {
                return true
            }
            if (file.setExecutable(true)) {
                return file.canExecute()
            }
        } catch (ignored: Exception) {
        }
        return false
    }

    internal enum class PLATFORM {
        Windows, Linux, MacOS
    }
}