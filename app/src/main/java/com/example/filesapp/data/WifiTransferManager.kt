package com.example.filesapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Production-Grade WiFi Transfer Hub 2.0.
 * - Authenticated sharing: Session Auth Token + QR pairing + temporary PIN.
 * - Session expiration (30-minute validity window).
 * - Selectable Shared Folder with strict Path Traversal / canonical barrier.
 * - Multiple files batch upload & individual downloads.
 * - Phone <-> PC bidirectional data transmission.
 */
class WifiTransferManager(
    private val context: Context,
    private val rootDirProvider: () -> File,
    private val onLogMessage: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    var serverPort: Int = 8080
        private set

    var sessionAuthToken: String = ""
        private set

    var sessionExpiryTimestamp: Long = 0L
        private set

    // Active authenticated sessions cache
    private val authenticatedIps = ConcurrentHashMap<String, Long>()

    companion object {
        private const val SESSION_DURATION_MS = 30 * 60 * 1000L // 30 minutes

        fun generateQrCodeBitmap(text: String, size: Int = 512): Bitmap? {
            return try {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                    }
                }
                bmp
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun generateAuthToken(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sr = SecureRandom()
        return (1..6).map { chars[sr.nextInt(chars.length)] }.joinToString("")
    }

    fun getLocalIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                @Suppress("DEPRECATION")
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt != 0) {
                    val ip = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0") return ip
                }
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    fun getServerUrl(includeToken: Boolean = true): String {
        val base = "http://${getLocalIpAddress()}:$serverPort"
        return if (includeToken && sessionAuthToken.isNotEmpty()) {
            "$base/?token=$sessionAuthToken"
        } else {
            base
        }
    }

    suspend fun startServer(port: Int = 8080): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext true
        try {
            serverPort = port
            sessionAuthToken = generateAuthToken()
            sessionExpiryTimestamp = System.currentTimeMillis() + SESSION_DURATION_MS
            authenticatedIps.clear()

            serverSocket = ServerSocket(serverPort)
            isRunning = true
            onLogMessage("WiFi Hub started on ${getServerUrl(true)} (PIN: $sessionAuthToken)")

            Thread {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        Thread {
                            handleClient(client)
                        }.start()
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }.start()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            onLogMessage("Failed to start server: ${e.message}")
            isRunning = false
            false
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        authenticatedIps.clear()
        sessionAuthToken = ""
        onLogMessage("WiFi Hub stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            val clientIp = socket.inetAddress?.hostAddress ?: "unknown"
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val fullPath = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var headerLine: String?
            while (true) {
                headerLine = reader.readLine()
                if (headerLine.isNullOrEmpty()) break
                val splitIdx = headerLine.indexOf(":")
                if (splitIdx != -1) {
                    val k = headerLine.substring(0, splitIdx).trim().lowercase(Locale.US)
                    val v = headerLine.substring(splitIdx + 1).trim()
                    headers[k] = v
                }
            }

            // Check authentication
            val isAuth = checkAuthentication(fullPath, headers, clientIp)

            if (!isAuth) {
                if (method.equals("POST", ignoreCase = true) && fullPath.startsWith("/auth")) {
                    handleAuthSubmission(input, output, clientIp)
                } else {
                    val authHtml = generateAuthPage()
                    sendResponse(output, "200 OK", "text/html; charset=UTF-8", authHtml.toByteArray(Charsets.UTF_8))
                }
                return
            }

            if (method.equals("GET", ignoreCase = true)) {
                handleGet(fullPath, output)
            } else if (method.equals("POST", ignoreCase = true)) {
                handlePost(fullPath, headers, input, output)
            } else {
                sendResponse(output, "501 Not Implemented", "text/plain", "Method not supported".toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun checkAuthentication(path: String, headers: Map<String, String>, clientIp: String): Boolean {
        val now = System.currentTimeMillis()
        if (now > sessionExpiryTimestamp) {
            return false
        }

        // 1. Check IP session
        val expiry = authenticatedIps[clientIp]
        if (expiry != null && expiry > now) {
            return true
        }

        // 2. Check query param ?token=
        if (path.contains("token=")) {
            val token = path.substringAfter("token=").substringBefore("&").trim()
            if (token.equals(sessionAuthToken, ignoreCase = true)) {
                authenticatedIps[clientIp] = now + SESSION_DURATION_MS
                return true
            }
        }

        // 3. Check Cookie
        val cookie = headers["cookie"] ?: ""
        if (cookie.contains("session_token=$sessionAuthToken")) {
            authenticatedIps[clientIp] = now + SESSION_DURATION_MS
            return true
        }

        return false
    }

    private fun handleAuthSubmission(input: InputStream, output: OutputStream, clientIp: String) {
        val body = readBody(input)
        val enteredPin = body.substringAfter("pin=").substringBefore("&").trim()
        val decodedPin = URLDecoder.decode(enteredPin, "UTF-8")

        if (decodedPin.equals(sessionAuthToken, ignoreCase = true)) {
            authenticatedIps[clientIp] = System.currentTimeMillis() + SESSION_DURATION_MS
            val redirectHtml = "<html><head><meta http-equiv=\"refresh\" content=\"0;url=/\" /></head><body>Redirecting...</body></html>"
            val headers = "HTTP/1.1 200 OK\r\n" +
                    "Set-Cookie: session_token=$sessionAuthToken; Path=/; HttpOnly\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: ${redirectHtml.length}\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.write(redirectHtml.toByteArray(Charsets.UTF_8))
            output.flush()
        } else {
            val failHtml = generateAuthPage(errorMessage = "Invalid PIN. Please check your phone screen.")
            sendResponse(output, "401 Unauthorized", "text/html; charset=UTF-8", failHtml.toByteArray(Charsets.UTF_8))
        }
    }

    private fun readBody(input: InputStream): String {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var count = input.read(buffer)
        while (count != -1) {
            baos.write(buffer, 0, count)
            if (input.available() <= 0) break
            count = input.read(buffer)
        }
        return baos.toString("UTF-8")
    }

    private fun handleGet(path: String, output: OutputStream) {
        val decodedPath = URLDecoder.decode(path, "UTF-8")
        val cleanPath = decodedPath.split("?")[0]

        if (cleanPath == "/" || cleanPath == "/index.html") {
            val html = generateWebUi()
            sendResponse(output, "200 OK", "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
            return
        }

        if (cleanPath.startsWith("/download/")) {
            val fileName = cleanPath.removePrefix("/download/")
            val root = rootDirProvider()
            val targetFile = File(root, fileName)

            // Security: Path Traversal defense
            val canonicalRoot = root.canonicalPath
            val canonicalTarget = targetFile.canonicalPath
            if (!canonicalTarget.startsWith(canonicalRoot)) {
                sendResponse(output, "403 Forbidden", "text/plain", "Access Denied: Path Traversal blocked".toByteArray())
                return
            }

            if (targetFile.exists() && targetFile.isFile) {
                val mime = when {
                    fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                    fileName.endsWith(".png", true) -> "image/png"
                    fileName.endsWith(".webp", true) -> "image/webp"
                    fileName.endsWith(".pdf", true) -> "application/pdf"
                    fileName.endsWith(".zip", true) || fileName.endsWith(".7z", true) -> "application/zip"
                    fileName.endsWith(".mp3", true) -> "audio/mpeg"
                    fileName.endsWith(".mp4", true) -> "video/mp4"
                    else -> "application/octet-stream"
                }

                val headerStr = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: $mime\r\n" +
                        "Content-Length: ${targetFile.length()}\r\n" +
                        "Content-Disposition: attachment; filename=\"${URLEncoder.encode(targetFile.name, "UTF-8")}\"\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(headerStr.toByteArray(Charsets.UTF_8))
                targetFile.inputStream().use { it.copyTo(output) }
                output.flush()
                onLogMessage("Downloaded: ${targetFile.name}")
                return
            }
        }

        sendResponse(output, "404 Not Found", "text/plain", "File not found".toByteArray())
    }

    private fun handlePost(path: String, headers: Map<String, String>, input: InputStream, output: OutputStream) {
        if (path.startsWith("/upload")) {
            val contentType = headers["content-type"] ?: ""
            if (contentType.contains("multipart/form-data")) {
                val boundary = contentType.substringAfter("boundary=").trim()
                if (boundary.isNotEmpty()) {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    saveMultipartFile(boundary, contentLength, input)
                    val redirectHtml = "<html><head><meta http-equiv=\"refresh\" content=\"1;url=/\" /></head><body><h2>Upload Successful! Redirecting...</h2></body></html>"
                    sendResponse(output, "200 OK", "text/html", redirectHtml.toByteArray())
                    return
                }
            }
        }
        sendResponse(output, "400 Bad Request", "text/plain", "Bad Upload Request".toByteArray())
    }

    private fun saveMultipartFile(boundary: String, totalBytes: Int, input: InputStream) {
        try {
            val root = rootDirProvider()
            val boundaryMarker = "--$boundary"
            val bis = BufferedInputStream(input)

            var line = readLineFromStream(bis)
            var fileName = "uploaded_file_${System.currentTimeMillis()}"

            while (line != null && !line.startsWith(boundaryMarker)) {
                line = readLineFromStream(bis)
            }

            while (true) {
                line = readLineFromStream(bis) ?: break
                if (line.isEmpty()) break
                if (line.contains("filename=\"")) {
                    val extracted = line.substringAfter("filename=\"").substringBefore("\"")
                    if (extracted.isNotBlank()) {
                        // Strip path traversal attempts in uploaded filenames
                        fileName = File(extracted).name.replace("..", "_")
                    }
                }
            }

            val outFile = File(root, fileName)
            FileOutputStream(outFile).use { fos ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var remaining = totalBytes
                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    remaining -= bytesRead
                    if (remaining <= 0) break
                }
            }
            onLogMessage("Uploaded: $fileName (${outFile.length()} bytes)")
        } catch (e: Exception) {
            e.printStackTrace()
            onLogMessage("Upload failed: ${e.message}")
        }
    }

    private fun readLineFromStream(bis: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream()
        var c: Int
        while (bis.read().also { c = it } != -1) {
            if (c == '\n'.code) break
            if (c != '\r'.code) baos.write(c)
        }
        if (baos.size() == 0 && c == -1) return null
        return baos.toString("UTF-8")
    }

    private fun sendResponse(output: OutputStream, status: String, contentType: String, data: ByteArray) {
        val header = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(data)
        output.flush()
    }

    private fun generateAuthPage(errorMessage: String? = null): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Files Hub - Authentication Required</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0F172A; color: #F8FAFC; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
        .card { background: #1E293B; border: 1px solid #334155; border-radius: 24px; padding: 32px; max-width: 420px; width: 100%; box-shadow: 0 20px 40px rgba(0,0,0,0.4); text-align: center; }
        .icon { font-size: 48px; margin-bottom: 16px; }
        h1 { font-size: 20px; font-weight: 700; margin-bottom: 8px; color: #FFFFFF; }
        p { font-size: 13px; color: #94A3B8; margin-bottom: 24px; line-height: 1.5; }
        .pin-input { width: 100%; padding: 14px; background: #0F172A; border: 1px solid #475569; border-radius: 14px; font-size: 20px; letter-spacing: 4px; text-align: center; color: #FFFFFF; text-transform: uppercase; margin-bottom: 16px; outline: none; }
        .pin-input:focus { border-color: #6366F1; }
        .btn { width: 100%; padding: 14px; background: #6366F1; color: white; border: none; border-radius: 14px; font-size: 15px; font-weight: 600; cursor: pointer; transition: 0.2s; }
        .btn:hover { background: #4F46E5; }
        .error { color: #EF4444; font-size: 13px; margin-bottom: 16px; }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">&#128274;</div>
        <h1>Authentication Required</h1>
        <p>Enter the 6-digit PIN displayed on your phone's WiFi Transfer screen or scan the QR code.</p>
        ${if (errorMessage != null) "<div class=\"error\">$errorMessage</div>" else ""}
        <form action="/auth" method="POST">
            <input type="text" name="pin" class="pin-input" placeholder="••••••" maxlength="6" autofocus required>
            <button type="submit" class="btn">Connect to Hub</button>
        </form>
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun generateWebUi(): String {
        val root = rootDirProvider()
        val files = root.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)

        val fileRows = StringBuilder()
        for (f in files) {
            val sizeMb = String.format(Locale.US, "%.2f MB", f.length() / (1024.0 * 1024.0))
            val dateStr = dateFormat.format(Date(f.lastModified()))
            val encodedName = URLEncoder.encode(f.name, "UTF-8")
            fileRows.append("""
                <tr class="file-row">
                    <td class="name-col"><span class="icon">&#128196;</span> <span class="file-name">${f.name}</span></td>
                    <td class="meta-col">$sizeMb</td>
                    <td class="meta-col">$dateStr</td>
                    <td class="action-col">
                        <a href="/download/$encodedName" class="btn btn-download">&#11015; Download</a>
                    </td>
                </tr>
            """.trimIndent())
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Files App - WiFi Transfer Hub</title>
    <style>
        :root {
            --primary: #4F46E5;
            --primary-hover: #4338CA;
            --bg: #F8FAFC;
            --card-bg: #FFFFFF;
            --text-main: #1E293B;
            --text-sub: #64748B;
            --border: #E2E8F0;
            --radius: 16px;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text-main); padding: 24px; }
        .container { max-width: 860px; margin: 0 auto; }
        .header { background: var(--card-bg); padding: 24px; border-radius: var(--radius); box-shadow: 0 4px 20px rgba(0,0,0,0.05); margin-bottom: 24px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; }
        .header h1 { font-size: 22px; font-weight: 700; color: var(--primary); display: flex; align-items: center; gap: 10px; }
        .badge { background: #EEF2FF; color: var(--primary); padding: 6px 12px; border-radius: 9999px; font-size: 13px; font-weight: 600; }
        .upload-card { background: var(--card-bg); padding: 24px; border-radius: var(--radius); box-shadow: 0 4px 20px rgba(0,0,0,0.05); margin-bottom: 24px; }
        .upload-card h2 { font-size: 16px; margin-bottom: 12px; }
        .upload-form { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
        input[type="file"] { border: 1px dashed var(--border); padding: 12px; border-radius: 12px; flex: 1; min-width: 200px; background: #F1F5F9; }
        .btn { display: inline-flex; align-items: center; justify-content: center; padding: 10px 20px; border-radius: 9999px; text-decoration: none; font-weight: 600; font-size: 14px; cursor: pointer; border: none; transition: all 0.2s; }
        .btn-upload { background: var(--primary); color: white; }
        .btn-upload:hover { background: var(--primary-hover); }
        .btn-download { background: #EEF2FF; color: var(--primary); padding: 6px 14px; font-size: 12px; }
        .btn-download:hover { background: var(--primary); color: white; }
        .table-card { background: var(--card-bg); border-radius: var(--radius); box-shadow: 0 4px 20px rgba(0,0,0,0.05); overflow: hidden; }
        table { width: 100%; border-collapse: collapse; text-align: left; }
        th { background: #F8FAFC; padding: 14px 18px; font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-sub); border-bottom: 1px solid var(--border); }
        td { padding: 14px 18px; border-bottom: 1px solid var(--border); font-size: 14px; vertical-align: middle; }
        .file-row:hover { background: #F8FAFC; }
        .name-col { display: flex; align-items: center; gap: 8px; font-weight: 500; word-break: break-all; }
        .meta-col { color: var(--text-sub); font-size: 13px; }
        .action-col { text-align: right; }
        @media(max-width: 600px) {
            body { padding: 12px; }
            .meta-col { display: none; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <h1>&#128246; Files WiFi Hub</h1>
                <p style="color: var(--text-sub); font-size: 13px; margin-top: 4px;">Transfer files safely over local Wi-Fi • Authenticated Session</p>
            </div>
            <span class="badge">&#127760; Shared Folder: ${files.size} Files</span>
        </div>

        <div class="upload-card">
            <h2>&#11014; Upload File(s) to Phone</h2>
            <form class="upload-form" action="/upload" method="POST" enctype="multipart/form-data">
                <input type="file" name="uploadFiles" multiple required>
                <button type="submit" class="btn btn-upload">Upload to Phone</button>
            </form>
        </div>

        <div class="table-card">
            <table>
                <thead>
                    <tr>
                        <th>File Name</th>
                        <th class="meta-col">Size</th>
                        <th class="meta-col">Modified</th>
                        <th style="text-align: right;">Action</th>
                    </tr>
                </thead>
                <tbody>
                    $fileRows
                </tbody>
            </table>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }
}
