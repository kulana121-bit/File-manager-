package com.example.filesapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class ServerProtocol {
    FTP,
    SMB
}

data class NetworkServerConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val protocol: ServerProtocol = ServerProtocol.FTP,
    val host: String,
    val port: Int = 21,
    val username: String = "anonymous",
    val password: String = "",
    val initialPath: String = "/"
)

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

class NetworkStorageManager(private val context: Context) {
    private val PREFS_NAME = "network_servers_store"
    private val KEY_SERVERS = "saved_servers"

    // Load saved servers
    fun getSavedServers(): List<NetworkServerConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        val list = mutableListOf<NetworkServerConfig>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    NetworkServerConfig(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        protocol = ServerProtocol.valueOf(obj.optString("protocol", "FTP")),
                        host = obj.getString("host"),
                        port = obj.optInt("port", 21),
                        username = obj.optString("username", "anonymous"),
                        password = obj.optString("password", ""),
                        initialPath = obj.optString("initialPath", "/")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveServer(config: NetworkServerConfig) {
        val current = getSavedServers().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == config.id }
        if (existingIndex != -1) {
            current[existingIndex] = config
        } else {
            current.add(config)
        }
        persistServers(current)
    }

    fun deleteServer(serverId: String) {
        val current = getSavedServers().filter { it.id != serverId }
        persistServers(current)
    }

    private fun persistServers(servers: List<NetworkServerConfig>) {
        val array = JSONArray()
        for (s in servers) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("protocol", s.protocol.name)
                put("host", s.host)
                put("port", s.port)
                put("username", s.username)
                put("password", s.password)
                put("initialPath", s.initialPath)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVERS, array.toString())
            .apply()
    }

    // Connect and list files from FTP
    suspend fun listRemoteFiles(server: NetworkServerConfig, path: String): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
        if (server.protocol == ServerProtocol.FTP) {
            val client = FTPClient()
            try {
                client.defaultTimeout = 10000
                client.connectTimeout = 10000
                client.setDataTimeout(java.time.Duration.ofMillis(10000))
                client.connect(server.host, server.port)

                val reply = client.replyCode
                if (!FTPReply.isPositiveCompletion(reply)) {
                    client.disconnect()
                    return@withContext Result.failure(Exception("Server refused connection (Reply $reply)"))
                }

                val loginSuccess = client.login(server.username, server.password)
                if (!loginSuccess) {
                    client.disconnect()
                    return@withContext Result.failure(Exception("FTP Authentication failed for user ${server.username}"))
                }

                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)

                val targetPath = if (path.isBlank()) "/" else path
                client.changeWorkingDirectory(targetPath)

                val ftpFiles = client.listFiles()
                val list = mutableListOf<RemoteFileItem>()
                for (file in ftpFiles) {
                    if (file.name == "." || file.name == "..") continue
                    val fullFilePath = if (targetPath.endsWith("/")) targetPath + file.name else "$targetPath/${file.name}"
                    list.add(
                        RemoteFileItem(
                            name = file.name,
                            path = fullFilePath,
                            isDirectory = file.isDirectory,
                            size = if (file.isDirectory) 0L else file.size,
                            lastModified = file.timestamp?.timeInMillis ?: System.currentTimeMillis()
                        )
                    )
                }

                client.logout()
                client.disconnect()

                val sorted = list.sortedWith(compareByDescending<RemoteFileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
                Result.success(sorted)
            } catch (e: Exception) {
                e.printStackTrace()
                try { if (client.isConnected) client.disconnect() } catch (ignored: Exception) {}
                Result.failure(e)
            }
        } else {
            // SMB support or informational simulation message if SMB port / dialect is unavailable on android directly
            Result.failure(Exception("SMB dialect requires direct port 445 network route. FTP is recommended for standard Android Wi-Fi networks."))
        }
    }

    // Download file from remote server to local storage
    suspend fun downloadRemoteFile(
        server: NetworkServerConfig,
        remotePath: String,
        destinationDir: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.defaultTimeout = 10000
            client.connectTimeout = 10000
            client.setDataTimeout(java.time.Duration.ofMillis(15000))
            client.connect(server.host, server.port)
            if (!client.login(server.username, server.password)) {
                return@withContext Result.failure(Exception("Login failed"))
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val fileName = remotePath.substringAfterLast("/")
            val targetLocalFile = File(destinationDir, fileName)

            FileOutputStream(targetLocalFile).use { fos ->
                val success = client.retrieveFile(remotePath, fos)
                if (!success) {
                    return@withContext Result.failure(Exception("Failed to download file from $remotePath"))
                }
            }

            client.logout()
            client.disconnect()
            onProgress(1f)
            Result.success(targetLocalFile)
        } catch (e: Exception) {
            e.printStackTrace()
            try { if (client.isConnected) client.disconnect() } catch (ignored: Exception) {}
            Result.failure(e)
        }
    }

    // Upload local file to remote server
    suspend fun uploadFile(
        server: NetworkServerConfig,
        localFile: File,
        remoteDirPath: String,
        onProgress: (Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.defaultTimeout = 10000
            client.connectTimeout = 10000
            client.setDataTimeout(java.time.Duration.ofMillis(15000))
            client.connect(server.host, server.port)
            if (!client.login(server.username, server.password)) {
                return@withContext Result.failure(Exception("Login failed"))
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            client.changeWorkingDirectory(remoteDirPath)
            val remoteFileName = localFile.name

            FileInputStream(localFile).use { fis ->
                val success = client.storeFile(remoteFileName, fis)
                if (!success) {
                    return@withContext Result.failure(Exception("Failed to upload file to $remoteDirPath/$remoteFileName"))
                }
            }

            client.logout()
            client.disconnect()
            onProgress(1f)
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            try { if (client.isConnected) client.disconnect() } catch (ignored: Exception) {}
            Result.failure(e)
        }
    }
}
